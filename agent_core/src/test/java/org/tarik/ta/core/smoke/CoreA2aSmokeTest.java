/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.a2a.AbstractAgentExecutor;
import org.tarik.ta.core.a2a.AgentExecutionResource;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.smoke.A2aTestClient.Response;
import org.tarik.ta.core.smoke.A2aTestClient.SseStream;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;

/**
 * Hermetic end-to-end smoke test for the shared A2A surface in {@code agent_core}. Unlike the unit tests, which drive
 * {@link AgentExecutionResource} with mocked Javalin contexts, this suite starts the real {@link AbstractServer} over
 * HTTP (real {@link AgentExecutionResource} + a recording {@link AbstractAgentExecutor}) and exercises the real
 * JSON-RPC + Server-Sent-Events transport end to end through {@link A2aTestClient}. Only the agent's test-execution
 * body is a stand-in (the recording executor); the discovery, auth, request-routing, task lifecycle and streaming
 * machinery are the production ones.
 */
@Tag("smoke")
@DisplayName("agent_core - A2A HTTP + SSE transport smoke")
class CoreA2aSmokeTest {

    private static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";
    private static final String TOKEN = "s3cr3t-token";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentCard agentCard;
    private RecordingAgentExecutor executor;
    private Javalin server;
    private A2aTestClient client;

    @BeforeEach
    void setUp() {
        executor = new RecordingAgentExecutor();
        agentCard = AgentCard.builder()
                .name("core-smoke-agent")
                .description("Smoke-test agent")
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

    @AfterEach
    void tearDown() {
        // Release any execution still blocked in the recording executor so its worker thread can finish.
        executor.resume();
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("Agent card is public and advertises streaming even when a token is configured")
    void agentCardIsPublicAndAdvertisesStreaming() throws Exception {
        startServer(Optional.of(TOKEN));

        Response response = client.get(AGENT_CARD_PATH, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(advertisesStreaming(MAPPER.readTree(response.body()))).isTrue();
    }

    @Test
    @DisplayName("Main endpoint requires a valid bearer token: 401 when missing/wrong, 200 when correct")
    void mainEndpointRequiresBearerToken() {
        startServer(Optional.of(TOKEN));
        String body = sendBody("run");

        assertThat(client.postRpc(body, null).status()).isEqualTo(401);
        assertThat(client.postRpc(body, "wrong-token").status()).isEqualTo(401);
        assertThat(client.postRpc(body, TOKEN).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("message/send returns the consolidated TestExecutionResult")
    void messageSendReturnsConsolidatedResult() {
        executor.withTestCaseName("Core smoke send").withPrecondition("service is reachable")
                .withStep(new TestStep("do the thing", List.of(), "it is done")).withLogs(List.of("started", "finished"));
        startServer(Optional.empty());

        Response response = client.postRpc(sendBody("run"), null);

        assertThat(response.status()).isEqualTo(200);
        // The consolidated result JSON (test case name + PASSED status) is carried in the final message / artifact.
        assertThat(response.body()).contains("Core smoke send").contains("PASSED");
        assertThat(executor.receivedMessages()).containsExactly("run");
    }

    @Test
    @DisplayName("message/stream streams working status, step + precondition + log artifacts and the final result")
    void messageStreamStreamsAllEventKinds() {
        executor.withTestCaseName("Core smoke stream").withPrecondition("service is reachable")
                .withStep(new TestStep("do the thing", List.of(), "it is done")).withLogs(List.of("started", "finished"));
        startServer(Optional.empty());

        try (SseStream stream = client.openSse(streamBody("run"), null)) {
            assertThat(stream.await(inState("TASK_STATE_WORKING"), STREAM_TIMEOUT)).isPresent();
            assertThat(stream.await(inState("TASK_STATE_COMPLETED"), STREAM_TIMEOUT))
                    .as("the stream must reach a terminal completed state").isPresent();

            // Awaiting the terminal event drains every earlier event into received(); the whole stream is now visible.
            String allEvents = combine(stream);
            assertThat(allEvents).contains("precondition_result").contains("test_step_result")
                    .contains("execution_log").contains("final_result");
        }
    }

    @Test
    @DisplayName("A throwing executor fails the task on message/send")
    void failingExecutionFailsTaskOnSend() throws Exception {
        executor.withFailure("boom");
        startServer(Optional.empty());

        Response response = client.postRpc(sendBody("run"), null);

        assertThat(response.status()).isEqualTo(200);
        JsonNode json = MAPPER.readTree(response.body());
        assertThat(json.has("error")).isFalse();
        assertThat(response.body()).contains("TASK_STATE_FAILED");
        // The production failure text is surfaced to the caller; the executor's internal message is not leaked.
        assertThat(response.body()).contains("Got exception while executing the test case");
        assertThat(executor.receivedMessages()).containsExactly("run");
    }

    @Test
    @DisplayName("A throwing executor drives the stream to a terminal failed state")
    void failingExecutionFailsTaskOnStream() {
        executor.withFailure("boom");
        startServer(Optional.empty());

        try (SseStream stream = client.openSse(streamBody("run"), null)) {
            assertThat(stream.await(inState("TASK_STATE_FAILED"), STREAM_TIMEOUT))
                    .as("the stream must reach a terminal failed state").isPresent();
        }
    }

    @Test
    @DisplayName("Malformed JSON and unknown methods map to the standard JSON-RPC error codes")
    void malformedRequestsMapToJsonRpcErrorCodes() {
        startServer(Optional.empty());

        Response parseError = client.postRpc("{not valid json", null);
        Response unknownMethod = client.postRpc(rpc("no/such-method", "{}", 9), null);

        assertThat(parseError.body()).contains("error").contains("-32700");
        assertThat(unknownMethod.body()).contains("error").contains("-32601");
    }

    @Test
    @DisplayName("tasks/cancel on a running task cancels it")
    void cancelRunningTaskSucceeds() throws Exception {
        executor.withStep(new TestStep("long action", List.of(), "done")).blockUntilResumed();
        startServer(Optional.empty());

        try (SseStream stream = client.openSse(streamBody("run"), null)) {
            String taskId = awaitTaskId(stream);

            Response cancel = client.postRpc(cancelBody(taskId), null);

            // A successful cancel is a JSON-RPC result (no top-level error) whose task is now in the canceled state.
            JsonNode cancelJson = MAPPER.readTree(cancel.body());
            assertThat(cancel.status()).isEqualTo(200);
            assertThat(cancelJson.has("error")).isFalse();
            assertThat(stateOf(cancelJson.path("result"))).isEqualTo("TASK_STATE_CANCELED");
            assertThat(stream.await(inState("TASK_STATE_CANCELED"), STREAM_TIMEOUT))
                    .as("the running task must transition to canceled on the stream").isPresent();
        }
    }

    @Test
    @DisplayName("tasks/cancel on an unknown task returns TaskNotFound")
    void cancelUnknownTaskReturnsTaskNotFound() {
        startServer(Optional.empty());

        Response response = client.postRpc(cancelBody("does-not-exist"), null);

        assertThat(response.body()).contains("error").contains("-32001");
    }

    @Test
    @DisplayName("tasks/cancel on a completed task returns TaskNotCancelable")
    void cancelCompletedTaskReturnsNotCancelable() {
        executor.withStep(new TestStep("quick action", List.of(), "done"));
        startServer(Optional.empty());

        String taskId;
        try (SseStream stream = client.openSse(streamBody("run"), null)) {
            assertThat(stream.await(inState("TASK_STATE_COMPLETED"), STREAM_TIMEOUT)).isPresent();
            taskId = awaitTaskId(stream);
        }

        Response response = client.postRpc(cancelBody(taskId), null);

        assertThat(response.body()).contains("error").contains("-32002");
    }

    @Test
    @DisplayName("tasks/get on a completed task returns it with the completed state")
    void getCompletedTaskReturnsCompletedState() throws Exception {
        executor.withStep(new TestStep("quick action", List.of(), "done"));
        startServer(Optional.empty());

        String taskId;
        try (SseStream stream = client.openSse(streamBody("run"), null)) {
            assertThat(stream.await(inState("TASK_STATE_COMPLETED"), STREAM_TIMEOUT)).isPresent();
            taskId = awaitTaskId(stream);
        }

        Response response = client.postRpc(getBody(taskId), null);

        JsonNode json = MAPPER.readTree(response.body());
        assertThat(response.status()).isEqualTo(200);
        assertThat(json.has("error")).isFalse();
        assertThat(stateOf(json.path("result"))).isEqualTo("TASK_STATE_COMPLETED");
    }

    @Test
    @DisplayName("tasks/get on an unknown task returns TaskNotFound")
    void getUnknownTaskReturnsTaskNotFound() {
        startServer(Optional.empty());

        Response response = client.postRpc(getBody("does-not-exist"), null);

        assertThat(response.body()).contains("error").contains("-32001");
    }

    @Test
    @DisplayName("A domain-level FAILED result still completes the task and is carried in the final_result artifact")
    void completedTaskCarriesFailedDomainResult() {
        executor.withStep(new TestStep("failing action", List.of(), "expected"))
                .withResult(FAILED, "Verification failed. Expected status 200 but got 500");
        startServer(Optional.empty());

        try (SseStream stream = client.openSse(streamBody("run"), null)) {
            assertThat(stream.await(inState("TASK_STATE_COMPLETED"), STREAM_TIMEOUT))
                    .as("a failed test is a completed task, not a failed one").isPresent();

            String allEvents = combine(stream);
            assertThat(allEvents).doesNotContain("TASK_STATE_FAILED");
            // The final_result artifact carries the consolidated result JSON (escaped inside the event's text part).
            assertThat(allEvents).contains("final_result")
                    .contains("\\\"testExecutionStatus\\\":\\\"FAILED\\\"")
                    .contains("Verification failed. Expected status 200 but got 500");
        }
    }

    @Test
    @DisplayName("tasks/resubscribe on an unknown task returns TaskNotFound")
    void resubscribeUnknownTaskReturnsTaskNotFound() {
        startServer(Optional.empty());

        Response response = client.postRpc(resubscribeBody("does-not-exist"), null);

        assertThat(response.body()).contains("error").contains("-32001");
    }

    @Test
    @DisplayName("tasks/resubscribe on a completed task returns an error because the task is terminal")
    void resubscribeCompletedTaskReturnsTerminalStateError() {
        executor.withStep(new TestStep("quick action", List.of(), "done"));
        startServer(Optional.empty());

        String taskId;
        try (SseStream stream = client.openSse(streamBody("run"), null)) {
            assertThat(stream.await(inState("TASK_STATE_COMPLETED"), STREAM_TIMEOUT)).isPresent();
            taskId = awaitTaskId(stream);
        }

        Response response = client.postRpc(resubscribeBody(taskId), null);

        assertThat(response.body()).contains("error").contains("-32004").contains("terminal state TASK_STATE_COMPLETED");
    }

    @Test
    @DisplayName("tasks/resubscribe re-attaches a streaming client to a running task")
    void resubscribeReattachesToRunningTask() {
        executor.withStep(new TestStep("long action", List.of(), "done")).withLogs(List.of("running")).blockUntilResumed();
        startServer(Optional.empty());

        try (SseStream first = client.openSse(streamBody("run"), null)) {
            String taskId = awaitTaskId(first);

            try (SseStream resubscribed = client.openSse(resubscribeBody(taskId), null)) {
                // Let the still-running task finish only after the second client has re-attached.
                executor.resume();

                assertThat(resubscribed.await(inState("TASK_STATE_COMPLETED"), STREAM_TIMEOUT))
                        .as("the re-attached client must observe the task completing").isPresent();
            }
        }
    }

    private void startServer(@NotNull Optional<String> token) {
        int port = findFreePort();
        AgentConfig config = mock(AgentConfig.class);
        lenient().when(config.getStartPort()).thenReturn(port);
        lenient().when(config.getHost()).thenReturn("localhost");
        lenient().when(config.getAuthToken()).thenReturn(token);

        AgentExecutionResource resource = new AgentExecutionResource(() -> executor, () -> agentCard);
        initialize(resource);
        server = new AbstractServer(resource, config).start();
        client = new A2aTestClient("http://localhost:" + port);
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

    /** Awaits the first streamed event that carries a task id and returns it. */
    private static String awaitTaskId(@NotNull SseStream stream) {
        return stream.await(event -> taskIdOf(event).isPresent(), STREAM_TIMEOUT)
                .flatMap(CoreA2aSmokeTest::taskIdOf)
                .orElseThrow(() -> new AssertionError("No task id was streamed within the timeout"));
    }

    private static String combine(@NotNull SseStream stream) {
        return stream.received().stream().map(JsonNode::toString).reduce("", (left, right) -> left + right);
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

    private static Optional<String> taskIdOf(@NotNull JsonNode result) {
        for (String kind : List.of("statusUpdate", "artifactUpdate")) {
            JsonNode node = result.path(kind);
            if (node.has("taskId")) {
                return Optional.of(node.get("taskId").asText());
            }
        }
        JsonNode task = result.path("task");
        return task.has("id") ? Optional.of(task.get("id").asText()) : Optional.empty();
    }

    /** Recursively looks for a {@code "streaming": true} flag anywhere in the agent-card JSON. */
    private static boolean advertisesStreaming(@NotNull JsonNode node) {
        if (node.path("streaming").asBoolean(false)) {
            return true;
        }
        for (JsonNode child : node) {
            if (advertisesStreaming(child)) {
                return true;
            }
        }
        return false;
    }

    private static String sendBody(@NotNull String text) {
        return rpc("message/send", messageParams(text), 1);
    }

    private static String streamBody(@NotNull String text) {
        return rpc("message/stream", messageParams(text), 2);
    }

    private static String cancelBody(@NotNull String taskId) {
        return rpc("tasks/cancel", "{\"id\":\"%s\"}".formatted(taskId), 3);
    }

    private static String getBody(@NotNull String taskId) {
        return rpc("tasks/get", "{\"id\":\"%s\"}".formatted(taskId), 5);
    }

    private static String resubscribeBody(@NotNull String taskId) {
        return rpc("tasks/resubscribe", "{\"id\":\"%s\"}".formatted(taskId), 4);
    }

    private static String messageParams(@NotNull String text) {
        return "{\"message\":{\"messageId\":\"msg-1\",\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"%s\"}]}}".formatted(text);
    }

    private static String rpc(@NotNull String method, @NotNull String params, int id) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":%s,\"id\":%d}".formatted(method, params, id);
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not find a free port", e);
        }
    }

    /**
     * A recording {@link AbstractAgentExecutor} that emits a configurable set of streaming events (a precondition, a
     * step and log lines) and then returns a configurable result (PASSED by default). It can optionally block until {@link #resume()} is called
     * (or the execution is interrupted by a cancel), which keeps a task in the running state long enough to exercise
     * the cancel and resubscribe paths.
     */
    private static final class RecordingAgentExecutor extends AbstractAgentExecutor {
        private final List<String> receivedMessages = new CopyOnWriteArrayList<>();
        private final CountDownLatch resume = new CountDownLatch(1);

        private volatile String testCaseName = "Core smoke test";
        private volatile String precondition;
        private volatile TestStep step;
        private volatile List<String> logs = List.of();
        private volatile boolean blockUntilResumed;
        private volatile String failureMessage;
        private volatile TestExecutionStatus resultStatus = PASSED;
        private volatile String generalErrorMessage;

        RecordingAgentExecutor withTestCaseName(@NotNull String name) {
            this.testCaseName = name;
            return this;
        }

        RecordingAgentExecutor withPrecondition(@NotNull String precondition) {
            this.precondition = precondition;
            return this;
        }

        RecordingAgentExecutor withStep(@NotNull TestStep step) {
            this.step = step;
            return this;
        }

        RecordingAgentExecutor withLogs(@NotNull List<String> logs) {
            this.logs = logs;
            return this;
        }

        RecordingAgentExecutor blockUntilResumed() {
            this.blockUntilResumed = true;
            return this;
        }

        /** Makes {@link #executeTestCase} throw, exercising the transport's failure-propagation path. */
        RecordingAgentExecutor withFailure(@NotNull String failureMessage) {
            this.failureMessage = failureMessage;
            return this;
        }

        /** Makes {@link #executeTestCase} return the given domain-level status normally instead of the default PASSED. */
        RecordingAgentExecutor withResult(@NotNull TestExecutionStatus resultStatus, @Nullable String generalErrorMessage) {
            this.resultStatus = resultStatus;
            this.generalErrorMessage = generalErrorMessage;
            return this;
        }

        void resume() {
            resume.countDown();
        }

        List<String> receivedMessages() {
            return List.copyOf(receivedMessages);
        }

        @Override
        protected TestExecutionResult executeTestCase(String message, StreamingEventEmitter eventEmitter) {
            receivedMessages.add(message);
            if (failureMessage != null) {
                throw new IllegalStateException(failureMessage);
            }
            Instant start = Instant.now();

            List<PreconditionResult> preconditionResults = emitPrecondition(eventEmitter, start);
            List<TestStepResult> stepResults = emitStep(eventEmitter, start);
            logs.forEach(eventEmitter::emitLog);

            if (blockUntilResumed) {
                awaitResume();
            }

            return new TestExecutionResult(testCaseName, resultStatus, preconditionResults, stepResults, start, Instant.now(),
                    generalErrorMessage, null, logs);
        }

        private List<PreconditionResult> emitPrecondition(StreamingEventEmitter eventEmitter, Instant start) {
            if (precondition == null) {
                return List.of();
            }
            eventEmitter.emitPreconditionStarted(precondition);
            PreconditionResult result = new PreconditionResult(precondition, true, null, start, Instant.now());
            eventEmitter.emitPreconditionResult(result);
            return List.of(result);
        }

        private List<TestStepResult> emitStep(StreamingEventEmitter eventEmitter, Instant start) {
            if (step == null) {
                return List.of();
            }
            eventEmitter.emitStepStarted(step);
            TestStepResult result = new TestStepResult(step, SUCCESS, null, "done", start, Instant.now());
            eventEmitter.emitStepResult(result);
            return List.of(result);
        }

        private void awaitResume() {
            try {
                resume.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Execution interrupted before completion (cancelled)", e);
            }
        }
    }
}
