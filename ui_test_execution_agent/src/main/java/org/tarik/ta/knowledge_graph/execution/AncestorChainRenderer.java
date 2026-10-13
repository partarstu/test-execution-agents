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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNullElse;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.core.utils.MarkdownSections.SECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.SUBSECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.bulletList;
import static org.tarik.ta.core.utils.MarkdownSections.joinLines;
import static org.tarik.ta.core.utils.MarkdownSections.joinSections;
import static org.tarik.ta.core.utils.MarkdownSections.listItem;
import static org.tarik.ta.core.utils.MarkdownSections.nestedList;
import static org.tarik.ta.core.utils.MarkdownSections.section;

/**
 * Renders the chain of composite procedures a decomposed atomic step is nested in, and resolves the data of that step.
 * The chain gives the action and verification agents the intent an otherwise ambiguous step ("Click 'Save'") serves.
 */
public final class AncestorChainRenderer {
    private static final String CHAIN_HEADING = "Procedure hierarchy";
    private static final String CHAIN_EXPLANATION = "Innermost first; every level is the parent of the previous one.";
    private static final String DIRECT_PARENT_LABEL = "Direct parent (the procedure this step is a part of)";
    private static final String TEST_STEP_DATA_LABEL = "Data of the whole test step";
    private static final String PRECONDITION_DATA_LABEL = "Data of the whole test case precondition";
    private static final String STEP_NOTES_HEADING = "Notes for the executed step";

    private AncestorChainRenderer() {
    }

    /**
     * Renders one numbered section per ancestor, given outermost first, from the direct parent outwards with the details it
     * declares, followed by the item's data, or an empty string when there are no ancestors.
     */
    public static String renderChain(@NotNull List<Procedure> ancestorChain, @NotNull ExecutionItem item) {
        if (ancestorChain.isEmpty()) {
            return "";
        }
        var sections = new ArrayList<String>();
        sections.add(section(SECTION_LEVEL, CHAIN_HEADING, CHAIN_EXPLANATION));
        var innermostFirst = ancestorChain.reversed();
        for (int level = 1; level <= innermostFirst.size(); level++) {
            sections.add(renderLevel(level, innermostFirst.get(level - 1)));
        }
        sections.add(section(SUBSECTION_LEVEL, itemDataLabel(item), dataList(item.getTestData())));
        return joinSections(sections.toArray(String[]::new));
    }

    /**
     * Renders the chain with the executed atomic step's notes so the verifier judges the screen against the surrounding intent,
     * or an empty string when there is neither.
     */
    public static String renderForVerification(@NotNull List<Procedure> ancestorChain, @NotNull ExecutionItem item,
                                               @Nullable String stepNotes) {
        return joinSections(renderChain(ancestorChain, item), section(SECTION_LEVEL, STEP_NOTES_HEADING, stepNotes));
    }

    /**
     * Returns the item's data for an atomic step matched to the item directly, otherwise the step's own stored data, as a Markdown
     * list, because the item's data belongs to the whole item and is rendered after the outermost level of the chain.
     */
    public static String resolveRelevantData(@NotNull ExecutionItem item, @NotNull Procedure atomicStep,
                                             @NotNull List<Procedure> ancestorChain) {
        var itemData = dataList(item.getTestData());
        if (ancestorChain.isEmpty() && !itemData.isEmpty()) {
            return itemData;
        }
        // A precondition procedure stores its relevant data in the expected results field.
        if (item instanceof PreconditionItem && !isBlank(atomicStep.expectedResults())) {
            return atomicStep.expectedResults();
        }
        return dataList(atomicStep.testData());
    }

    private static String itemDataLabel(ExecutionItem item) {
        return switch (item) {
            case TestStepItem _ -> TEST_STEP_DATA_LABEL;
            case PreconditionItem _ -> PRECONDITION_DATA_LABEL;
        };
    }

    private static String renderLevel(int level, Procedure ancestor) {
        var relation = level == 1 ? DIRECT_PARENT_LABEL : "Parent of level %d".formatted(level - 1);
        return section(SUBSECTION_LEVEL, "Level %d: %s".formatted(level, relation), joinLines(
                ancestor.description(),
                nestedList("Data", ancestor.testData()),
                listItem("Expected results", ancestor.expectedResults()),
                listItem("Notes", ancestor.additionalInfo())));
    }

    private static String dataList(@Nullable List<String> data) {
        return bulletList(requireNonNullElse(data, List.of()));
    }
}
