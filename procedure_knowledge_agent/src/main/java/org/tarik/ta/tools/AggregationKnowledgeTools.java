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
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.knowledge.ProcedureTreeAssembler;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;

import java.util.List;

import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.knowledge.KnowledgeJson.toJson;

/**
 * The tools of an aggregation run: those of an item run and the search for procedures that contain several given ones.
 */
public class AggregationKnowledgeTools extends ProcedureKnowledgeTools {
    static final int MIN_COMMON_PROCEDURES = 2;
    static final int MAX_COMMON_PROCEDURES = 20;

    public AggregationKnowledgeTools(@NotNull ProcedureRepository procedureRepository, @NotNull EmbeddingService embeddingService,
                                     @NotNull ProcedureTreeAssembler treeAssembler, @NotNull KnowledgeAgentConfig config) {
        super(procedureRepository, embeddingService, treeAssembler, config);
    }

    @Tool("Returns as JSON the recorded procedures that contain all the procedures with the given IDs as steps at any depth, each with " +
            "its own data only. Use it to find one recorded workflow that covers several consecutive items, starting from the " +
            "procedures selected for them.")
    public String findCommonContainingProcedures(
            @P("The IDs of the procedures, at least two, each returned by an earlier tool call or given as a starting point")
            List<String> procedureIds) {
        countToolCall();
        if (procedureIds == null || procedureIds.size() < MIN_COMMON_PROCEDURES || procedureIds.size() > MAX_COMMON_PROCEDURES) {
            throw new ToolExecutionException("Give between %d and %d procedure IDs".formatted(MIN_COMMON_PROCEDURES, MAX_COMMON_PROCEDURES),
                    TRANSIENT_TOOL_ERROR);
        }
        var ids = procedureIds.stream().map(this::parseProcedureId).distinct().toList();
        try {
            var ancestors = procedureRepository.findCommonAncestors(ids, config.getMaxTreeDepth(), config.getSearchTopN());
            return toJson(summarize(ancestors));
        } catch (Exception e) {
            throw rethrowAsToolException(e, "searching the procedures containing %s".formatted(ids));
        }
    }
}
