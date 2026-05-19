/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.core.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.tasks.AgentEmitter;
import io.a2a.spec.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.utils.CommonUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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
                            .ifPresentOrElse(userMessage -> requestTestCaseExecution(userMessage, emitter),
                                    () -> {
                                        var message = "Request for test case execution failed either contained no valid test "                                                +
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

    private void requestTestCaseExecution(String message, AgentEmitter emitter) {
        getTestExecutionResult(message, emitter).ifPresent(result -> {
            try {
                List<Part<?>> parts = new LinkedList<>();
                String resultJson = OBJECT_MAPPER.writeValueAsString(result);
                LOG.info("Sending test execution result back to caller: {}", resultJson);
                TextPart textPart = new TextPart(resultJson, null);
                parts.add(textPart);
                addSpecificArtifacts(result, parts);
                addLogsArtifact(result, parts);
                emitter.addArtifact(parts, null, null, null);
                emitter.complete(emitter.newAgentMessage(List.of(textPart), null));
            } catch (Exception e) {
                LOG.error("Got exception while preparing the task artifacts for the test case '{}'",
                        result.getTestCaseName(), e);
                failTask(emitter, "Got exception while preparing the task artifacts for the test case. " +
                        "Before re-sending please investigate the root cause based on the agent's logs.");
            }
        });
    }

    private void addLogsArtifact(TestExecutionResult result, List<Part<?>> parts) {
        extractLogs(result).ifPresent(logs -> {
            String logsContent = String.join("\n", logs);
            String base64Logs = Base64.getEncoder().encodeToString(logsContent.getBytes(StandardCharsets.UTF_8));
            FileWithBytes logsFile = new FileWithBytes(
                    "text/plain",
                    "execution_logs_%s.log".formatted(result.getTestCaseName().replaceAll("\\s", "_").toLowerCase()),
                    base64Logs);
            parts.add(new FilePart(logsFile));
        });
    }

    private Optional<TestExecutionResult> getTestExecutionResult(String message, AgentEmitter emitter) {
        try {
            TestExecutionResult result = executeTestCase(message);
            return ofNullable(result);
        } catch (Exception e) {
            LOG.error("Got exception during the execution of the test case.", e);
            failTask(emitter, "Got exception while executing the test case, no results available. " +
                    "Before re-sending please investigate the root cause based on the agent's logs.");
            return empty();
        }
    }

    protected abstract TestExecutionResult executeTestCase(String message);

    protected abstract void addSpecificArtifacts(TestExecutionResult result, List<Part<?>> parts);

    protected abstract Optional<List<String>> extractLogs(TestExecutionResult result);

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
}
