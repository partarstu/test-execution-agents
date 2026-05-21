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

import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.model.UiTestExecutionContext;

import java.util.List;

import static java.lang.String.join;

/**
 * Builds projected execution graph context strings for AI suggestion prompts.
 */
public class ExecutionGraphContextBuilder {

    private ExecutionGraphContextBuilder() {
    }

    /**
     * Builds the projected execution graph context for the AI suggestions agent.
     * Lists every atomic procedure already executed and to be executed, in chronological order.
     *
     * @param executionContext  the current test case context
     * @param executedAtomics   atomic procedures already executed during this test run, in order
     * @param precedingAtomics  atomic procedures that will execute before the new one (preceding siblings, flattened)
     */
    public static String buildExecutionGraphContext(TestCase testCase, UiTestExecutionContext executionContext,
                                                    List<Procedure> executedAtomics,
                                                    List<Procedure> precedingAtomics) {
        var sb = new StringBuilder();
        appendTestCaseContext(sb, testCase);

        if (executedAtomics.isEmpty() && precedingAtomics.isEmpty()) {
            sb.append("\nNo procedures have executed yet — this is likely the first step being defined.\n");
            return sb.toString();
        }

        if (!executedAtomics.isEmpty()) {
            sb.append("\nAlready executed atomic steps (scenarios) (in execution order):\n");
            for (int i = 0; i < executedAtomics.size(); i++) {
                appendAtomicEntry(sb, i + 1, executedAtomics.get(i));
            }
        }

        if (!precedingAtomics.isEmpty()) {
            sb.append("\nAtomic steps (scenarios) that are planned to be executed (in execution order):\n");
            int offset = executedAtomics.size();
            for (int i = 0; i < precedingAtomics.size(); i++) {
                appendAtomicEntry(sb, offset + i + 1, precedingAtomics.get(i));
            }
        }

        return sb.toString();
    }

    private static void appendAtomicEntry(StringBuilder sb, int index, Procedure procedure) {
        sb.append("%d. %s".formatted(index, procedure.description()));
        var effects = procedure.effects();
        if (!effects.isEmpty()) {
            sb.append(" → effects: [%s]".formatted(join(", ", effects)));
        }
        sb.append("\n");
    }

    private static void appendTestCaseContext(StringBuilder sb, TestCase testCase) {
        sb.append("Test case: %s\n".formatted(testCase.name()));
        if (testCase.preconditions() != null && !testCase.preconditions().isEmpty()) {
            sb.append("Test case preconditions: %s\n".formatted(join(", ", testCase.preconditions())));
        }
    }
}
