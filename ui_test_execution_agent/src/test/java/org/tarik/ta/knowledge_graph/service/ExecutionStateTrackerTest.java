/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.knowledge_graph.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ExecutionStateTrackerTest {

    private ExecutionStateTracker stateTracker;

    @BeforeEach
    void setUp() {
        UiTestAgentConfig config = mock(UiTestAgentConfig.class);
        org.mockito.Mockito.when(config.getAncestryWindowSize()).thenReturn(5);
        stateTracker = new ExecutionStateTracker(config);
    }

    private static PhraseEmbedding pe() {
        return new PhraseEmbedding(UUID.randomUUID(), "test effect", new float[0], PhraseType.EFFECT);
    }

    private static Procedure compositeProc() {
        return Procedure.createComposite("composite", List.of(), "", List.of(), List.of(), false);
    }

    @Test
    void directAtomicAtRootLevel_effectsPersistedForever() {
        PhraseEmbedding effect1 = pe();
        PhraseEmbedding effect2 = pe();
        stateTracker.addEffects(List.of(effect1, effect2));

        Set<UUID> ids = stateTracker.getEffectNodeIds();
        assertThat(ids).containsExactlyInAnyOrder(effect1.id(), effect2.id());
    }

    @Test
    void enterCompositeScope_addEffects_closeCompositeScope() {
        PhraseEmbedding atomicEffect = pe();
        PhraseEmbedding compositeEffect = pe();

        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(atomicEffect));

        // Atomic effect is in the child frame — visible because all open frames are included.
        assertThat(stateTracker.getEffectNodeIds()).containsExactly(atomicEffect.id());

        stateTracker.closeCompositeScope(List.of(compositeEffect));

        // After closing, atomic effect is gone, composite effect promoted to root
        assertThat(stateTracker.getEffectNodeIds()).containsExactly(compositeEffect.id());
    }

    @Test
    void getEffectNodeIds_returnsAllOpenScopeFrames() {
        PhraseEmbedding rootEffect = pe();
        PhraseEmbedding aEffect = pe();
        PhraseEmbedding bEffect = pe();

        // Root
        stateTracker.addEffects(List.of(rootEffect));

        // Frame A
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(aEffect));

        // Frame B
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(bEffect));

        // All open frames (root, A, B) must be visible
        assertThat(stateTracker.getEffectNodeIds()).containsExactlyInAnyOrder(rootEffect.id(), aEffect.id(), bEffect.id());
    }

    @Test
    void abandonCompositeScope_discardsAccumulatedEffects() {
        PhraseEmbedding rootEffect = pe();
        PhraseEmbedding abandonedEffect = pe();

        stateTracker.addEffects(List.of(rootEffect));

        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(abandonedEffect));
        assertThat(stateTracker.getEffectNodeIds()).containsExactlyInAnyOrder(rootEffect.id(), abandonedEffect.id());

        stateTracker.abandonCompositeScope();

        // Abandoned effects should be gone
        assertThat(stateTracker.getEffectNodeIds()).containsExactly(rootEffect.id());
    }

    @Test
    void reset_clearsAllScopeFrames() {
        stateTracker.addEffects(List.of(pe()));
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(pe()));

        assertThat(stateTracker.getEffectNodeIds()).hasSize(2);

        stateTracker.reset();

        assertThat(stateTracker.getEffectNodeIds()).isEmpty();
    }

    @Test
    void addExecutedAtomicProcedure_shouldRecordProceduresInOrder() {
        var p1 = Procedure.createAtomic("step 1", List.of(), "", List.of(), List.of(), false);
        var p2 = Procedure.createAtomic("step 2", List.of(), "", List.of(), List.of(), false);

        stateTracker.addExecutedAtomicProcedure(p1);
        stateTracker.addExecutedAtomicProcedure(p2);

        assertThat(stateTracker.getExecutedAtomicProcedures()).containsExactly(p1, p2);
    }

    @Test
    void addExecutedAtomicProcedure_shouldThrowOnNull() {
        assertThatThrownBy(() -> stateTracker.addExecutedAtomicProcedure(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getExecutedAtomicProcedures_shouldReturnUnmodifiableCopy() {
        var p1 = Procedure.createAtomic("step 1", List.of(), "", List.of(), List.of(), false);
        stateTracker.addExecutedAtomicProcedure(p1);

        var snapshot = stateTracker.getExecutedAtomicProcedures();

        assertThatThrownBy(() -> snapshot.add(Procedure.createAtomic("x", List.of(), "", List.of(), List.of(), false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reset_shouldClearExecutedAtomicProcedures() {
        stateTracker.addExecutedAtomicProcedure(Procedure.createAtomic("step", List.of(), "", List.of(), List.of(), false));
        assertThat(stateTracker.getExecutedAtomicProcedures()).hasSize(1);

        stateTracker.reset();

        assertThat(stateTracker.getExecutedAtomicProcedures()).isEmpty();
    }

    @Test
    void addRecentParent_shouldAddIdToDeque() {
        UUID id = UUID.randomUUID();
        stateTracker.addRecentParent(id);
        assertThat(stateTracker.getRecentParentIds()).containsExactly(id);
    }

    @Test
    void addRecentParent_shouldEvictOldestWhenOverLimit() {
        // Ancestry window size is 5 by default
        List<UUID> ids = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );

        for (UUID id : ids) {
            stateTracker.addRecentParent(id);
        }

        assertThat(stateTracker.getRecentParentIds()).hasSize(5);
        assertThat(stateTracker.getRecentParentIds()).doesNotContain(ids.get(0));
        assertThat(stateTracker.getRecentParentIds()).containsAll(ids.subList(1, 6));
    }

    @Test
    void addRecentParent_shouldIgnoreNull() {
        stateTracker.addRecentParent(null);
        assertThat(stateTracker.getRecentParentIds()).isEmpty();
    }

    @Test
    void reset_shouldClearRecentParentIds() {
        stateTracker.addRecentParent(UUID.randomUUID());
        assertThat(stateTracker.getRecentParentIds()).isNotEmpty();

        stateTracker.reset();

        assertThat(stateTracker.getRecentParentIds()).isEmpty();
    }

    @Test
    void snapshot_producesConsistentView() {
        PhraseEmbedding rootEffect = pe();
        PhraseEmbedding scopeEffect = pe();
        var p1 = Procedure.createAtomic("step 1", List.of(), "", List.of(), List.of(), false);
        UUID parentId = UUID.randomUUID();

        stateTracker.addEffects(List.of(rootEffect));
        stateTracker.addExecutedAtomicProcedure(p1);
        stateTracker.addRecentParent(parentId);
        
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(scopeEffect));

        var snapshot = stateTracker.snapshot();

        assertThat(snapshot.effectNodeIds()).containsExactlyInAnyOrder(rootEffect.id(), scopeEffect.id());
        assertThat(snapshot.executedAtomicProcedures()).containsExactly(p1);
        assertThat(snapshot.recentParentIds()).containsExactly(parentId);
    }
}
