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

import java.util.Arrays;
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
        stateTracker = new ExecutionStateTracker(mock(UiTestAgentConfig.class));
    }

    private static PhraseEmbedding pe(String phrase) {
        return new PhraseEmbedding(UUID.randomUUID(), phrase, new float[0], PhraseType.EFFECT);
    }

    private static List<PhraseEmbedding> phrases(String... values) {
        return Arrays.stream(values).map(ExecutionStateTrackerTest::pe).toList();
    }

    @Test
    void addEffects_shouldAddEffectsToCurrentState() {
        stateTracker.addEffects(phrases("user logged in", "page loaded"));

        Set<String> currentState = stateTracker.getCurrentState();
        assertThat(currentState).contains("user logged in", "page loaded");
        assertThat(currentState).hasSize(2);
    }

    @Test
    void addEffects_shouldNormalizeEffectsToLowercase() {
        stateTracker.addEffects(phrases("USER LOGGED IN", "Page Loaded"));

        Set<String> currentState = stateTracker.getCurrentState();
        assertThat(currentState).contains("user logged in", "page loaded");
    }

    @Test
    void addEffects_shouldTrimAndNormalizeWhitespace() {
        stateTracker.addEffects(phrases("  user   logged   in  ", "\tpage\tloaded\t"));

        Set<String> currentState = stateTracker.getCurrentState();
        assertThat(currentState).contains("user logged in", "page loaded");
    }

    @Test
    void addEffects_shouldIgnoreBlankEffects() {
        stateTracker.addEffects(Arrays.asList(pe("valid effect"), pe(""), pe("  "), null));

        Set<String> currentState = stateTracker.getCurrentState();
        assertThat(currentState).containsExactly("valid effect");
    }

    @Test
    void addEffects_shouldNotDuplicateEffects() {
        stateTracker.addEffects(phrases("user logged in"));
        stateTracker.addEffects(phrases("USER LOGGED IN")); // Same effect, different case

        Set<String> currentState = stateTracker.getCurrentState();
        assertThat(currentState).hasSize(1);
        assertThat(currentState).contains("user logged in");
    }

    @Test
    void findMissingPrerequisites_shouldReturnEmptyWhenAllMet() {
        stateTracker.addEffects(phrases("user logged in", "page loaded"));

        List<String> missing = stateTracker.findMissingPrerequisites(List.of("user logged in", "page loaded"));

        assertThat(missing).isEmpty();
    }

    @Test
    void findMissingPrerequisites_shouldReturnMissingPreconditions() {
        stateTracker.addEffects(phrases("user logged in"));

        List<String> missing = stateTracker.findMissingPrerequisites(List.of("user logged in", "page loaded", "data available"));

        assertThat(missing).containsExactly("page loaded", "data available");
    }

    @Test
    void findMissingPrerequisites_shouldBeCaseInsensitive() {
        stateTracker.addEffects(phrases("User Logged In"));

        List<String> missing = stateTracker.findMissingPrerequisites(List.of("USER LOGGED IN"));

        assertThat(missing).isEmpty();
    }

    @Test
    void findMissingPrerequisites_shouldNormalizeWhitespace() {
        stateTracker.addEffects(phrases("user   logged   in"));

        List<String> missing = stateTracker.findMissingPrerequisites(List.of("  user logged in  "));

        assertThat(missing).isEmpty();
    }

    @Test
    void findMissingPrerequisites_shouldIgnoreNullOrBlankPreconditions() {
        stateTracker.addEffects(phrases("user logged in"));

        List<String> missing = stateTracker.findMissingPrerequisites(
                Arrays.asList("user logged in", "", "  ", null));

        assertThat(missing).isEmpty();
    }

    @Test
    void arePrerequisitesMet_shouldReturnTrueWhenAllMet() {
        stateTracker.addEffects(phrases("user logged in", "page loaded"));

        boolean result = stateTracker.arePrerequisitesMet(List.of("user logged in", "page loaded"));

        assertThat(result).isTrue();
    }

    @Test
    void arePrerequisitesMet_shouldReturnFalseWhenSomeMissing() {
        stateTracker.addEffects(phrases("user logged in"));

        boolean result = stateTracker.arePrerequisitesMet(List.of("user logged in", "page loaded"));

        assertThat(result).isFalse();
    }

    @Test
    void arePrerequisitesMet_shouldReturnTrueForEmptyPreconditions() {
        stateTracker.addEffects(phrases("user logged in"));

        boolean result = stateTracker.arePrerequisitesMet(List.of());

        assertThat(result).isTrue();
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
    void reset_shouldClearCurrentState() {
        stateTracker.addEffects(phrases("user logged in", "page loaded"));
        assertThat(stateTracker.getCurrentState()).hasSize(2);

        stateTracker.reset();

        assertThat(stateTracker.getCurrentState()).isEmpty();
    }

    @Test
    void reset_shouldClearExecutedAtomicProcedures() {
        stateTracker.addExecutedAtomicProcedure(Procedure.createAtomic("step", List.of(), "", List.of(), List.of(), false));
        assertThat(stateTracker.getExecutedAtomicProcedures()).hasSize(1);

        stateTracker.reset();

        assertThat(stateTracker.getExecutedAtomicProcedures()).isEmpty();
    }

    @Test
    void getCurrentState_shouldReturnUnmodifiableCopy() {
        stateTracker.addEffects(phrases("user logged in"));
        Set<String> currentState = stateTracker.getCurrentState();

        assertThatThrownBy(() -> currentState.add("new effect"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getCurrentState_shouldReturnDefensiveCopy() {
        stateTracker.addEffects(phrases("user logged in"));
        Set<String> state1 = stateTracker.getCurrentState();
        Set<String> state2 = stateTracker.getCurrentState();

        assertThat(state1).isNotSameAs(state2);
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
}
