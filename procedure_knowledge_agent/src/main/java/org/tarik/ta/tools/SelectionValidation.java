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

import org.tarik.ta.core.exceptions.ToolExecutionException;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.tarik.ta.core.error.ErrorCategory.NON_RETRYABLE_ERROR;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;

/**
 * The checks the selections of one run pass, shared by the result tools of the item and the aggregation runs. It counts the rejected
 * selections of its run, so that only they, not the errors of the exploration tools, use up the allowed corrections.
 */
final class SelectionValidation {
    private final ProcedureKnowledgeTools explorationTools;
    private final int maxSelections;
    private final int maxCorrections;
    private final AtomicInteger rejections = new AtomicInteger();

    SelectionValidation(ProcedureKnowledgeTools explorationTools, int maxSelections, int maxCorrections) {
        this.explorationTools = explorationTools;
        this.maxSelections = maxSelections;
        this.maxCorrections = maxCorrections;
    }

    /**
     * Throws the error of {@link #invalid(String)} unless every ID is distinct and was returned by a tool of this run and there are at
     * most the allowed number of them.
     */
    void requireValidProcedureIds(List<String> procedureIds, String owner) {
        if (procedureIds.size() > maxSelections) {
            throw invalid("%s has %d procedures, but at most %d may be selected".formatted(owner, procedureIds.size(), maxSelections));
        }
        if (new HashSet<>(procedureIds).size() != procedureIds.size()) {
            throw invalid("%s lists a procedure more than once".formatted(owner));
        }
        for (var procedureId : procedureIds) {
            if (explorationTools.findReturnedDescription(procedureId).isEmpty()) {
                throw invalid(("%s selects '%s', which no tool returned in this run; select only IDs exactly as the tools returned " +
                        "them").formatted(owner, procedureId));
            }
        }
    }

    /**
     * Returns a retryable tool error telling the model what to correct, or a terminal one that ends the run once the selection was
     * rejected more often than the allowed corrections.
     */
    ToolExecutionException invalid(String reason) {
        if (rejections.incrementAndGet() > maxCorrections) {
            return new ToolExecutionException("%s; the selection is still invalid after %d corrections".formatted(reason, maxCorrections),
                    NON_RETRYABLE_ERROR);
        }
        return new ToolExecutionException("%s. Correct the selection and return it again.".formatted(reason), TRANSIENT_TOOL_ERROR);
    }
}
