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
import org.tarik.ta.dto.AggregationSelectionResult;
import org.tarik.ta.dto.KnowledgeTestCase;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;

/**
 * Ends an aggregation run with its selections, after checking each against the test case and the procedures the run knows.
 */
public class AggregationSelectionResultTool {
    private final KnowledgeTestCase testCase;
    private final SelectionValidation validation;

    public AggregationSelectionResultTool(@NotNull KnowledgeTestCase testCase, @NotNull ProcedureKnowledgeTools explorationTools,
                                          int maxSelections, int maxCorrections) {
        this.testCase = testCase;
        this.validation = new SelectionValidation(explorationTools, maxSelections, maxCorrections);
    }

    @Tool(value = "Ends the run and returns the procedures selected for the runs of consecutive items that one recorded workflow covers. " +
            "Each entry covers at least two consecutive items of one kind (only preconditions or only test steps) and selects at least " +
            "one procedure; only IDs the tools of this run returned or the starting points may be selected. Return an empty list when " +
            "no recorded workflow covers consecutive items.", returnBehavior = IMMEDIATE_IF_LAST)
    public AggregationSelectionResult returnAggregationSelections(@P("The selections for the runs of consecutive items")
                                                                  AggregationSelectionResult result) {
        if (result == null) {
            throw validation.invalid("The result is missing");
        }
        for (var aggregation : result.aggregations()) {
            var owner = "The selection for %s".formatted(aggregation.itemIds());
            if (!testCase.isConsecutiveRunOfOneKind(aggregation.itemIds())) {
                throw validation.invalid(
                        "%s does not name at least two existing consecutive items of one kind in their order".formatted(owner));
            }
            if (aggregation.procedureIds().isEmpty()) {
                throw validation.invalid("%s selects no procedure; leave it out instead".formatted(owner));
            }
            validation.requireValidProcedureIds(aggregation.procedureIds(), owner);
        }
        return result;
    }
}
