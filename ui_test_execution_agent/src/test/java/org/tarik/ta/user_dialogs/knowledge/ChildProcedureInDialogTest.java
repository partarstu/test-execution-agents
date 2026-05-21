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
package org.tarik.ta.user_dialogs.knowledge;

import org.junit.jupiter.api.Test;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ChildProcedureInDialog} construction and validation logic.
 */
class ChildProcedureInDialogTest {

    // --- DialogStep.New construction ---

    @Test
    void newStep_blankDescription_isBlank() {
        var step = newStep("", true);
        assertThat(step.description()).isBlank();
    }

    @Test
    void newStep_atomicStep_roundtrip() {
        var step = newStep("Click the submit button", true);

        assertThat(step.description()).isEqualTo("Click the submit button");
        assertThat(step.isAtomic()).isTrue();
        assertThat(step.targetUiElementId()).isNull();
        assertThat(step.needsSave()).isFalse();
        assertThat(step.children()).isEmpty();
    }

    @Test
    void newStep_compositeStep_roundtrip() {
        var step = newStep("Fill the login form", false);

        assertThat(step.description()).isEqualTo("Fill the login form");
        assertThat(step.isAtomic()).isFalse();
        assertThat(step.children()).isEmpty();
    }

    @Test
    void newStep_needsSave_true_flagIsPreserved() {
        var step = new ChildProcedureInDialog.New(
                Procedure.createAtomic("TBD", List.of(), "", List.of(), List.of(), false),
                null, null, true, List.of());

        assertThat(step.needsSave()).isTrue();
    }

    @Test
    void linkedStep_delegatesFieldsToWrappedProcedure() {
        var procedure = Procedure.createAtomic("Click OK", List.of(), "Button clicked", List.of(), List.of(), false);
        var step = new ChildProcedureInDialog.Linked(procedure, null);

        assertThat(step.description()).isEqualTo("Click OK");
        assertThat(step.isAtomic()).isTrue();
        assertThat(step.isPrecondition()).isFalse();
        assertThat(step.procedure()).isSameAs(procedure);
    }

    // --- findInvalidComposite ---

    @Test
    void findInvalidComposite_emptyList_returnsEmpty() {
        assertThat(ChildProcedureInDialog.findInvalidComposite(List.of())).isEmpty();
    }

    @Test
    void findInvalidComposite_allAtomic_returnsEmpty() {
        var a = newStep("Click OK", true);
        var b = newStep("Type text", true);

        assertThat(ChildProcedureInDialog.findInvalidComposite(List.of(a, b))).isEmpty();
    }

    @Test
    void findInvalidComposite_compositeWithNoChildren_returnsItsDescription() {
        var bad = newStep("Fill form", false);

        assertThat(ChildProcedureInDialog.findInvalidComposite(List.of(bad))).contains("Fill form");
    }

    @Test
    void findInvalidComposite_compositeWithChildren_returnsEmpty() {
        var leaf = newStep("Click OK", true);
        var good = new ChildProcedureInDialog.New(
                Procedure.createComposite("Fill form", List.of(), "", List.of(), List.of(), false),
                null, null, false, List.of(leaf));

        assertThat(ChildProcedureInDialog.findInvalidComposite(List.of(good))).isEmpty();
    }

    @Test
    void findInvalidComposite_deeplyNestedInvalidComposite_returnsItsDescription() {
        var invalidDeep = newStep("Deep composite", false);
        var mid = new ChildProcedureInDialog.New(
                Procedure.createComposite("Mid composite", List.of(), "", List.of(), List.of(), false),
                null, null, false, List.of(invalidDeep));
        var top = new ChildProcedureInDialog.New(
                Procedure.createComposite("Top composite", List.of(), "", List.of(), List.of(), false),
                null, null, false, List.of(mid));

        assertThat(ChildProcedureInDialog.findInvalidComposite(List.of(top))).contains("Deep composite");
    }

    @Test
    void findInvalidComposite_firstInvalidAmongSiblings_returnsThatOne() {
        var atomic = newStep("Click OK", true);
        var bad = newStep("Empty composite", false);
        var anotherBad = newStep("Another composite", false);

        assertThat(ChildProcedureInDialog.findInvalidComposite(List.of(atomic, bad, newStep("Type text", true), anotherBad)))
                .contains("Empty composite");
    }

    @Test
    void findInvalidComposite_linkedCompositeWithNoInMemoryChildren_returnsEmpty() {
        // Linked steps are persisted in the DB; their children are not held in memory.
        // The validator must not flag them as invalid based on the empty in-memory children list.
        var procedure = Procedure.createComposite("Select date", List.of(), "", List.of(), List.of(), false);
        var linkedComposite = new ChildProcedureInDialog.Linked(procedure, null);

        assertThat(ChildProcedureInDialog.findInvalidComposite(List.of(linkedComposite))).isEmpty();
    }

    private static ChildProcedureInDialog.New newStep(String description, boolean isAtomic) {
        var procedure = isAtomic
                ? Procedure.createAtomic(description, List.of(), "", List.of(), List.of(), false)
                : Procedure.createComposite(description, List.of(), "", List.of(), List.of(), false);
        return new ChildProcedureInDialog.New(procedure, null, null, false, List.of());
    }
}
