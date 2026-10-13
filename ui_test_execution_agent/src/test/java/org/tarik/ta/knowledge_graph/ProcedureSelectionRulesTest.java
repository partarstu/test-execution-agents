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
package org.tarik.ta.knowledge_graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tarik.ta.dto.ProcedureExecutionConfirmationResult;
import org.tarik.ta.dto.RerunOptions;
import org.tarik.ta.dto.RerunOptions.Halt;
import org.tarik.ta.dto.UserSelectionResult;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.ScoredProcedure;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.dto.UserSelectionResult.SelectionAction.BROWSE;
import static org.tarik.ta.dto.UserSelectionResult.SelectionAction.CANCEL;
import static org.tarik.ta.dto.UserSelectionResult.SelectionAction.CONTINUE;
import static org.tarik.ta.dto.UserSelectionResult.SelectionAction.CREATE;
import static org.tarik.ta.dto.UserSelectionResult.SelectionAction.PROCEED;
import static org.tarik.ta.dto.UserSelectionResult.SelectionAction.RERUN_ATOMIC_PROCEDURE;
import static org.tarik.ta.dto.UserSelectionResult.SelectionAction.RERUN_TEST_STEP;
import static org.tarik.ta.dto.UserSelectionResult.SelectionAction.RETRY;

class ProcedureSelectionRulesTest {

    private static final ScoredProcedure PRECONDITION = scored("the user is logged in", true, false);
    private static final ScoredProcedure TEST_STEP = scored("click the Save button", false, true);

    @Test
    @DisplayName("filterByType keeps only preconditions when the lookup runs for a precondition")
    void filterByType_shouldKeepOnlyPreconditions_whenPreconditionContext() {
        var filtered = ProcedureSelectionRules.filterByType(List.of(PRECONDITION, TEST_STEP), true, false);

        assertThat(filtered).containsExactly(PRECONDITION);
    }

    @Test
    @DisplayName("filterByType keeps only test steps when the lookup runs for a test step")
    void filterByType_shouldKeepOnlyTestSteps_whenTestStepContext() {
        var filtered = ProcedureSelectionRules.filterByType(List.of(PRECONDITION, TEST_STEP), false, false);

        assertThat(filtered).containsExactly(TEST_STEP);
    }

    @Test
    @DisplayName("filterByType keeps both types when the user asked to see all types")
    void filterByType_shouldKeepBothTypes_whenShowAllTypesRequested() {
        var filtered = ProcedureSelectionRules.filterByType(List.of(PRECONDITION, TEST_STEP), true, true);

        assertThat(filtered).containsExactly(PRECONDITION, TEST_STEP);
    }

    @Test
    @DisplayName("visibleResults shows only the current item's type until the operator asks for all types")
    void visibleResults_shouldKeepOnlyTheCurrentType_whenShowAllTypesIsUnchecked() {
        var visible = ProcedureSelectionRules.visibleResults(List.of(PRECONDITION, TEST_STEP), Set.of(), false, false);

        assertThat(visible).containsExactly(TEST_STEP);
    }

    @Test
    @DisplayName("visibleResults shows both types once the operator checks 'Show all types'")
    void visibleResults_shouldKeepBothTypes_whenShowAllTypesIsChecked() {
        var visible = ProcedureSelectionRules.visibleResults(List.of(PRECONDITION, TEST_STEP), Set.of(), true, true);

        assertThat(visible).containsExactly(PRECONDITION, TEST_STEP);
    }

    @Test
    @DisplayName("visibleResults drops the excluded procedures even when all types are shown")
    void visibleResults_shouldDropExcludedProcedures_whenTheirIdsAreExcluded() {
        var visible = ProcedureSelectionRules.visibleResults(List.of(PRECONDITION, TEST_STEP),
                Set.of(TEST_STEP.procedure().id()), true, true);

        assertThat(visible).containsExactly(PRECONDITION);
    }

    @Test
    @DisplayName("isCrossTypeSelection is true when a precondition is picked for a test step")
    void isCrossTypeSelection_shouldBeTrue_whenPreconditionPickedForTestStep() {
        var precondition = procedure("the user is logged in", true);

        assertThat(ProcedureSelectionRules.isCrossTypeSelection(precondition, false)).isTrue();
    }

    @Test
    @DisplayName("isCrossTypeSelection is true when a test step is picked for a precondition")
    void isCrossTypeSelection_shouldBeTrue_whenTestStepPickedForPrecondition() {
        var testStep = procedure("click the Save button", false);

        assertThat(ProcedureSelectionRules.isCrossTypeSelection(testStep, true)).isTrue();
    }

    @Test
    @DisplayName("isCrossTypeSelection is false when the picked procedure has the type of the executed item")
    void isCrossTypeSelection_shouldBeFalse_whenTypesMatch() {
        var testStep = procedure("click the Save button", false);

        assertThat(ProcedureSelectionRules.isCrossTypeSelection(testStep, false)).isFalse();
    }

    @Test
    @DisplayName("Picking a procedure of the current item's type needs no confirmation")
    void isSelectionAccepted_shouldAcceptWithoutAsking_whenTypesMatch() {
        var precondition = procedure("the user is logged in", true);
        var asked = new AtomicBoolean();

        var accepted = ProcedureSelectionRules.isSelectionAccepted(precondition, true, () -> {
            asked.set(true);
            return false;
        });

        assertThat(accepted).isTrue();
        assertThat(asked).isFalse();
    }

    @Test
    @DisplayName("Picking a procedure of the other type for execution is accepted once the operator confirms it")
    void isSelectionAccepted_shouldAccept_whenTheCrossTypePickIsConfirmed() {
        var testStep = procedure("click the Save button", false);

        assertThat(ProcedureSelectionRules.isSelectionAccepted(testStep, true, () -> true)).isTrue();
    }

    @Test
    @DisplayName("Declining the cross-type confirmation keeps the operator in the browse dialog")
    void isSelectionAccepted_shouldReject_whenTheCrossTypePickIsDeclined() {
        var testStep = procedure("click the Save button", false);

        assertThat(ProcedureSelectionRules.isSelectionAccepted(testStep, true, () -> false)).isFalse();
    }

    @Test
    void offeredActions_shouldOfferRetryCreateBrowseAndTerminate_whenNotInRerunMode() {
        assertThat(ProcedureSelectionRules.offeredActions(null)).containsExactly(RETRY, CREATE, BROWSE, CANCEL);
    }

    @Test
    void offeredActions_shouldOfferBothReruns_whenAtomicRerunAvailable() {
        assertThat(ProcedureSelectionRules.offeredActions(new RerunOptions(false, true, Halt.NONE)))
                .containsExactly(RERUN_TEST_STEP, RERUN_ATOMIC_PROCEDURE, CREATE, BROWSE, CANCEL);
    }

    @Test
    void offeredActions_shouldNotOfferAtomicRerun_whenUnavailable() {
        assertThat(ProcedureSelectionRules.offeredActions(new RerunOptions(false, false, Halt.NONE)))
                .containsExactly(RERUN_TEST_STEP, CREATE, BROWSE, CANCEL);
    }

    @Test
    void offeredActions_shouldOfferProceedFirstWithoutAtomicRerun_whenHaltedBeforeExecution() {
        assertThat(ProcedureSelectionRules.offeredActions(new RerunOptions(false, false, Halt.BEFORE_EXECUTION)))
                .containsExactly(PROCEED, RERUN_TEST_STEP, CREATE, BROWSE, CANCEL);
    }

    @Test
    void offeredActions_shouldOfferContinueFirstAndBothReruns_whenHaltedAfterSuccess() {
        assertThat(ProcedureSelectionRules.offeredActions(new RerunOptions(false, true, Halt.AFTER_SUCCESS)))
                .containsExactly(CONTINUE, RERUN_TEST_STEP, RERUN_ATOMIC_PROCEDURE, CREATE, BROWSE, CANCEL);
    }

    @Test
    void toSelectionResult_shouldReturnRerunTestStep_whenStepRerunChosen() {
        assertThat(ProcedureSelectionRules.toSelectionResult(RERUN_TEST_STEP, null))
                .contains(new UserSelectionResult(RERUN_TEST_STEP, null, null));
    }

    @Test
    void toSelectionResult_shouldReturnRerunAtomicProcedure_whenAtomicRerunChosen() {
        assertThat(ProcedureSelectionRules.toSelectionResult(RERUN_ATOMIC_PROCEDURE, null))
                .contains(new UserSelectionResult(RERUN_ATOMIC_PROCEDURE, null, null));
    }

    @Test
    void toSelectionResult_shouldReturnRetry_whenRetryChosen() {
        assertThat(ProcedureSelectionRules.toSelectionResult(RETRY, null)).contains(new UserSelectionResult(RETRY, null, null));
    }

    @Test
    void toSelectionResult_shouldCarryTheBrowsedProcedure_whenAProcedureWasBrowsed() {
        var procedure = Procedure.createAtomic("click the Save button", List.of(), "", List.of(), List.of(), false);

        assertThat(ProcedureSelectionRules.toSelectionResult(BROWSE, procedure))
                .contains(new UserSelectionResult(BROWSE, procedure.id(), procedure));
    }

    @Test
    void toSelectionResult_shouldReturnEmpty_whenTerminated() {
        assertThat(ProcedureSelectionRules.toSelectionResult(CANCEL, null)).isEmpty();
    }

    @Test
    void defaultDecision_shouldProceed_whenCountdownElapses() {
        assertThat(ProcedureSelectionRules.defaultDecision().decision())
                .isEqualTo(ProcedureExecutionConfirmationResult.Decision.PROCEED);
    }

    private static Procedure procedure(String description, boolean isPrecondition) {
        return Procedure.createAtomic(description, List.of(), "", List.of(), List.of(), isPrecondition);
    }

    private static ScoredProcedure scored(String description, boolean isPrecondition, boolean isAtomic) {
        var procedure = isAtomic
                ? Procedure.createAtomic(description, List.of(), "", List.of(), List.of(), isPrecondition)
                : Procedure.createComposite(description, List.of(), "", List.of(), List.of(), isPrecondition);
        return new ScoredProcedure(procedure, 0, 0);
    }
}
