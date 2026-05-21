/*
 * agent-core - ${project.description}
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
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
    public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater updater = new TaskUpdater(context, eventQueue);
        if (context.getTask() == null) {
            updater.submit();
        }

        LOG.info("Received test case execution request. Submitting to the execution queue.");
        try {
            // Only a single execution may be running at a time
            taskExecutor.submit(() -> {
                var taskId = context.getTaskId();
                LOG.info("Starting task {} from the queue.", taskId);
                try {
                    updater.startWork();
                    extractTextFromMessage(context.getMessage())
                            .ifPresentOrElse(userMessage -> requestTestCaseExecution(userMessage, updater),
                                    () -> {
                                        var message = "Request for test case execution failed either contained no valid test "                                                +
                                                "case or insufficient information in order to execute it.";
                                        LOG.error(message);
                                        failTask(updater, message);
                                    });
                } catch (Exception e) {
                    LOG.error("Error while processing test case execution request for task {}", taskId, e);
                    failTask(updater, "Couldn't start the task %s".formatted(taskId));
                }
            }).get();
        } catch (InterruptedException e) {
            currentThread().interrupt();
            LOG.error("Task execution was interrupted.", e);
            failTask(updater, "Task execution was interrupted.");
        } catch (ExecutionException e) {
            LOG.error("Error during task execution.", e.getCause());
            failTask(updater, "Error during task execution: %s".formatted(e.getCause().getMessage()));
        }
    }

    private void requestTestCaseExecution(String message, TaskUpdater updater) {
        getTestExecutionResult(message, updater).ifPresent(result -> {
            try {
                List<Part<?>> parts = new LinkedList<>();
                String resultJson = OBJECT_MAPPER.writeValueAsString(result);
                LOG.info("Sending test execution result back to caller: {}", resultJson);
                TextPart textPart = new TextPart(resultJson, null);
                parts.add(textPart);
                addSpecificArtifacts(result, parts);
                addLogsArtifact(result, parts);
                updater.addArtifact(parts, null, null, null);
                updater.complete(updater.newAgentMessage(List.of(textPart), null));
            } catch (Exception e) {
                LOG.error("Got exception while preparing the task artifacts for the test case '{}'",
                        result.getTestCaseName(), e);
                failTask(updater, "Got exception while preparing the task artifacts for the test case. " +
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

    private Optional<TestExecutionResult> getTestExecutionResult(String message, TaskUpdater updater) {
        try {
            TestExecutionResult result = executeTestCase(message);
            return ofNullable(result);
        } catch (Exception e) {
            LOG.error("Got exception during the execution of the test case.", e);
            failTask(updater, "Got exception while executing the test case, no results available. " +
                    "Before re-sending please investigate the root cause based on the agent's logs.");
            return empty();
        }
    }

    protected abstract TestExecutionResult executeTestCase(String message);

    protected abstract void addSpecificArtifacts(TestExecutionResult result, List<Part<?>> parts);

    protected abstract Optional<List<String>> extractLogs(TestExecutionResult result);

    protected void failTask(TaskUpdater updater, String message) {
        TextPart errorPart = new TextPart(message, null);
        List<Part<?>> parts = List.of(errorPart);
        updater.fail(updater.newAgentMessage(parts, null));
    }

    @Override
    public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
        Task task = context.getTask();

        if (task.getStatus().state() == TaskState.CANCELED) {
            throw new TaskNotCancelableError();
        }

        if (task.getStatus().state() == TaskState.COMPLETED) {
            throw new TaskNotCancelableError();
        }

        TaskUpdater updater = new TaskUpdater(context, eventQueue);
        updater.cancel();
    }

    protected Optional<String> extractTextFromMessage(Message message) {
        String result = ofNullable(message.getParts())
                .stream()
                .flatMap(Collection::stream)
                .filter(TextPart.class::isInstance)
                .map(part -> ((TextPart) part).getText())
                .filter(CommonUtils::isNotBlank)
                .map(String::trim)
                .collect(joining("\n"))
                .trim();
        return result.isBlank() ? empty() : of(result);
    }
}
