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
package org.tarik.ta.a2a;

import jakarta.inject.Singleton;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.a2a.TaskTokenUsage;
import org.tarik.ta.core.exceptions.BudgetExceededException;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.dto.KnowledgeResponse;
import org.tarik.ta.knowledge.KnowledgeSelectionService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Thread.currentThread;
import static org.tarik.ta.knowledge.KnowledgeJson.toData;

/**
 * Serves the A2A tasks of the agent, several at the same time: it reads the test case of a task, selects its procedures and completes
 * the task with them as one data part, or fails it with the reason. Thread-safe.
 */
@Singleton
public class KnowledgeAgentExecutor implements AgentExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeAgentExecutor.class);
    static final String RESULT_ARTIFACT = "procedure_selection";

    private final KnowledgeRequestReader requestReader;
    private final KnowledgeSelectionService selectionService;
    private final BudgetManager budgetManager;
    private final Map<String, Thread> runningTasks = new ConcurrentHashMap<>();

    public KnowledgeAgentExecutor(KnowledgeRequestReader requestReader, KnowledgeSelectionService selectionService,
                                  BudgetManager budgetManager) {
        this.requestReader = requestReader;
        this.selectionService = selectionService;
        this.budgetManager = budgetManager;
    }

    private sealed interface Outcome permits Selected, Failed {
    }

    private record Selected(KnowledgeResponse response) implements Outcome {
    }

    private record Failed(String reason) implements Outcome {
    }

    @Override
    public void execute(@NotNull RequestContext context, @NotNull AgentEmitter emitter) throws A2AError {
        if (context.getTask() == null) {
            emitter.submit();
        }
        var taskId = context.getTaskId();
        runningTasks.put(taskId, currentThread());
        try {
            emitter.startWork();
            var outcome = select(taskId, context);
            var usage = budgetManager.releaseTask(taskId);
            if (runningTasks.remove(taskId, currentThread())) {
                TaskTokenUsage.emit(emitter, taskId, usage);
                finish(outcome, emitter);
            } else {
                // cancel() removed the task, which then accepts no further event, and interrupted this thread for that task only, so
                // the pooled thread must not carry the interrupt into its next task.
                Thread.interrupted();
            }
        } finally {
            runningTasks.remove(taskId, currentThread());
        }
    }

    private Outcome select(String taskId, RequestContext context) {
        try {
            var testCase = requestReader.read(context.getMessage());
            return new Selected(selectionService.select(taskId, testCase));
        } catch (InvalidKnowledgeRequestException e) {
            LOG.warn("Rejected the request of task {}: {}", taskId, e.getMessage());
            return new Failed("Invalid request: %s".formatted(e.getMessage()));
        } catch (BudgetExceededException e) {
            LOG.error("Task {} used up its budget", taskId, e);
            return new Failed("The procedure selection used up its budget: %s".formatted(e.getMessage()));
        } catch (TimeoutException e) {
            LOG.error("The item runs of task {} timed out", taskId, e);
            return new Failed("The procedure selection exceeded its execution time budget");
        } catch (InterruptedException _) {
            currentThread().interrupt();
            LOG.warn("The procedure selection of task {} was interrupted", taskId);
            return new Failed("The procedure selection was interrupted");
        } catch (RuntimeException e) {
            LOG.error("The procedure selection of task {} failed", taskId, e);
            return new Failed("The procedure selection failed; see the agent's logs of task %s".formatted(taskId));
        }
    }

    private static void finish(Outcome outcome, AgentEmitter emitter) {
        switch (outcome) {
            case Selected(var response) -> {
                emitter.addArtifact(List.of(new DataPart(toData(response))), null, RESULT_ARTIFACT, null);
                emitter.complete();
            }
            case Failed(var reason) -> emitter.fail(emitter.newAgentMessage(List.of(new TextPart(reason)), null));
        }
    }

    @Override
    public void cancel(@NotNull RequestContext context, @NotNull AgentEmitter emitter) throws A2AError {
        var taskId = context.getTaskId();
        var wasRunning = new AtomicBoolean(false);
        // Interrupting inside the atomic removal keeps the interrupt from reaching the thread after it has moved on to another task.
        runningTasks.computeIfPresent(taskId, (_, thread) -> {
            thread.interrupt();
            wasRunning.set(true);
            return null;
        });
        if (!wasRunning.get()) {
            throw new TaskNotCancelableError();
        }
        LOG.info("Cancelled task {}", taskId);
        emitter.cancel();
    }
}
