/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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
import com.github.tomakehurst.wiremock.WireMockServer;
import io.javalin.Javalin;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.tarik.ta.ApiTestAgentConfig;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.a2a.AbstractAgentExecutor;
import org.tarik.ta.core.a2a.AgentExecutionResource;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.smoke.A2aTestClient;
import org.tarik.ta.core.smoke.A2aTestClient.SseStream;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.smoke.ScriptedChatModel.ScriptedToolCall;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tarik.ta.smoke.ApiAgentSmokeTest.buildAgent;
import static org.tarik.ta.smoke.ApiAgentSmokeTest.newSmokeConfig;
import static org.tarik.ta.smoke.ApiAgentSmokeTest.result;
import static org.tarik.ta.smoke.ApiAgentSmokeTest.sendGet;

/**
 * Full-stack smoke test closing the seam the other suites leave open: {@link ApiAgentSmokeTest} calls
 * {@code executeTestCase()} directly and {@code CoreA2aSmokeTest} drives the transport against a recording executor
 * stand-in. Here one request travels the whole production path — HTTP → {@link AgentExecutionResource} → an
 * {@link AbstractAgentExecutor} → the real {@code ApiTestAgent} with real tools → WireMock — with only the LLM
 * (a {@link ScriptedChatModel}) and the {@link TestCaseExtractor} mocked. The DI-produced {@code ApiAgentExecutor}
 * (a thin pass-through with its own unit test) is deliberately not bootstrapped: wiring the Avaje request scopes
 * would drag {@code ModelFactory} into the test for little extra signal.
 */
@Tag("smoke")
@DisplayName("API agent - full-stack A2A transport-to-agent smoke")
class ApiAgentA2aSmokeTest {

    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(20);

    private static WireMockServer wireMock;

    private ApiTestAgentConfig config;
    private TestCaseExtractor testCaseExtractor;
    private LogCapture logCapture;
    private ScriptedChatModel model;
    private Javalin server;
    private A2aTestClient client;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        config = newSmokeConfig();
        new BudgetManager(config);
        testCaseExtractor = mock(TestCaseExtractor.class);
        logCapture = mock(LogCapture.class);
        lenient().when(logCapture.getLogs()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("message/stream drives the real ApiTestAgent end to end and streams the step + final result")
    void messageStreamRunsRealApiAgentEndToEnd() {
        wireMock.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody("pong")));
        var step = new TestStep("Send a GET request to /ping", List.of(), "Status is 200");
        var testCase = new TestCase("Full stack ping", List.of(), List.of(step));
        when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.of(testCase));
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/ping"), result(true, "Received status 200"));
        model = new ScriptedChatModel(ScriptedChatModel.ofSequence(script));
        startServer();

        try (SseStream stream = client.openSse(streamBody("run"), null)) {
            assertThat(stream.await(inState("TASK_STATE_WORKING"), STREAM_TIMEOUT)).isPresent();
            assertThat(stream.await(inState("TASK_STATE_COMPLETED"), STREAM_TIMEOUT))
                    .as("the stream must reach a terminal completed state").isPresent();

            // Awaiting the terminal event drains every earlier event; the whole stream is now visible.
            String allEvents = combine(stream);
            assertThat(allEvents).contains("test_step_result").contains("final_result")
                    .contains("PASSED").contains("Full stack ping");
        }
        wireMock.verify(getRequestedFor(urlEqualTo("/ping")));
    }

    private void startServer() {
        int port = findFreePort();
        lenient().when(config.getStartPort()).thenReturn(port);
        lenient().when(config.getHost()).thenReturn("localhost");
        lenient().when(config.getAuthToken()).thenReturn(Optional.empty());

        AgentExecutionResource resource = new AgentExecutionResource(ScriptedApiAgentExecutor::new, () -> agentCard());
        initialize(resource);
        server = new AbstractServer(resource, config).start();
        client = new A2aTestClient("http://localhost:" + port);
    }

    private static AgentCard agentCard() {
        return AgentCard.builder()
                .name("api-full-stack-smoke-agent")
                .description("Full-stack smoke-test agent")
                .version("1.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost")))
                .build();
    }

    /**
     * Runs the resource's {@code @PostConstruct} initializer, which the DI container invokes in production. The hook is
     * package-private, so it is called reflectively here rather than relaxing its visibility purely for the test.
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

    private static Predicate<JsonNode> inState(@NotNull String state) {
        return result -> stateOf(result).equals(state);
    }

    /** Reads the task state out of whichever streaming event kind ({@code statusUpdate} or {@code task}) carries it. */
    private static String stateOf(@NotNull JsonNode result) {
        JsonNode statusUpdate = result.path("statusUpdate");
        if (!statusUpdate.isMissingNode()) {
            return statusUpdate.path("status").path("state").asText("");
        }
        return result.path("task").path("status").path("state").asText("");
    }

    private static String combine(@NotNull SseStream stream) {
        return stream.received().stream().map(JsonNode::toString).reduce("", (left, right) -> left + right);
    }

    private static String streamBody(@NotNull String text) {
        return ("{\"jsonrpc\":\"2.0\",\"method\":\"message/stream\",\"params\":{\"message\":{\"messageId\":\"msg-1\"," +
                "\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"%s\"}]}},\"id\":1}").formatted(text);
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not find a free port", e);
        }
    }

    /**
     * A thin stand-in for the production {@code ApiAgentExecutor}: it hand-wires the same real {@code ApiTestAgent}
     * graph as {@link ApiAgentSmokeTest#buildAgent}, but around a {@link TestExecutionContext} carrying the transport's
     * streaming emitter, so step/final events flow to the SSE client.
     */
    private final class ScriptedApiAgentExecutor extends AbstractAgentExecutor {
        @Override
        protected TestExecutionResult executeTestCase(String message, StreamingEventEmitter eventEmitter) {
            var executionContext = new TestExecutionContext(eventEmitter);
            var apiContext = new ApiContext(config);
            return buildAgent(model, config, apiContext, executionContext, testCaseExtractor, logCapture)
                    .executeTestCase(message);
        }
    }
}
