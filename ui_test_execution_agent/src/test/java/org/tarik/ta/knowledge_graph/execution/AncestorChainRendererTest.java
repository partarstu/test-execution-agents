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
package org.tarik.ta.knowledge_graph.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AncestorChainRendererTest {
    private static final TestStepItem STEP_WITHOUT_DATA = testStep(List.of());

    @Test
    @DisplayName("renderChain returns an empty string when the atomic step has no ancestors")
    void renderChain_shouldReturnEmptyString_whenChainIsEmpty() {
        assertThat(AncestorChainRenderer.renderChain(List.of(), testStep(List.of("user=admin")))).isEmpty();
    }

    @Test
    @DisplayName("renderChain renders one section per ancestor innermost first, headed by its relation to the step")
    void renderChain_shouldListEveryAncestorInnermostFirst() {
        var outer = composite("Place an order");
        var middle = composite("Fill in the delivery address");
        var inner = composite("Enter the street");

        var rendered = AncestorChainRenderer.renderChain(List.of(outer, middle, inner), STEP_WITHOUT_DATA);

        assertThat(rendered).isEqualTo("""
                ## Procedure hierarchy
                Innermost first; every level is the parent of the previous one.

                ### Level 1: Direct parent (the procedure this step is a part of)
                Enter the street

                ### Level 2: Parent of level 1
                Fill in the delivery address

                ### Level 3: Parent of level 2
                Place an order""");
    }

    @Test
    @DisplayName("renderChain renders the data, expected results and notes an ancestor declares as list items below its description")
    void renderChain_shouldRenderTheDetailsOfAnAncestor() {
        var now = Instant.now();
        var ancestor = new Procedure(UUID.randomUUID(), "Log in", List.of("user=admin", "password=secret"),
                "The dashboard is shown\nThe user menu shows 'admin'", false, false, false, List.of(), List.of(), List.of(), "Use the staging tenant",
                now, now, null, null);

        var rendered = AncestorChainRenderer.renderChain(List.of(ancestor), STEP_WITHOUT_DATA);

        assertThat(rendered).endsWith("""
                ### Level 1: Direct parent (the procedure this step is a part of)
                Log in
                - Data:
                  - user=admin
                  - password=secret
                - Expected results:
                  The dashboard is shown
                  The user menu shows 'admin'
                - Notes: Use the staging tenant""");
    }

    @Test
    @DisplayName("renderChain escapes the heading lines inside the ancestor texts")
    void renderChain_shouldEscapeTheHeadingLinesOfAnAncestor() {
        var rendered = AncestorChainRenderer.renderChain(List.of(composite("Place an order\n## Target UI element ID")), STEP_WITHOUT_DATA);

        assertThat(rendered).endsWith("Place an order\n\\## Target UI element ID");
    }

    @Test
    @DisplayName("renderChain leaves out every detail an ancestor does not declare")
    void renderChain_shouldOmitBlankDetails() {
        var rendered = AncestorChainRenderer.renderChain(List.of(composite("Place an order")), testStep(List.of(" ")));

        assertThat(rendered).endsWith("### Level 1: Direct parent (the procedure this step is a part of)\nPlace an order")
                .doesNotContain("Data").doesNotContain("Expected results").doesNotContain("Notes").doesNotContain("none");
    }

    @Test
    @DisplayName("renderChain renders the test step's data once, after the outermost level")
    void renderChain_shouldRenderTheTestStepDataAfterTheOutermostLevel() {
        var outer = composite("Open the dashboard using the search field");
        var inner = composite("Search for the dashboard");

        var rendered = AncestorChainRenderer.renderChain(List.of(outer, inner),
                testStep(List.of("Dashboard name is 'Table Panel Showcase'", "Folder is 'Examples'")));

        assertThat(rendered).endsWith("""
                ### Level 2: Parent of level 1
                Open the dashboard using the search field

                ### Data of the whole test step
                - Dashboard name is 'Table Panel Showcase'
                - Folder is 'Examples'""");
        assertThat(rendered).containsOnlyOnce("Data of the whole test step");
    }

    @Test
    @DisplayName("renderChain labels the data of a precondition item as the data of the whole test case precondition")
    void renderChain_shouldLabelThePreconditionDataAsSuch() {
        var item = new PreconditionItem(new Precondition("The user is logged in", List.of("user=admin")));

        var rendered = AncestorChainRenderer.renderChain(List.of(composite("Log in")), item);

        assertThat(rendered).endsWith("### Data of the whole test case precondition\n- user=admin");
    }

    @Test
    @DisplayName("renderForVerification appends the notes of the executed step to the chain as their own section")
    void renderForVerification_shouldAppendTheStepNotesToTheChain() {
        var rendered = AncestorChainRenderer.renderForVerification(List.of(composite("Place an order")), testStep(List.of("user=admin")),
                "The toast fades after 3 s");

        assertThat(rendered).contains("### Level 1: Direct parent (the procedure this step is a part of)\nPlace an order")
                .contains("### Data of the whole test step\n- user=admin")
                .endsWith("\n\n## Notes for the executed step\nThe toast fades after 3 s");
    }

    @Test
    @DisplayName("renderForVerification returns only the notes section when the step has no ancestors")
    void renderForVerification_shouldReturnOnlyTheNotes_whenChainIsEmpty() {
        var rendered = AncestorChainRenderer.renderForVerification(List.of(), testStep(List.of("user=admin")), "The toast fades after 3 s");

        assertThat(rendered).isEqualTo("## Notes for the executed step\nThe toast fades after 3 s");
    }

    @Test
    @DisplayName("renderForVerification returns an empty string when there is neither a chain nor a note")
    void renderForVerification_shouldReturnEmptyString_whenThereIsNothingToRender() {
        assertThat(AncestorChainRenderer.renderForVerification(List.of(), STEP_WITHOUT_DATA, null)).isEmpty();
        assertThat(AncestorChainRenderer.renderForVerification(List.of(), STEP_WITHOUT_DATA, "  ")).isEmpty();
    }

    @Test
    @DisplayName("resolveRelevantData gives the item's data to an atomic step matched to the item directly")
    void resolveRelevantData_shouldReturnTheItemData_whenStepHasNoAncestors() {
        var atomic = atomicWithData("Log in", List.of("atomic data"));

        var resolved = AncestorChainRenderer.resolveRelevantData(testStep(List.of("user=admin", "password=secret")), atomic, List.of());

        assertThat(resolved).isEqualTo("- user=admin\n- password=secret");
    }

    @Test
    @DisplayName("resolveRelevantData falls back to the step's own data when a directly matched step's item has none")
    void resolveRelevantData_shouldReturnTheStepData_whenStepHasNoAncestorsAndItemHasNoData() {
        var atomic = atomicWithData("Log in", List.of("atomic data"));

        var resolved = AncestorChainRenderer.resolveRelevantData(testStep(List.of("  ")), atomic, List.of());

        assertThat(resolved).isEqualTo("- atomic data");
    }

    @Test
    @DisplayName("resolveRelevantData never gives the item's data to a decomposed atomic step")
    void resolveRelevantData_shouldReturnOnlyTheStepData_whenStepHasAncestors() {
        var atomic = atomicWithData("Type the dashboard name", List.of("atomic data"));
        var ancestor = compositeWithData("Open the dashboard", List.of("ancestor data"));

        var resolved = AncestorChainRenderer.resolveRelevantData(testStep(List.of("item data")), atomic, List.of(ancestor));

        assertThat(resolved).isEqualTo("- atomic data");
    }

    @Test
    @DisplayName("resolveRelevantData returns an empty string for a decomposed atomic step without data of its own")
    void resolveRelevantData_shouldReturnEmptyString_whenDecomposedStepHasNoData() {
        var atomic = atomicWithData("Click on the search field", List.of());
        var ancestor = compositeWithData("Open the dashboard", List.of("ancestor data"));

        var resolved = AncestorChainRenderer.resolveRelevantData(testStep(List.of("item data")), atomic, List.of(ancestor));

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("resolveRelevantData takes the relevant data a precondition procedure stores in its expected results")
    void resolveRelevantData_shouldReturnThePreconditionRelevantData_whenStepHasAncestors() {
        var item = new PreconditionItem(new Precondition("The user is logged in", List.of("item data")));
        var atomic = Procedure.createAtomic("Enter the user name", List.of("atomic data"), "user=admin", List.of(), List.of(), true);

        var resolved = AncestorChainRenderer.resolveRelevantData(item, atomic, List.of(composite("Log in")));

        assertThat(resolved).isEqualTo("user=admin");
    }

    @Test
    @DisplayName("resolveRelevantData falls back to the test data of a precondition procedure without relevant data")
    void resolveRelevantData_shouldReturnThePreconditionTestData_whenItHasNoRelevantData() {
        var item = new PreconditionItem(new Precondition("The user is logged in", null));
        var atomic = Procedure.createAtomic("Log in", List.of("atomic data"), "", List.of(), List.of(), true);

        var resolved = AncestorChainRenderer.resolveRelevantData(item, atomic, List.of());

        assertThat(resolved).isEqualTo("- atomic data");
    }

    private static TestStepItem testStep(List<String> testData) {
        return new TestStepItem(new TestStep("Open the dashboard", testData, "The dashboard is opened"));
    }

    private static Procedure composite(String description) {
        return compositeWithData(description, List.of());
    }

    private static Procedure compositeWithData(String description, List<String> testData) {
        return Procedure.createComposite(description, testData, "", List.of(), List.of(), false);
    }

    private static Procedure atomicWithData(String description, List<String> testData) {
        return Procedure.createAtomic(description, testData, "", List.of(), List.of(), false);
    }
}
