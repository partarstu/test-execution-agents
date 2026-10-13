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

import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionGraphContextBuilderTest {
    @Test
    void buildExecutionGraphContext_shouldRenderTheTestCaseAndTheExecutedAndPlannedStepsAsListItems() {
        var testCase = new TestCase("Order flow", List.of(new Precondition("The user is logged in", List.of())), List.of(), List.of());
        var openOrders = Procedure.createAtomic("Open the orders", List.of(), "", List.of(), List.of("The order list is shown"), false);
        var selectOrder = Procedure.createAtomic("Select the order", List.of(), "", List.of(), List.of(), false);
        var cancelOrder = Procedure.createAtomic("Cancel the order", List.of(), "", List.of(), List.of(), false);

        var context = ExecutionGraphContextBuilder.buildExecutionGraphContext(testCase, List.of(openOrders, selectOrder),
                List.of(cancelOrder));

        assertThat(context).isEqualTo("""
                - Test case: Order flow
                - Test case preconditions:
                  - The user is logged in
                - Already executed atomic steps (scenarios) (in execution order):
                  1. Open the orders → effects: [The order list is shown]
                  2. Select the order
                - Atomic steps (scenarios) that are planned to be executed (in execution order): 3. Cancel the order""");
    }

    @Test
    void buildExecutionGraphContext_shouldSayThatNothingWasExecuted_whenThereAreNoSteps() {
        var testCase = new TestCase("Order flow", List.of(), List.of(), List.of());

        var context = ExecutionGraphContextBuilder.buildExecutionGraphContext(testCase, List.of(), List.of());

        assertThat(context).isEqualTo("""
                - Test case: Order flow
                - Already executed atomic steps (scenarios) (in execution order): No procedures have executed yet — this is likely \
                the first step being defined.""");
    }
}
