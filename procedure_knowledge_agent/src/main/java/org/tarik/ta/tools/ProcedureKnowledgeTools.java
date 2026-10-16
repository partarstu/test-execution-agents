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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.tools.AbstractTools;
import org.tarik.ta.dto.ProcedureNode;
import org.tarik.ta.dto.ProcedureSummary;
import org.tarik.ta.knowledge.ProcedureTreeAssembler;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.tarik.ta.core.error.ErrorCategory.NON_RETRYABLE_ERROR;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.knowledge.KnowledgeJson.toJson;

/**
 * The read-only tools one selection run explores the recorded procedures with. An instance belongs to one run: it counts the run's tool
 * calls against its cap and remembers every procedure it returned, which is the only source of IDs the run may select.
 */
public class ProcedureKnowledgeTools extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureKnowledgeTools.class);
    static final int MAX_QUERY_LENGTH = 300;

    protected final ProcedureRepository procedureRepository;
    protected final KnowledgeAgentConfig config;
    private final EmbeddingService embeddingService;
    private final ProcedureTreeAssembler treeAssembler;
    private final Map<UUID, String> returnedDescriptions = new ConcurrentHashMap<>();
    private final AtomicInteger toolCalls = new AtomicInteger();

    public ProcedureKnowledgeTools(@NotNull ProcedureRepository procedureRepository, @NotNull EmbeddingService embeddingService,
                                   @NotNull ProcedureTreeAssembler treeAssembler, @NotNull KnowledgeAgentConfig config) {
        this.procedureRepository = procedureRepository;
        this.embeddingService = embeddingService;
        this.treeAssembler = treeAssembler;
        this.config = config;
    }

    @Tool("Searches the recorded procedures whose description is semantically closest to the query. Returns up to the configured number " +
            "of hits, best first, as JSON, each with its own data only and without its children: id, similarity score, description, " +
            "whether it is atomic (one UI action) or composite (a workflow of procedures), whether it is a precondition, the UI " +
            "element an atomic procedure targets, additional info, prerequisites, effects and negated effects. Returns an empty list " +
            "when nothing is similar enough.")
    public String searchProcedures(
            @P("A short description of one action or workflow to search for, e.g. \"log in as an administrator\"") String query) {
        countToolCall();
        if (isBlank(query) || query.length() > MAX_QUERY_LENGTH) {
            throw new ToolExecutionException("The query must be a non-blank text of at most %d characters".formatted(MAX_QUERY_LENGTH),
                    TRANSIENT_TOOL_ERROR);
        }
        try {
            var vector = embeddingService.embedForQuery(query).vector();
            var matches = procedureRepository.findBySemanticSearch(vector, config.getSearchTopN(), config.getSearchMinScore());
            var scores = new HashMap<UUID, Double>();
            matches.forEach(match -> scores.put(match.procedure().id(), match.score()));
            var procedures = matches.stream().map(ProcedureMatch::procedure).toList();
            LOG.debug("Search for '{}' returned {} procedures with the scores {}", query, procedures.size(),
                    matches.stream().map(match -> "%.3f".formatted(match.score())).toList());
            return toJson(remember(treeAssembler.summarize(procedures, scores)));
        } catch (Exception e) {
            throw rethrowAsToolException(e, "searching the recorded procedures");
        }
    }

    @Tool("Returns the whole tree of the recorded procedure with the given ID as JSON: the procedure and its descendants down to the " +
            "atomic procedures, every node with all its data (description, test data labels, expected results, prerequisites, effects, " +
            "negated effects, additional info, how often it was executed successfully, the UI element of an atomic node) and its " +
            "children in their recorded order. A composite whose children were cut off by the size limit has children_truncated set.")
    public String getProcedureTree(@P("The ID of a procedure an earlier tool call returned") String procedureId) {
        countToolCall();
        var id = parseProcedureId(procedureId);
        try {
            var tree = treeAssembler.assembleTree(id)
                    .orElseThrow(() -> new ToolExecutionException("No recorded procedure has the ID %s".formatted(id), TRANSIENT_TOOL_ERROR));
            rememberTree(tree);
            return toJson(tree);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "reading the tree of procedure %s".formatted(id));
        }
    }

    @Tool("Returns as JSON the recorded procedures one level up that contain the procedure with the given ID as one of their steps, each " +
            "with its own data only. Use it when a larger recorded workflow might cover the item together with its neighbours.")
    public String getContainingProcedures(@P("The ID of a procedure an earlier tool call returned") String procedureId) {
        countToolCall();
        var id = parseProcedureId(procedureId);
        try {
            return toJson(remember(treeAssembler.summarize(procedureRepository.findParents(id), Map.of())));
        } catch (Exception e) {
            throw rethrowAsToolException(e, "reading the procedures containing procedure %s".formatted(id));
        }
    }

    /**
     * Returns the description of a procedure a tool of this run returned, or nothing when none did.
     */
    public Optional<String> findReturnedDescription(@Nullable String procedureId) {
        return parseUuid(procedureId).map(returnedDescriptions::get);
    }

    /**
     * Makes procedures known to the run as if a tool had returned them, e.g. the selections an aggregation run starts from.
     */
    public void rememberProcedure(@NotNull UUID procedureId, @NotNull String description) {
        returnedDescriptions.put(procedureId, description);
    }

    public int getToolCallCount() {
        return toolCalls.get();
    }

    // A call beyond the cap is a terminal error, so the run ends instead of exploring on.
    protected void countToolCall() {
        var calls = toolCalls.incrementAndGet();
        if (calls > config.getExplorationMaxToolCalls()) {
            throw new ToolExecutionException("The budget of %d tool calls of this run is exhausted".formatted(config.getExplorationMaxToolCalls()),
                    NON_RETRYABLE_ERROR);
        }
    }

    protected UUID parseProcedureId(@Nullable String procedureId) {
        return parseUuid(procedureId).orElseThrow(() -> new ToolExecutionException(
                "'%s' is not a procedure ID; use an ID exactly as a tool returned it".formatted(procedureId), TRANSIENT_TOOL_ERROR));
    }

    protected List<ProcedureSummary> remember(List<ProcedureSummary> summaries) {
        summaries.forEach(summary -> returnedDescriptions.put(UUID.fromString(summary.id()), summary.description()));
        return summaries;
    }

    protected List<ProcedureSummary> summarize(List<Procedure> procedures) {
        return remember(treeAssembler.summarize(procedures, Map.of()));
    }

    private void rememberTree(ProcedureNode node) {
        returnedDescriptions.put(UUID.fromString(node.id()), node.description());
        node.children().forEach(this::rememberTree);
    }

    private static Optional<UUID> parseUuid(@Nullable String value) {
        if (isBlank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
