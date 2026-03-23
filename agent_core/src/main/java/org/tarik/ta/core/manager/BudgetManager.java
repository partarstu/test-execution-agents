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
package org.tarik.ta.core.manager;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.AgentConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.time.Instant.now;

@Singleton
public class BudgetManager {
    private static final Logger LOG = LoggerFactory.getLogger(BudgetManager.class);

    // Bridge accessor for non-injectable contexts (e.g. GenericAiAgent interface default methods).
    // Falls back to a lazily created default in unit-test contexts that skip the DI container.
    private static volatile BudgetManager instance;

    public final int timeBudgetSeconds;
    private final int tokenBudget;
    private final int toolCallsBudget;
    private final AtomicInteger toolCallUsage = new AtomicInteger(0);
    private final AtomicReference<Instant> startTime = new AtomicReference<>(null);
    private final Map<String, ModelUsage> tokenUsagePerModel = new ConcurrentHashMap<>();

    @Inject
    public BudgetManager(AgentConfig agentConfig) {
        timeBudgetSeconds = agentConfig.getAgentExecutionTimeBudgetSeconds();
        tokenBudget = agentConfig.getAgentTokenBudget();
        toolCallsBudget = agentConfig.getAgentToolCallsBudget();
        instance = this;
    }

    public record ModelUsage(AtomicInteger input, AtomicInteger output, AtomicInteger cached, AtomicInteger total) {
        public ModelUsage() {
            this(new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0));
        }
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
        startTime.set(null);
        tokenUsagePerModel.clear();
        LOG.debug("Budget counters reset.");
    }

    public void activateTimeBudget() {
        startTime.set(now());
        LOG.debug("Execution time budget activated.");
    }

    public void resetToolCallUsage() {
        toolCallUsage.set(0);
        LOG.debug("Tool call usage reset.");
    }

    public void consumeTokens(String modelName, int input, int output, int cached) {
        ModelUsage usage = tokenUsagePerModel.computeIfAbsent(modelName, _ -> new ModelUsage());
        usage.input.addAndGet(input);
        usage.output.addAndGet(output);
        usage.cached.addAndGet(cached);
        usage.total.addAndGet(input + output + cached);
    }

    public int getAccumulatedInputTokens() {
        return tokenUsagePerModel.values().stream().mapToInt(u -> u.input.get()).sum();
    }

    public int getAccumulatedOutputTokens() {
        return tokenUsagePerModel.values().stream().mapToInt(u -> u.output.get()).sum();
    }

    public int getAccumulatedCachedTokens() {
        return tokenUsagePerModel.values().stream().mapToInt(u -> u.cached.get()).sum();
    }

    public int getAccumulatedTotalTokens() {
        return getAccumulatedInputTokens() + getAccumulatedOutputTokens() + getAccumulatedCachedTokens();
    }

    public int getAccumulatedInputTokens(String modelName) {
        ModelUsage usage = tokenUsagePerModel.get(modelName);
        return usage != null ? usage.input.get() : 0;
    }

    public int getAccumulatedOutputTokens(String modelName) {
        ModelUsage usage = tokenUsagePerModel.get(modelName);
        return usage != null ? usage.output.get() : 0;
    }

    public int getAccumulatedCachedTokens(String modelName) {
        ModelUsage usage = tokenUsagePerModel.get(modelName);
        return usage != null ? usage.cached.get() : 0;
    }

    public int getAccumulatedTotalTokens(String modelName) {
        return getAccumulatedInputTokens(modelName) + getAccumulatedOutputTokens(modelName)
                + getAccumulatedCachedTokens(modelName);
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
            throw new RuntimeException(
                    "Execution time budget exceeded: " + elapsedSeconds + "s > " + timeBudgetSeconds + "s");
        }
    }

    public void checkTokenBudget() {
        int current = getAccumulatedTotalTokens();
        if (tokenBudget > 0 && current > tokenBudget) {
            throw new RuntimeException("Token budget exceeded: " + current + " > " + tokenBudget);
        }
    }

    public void checkToolCallBudget() {
        int current = toolCallUsage.get();
        if (toolCallsBudget > 0 && current > toolCallsBudget) {
            throw new RuntimeException("Tool call budget exceeded: " + current + " > " + toolCallsBudget);
        }
    }

    public void checkAllBudgets() {
        checkTimeBudget();
        checkTokenBudget();
        checkToolCallBudget();
    }
}
