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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.core.AgentConfig.ModelProvider;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.dto.ItemProcedureSelection;
import org.tarik.ta.dto.SelectedProcedure;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.tarik.ta.knowledge.ProcedureFixtures.composite;
import static org.tarik.ta.knowledge.ProcedureFixtures.testCase;

@ExtendWith(MockitoExtension.class)
class ProcedureSelectorTest {
    private static final int MAX_TOOL_CALLS = 40;

    @Mock
    private ModelFactory modelFactory;
    @Mock
    private KnowledgeAgentConfig config;
    @Mock
    private ProcedureRepository procedureRepository;
    @Mock
    private EmbeddingService embeddingService;

    private ProcedureSelector selector;

    @BeforeEach
    void setUp() {
        lenient().when(config.getItemSelectionAgentPromptVersion()).thenReturn("v1.0.0");
        lenient().when(config.getAggregationSelectionAgentPromptVersion()).thenReturn("v1.0.0");
        lenient().when(config.getItemSelectionAgentModelName()).thenReturn("item-model");
        lenient().when(config.getItemSelectionAgentModelProvider()).thenReturn(ModelProvider.GOOGLE);
        lenient().when(config.getAggregationSelectionAgentModelName()).thenReturn("aggregation-model");
        lenient().when(config.getAggregationSelectionAgentModelProvider()).thenReturn(ModelProvider.GOOGLE);
        lenient().when(config.getMaxSelectionsPerItem()).thenReturn(3);
        lenient().when(config.getExplorationMaxToolCalls()).thenReturn(MAX_TOOL_CALLS);
        lenient().when(config.getSelectionMaxRetries()).thenReturn(3);
        lenient().when(config.getSearchTopN()).thenReturn(10);
        lenient().when(config.getSearchMinScore()).thenReturn(0.8);
        lenient().when(embeddingService.embedForQuery(anyString())).thenReturn(Embedding.from(new float[]{0.1f}));
        selector = new ProcedureSelector(modelFactory, config, procedureRepository, embeddingService,
                new ProcedureTreeAssembler(procedureRepository, config));
    }

    @Test
    void selectForItem_shouldReturnTheSelectedProceduresWithTheirDescriptions() {
        var openCatalog = composite("Open the product catalog");
        givenSearchResult(openCatalog);
        var model = givenModel(
                toolCall("searchProcedures", "{\"query\":\"open catalog\"}"),
                toolCall("returnItemSelection", selectionArguments("S1", openCatalog.id())));

        var selection = selector.selectForItem(testCase(), testCase().findItem("S1").orElseThrow());

        assertThat(selection).isEqualTo(new ItemProcedureSelection("S1", List.of(new SelectedProcedure(openCatalog.id(),
                "Open the product catalog"))));
        assertThat(model.firstUserText()).contains("## Item to select procedures for\nS1", "### S2", "- Action: Add the product to the cart",
                "## Exploration tool calls available in this run\n40");
    }

    @Test
    void selectForItem_shouldLetTheModelCorrectAnInvalidSelectionWithinTheRun() {
        var openCatalog = composite("Open the product catalog");
        var foreignId = UUID.randomUUID();
        givenSearchResult(openCatalog);
        var model = givenModel(
                toolCall("searchProcedures", "{\"query\":\"open catalog\"}"),
                toolCall("returnItemSelection", selectionArguments("S1", foreignId)),
                toolCall("returnItemSelection", selectionArguments("S1", openCatalog.id())));

        var selection = selector.selectForItem(testCase(), testCase().findItem("S1").orElseThrow());

        assertThat(selection.procedures()).extracting(SelectedProcedure::id).containsExactly(openCatalog.id());
        assertThat(model.toolResults()).anyMatch(result -> result.contains(foreignId.toString()) && result.contains("no tool returned"));
    }

    @Test
    void selectForItem_shouldSelectNothing_whenTheToolCallCapEndsTheRun() {
        when(config.getExplorationMaxToolCalls()).thenReturn(1);
        when(procedureRepository.findBySemanticSearch(any(), anyInt(), anyDouble())).thenReturn(List.of());
        givenModel(
                toolCall("searchProcedures", "{\"query\":\"open catalog\"}"),
                toolCall("searchProcedures", "{\"query\":\"catalog page\"}"));

        var selection = selector.selectForItem(testCase(), testCase().findItem("S1").orElseThrow());

        assertThat(selection).isEqualTo(new ItemProcedureSelection("S1", List.of()));
    }

    @Test
    void selectForItem_shouldSelectNothing_whenTheModelAnswersInTextInsteadOfCallingTheResultTool() {
        givenModel(AiMessage.from("I found nothing"));

        var selection = selector.selectForItem(testCase(), testCase().findItem("S1").orElseThrow());

        assertThat(selection).isEqualTo(new ItemProcedureSelection("S1", List.of()));
    }

    @Test
    void selectAggregations_shouldSelectNothing_whenTheRunEndsWithoutAResult() {
        givenModel(AiMessage.from("No workflow covers consecutive items"));

        assertThat(selector.selectAggregations(testCase(), List.of())).isEmpty();
    }

    @Test
    void selectAggregations_shouldStartFromTheItemSelections() {
        var addToCart = composite("Add a product to the cart and check out");
        var checkout = composite("Check out");
        var itemSelections = List.of(
                new ItemProcedureSelection("S1", List.of()),
                new ItemProcedureSelection("S2", List.of(new SelectedProcedure(addToCart.id(), addToCart.description()))),
                new ItemProcedureSelection("S3", List.of(new SelectedProcedure(checkout.id(), checkout.description()))));
        var model = givenModel(toolCall("returnAggregationSelections",
                "{\"result\":{\"aggregations\":[{\"itemIds\":[\"S2\",\"S3\"],\"procedureIds\":[\"%s\"]}]}}".formatted(addToCart.id())));

        var aggregations = selector.selectAggregations(testCase(), itemSelections);

        assertThat(aggregations).hasSize(1);
        assertThat(aggregations.getFirst().itemIds()).containsExactly("S2", "S3");
        assertThat(aggregations.getFirst().procedures()).containsExactly(new SelectedProcedure(addToCart.id(), addToCart.description()));
        assertThat(model.firstUserText()).contains("## Procedures selected for the single items",
                "- S2:\n  - %s: Add a product to the cart and check out".formatted(addToCart.id()));
    }

    private void givenSearchResult(Procedure procedure) {
        when(procedureRepository.findBySemanticSearch(any(), anyInt(), anyDouble())).thenReturn(List.of(new ProcedureMatch(procedure, 0.9)));
        lenient().when(procedureRepository.findTargetedUiElements(any())).thenReturn(Map.of());
    }

    private ScriptedChatModel givenModel(AiMessage... turns) {
        var model = new ScriptedChatModel(turns);
        when(modelFactory.getModel(anyString(), anyString(), any())).thenReturn(new GenAiModel(model));
        return model;
    }

    private static String selectionArguments(String itemId, UUID procedureId) {
        return "{\"selection\":{\"itemId\":\"%s\",\"procedureIds\":[\"%s\"]}}".formatted(itemId, procedureId);
    }

    private static AiMessage toolCall(String toolName, String arguments) {
        return AiMessage.from(ToolExecutionRequest.builder().id(UUID.randomUUID().toString()).name(toolName).arguments(arguments).build());
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final Deque<AiMessage> turns;
        private final List<ChatRequest> requests = new ArrayList<>();

        private ScriptedChatModel(AiMessage... turns) {
            this.turns = new ArrayDeque<>(Arrays.asList(turns));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            return ChatResponse.builder().aiMessage(turns.removeFirst()).build();
        }

        private String firstUserText() {
            return requests.getFirst().messages().stream()
                    .filter(UserMessage.class::isInstance)
                    .map(message -> ((UserMessage) message).singleText())
                    .findFirst()
                    .orElseThrow();
        }

        private List<String> toolResults() {
            return requests.getLast().messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(message -> ((ToolExecutionResultMessage) message).text())
                    .toList();
        }
    }
}
