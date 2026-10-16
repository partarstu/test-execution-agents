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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.ItemSelection;
import org.tarik.ta.dto.TestCaseItem;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;

/**
 * Ends an item run with its selection, after checking it against the item and the procedures the run's tools returned.
 */
public class ItemSelectionResultTool {
    private final TestCaseItem item;
    private final SelectionValidation validation;

    public ItemSelectionResultTool(@NotNull TestCaseItem item, @NotNull ProcedureKnowledgeTools explorationTools, int maxSelections,
                                   int maxCorrections) {
        this.item = item;
        this.validation = new SelectionValidation(explorationTools, maxSelections, maxCorrections);
    }

    @Tool(value = "Ends the run and returns the procedures selected for the item. Only IDs returned by the tools of this run may be " +
            "selected. Return an empty list when no recorded procedure really matches the item.", returnBehavior = IMMEDIATE_IF_LAST)
    public ItemSelection returnItemSelection(@P("The selection for the item") ItemSelection selection) {
        if (selection == null || !item.id().equals(selection.itemId())) {
            throw validation.invalid("The selection must be for item %s".formatted(item.id()));
        }
        validation.requireValidProcedureIds(selection.procedureIds(), "The selection for item %s".formatted(item.id()));
        return selection;
    }
}
