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

import dev.langchain4j.model.chat.ChatModel;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.agents.AggregationSelectionAgent;
import org.tarik.ta.agents.ItemSelectionAgent;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.model.DefaultToolErrorHandler;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.dto.AggregationProcedureSelection;
import org.tarik.ta.dto.AggregationSelectionResult;
import org.tarik.ta.dto.ItemProcedureSelection;
import org.tarik.ta.dto.ItemSelection;
import org.tarik.ta.dto.KnowledgeTestCase;
import org.tarik.ta.dto.SelectedProcedure;
import org.tarik.ta.dto.TestCaseItem;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;
import org.tarik.ta.tools.AggregationKnowledgeTools;
import org.tarik.ta.tools.AggregationSelectionResultTool;
import org.tarik.ta.tools.ItemSelectionResultTool;
import org.tarik.ta.tools.ProcedureKnowledgeTools;

import java.util.List;
import java.util.UUID;

import static dev.langchain4j.service.AiServices.builder;
import static org.tarik.ta.core.model.DefaultToolErrorHandler.forToolArguments;
import static org.tarik.ta.core.utils.MarkdownSections.userMessageContents;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

/**
 * Runs the selection runs of a test case: one item run per precondition or test step and one aggregation run that starts from their
 * selections. Every run gets its own tools and agent, so runs can execute in parallel; a failed run selects nothing and is logged,
 * while a run started after the request's budget is exhausted throws the {@link org.tarik.ta.core.exceptions.BudgetExceededException}.
 */
@Singleton
public class ProcedureSelector {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureSelector.class);
    private static final String ITEM_SELECTION_OPERATION = "procedure_item_selection";
    private static final String AGGREGATION_SELECTION_OPERATION = "procedure_aggregation_selection";
    private static final long NO_RETRY_DELAY_MILLIS = 0;
    private static final long NO_RETRY_TIMEOUT_MILLIS = 0;

    private final ModelFactory modelFactory;
    private final KnowledgeAgentConfig config;
    private final ProcedureRepository procedureRepository;
    private final EmbeddingService embeddingService;
    private final ProcedureTreeAssembler treeAssembler;
    private final String itemSelectionPrompt;
    private final String aggregationSelectionPrompt;

    public ProcedureSelector(ModelFactory modelFactory, KnowledgeAgentConfig config, ProcedureRepository procedureRepository,
                             EmbeddingService embeddingService, ProcedureTreeAssembler treeAssembler) {
        this.modelFactory = modelFactory;
        this.config = config;
        this.procedureRepository = procedureRepository;
        this.embeddingService = embeddingService;
        this.treeAssembler = treeAssembler;
        this.itemSelectionPrompt = loadSystemPrompt(ITEM_SELECTION_OPERATION, config.getItemSelectionAgentPromptVersion(),
                "procedure_item_selection_prompt.md");
        this.aggregationSelectionPrompt = loadSystemPrompt(AGGREGATION_SELECTION_OPERATION, config.getAggregationSelectionAgentPromptVersion(),
                "procedure_aggregation_selection_prompt.md");
    }

    public ItemProcedureSelection selectForItem(@NotNull KnowledgeTestCase testCase, @NotNull TestCaseItem item) {
        var tools = new ProcedureKnowledgeTools(procedureRepository, embeddingService, treeAssembler, config);
        var resultTool = new ItemSelectionResultTool(item, tools, config.getMaxSelectionsPerItem(), config.getSelectionMaxRetries());
        var model = modelFactory.getModel(ITEM_SELECTION_OPERATION, config.getItemSelectionAgentModelName(),
                config.getItemSelectionAgentModelProvider());
        var agent = buildAgent(ItemSelectionAgent.class, model.chatModel(), itemSelectionPrompt, tools, resultTool, ItemSelection.class);
        var request = SelectionRequests.forItem(testCase, item, config.getMaxSelectionsPerItem(), config.getExplorationMaxToolCalls());
        var outcome = agent.executeAndGetResult(() -> agent.select(userMessageContents(request)));
        // A model answering in text instead of calling the result tool leaves its text as the payload, so the type is checked.
        Object payload = outcome.getResultPayload();
        if (!outcome.isSuccess() || !(payload instanceof ItemSelection selection)) {
            LOG.warn("The item run for {} of test case '{}' ended without a selection: {}", item.id(), testCase.name(), outcome.getMessage());
            return new ItemProcedureSelection(item.id(), List.of());
        }
        LOG.info("The item run for {} of test case '{}' selected {} procedures", item.id(), testCase.name(), selection.procedureIds().size());
        return new ItemProcedureSelection(item.id(), toSelectedProcedures(selection.procedureIds(), tools));
    }

    public List<AggregationProcedureSelection> selectAggregations(@NotNull KnowledgeTestCase testCase,
                                                                  @NotNull List<ItemProcedureSelection> itemSelections) {
        var tools = new AggregationKnowledgeTools(procedureRepository, embeddingService, treeAssembler, config);
        itemSelections.forEach(selection -> selection.procedures()
                .forEach(procedure -> tools.rememberProcedure(procedure.id(), procedure.description())));
        var resultTool = new AggregationSelectionResultTool(testCase, tools, config.getMaxSelectionsPerItem(),
                config.getSelectionMaxRetries());
        var model = modelFactory.getModel(AGGREGATION_SELECTION_OPERATION, config.getAggregationSelectionAgentModelName(),
                config.getAggregationSelectionAgentModelProvider());
        var agent = buildAgent(AggregationSelectionAgent.class, model.chatModel(), aggregationSelectionPrompt, tools, resultTool,
                AggregationSelectionResult.class);
        var request = SelectionRequests.forAggregation(testCase, itemSelections, config.getMaxSelectionsPerItem(),
                config.getExplorationMaxToolCalls());
        var outcome = agent.executeAndGetResult(() -> agent.select(userMessageContents(request)));
        Object payload = outcome.getResultPayload();
        if (!outcome.isSuccess() || !(payload instanceof AggregationSelectionResult result)) {
            LOG.warn("The aggregation run of test case '{}' ended without a selection: {}", testCase.name(), outcome.getMessage());
            return List.of();
        }
        LOG.info("The aggregation run of test case '{}' selected {} runs of items", testCase.name(), result.aggregations().size());
        return result.aggregations().stream()
                .map(aggregation -> new AggregationProcedureSelection(aggregation.itemIds(),
                        toSelectedProcedures(aggregation.procedureIds(), tools)))
                .toList();
    }

    // Every exploration call of the run and every correction of a rejected selection may fail recoverably; the tools end the run at
    // their own caps, so the handler's limit and the round trips (plus one for the final result) are only a backstop.
    private int maxRecoverableErrors() {
        return config.getExplorationMaxToolCalls() + config.getSelectionMaxRetries();
    }

    private <A, R extends FinalResult> A buildAgent(Class<A> agentClass, ChatModel chatModel, String prompt, ProcedureKnowledgeTools tools,
                                                   Object resultTool, Class<R> resultClass) {
        var retryPolicy = new RetryPolicy(maxRecoverableErrors(), NO_RETRY_DELAY_MILLIS, NO_RETRY_TIMEOUT_MILLIS);
        return builder(agentClass)
                .chatModel(chatModel)
                .systemMessageProvider(_ -> prompt)
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(tools), List.of(resultTool), resultClass))
                .toolExecutionErrorHandler(new DefaultToolErrorHandler(retryPolicy))
                .toolArgumentsErrorHandler(forToolArguments(retryPolicy))
                .maxToolCallingRoundTrips(maxRecoverableErrors() + 1)
                .build();
    }

    private static List<SelectedProcedure> toSelectedProcedures(List<String> procedureIds, ProcedureKnowledgeTools tools) {
        return procedureIds.stream()
                .map(procedureId -> new SelectedProcedure(UUID.fromString(procedureId.strip()),
                        tools.findReturnedDescription(procedureId).orElseThrow()))
                .toList();
    }
}
