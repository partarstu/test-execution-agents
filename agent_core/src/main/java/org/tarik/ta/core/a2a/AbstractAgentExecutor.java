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
package org.tarik.ta.core.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.tasks.AgentEmitter;
import io.a2a.spec.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.dto.ExecutionActivity;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.utils.CommonUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import static io.a2a.spec.TaskState.TASK_STATE_WORKING;
import static java.lang.Thread.currentThread;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.stream.Collectors.joining;

public abstract class AbstractAgentExecutor implements AgentExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAgentExecutor.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private static final String STEP_RESULT_ARTIFACT = "test_step_result";
    private static final String PRECONDITION_RESULT_ARTIFACT = "precondition_result";
    private static final String FINAL_RESULT_ARTIFACT = "final_result";
    private static final String LOG_ARTIFACT = "execution_log";
    private static final String STREAMED_LOG_FILE_NAME = "execution_log.log";

    private final ExecutorService taskExecutor = newSingleThreadExecutor();

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        if (context.getTask() == null) {
            emitter.submit();
        }

        LOG.info("Received test case execution request. Submitting to the execution queue.");
        try {
            // Only a single execution may be running at a time
            taskExecutor.submit(() -> {
                var taskId = context.getTaskId();
                LOG.info("Starting task {} from the queue.", taskId);
                try {
                    emitter.startWork();
                    extractTextFromMessage(context.getMessage())
                            .ifPresentOrElse(userMessage -> runTestCase(userMessage, emitter),
                                    () -> {
                                        var message = "Request for test case execution failed either contained no valid test " +
                                                "case or insufficient information in order to execute it.";
                                        LOG.error(message);
                                        failTask(emitter, message);
                                    });
                } catch (Exception e) {
                    LOG.error("Error while processing test case execution request for task {}", taskId, e);
                    failTask(emitter, "Couldn't start the task %s".formatted(taskId));
                }
            }).get();
        } catch (InterruptedException e) {
            currentThread().interrupt();
            LOG.error("Task execution was interrupted.", e);
            failTask(emitter, "Task execution was interrupted.");
        } catch (ExecutionException e) {
            LOG.error("Error during task execution.", e.getCause());
            failTask(emitter, "Error during task execution: %s".formatted(e.getCause().getMessage()));
        }
    }

    private void runTestCase(String message, AgentEmitter emitter) {
        var eventEmitter = new AgentBackedStreamingEventEmitter(emitter);
        getTestExecutionResult(message, emitter, eventEmitter)
                .ifPresent(result -> completeWithResult(result, emitter));
    }

    private Optional<TestExecutionResult> getTestExecutionResult(String message, AgentEmitter emitter,
                                                                 StreamingEventEmitter eventEmitter) {
        try {
            return ofNullable(executeTestCase(message, eventEmitter));
        } catch (Exception e) {
            LOG.error("Got exception during the execution of the test case.", e);
            failTask(emitter, "Got exception while executing the test case, no results available. " +
                    "Before re-sending please investigate the root cause based on the agent's logs.");
            return empty();
        }
    }

    /**
     * Terminal event of an execution. Although step results, precondition results and logs were also streamed live as
     * they happened, the completion emits a single consolidated {@link TestExecutionResult} artifact plus a completion
     * message carrying the same result JSON. Per-step artifacts (e.g. screenshots) are not re-bundled here: they already
     * live in the task's accumulated artifact list from the streamed step events, so the final artifact carries only the
     * full result JSON, the logs file and any end-of-test artifact (e.g. the general screenshot). This ensures
     * non-streaming callers receive one ready-to-consume result object without having to reassemble it from the
     * individual streamed events.
     */
    private void completeWithResult(TestExecutionResult result, AgentEmitter emitter) {
        try {
            String resultJson = OBJECT_MAPPER.writeValueAsString(result);
            List<Part<?>> parts = new ArrayList<>();
            parts.add(new TextPart(resultJson));
            parts.addAll(buildFinalArtifactParts(result));
            addLogsArtifact(result, parts);
            emitter.addArtifact(parts, null, FINAL_RESULT_ARTIFACT, null);
            LOG.info("Completing test execution with the consolidated result: {}", resultJson);
            emitter.complete(emitter.newAgentMessage(List.of(new TextPart(resultJson)), null));
        } catch (Exception e) {
            LOG.error("Got exception while finalizing the result for the test case '{}'", result.getTestCaseName(), e);
            failTask(emitter, "Got exception while finalizing the test result. " +
                    "Before re-sending please investigate the root cause based on the agent's logs.");
        }
    }

    private void addLogsArtifact(TestExecutionResult result, List<Part<?>> parts) {
        ofNullable(result.getLogs()).ifPresent(logs -> {
            String logsContent = String.join("\n", logs);
            String base64Logs = Base64.getEncoder().encodeToString(logsContent.getBytes(StandardCharsets.UTF_8));
            FileWithBytes logsFile = new FileWithBytes(
                    "text/plain",
                    "execution_logs_%s.log".formatted(result.getTestCaseName().replaceAll("\\s", "_").toLowerCase()),
                    base64Logs);
            parts.add(new FilePart(logsFile));
        });
    }

    /**
     * Executes the test case described by {@code message}, streaming progress through the supplied
     * {@code eventEmitter} as the execution advances.
     */
    protected abstract TestExecutionResult executeTestCase(String message, StreamingEventEmitter eventEmitter);

    /**
     * Builds the agent-specific artifact parts streamed alongside a single step result (e.g. a UI screenshot).
     * The default implementation contributes nothing.
     */
    protected List<Part<?>> buildStepArtifactParts(TestStepResult stepResult) {
        return List.of();
    }

    /**
     * Builds the agent-specific artifact parts streamed with the terminal completion event (e.g. an end-of-test
     * screenshot). The default implementation contributes nothing.
     */
    protected List<Part<?>> buildFinalArtifactParts(TestExecutionResult result) {
        return List.of();
    }

    protected void failTask(AgentEmitter emitter, String message) {
        TextPart errorPart = new TextPart(message, null);
        List<Part<?>> parts = List.of(errorPart);
        emitter.fail(emitter.newAgentMessage(parts, null));
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        Task task = context.getTask();

        if (task.status().state() == TaskState.TASK_STATE_CANCELED) {
            throw new TaskNotCancelableError();
        }

        if (task.status().state() == TaskState.TASK_STATE_COMPLETED) {
            throw new TaskNotCancelableError();
        }

        emitter.cancel();
    }

    protected Optional<String> extractTextFromMessage(Message message) {
        String result = ofNullable(message.parts())
                .stream()
                .flatMap(Collection::stream)
                .filter(TextPart.class::isInstance)
                .map(part -> ((TextPart) part).text())
                .filter(CommonUtils::isNotBlank)
                .map(String::trim)
                .collect(joining("\n"))
                .trim();
        return result.isBlank() ? empty() : of(result);
    }

    /**
     * {@link StreamingEventEmitter} that publishes incremental progress through an {@link AgentEmitter}. Step and
     * precondition results are streamed as artifacts (a JSON part plus any agent-specific parts), while log lines are
     * streamed as {@code WORKING} status updates. Emissions are serialized through a lock to keep the order of events
     * deterministic when they originate from different execution threads.
     */
    private final class AgentBackedStreamingEventEmitter implements StreamingEventEmitter {
        private final AgentEmitter agentEmitter;
        private final ReentrantLock lock = new ReentrantLock();
        private boolean logArtifactStarted = false;

        private AgentBackedStreamingEventEmitter(AgentEmitter agentEmitter) {
            this.agentEmitter = agentEmitter;
        }

        @Override
        public void emitStepStarted(@NotNull TestStep step) {
            emitActivity(new ExecutionActivity(ExecutionActivity.ActivityType.TEST_STEP, step.stepDescription()));
        }

        @Override
        public void emitPreconditionStarted(@NotNull String precondition) {
            emitActivity(new ExecutionActivity(ExecutionActivity.ActivityType.PRECONDITION, precondition));
        }

        /**
         * Streams the currently-executing item as the message of a {@code working} status update, so observers see what
         * is being executed right now (as opposed to the result artifacts, which describe what has already completed).
         */
        private void emitActivity(ExecutionActivity activity) {
            serializeToJson(activity).ifPresent(json -> {
                lock.lock();
                try {
                    agentEmitter.updateStatus(TASK_STATE_WORKING, agentEmitter.newAgentMessage(List.of(new TextPart(json)), null));
                } catch (Exception e) {
                    LOG.warn("Failed to stream the current activity to the caller.", e);
                } finally {
                    lock.unlock();
                }
            });
        }

        @Override
        public void emitStepResult(@NotNull TestStepResult stepResult) {
            serializeToJson(stepResult).ifPresent(json -> {
                List<Part<?>> parts = new ArrayList<>();
                parts.add(new TextPart(json));
                parts.addAll(buildStepArtifactParts(stepResult));
                emitArtifact(parts, STEP_RESULT_ARTIFACT);
            });
        }

        @Override
        public void emitPreconditionResult(@NotNull PreconditionResult preconditionResult) {
            serializeToJson(preconditionResult)
                    .ifPresent(json -> emitArtifact(List.of(new TextPart(json)), PRECONDITION_RESULT_ARTIFACT));
        }

        /**
         * Streams a single log line into one continuously growing {@code execution_log.log} artifact. The first line
         * establishes the artifact; every subsequent line is appended to it as a new chunk, so the caller reconstructs
         * the full log by concatenating the artifact's parts instead of receiving one artifact per line.
         */
        @Override
        public void emitLog(@NotNull String logLine) {
            lock.lock();
            try {
                List<Part<?>> parts = List.of(new TextPart("%s\n".formatted(logLine)));
                agentEmitter.addArtifact(parts, LOG_ARTIFACT, STREAMED_LOG_FILE_NAME, null, logArtifactStarted, false);
                logArtifactStarted = true;
            } catch (Exception e) {
                LOG.warn("Failed to stream a log line to the caller.", e);
            } finally {
                lock.unlock();
            }
        }

        private void emitArtifact(List<Part<?>> parts, String artifactName) {
            lock.lock();
            try {
                agentEmitter.addArtifact(parts, null, artifactName, null);
            } catch (Exception e) {
                String warning = "Failed to stream the artifact '%s' to the caller.".formatted(artifactName);
                LOG.warn(warning, e);
            } finally {
                lock.unlock();
            }
        }

        private Optional<String> serializeToJson(Object value) {
            try {
                return of(OBJECT_MAPPER.writeValueAsString(value));
            } catch (JsonProcessingException e) {
                String error = "Failed to serialize %s for streaming.".formatted(value.getClass().getSimpleName());
                LOG.error(error, e);
                return empty();
            }
        }
    }
}
