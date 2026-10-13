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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.telemetry.TokenUsageRecord;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mockStatic;

class BudgetManagerTest {

    private BudgetManager budgetManager;

    @BeforeEach
    void setUp() {
        budgetManager = new BudgetManager(new AgentConfig());
        budgetManager.reset();
    }

    private static TokenUsageRecord usage(String operationName, int inputTokens, int outputTokens, int cachedTokens) {
        return new TokenUsageRecord(operationName, "openai", "gpt-test", "gpt-test", "api.example.org", inputTokens,
                outputTokens, cachedTokens, 120);
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenUnderLimit() {
        int limit = new AgentConfig().getAgentTokenBudget();
        if (limit <= 0)
            return;

        budgetManager.consumeTokens(usage("test_step_action", limit - 1, 0, 0));

        assertThatCode(budgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenAtLimit() {
        int limit = new AgentConfig().getAgentTokenBudget();
        if (limit <= 0)
            return;

        budgetManager.consumeTokens(usage("test_step_action", limit, 0, 0));

        assertThatCode(budgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldThrow_whenOverLimit() {
        int limit = new AgentConfig().getAgentTokenBudget();
        if (limit <= 0)
            return;

        try {
            budgetManager.consumeTokens(usage("test_step_action", limit + 1, 0, 0));
        } catch (RuntimeException e) {
            // Expected
        }

        assertThatThrownBy(budgetManager::checkTokenBudget)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token budget exceeded");
    }

    @Test
    void consumeTokens_AndCheckBudget_shouldTrackDetailedUsage() {
        budgetManager.consumeTokens(usage("test_step_action", 50, 30, 20));

        assertThat(budgetManager.getAccumulatedTotalTokens()).isEqualTo(100);
        assertThat(budgetManager.getAccumulatedInputTokens()).isEqualTo(50);
        assertThat(budgetManager.getAccumulatedOutputTokens()).isEqualTo(30);
        assertThat(budgetManager.getAccumulatedCachedTokens()).isEqualTo(20);
    }

    @Test
    void consumeTokens_shouldAccumulatePerOperation() {
        budgetManager.consumeTokens(usage("test_step_action", 50, 30, 20));

        var accumulated = budgetManager.consumeTokens(usage("test_step_action", 5, 3, 2));

        assertThat(accumulated.inputTokens()).isEqualTo(55);
        assertThat(accumulated.outputTokens()).isEqualTo(33);
        assertThat(accumulated.cachedTokens()).isEqualTo(22);
        assertThat(accumulated.operationName()).isEqualTo("test_step_action");
    }

    @Test
    void releaseTask_shouldHandOutOneRecordPerOperationOfThatTaskAndFreeIt() {
        budgetManager.beginTask("task-1");
        budgetManager.consumeTokens(usage("test_step_action", 50, 30, 20));
        budgetManager.consumeTokens(usage("test_step_action", 5, 3, 2));
        budgetManager.consumeTokens(usage("precondition_action", 7, 1, 0));

        var released = budgetManager.releaseTask("task-1");

        assertThat(released).extracting(TokenUsageRecord::operationName, TokenUsageRecord::inputTokens)
                .containsExactly(tuple("precondition_action", 7), tuple("test_step_action", 55));
        assertThat(budgetManager.releaseTask("task-1")).isEmpty();
        assertThat(budgetManager.getAccumulatedTotalTokens()).isZero();
    }

    @Test
    void releaseTask_shouldKeepTheUsageOfAnotherTask() {
        budgetManager.beginTask("task-1");
        budgetManager.consumeTokens(usage("test_step_action", 50, 30, 20));
        budgetManager.beginTask("task-2");
        budgetManager.consumeTokens(usage("test_step_action", 1, 1, 1));

        budgetManager.releaseTask("task-1");

        assertThat(budgetManager.releaseTask("task-2")).extracting(TokenUsageRecord::totalTokens).containsExactly(3);
    }

    @Test
    void releaseTask_shouldLoseNoUsage_whenModelCallsLandWhileReleasing() throws Exception {
        int callsPerRound = 2_000;
        for (int round = 0; round < 50; round++) {
            budgetManager.reset();
            budgetManager.beginTask("task-1");
            var released = new AtomicInteger();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var calls = executor.submit(() -> {
                    for (int call = 0; call < callsPerRound; call++) {
                        budgetManager.consumeTokens(usage("test_step_action", 1, 0, 0));
                    }
                });
                var release = executor.submit(() -> released.set(budgetManager.releaseTask("task-1").stream()
                        .mapToInt(TokenUsageRecord::inputTokens).sum()));
                calls.get(10, TimeUnit.SECONDS);
                release.get(10, TimeUnit.SECONDS);
            }

            assertThat(released.get() + budgetManager.getAccumulatedInputTokens()).as("round %d", round).isEqualTo(callsPerRound);
        }
    }

    @Test
    void reset_shouldClearDetailedUsage() {
        budgetManager.consumeTokens(usage("test_step_action", 50, 30, 20));
        budgetManager.consumeToolCalls(5);
        budgetManager.reset();

        assertThat(budgetManager.getAccumulatedTotalTokens()).isZero();
        assertThat(budgetManager.getAccumulatedInputTokens()).isZero();
        assertThat(budgetManager.getAccumulatedOutputTokens()).isZero();
        assertThat(budgetManager.getAccumulatedCachedTokens()).isZero();
    }

    @Test
    void checkToolCallBudget_shouldThrow_whenOverLimit() {
        int limit = new AgentConfig().getAgentToolCallsBudget();
        if (limit <= 0)
            return;

        budgetManager.activateToolCallBudget();
        budgetManager.consumeToolCalls(limit + 1);

        assertThatThrownBy(budgetManager::checkToolCallBudget)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tool call budget exceeded");
    }

    @Test
    void checkToolCallBudget_shouldNotThrow_whenBudgetIsInactive() {
        int limit = new AgentConfig().getAgentToolCallsBudget();
        if (limit <= 0)
            return;

        budgetManager.consumeToolCalls(limit + 1);

        assertThatCode(budgetManager::checkToolCallBudget).doesNotThrowAnyException();
    }

    @Test
    void checkToolCallBudget_shouldNotThrow_whenUnderLimit() {
        int limit = new AgentConfig().getAgentToolCallsBudget();
        if (limit <= 0)
            return;

        budgetManager.activateToolCallBudget();
        budgetManager.consumeToolCalls(limit - 1);

        assertThatCode(budgetManager::checkToolCallBudget).doesNotThrowAnyException();
    }

    @Test
    void reset_shouldDeactivateToolCallBudget() {
        int limit = new AgentConfig().getAgentToolCallsBudget();
        if (limit <= 0)
            return;

        budgetManager.activateToolCallBudget();
        budgetManager.consumeToolCalls(limit + 1);
        budgetManager.reset();
        budgetManager.consumeToolCalls(limit + 1);

        assertThatCode(budgetManager::checkToolCallBudget).doesNotThrowAnyException();
    }

    @Test
    void resetToolCallUsage_shouldResetOnlyToolCalls() {
        budgetManager.consumeTokens(usage("test_step_action", 50, 30, 20));
        budgetManager.consumeToolCalls(5);

        budgetManager.resetToolCallUsage();

        assertThat(budgetManager.getAccumulatedTotalTokens()).isEqualTo(100);
        assertThatCode(budgetManager::checkToolCallBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTimeBudget_shouldNotThrow_whenNotYetActivated() {
        assertThatCode(budgetManager::checkTimeBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTimeBudget_shouldThrow_whenOverLimit() {
        int limit = new AgentConfig().getAgentExecutionTimeBudgetSeconds();
        if (limit <= 0)
            return;

        Instant start = Instant.ofEpochSecond(1000);
        Instant later = start.plusSeconds(limit + 10);

        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(start);
            budgetManager.reset();
            budgetManager.activateTimeBudget();

            mockedInstant.when(Instant::now).thenReturn(later);

            assertThatThrownBy(budgetManager::checkTimeBudget)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Execution time budget exceeded");
        }
    }

    @Test
    void checkAllBudgets_shouldCheckAll() {
        int toolLimit = new AgentConfig().getAgentToolCallsBudget();
        if (toolLimit > 0) {
            budgetManager.activateToolCallBudget();
            budgetManager.consumeToolCalls(toolLimit + 1);
            assertThatThrownBy(budgetManager::checkAllBudgets)
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
