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
package org.tarik.ta.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.embedding.Embedding;
import io.javalin.Javalin;
import io.opentelemetry.api.OpenTelemetry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.a2a.AgentCardProducer;
import org.tarik.ta.a2a.KnowledgeAgentExecutor;
import org.tarik.ta.a2a.KnowledgeRequestReader;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.a2a.AgentExecutionResource;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.ChatModelEventListener;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.smoke.A2aTestClient;
import org.tarik.ta.core.smoke.A2aTestClient.SseStream;
import org.tarik.ta.core.smoke.ScriptedChatModel;
import org.tarik.ta.core.smoke.ScriptedChatModel.ScriptedToolCall;
import org.tarik.ta.core.telemetry.GenAiMetrics;
import org.tarik.ta.knowledge.KnowledgeSelectionService;
import org.tarik.ta.knowledge.ProcedureSelector;
import org.tarik.ta.knowledge.ProcedureTreeAssembler;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.tarik.ta.knowledge.ProcedureFixtures.atomic;
import static org.tarik.ta.knowledge.ProcedureFixtures.child;
import static org.tarik.ta.knowledge.ProcedureFixtures.composite;
import static org.tarik.ta.knowledge.ProcedureFixtures.root;

/**
 * Full-stack smoke test of the procedure knowledge agent: one request travels HTTP → {@link AgentExecutionResource} → the real
 * {@link KnowledgeAgentExecutor}, selection service, selector, tools and tree assembler, with only the LLM (a {@link ScriptedChatModel})
 * and the Neo4j repository and embedding service mocked.
 */
@Tag("smoke")
@DisplayName("Procedure knowledge agent - full-stack A2A smoke")
class KnowledgeAgentA2aSmokeTest {
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(20);
    private static final String AUTH_TOKEN = "smoke-token";
    private static final String OPERATION = "scripted_selection_run";

    private final Procedure openCatalog = composite("Open the product catalog");
    private final Procedure clickCatalog = atomic("Click the catalog link");
    private KnowledgeAgentConfig config;
    private ProcedureRepository procedureRepository;
    private Javalin server;
    private A2aTestClient client;

    @BeforeEach
    void setUp() {
        config = spy(new KnowledgeAgentConfig());
        procedureRepository = mock(ProcedureRepository.class);
        when(procedureRepository.findBySemanticSearch(any(), anyInt(), anyDouble())).thenReturn(List.of(new ProcedureMatch(openCatalog, 0.93)));
        when(procedureRepository.findTargetedUiElements(any())).thenReturn(Map.of());
        when(procedureRepository.findSubtree(openCatalog.id(), config.getMaxTreeDepth(), config.getMaxTreeNodes()))
                .thenReturn(List.of(root(openCatalog, 1), child(clickCatalog, openCatalog, 1, 0)));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("message/stream selects the procedures per item and per run of items and returns them as original trees")
    void messageStreamReturnsTheSelectedProcedureTrees() {
        startServer();

        try (SseStream stream = client.openSse(streamBody(), AUTH_TOKEN)) {
            assertThat(stream.await(inState("TASK_STATE_COMPLETED"), STREAM_TIMEOUT)).as("the task must complete").isPresent();

            var events = combine(stream);
            assertThat(events).contains("procedure_selection", "\"item_id\":\"S1\"", "\"item_ids\":[\"S1\",\"S2\"]",
                    "Open the product catalog", "Click the catalog link", "\"id\":\"%s\"".formatted(openCatalog.id()));
            assertThat(events).as("the usage of all five model calls of the runs is reported for the task").contains("token_usage",
                    "\\\"operationName\\\":\\\"%s\\\"".formatted(OPERATION), "\\\"inputTokens\\\":35", "\\\"outputTokens\\\":15");
        }
    }

    @Test
    @DisplayName("A request without the bearer token is rejected with 401")
    void requestWithoutTokenIsRejected() {
        startServer();

        assertThat(client.postRpc(streamBody(), null).status()).isEqualTo(401);
    }

    private void startServer() {
        int port = findFreePort();
        when(config.getStartPort()).thenReturn(port);
        when(config.getHost()).thenReturn("localhost");
        when(config.getAuthToken()).thenReturn(Optional.of(AUTH_TOKEN));
        var budgetManager = new BudgetManager(config);
        var model = new ScriptedChatModel(ScriptedChatModel.ofSequence(this::script), List.of(new ChatModelEventListener(budgetManager,
                new GenAiMetrics(OpenTelemetry.noop()), OPERATION, "google", ScriptedChatModel.MODEL_NAME, null)));
        var modelFactory = mock(ModelFactory.class);
        when(modelFactory.getModel(anyString(), anyString(), any())).thenReturn(new GenAiModel(model));
        var embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embedForQuery(anyString())).thenReturn(Embedding.from(new float[]{0.1f}));
        var treeAssembler = new ProcedureTreeAssembler(procedureRepository, config);
        var selector = new ProcedureSelector(modelFactory, config, procedureRepository, embeddingService, treeAssembler);
        var executor = new KnowledgeAgentExecutor(new KnowledgeRequestReader(config),
                new KnowledgeSelectionService(selector, treeAssembler, budgetManager, config), budgetManager);

        var resource = new AgentExecutionResource(() -> executor, () -> new AgentCardProducer("http://localhost:" + port, config).agentCard());
        initialize(resource);
        server = new AbstractServer(resource, config).start();
        client = new A2aTestClient("http://localhost:" + port);
    }

    /**
     * Scripts the item run of S1 to find and select the catalog procedure, the other item runs to find nothing, and the aggregation run
     * to merge S1 and S2 into that procedure.
     */
    private List<ScriptedToolCall> script(String userText) {
        if (userText.contains("## Procedures selected for the single items")) {
            return List.of(new ScriptedToolCall("returnAggregationSelections",
                    "{\"result\":{\"aggregations\":[{\"itemIds\":[\"S1\",\"S2\"],\"procedureIds\":[\"%s\"]}]}}".formatted(openCatalog.id())));
        }
        if (userText.contains("## Item to select procedures for\nS1")) {
            return List.of(new ScriptedToolCall("searchProcedures", "{\"query\":\"open the catalog\"}"),
                    new ScriptedToolCall("returnItemSelection",
                            "{\"selection\":{\"itemId\":\"S1\",\"procedureIds\":[\"%s\"]}}".formatted(openCatalog.id())));
        }
        return List.of(ScriptedToolCall.plainText("No recorded procedure matches this item"));
    }

    /**
     * Runs the resource's {@code @PostConstruct} initializer, which the DI container invokes in production.
     */
    private static void initialize(@NotNull AgentExecutionResource resource) {
        try {
            var init = AgentExecutionResource.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(resource);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not initialize the AgentExecutionResource under test", e);
        }
    }

    private static String streamBody() {
        return """
                {"jsonrpc":"2.0","method":"message/stream","id":1,"params":{"message":{"messageId":"msg-1","role":"ROLE_USER",
                "parts":[{"data":{"name":"Order a lamp","preconditions":["The user is logged in"],"steps":[
                {"action":"Open the catalog","test_data":[],"expected_results":"The catalog is shown"},
                {"action":"Add the lamp to the cart","test_data":["product=Lamp"],"expected_results":"The cart holds the lamp"}]}}]}}}
                """;
    }

    private static Predicate<JsonNode> inState(@NotNull String state) {
        return result -> stateOf(result).equals(state);
    }

    private static String stateOf(@NotNull JsonNode result) {
        JsonNode statusUpdate = result.path("statusUpdate");
        if (!statusUpdate.isMissingNode()) {
            return statusUpdate.path("status").path("state").asText("");
        }
        return result.path("task").path("status").path("state").asText("");
    }

    private static String combine(@NotNull SseStream stream) {
        return stream.received().stream().map(JsonNode::toString).reduce("", String::concat);
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not find a free port", e);
        }
    }
}
