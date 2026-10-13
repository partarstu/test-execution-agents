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

import org.junit.jupiter.api.Test;
import org.tarik.ta.dto.EditorChild;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcedureEditorRulesTest {

    @Test
    void effectsBeforeChild_shouldPutTheEffectsBeforeTheParentFirst_whenChildHasPrecedingSiblings() {
        var precedingSibling = Procedure.createAtomic("open orders", List.of(), "", List.of(), List.of("Order list is shown"), false);

        var effects = ProcedureEditorRules.effectsBeforeChild(List.of("User is logged in"),
                List.of(new EditorChild.Linked(precedingSibling, null)));

        assertThat(effects).containsExactly("User is logged in", "Order list is shown");
    }

    @Test
    void effectsBeforeChild_shouldDropTheStatesAPrecedingSiblingNegates() {
        var openDialog = Procedure.createAtomic("open dialog", List.of(), "", List.of(), List.of("Dialog is open"), false);
        var closeDialog = Procedure.createAtomic("close dialog", List.of(), "", List.of(), List.of("Form is shown"), false)
                .withNegatedEffects(List.of("Dialog is open"));

        var effects = ProcedureEditorRules.effectsBeforeChild(List.of("User is logged in"),
                List.of(new EditorChild.Linked(openDialog, null), new EditorChild.Linked(closeDialog, null)));

        assertThat(effects).containsExactly("User is logged in", "Form is shown");
    }

    @Test
    void effectsBeforeChild_shouldTakeOnlyTheOwnEffectsOfACompositeSibling_whenItsChildStepsHaveEffectsToo() {
        var childStep = Procedure.createAtomic("click edit", List.of(), "", List.of(), List.of("Dashboard enters edit mode"), false);
        var compositeSibling = Procedure.createComposite("open the panel editor", List.of(), "", List.of(),
                List.of("Panel edit view is opened"), false);
        var sibling = new EditorChild.New(compositeSibling, null, null, false,
                List.of(new EditorChild.New(childStep, null, null, false, List.of())));

        assertThat(ProcedureEditorRules.effectsBeforeChild(List.of(), List.of(sibling))).containsExactly("Panel edit view is opened");
    }

    @Test
    void buildTransientProcedure_shouldPrefillTheSuggestedNegatedEffects_whenTheAiSuggestsThem() {
        var suggestions = new KnowledgeSuggestionResult(List.of("the dialog is open"), List.of("the form is shown"),
                List.of("the dialog is open"), List.of("The form is shown"), List.of(), true);

        var procedure = ProcedureEditorRules.buildTransientProcedure("Close the dialog", suggestions, true);

        assertThat(procedure).isNotNull();
        assertThat(procedure.effects()).containsExactly("the form is shown");
        assertThat(procedure.negatedEffects()).containsExactly("the dialog is open");
    }

    @Test
    void buildTransientProcedure_shouldLeaveTheNegatedEffectsEmpty_whenTheAiSuggestsNone() {
        var suggestions = new KnowledgeSuggestionResult(List.of(), List.of("the form is shown"), null, List.of("The form is shown"),
                List.of(), true);

        var procedure = ProcedureEditorRules.buildTransientProcedure("Open the form", suggestions, true);

        assertThat(procedure).isNotNull();
        assertThat(procedure.negatedEffects()).isEmpty();
    }

    @Test
    void distinctNewestFirst_shouldListTheLatestEffectFirstWithoutDuplicates_whenAnEffectIsProducedTwice() {
        var effects = ProcedureEditorRules.distinctNewestFirst(
                List.of("User is logged in", "Dashboard is shown", "Order list is shown", "Dashboard is shown"));

        assertThat(effects).containsExactly("Dashboard is shown", "Order list is shown", "User is logged in");
    }

    @Test
    void distinctNewestFirst_shouldReturnAnEmptyList_whenNothingRanBefore() {
        assertThat(ProcedureEditorRules.distinctNewestFirst(List.of())).isEmpty();
    }

    @Test
    void resolvePreservedFields_shouldKeepTypeAndHiddenTestData_forPrecondition() {
        var loaded = procedureOfType(true, List.of("customer = ACME"));

        var preserved = ProcedureEditorRules.resolvePreservedFields(loaded, false, List.of());

        assertThat(preserved.isPrecondition()).isTrue();
        assertThat(preserved.testData()).containsExactly("customer = ACME");
    }

    @Test
    void resolvePreservedFields_shouldTakeEditedTestData_forTestStep() {
        var loaded = procedureOfType(false, List.of("customer = ACME"));

        var preserved = ProcedureEditorRules.resolvePreservedFields(loaded, true, List.of("customer = GLOBEX"));

        assertThat(preserved.isPrecondition()).isFalse();
        assertThat(preserved.testData()).containsExactly("customer = GLOBEX");
    }

    @Test
    void resolvePreservedFields_shouldKeepLoadedType_whenFieldSetContradictsIt() {
        var loaded = procedureOfType(false, List.of());

        var preserved = ProcedureEditorRules.resolvePreservedFields(loaded, false, List.of());

        assertThat(preserved.isPrecondition()).isFalse();
    }

    @Test
    void resolvePreservedFields_shouldDeriveTypeFromFieldSet_whenNothingWasLoaded() {
        var preserved = ProcedureEditorRules.resolvePreservedFields(null, false, List.of());

        assertThat(preserved.isPrecondition()).isTrue();
        assertThat(preserved.testData()).isEmpty();
    }

    private static Procedure procedureOfType(boolean isPrecondition, List<String> testData) {
        return Procedure.createAtomic("some procedure", testData, "", List.of(), List.of(), isPrecondition);
    }
}
