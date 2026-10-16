/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
package org.tarik.ta.knowledge;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.ItemKind;
import org.tarik.ta.dto.ItemProcedureSelection;
import org.tarik.ta.dto.KnowledgeTestCase;
import org.tarik.ta.dto.TestCaseItem;

import java.util.List;

import static org.tarik.ta.core.utils.MarkdownSections.SECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.SUBSECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.bulletList;
import static org.tarik.ta.core.utils.MarkdownSections.joinLines;
import static org.tarik.ta.core.utils.MarkdownSections.joinSections;
import static org.tarik.ta.core.utils.MarkdownSections.listItem;
import static org.tarik.ta.core.utils.MarkdownSections.nestedList;
import static org.tarik.ta.core.utils.MarkdownSections.parentSection;
import static org.tarik.ta.core.utils.MarkdownSections.section;

/**
 * Renders the user messages of the selection runs, with every piece of the test case in its own Markdown section.
 */
final class SelectionRequests {
    private SelectionRequests() {
    }

    static String forItem(@NotNull KnowledgeTestCase testCase, @NotNull TestCaseItem item, int maxSelections, int maxToolCalls) {
        return joinSections(
                testCaseSections(testCase),
                section(SECTION_LEVEL, "Item to select procedures for", item.id()),
                section(SECTION_LEVEL, "Maximum number of procedures to select", String.valueOf(maxSelections)),
                section(SECTION_LEVEL, "Exploration tool calls available in this run", String.valueOf(maxToolCalls)));
    }

    static String forAggregation(@NotNull KnowledgeTestCase testCase, @NotNull List<ItemProcedureSelection> itemSelections,
                                 int maxSelections, int maxToolCalls) {
        var selectionLines = itemSelections.stream()
                .filter(selection -> !selection.procedures().isEmpty())
                .map(selection -> nestedList(selection.itemId(), selection.procedures().stream()
                        .map(procedure -> "%s: %s".formatted(procedure.id(), procedure.description()))
                        .toList()))
                .toArray(String[]::new);
        return joinSections(
                testCaseSections(testCase),
                section(SECTION_LEVEL, "Procedures selected for the single items", joinLines(selectionLines)),
                section(SECTION_LEVEL, "Maximum number of procedures to select per run of items", String.valueOf(maxSelections)),
                section(SECTION_LEVEL, "Exploration tool calls available in this run", String.valueOf(maxToolCalls)));
    }

    private static String testCaseSections(KnowledgeTestCase testCase) {
        var preconditions = testCase.items().stream()
                .filter(item -> item.kind() == ItemKind.PRECONDITION)
                .map(item -> "%s: %s".formatted(item.id(), item.text()))
                .toList();
        var steps = testCase.items().stream()
                .filter(item -> item.kind() == ItemKind.STEP)
                .map(SelectionRequests::stepSection)
                .toArray(String[]::new);
        return joinSections(
                section(SECTION_LEVEL, "Test case name", testCase.name()),
                section(SECTION_LEVEL, "Preconditions", bulletList(preconditions)),
                parentSection(SECTION_LEVEL, "Test steps", steps));
    }

    private static String stepSection(TestCaseItem step) {
        return section(SUBSECTION_LEVEL, step.id(), joinLines(
                listItem("Action", step.text()),
                nestedList("Test data", step.testData()),
                listItem("Expected results", step.expectedResults())));
    }
}
