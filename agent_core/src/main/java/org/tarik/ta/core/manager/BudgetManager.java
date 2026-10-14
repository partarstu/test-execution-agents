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
package org.tarik.ta.core.manager;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.exceptions.BudgetExceededException;
import org.tarik.ta.core.telemetry.TokenUsageRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static java.time.Instant.now;
import static java.util.Comparator.comparing;

@Singleton
public class BudgetManager {
    private static final Logger LOG = LoggerFactory.getLogger(BudgetManager.class);
    /** Bucket used when usage is recorded outside of an A2A task, e.g. by a manual run or a test. */
    private static final String UNKNOWN_TASK_ID = "unknown";
    /** The task an agent that runs several tasks at once binds to the work it does for that task; it overrides the current task. */
    private static final ScopedValue<String> BOUND_TASK_ID = ScopedValue.newInstance();

    // Bridge accessor for non-injectable contexts (e.g. GenericAiAgent interface default methods).
    private static volatile BudgetManager instance;

    public final int timeBudgetSeconds;
    private final int tokenBudget;
    private final int toolCallsBudget;
    private final AtomicInteger toolCallUsage = new AtomicInteger(0);
    private final AtomicBoolean toolCallBudgetActive = new AtomicBoolean(false);
    private final AtomicReference<Instant> startTime = new AtomicReference<>(null);
    /** Accumulated usage of the current run, one entry per {@code (task id, sub-agent operation)}. */
    private final Map<UsageKey, TokenUsageRecord> tokenUsage = new ConcurrentHashMap<>();
    private final AtomicReference<String> currentTaskId = new AtomicReference<>(UNKNOWN_TASK_ID);
    private final ReadWriteLock usageLock = new ReentrantReadWriteLock();

    @Inject
    public BudgetManager(AgentConfig agentConfig) {
        timeBudgetSeconds = agentConfig.getAgentExecutionTimeBudgetSeconds();
        tokenBudget = agentConfig.getAgentTokenBudget();
        toolCallsBudget = agentConfig.getAgentToolCallsBudget();
        instance = this;
    }

    /** Identifies the usage bucket of one sub-agent operation within one task. */
    private record UsageKey(String taskId, String operationName) {
    }

    public static BudgetManager getInstance() {
        if (instance == null) {
            synchronized (BudgetManager.class) {
                if (instance == null) {
                    instance = new BudgetManager(new AgentConfig());
                }
            }
        }
        return instance;
    }

    public void reset() {
        toolCallUsage.set(0);
        toolCallBudgetActive.set(false);
        startTime.set(null);
        tokenUsage.clear();
        LOG.debug("Budget counters reset.");
    }

    /**
     * Binds the usage recorded from now on to the given task, so that the report of a finished task carries exactly
     * what that task spent.
     */
    public void beginTask(@NotNull String taskId) {
        currentTaskId.set(taskId);
    }

    /**
     * Runs the action with the usage it records and the token budget it checks bound to the given task, so that tasks running at the
     * same time keep separate accounts; the binding covers only the calling thread.
     */
    public <T> T callInTask(@NotNull String taskId, @NotNull Supplier<T> action) {
        return ScopedValue.where(BOUND_TASK_ID, taskId).call(action::get);
    }

    private String activeTaskId() {
        return BOUND_TASK_ID.orElse(currentTaskId.get());
    }

    /**
     * Returns the task's usage, one entry per sub-agent operation, and frees its buckets; called once per task after the usage
     * has been reported.
     */
    public List<TokenUsageRecord> releaseTask(@NotNull String taskId) {
        // Exclusive, so that no model call is added to the task's buckets between reading and removing them
        usageLock.writeLock().lock();
        try {
            var released = tokenUsage.entrySet().stream()
                    .filter(entry -> entry.getKey().taskId().equals(taskId))
                    .map(Map.Entry::getValue)
                    .sorted(comparing(TokenUsageRecord::operationName))
                    .toList();
            tokenUsage.keySet().removeIf(key -> key.taskId().equals(taskId));
            currentTaskId.compareAndSet(taskId, UNKNOWN_TASK_ID);
            return released;
        } finally {
            usageLock.writeLock().unlock();
        }
    }

    public void activateTimeBudget() {
        startTime.set(now());
        LOG.debug("Execution time budget activated.");
    }

    public void activateToolCallBudget() {
        toolCallBudgetActive.set(true);
        LOG.debug("Tool call budget activated.");
    }

    public void resetToolCallUsage() {
        toolCallUsage.set(0);
        LOG.debug("Tool call usage reset.");
    }

    /**
     * Adds the usage of one model call to its operation's bucket within the current task and returns that operation's accumulated
     * usage.
     */
    public TokenUsageRecord consumeTokens(@NotNull TokenUsageRecord callUsage) {
        usageLock.readLock().lock();
        try {
            return tokenUsage.merge(new UsageKey(activeTaskId(), callUsage.operationName()), callUsage, TokenUsageRecord::plus);
        } finally {
            usageLock.readLock().unlock();
        }
    }

    public void consumeToolCalls(int count) {
        toolCallUsage.addAndGet(count);
    }

    public void checkTimeBudget() {
        var start = startTime.get();
        if (start == null) {
            // Time budget not yet activated (activateTimeBudget() has not been called)
            return;
        }
        long elapsedSeconds = Duration.between(start, now()).getSeconds();
        if (timeBudgetSeconds > 0 && elapsedSeconds > timeBudgetSeconds) {
            throw new BudgetExceededException("Execution time budget exceeded: %ds > %ds".formatted(elapsedSeconds, timeBudgetSeconds));
        }
    }

    /**
     * Fails when the active task has used more tokens than the budget allows.
     */
    public void checkTokenBudget() {
        var taskId = activeTaskId();
        int current = tokenUsage.entrySet().stream()
                .filter(entry -> entry.getKey().taskId().equals(taskId))
                .mapToInt(entry -> entry.getValue().totalTokens())
                .sum();
        if (tokenBudget > 0 && current > tokenBudget) {
            throw new BudgetExceededException("Token budget exceeded: %d > %d".formatted(current, tokenBudget));
        }
    }

    public void checkToolCallBudget() {
        int current = toolCallUsage.get();
        if (toolCallBudgetActive.get() && toolCallsBudget > 0 && current > toolCallsBudget) {
            throw new BudgetExceededException("Tool call budget exceeded: %d > %d".formatted(current, toolCallsBudget));
        }
    }

    public void checkAllBudgets() {
        checkTimeBudget();
        checkTokenBudget();
        checkToolCallBudget();
    }
}
