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

import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;

import static java.lang.String.join;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static org.tarik.ta.core.utils.MarkdownSections.joinLines;
import static org.tarik.ta.core.utils.MarkdownSections.listItem;
import static org.tarik.ta.core.utils.MarkdownSections.nestedList;

/**
 * Builds projected execution graph context strings for AI suggestion prompts.
 */
public class ExecutionGraphContextBuilder {
    private static final String NO_PROCEDURES_EXECUTED = "No procedures have executed yet — this is likely the first step being defined.";

    private ExecutionGraphContextBuilder() {
    }

    /**
     * Renders the test case and every atomic procedure already executed and to be executed, in chronological order, as Markdown
     * list items for the AI suggestions agent.
     *
     * @param executedAtomics   atomic procedures already executed during this test run, in order
     * @param precedingAtomics  atomic procedures that will execute before the new one (preceding siblings, flattened)
     */
    public static String buildExecutionGraphContext(TestCase testCase, List<Procedure> executedAtomics,
                                                    List<Procedure> precedingAtomics) {
        var preconditions = testCase.preconditions().stream().map(Precondition::description).toList();
        var executedSteps = executedAtomics.isEmpty() && precedingAtomics.isEmpty()
                ? NO_PROCEDURES_EXECUTED
                : renderAtomicEntries(executedAtomics, 1);
        return joinLines(
                listItem("Test case", testCase.name()),
                nestedList("Test case preconditions", preconditions),
                listItem("Already executed atomic steps (scenarios) (in execution order)", executedSteps),
                listItem("Atomic steps (scenarios) that are planned to be executed (in execution order)",
                        renderAtomicEntries(precedingAtomics, executedAtomics.size() + 1)));
    }

    private static String renderAtomicEntries(List<Procedure> atomics, int firstNumber) {
        return range(0, atomics.size())
                .mapToObj(index -> renderAtomicEntry(firstNumber + index, atomics.get(index)))
                .collect(joining("\n"));
    }

    private static String renderAtomicEntry(int number, Procedure procedure) {
        var entry = "%d. %s".formatted(number, procedure.description());
        var effects = procedure.effects();
        return effects.isEmpty() ? entry : "%s → effects: [%s]".formatted(entry, join(", ", effects));
    }
}
