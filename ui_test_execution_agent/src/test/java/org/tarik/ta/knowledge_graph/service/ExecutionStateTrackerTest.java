/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tarik.ta.knowledge_graph.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.execution.ClosingComposite;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
        return pe("test effect");
    }

    private static PhraseEmbedding pe(String phrase) {
        return new PhraseEmbedding(UUID.randomUUID(), phrase, new float[0], PhraseType.EFFECT);
    }

    private static Procedure compositeProc() {
        return Procedure.createComposite("composite", List.of(), "", List.of(), List.of(), false);
    }

    private static ClosingComposite closing(Supplier<List<PhraseEmbedding>> effects) {
        return new ClosingComposite(effects, _ -> Set.of());
    }

    @Test
    void removeEffects_shouldRemoveTheEffectsFromEveryOpenScopeFrame() {
        var rootEffect = pe();
        var keptEffect = pe();
        var scopeEffect = pe();
        stateTracker.addEffects(List.of(rootEffect, keptEffect));
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(scopeEffect));

        stateTracker.removeEffects(Set.of(rootEffect.id(), scopeEffect.id()));

        assertThat(stateTracker.getEffectNodeIds()).containsExactly(keptEffect.id());
    }

    @Test
    void closeCompositeScope_shouldRemoveTheNegatedEffectsBeforePromotingTheCompositeEffects() {
        var negatedRootEffect = pe();
        var compositeEffect = pe();
        stateTracker.addEffects(List.of(negatedRootEffect));
        stateTracker.enterCompositeScope(compositeProc());

        stateTracker.closeCompositeScope(List.of(compositeEffect), Set.of(negatedRootEffect.id(), compositeEffect.id()));

        assertThat(stateTracker.getEffectNodeIds()).containsExactly(compositeEffect.id());
    }

    @Test
    void projectedSnapshot_shouldRemoveTheAtomicNegatedEffects_beforeAddingItsEffects() {
        var negatedRootEffect = pe();
        var keptRootEffect = pe();
        var atomicEffect = pe();
        stateTracker.addEffects(List.of(negatedRootEffect, keptRootEffect));
        stateTracker.enterCompositeScope(compositeProc());

        var snapshot = stateTracker.projectedSnapshot(List.of(atomicEffect), Set.of(negatedRootEffect.id()), List.of()).get();

        assertThat(snapshot.effectNodeIds()).containsExactlyInAnyOrder(keptRootEffect.id(), atomicEffect.id());
        assertThat(stateTracker.getEffectNodeIds()).containsExactlyInAnyOrder(negatedRootEffect.id(), keptRootEffect.id());
    }

    @Test
    void projectedSnapshot_shouldApplyTheClosingCompositeNegations_resolvedAgainstTheProjectedState() {
        var negatedRootEffect = pe();
        var compositeEffect = pe();
        var atomicEffect = pe();
        stateTracker.addEffects(List.of(negatedRootEffect));
        stateTracker.enterCompositeScope(compositeProc());
        var negationInput = new AtomicReference<Set<UUID>>();

        var snapshot = stateTracker.projectedSnapshot(List.of(atomicEffect), Set.of(), List.of(new ClosingComposite(
                () -> List.of(compositeEffect),
                visibleEffectIds -> {
                    negationInput.set(Set.copyOf(visibleEffectIds));
                    return Set.of(negatedRootEffect.id());
                }))).get();

        assertThat(negationInput.get()).containsExactlyInAnyOrder(negatedRootEffect.id(), atomicEffect.id());
        assertThat(snapshot.effectNodeIds()).containsExactly(compositeEffect.id());
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

        stateTracker.closeCompositeScope(List.of(compositeEffect), Set.of());

        // After closing, atomic effect is gone, composite effect promoted to root
        assertThat(stateTracker.getEffectNodeIds()).containsExactly(compositeEffect.id());
    }

    @Test
    void projectedSnapshot_shouldAddAtomicEffects_whenNoCompositeCloses() {
        var rootEffect = pe();
        var atomicEffect = pe();
        stateTracker.addEffects(List.of(rootEffect));
        stateTracker.enterCompositeScope(compositeProc());

        var snapshot = stateTracker.projectedSnapshot(List.of(atomicEffect), Set.of(), List.of()).get();

        assertThat(snapshot.effectNodeIds()).containsExactlyInAnyOrder(rootEffect.id(), atomicEffect.id());
    }

    @Test
    void projectedSnapshot_shouldKeepOnlyTheCompositeEffects_whenTheCompositeClosesAfterTheAtomic() {
        var rootEffect = pe();
        var siblingEffect = pe();
        var compositeEffect = pe();
        stateTracker.addEffects(List.of(rootEffect));
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(siblingEffect));

        var snapshot = stateTracker.projectedSnapshot(List.of(pe()), Set.of(), List.of(closing(() -> List.of(compositeEffect)))).get();

        assertThat(snapshot.effectNodeIds()).containsExactlyInAnyOrder(rootEffect.id(), compositeEffect.id());
    }

    @Test
    void projectedSnapshot_shouldKeepOnlyTheOutermostCompositeEffects_whenNestedCompositesCloseTogether() {
        var outerEffect = pe();
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(pe()));
        stateTracker.enterCompositeScope(compositeProc());

        var snapshot = stateTracker.projectedSnapshot(List.of(pe()), Set.of(),
                List.of(closing(() -> List.of(pe())), closing(() -> List.of(outerEffect)))).get();

        assertThat(snapshot.effectNodeIds()).containsExactly(outerEffect.id());
    }

    @Test
    void projectedSnapshot_shouldLeaveTheLiveStateUnchanged() {
        var siblingEffect = pe();
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(siblingEffect));

        stateTracker.projectedSnapshot(List.of(pe()), Set.of(), List.of(closing(() -> List.of(pe())))).get();

        assertThat(stateTracker.getEffectNodeIds()).containsExactly(siblingEffect.id());
    }

    @Test
    void projectedSnapshot_shouldResolveTheClosingCompositesEffects_onlyWhenTheSnapshotIsRequested() {
        var effectsRequested = new AtomicBoolean();
        stateTracker.enterCompositeScope(compositeProc());

        var projectedSnapshot = stateTracker.projectedSnapshot(List.of(pe()), Set.of(), List.of(closing(() -> {
            effectsRequested.set(true);
            return List.of(pe());
        })));

        assertThat(effectsRequested).isFalse();
        projectedSnapshot.get();
        assertThat(effectsRequested).isTrue();
    }

    @Test
    void projectedSnapshot_shouldProjectTheStateCapturedAtCreation_whenTheLiveStateChangesBeforeTheSnapshotIsRequested() {
        var compositeEffect = pe();
        stateTracker.enterCompositeScope(compositeProc());
        var projectedSnapshot = stateTracker.projectedSnapshot(List.of(pe()), Set.of(), List.of(closing(() -> List.of(compositeEffect))));

        stateTracker.addEffects(List.of(pe()));
        stateTracker.closeCompositeScope(List.of(compositeEffect), Set.of());

        assertThat(projectedSnapshot.get().effectNodeIds()).containsExactly(compositeEffect.id());
    }

    @Test
    void closeCompositeScope_shouldDropTheScopeWithoutPromoting_whenTheCompositeEffectsAreNull() {
        var rootEffect = pe();
        stateTracker.addEffects(List.of(rootEffect));
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(pe()));

        stateTracker.closeCompositeScope(null, Set.of());

        assertThat(stateTracker.getEffectNodeIds()).containsExactly(rootEffect.id());
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
    void getEffectPhrasesInExecutionOrder_shouldKeepOnlyTheCompositeOwnEffects_whenTheCompositeScopeIsClosed() {
        stateTracker.addEffects(List.of(pe("User is logged in")));
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(pe("Dashboard enters edit mode")));

        stateTracker.closeCompositeScope(List.of(pe("Panel edit view is opened")), Set.of());

        assertThat(stateTracker.getEffectPhrasesInExecutionOrder()).containsExactly("User is logged in", "Panel edit view is opened");
    }

    @Test
    void getEffectPhrasesInExecutionOrder_shouldListOpenScopesInProductionOrder_whenAnEffectIsProducedAgain() {
        var dashboardShown = pe("Dashboard is shown");
        stateTracker.addEffects(List.of(dashboardShown, pe("User is logged in")));
        stateTracker.addEffects(List.of(dashboardShown));
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(pe("Order list is shown")));

        assertThat(stateTracker.getEffectPhrasesInExecutionOrder())
                .containsExactly("User is logged in", "Dashboard is shown", "Order list is shown");
    }

    @Test
    void getEffectPhrasesBeforeCurrentItem_shouldExcludeTheEffectsOfOpenCompositeScopes_whenAnItemIsInProgress() {
        stateTracker.addEffects(List.of(pe("User is logged in")));
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.addEffects(List.of(pe("Dashboard enters edit mode")));

        assertThat(stateTracker.getEffectPhrasesBeforeCurrentItem()).containsExactly("User is logged in");
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
    void restore_shouldDiscardTheExecutedAtomicProceduresRecordedAfterTheCheckpoint() {
        var p1 = Procedure.createAtomic("step 1", List.of(), "", List.of(), List.of(), false);
        var p2 = Procedure.createAtomic("step 2", List.of(), "", List.of(), List.of(), false);
        var p3 = Procedure.createAtomic("step 3", List.of(), "", List.of(), List.of(), false);
        stateTracker.addExecutedAtomicProcedure(p1);
        var checkpoint = stateTracker.checkpoint();
        stateTracker.addExecutedAtomicProcedure(p2);
        stateTracker.addExecutedAtomicProcedure(p3);

        stateTracker.restore(checkpoint);

        assertThat(stateTracker.getExecutedAtomicProcedures()).containsExactly(p1);
    }

    @Test
    void restore_shouldBringBackTheNegatedEffectsAndDropTheAddedOnes() {
        var negatedRootEffect = pe("dialog is open");
        var keptRootEffect = pe("user is logged in");
        stateTracker.addEffects(List.of(negatedRootEffect, keptRootEffect));
        var checkpoint = stateTracker.checkpoint();
        stateTracker.enterCompositeScope(compositeProc());
        stateTracker.removeEffects(Set.of(negatedRootEffect.id()));
        stateTracker.addEffects(List.of(pe("dialog is closed")));

        stateTracker.restore(checkpoint);

        assertThat(stateTracker.getEffectNodeIds()).containsExactlyInAnyOrder(negatedRootEffect.id(), keptRootEffect.id());
        stateTracker.addEffects(List.of(pe("form is filled")));
        assertThat(stateTracker.getEffectNodeIds()).hasSize(3);
    }

    @Test
    void restore_shouldKeepTheCheckpointUnchanged_whenTheStateChangesAfterARestore() {
        var effect = pe("user is logged in");
        stateTracker.addEffects(List.of(effect));
        var checkpoint = stateTracker.checkpoint();
        stateTracker.restore(checkpoint);
        stateTracker.removeEffects(Set.of(effect.id()));

        stateTracker.restore(checkpoint);

        assertThat(stateTracker.getEffectNodeIds()).containsExactly(effect.id());
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
