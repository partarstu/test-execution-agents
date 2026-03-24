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
            sb.append("\nAlready executed atomic procedures (in execution order):\n");
            for (int i = 0; i < executedAtomics.size(); i++) {
                appendAtomicEntry(sb, i + 1, executedAtomics.get(i));
            }
        }

        if (!precedingAtomics.isEmpty()) {
            sb.append("\nAtomic procedures that are planned to be executed (in execution order):\n");
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
