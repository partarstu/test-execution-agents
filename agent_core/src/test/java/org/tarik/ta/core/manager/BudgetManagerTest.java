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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.tarik.ta.core.AgentConfig;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class BudgetManagerTest {

    private BudgetManager budgetManager;

    @BeforeEach
    void setUp() {
        budgetManager = new BudgetManager(new AgentConfig());
        budgetManager.reset();
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenUnderLimit() {
        int limit = new AgentConfig().getAgentTokenBudget();
        if (limit <= 0)
            return;

        budgetManager.consumeTokens("test-model", limit - 1, 0, 0);

        assertThatCode(budgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenAtLimit() {
        int limit = new AgentConfig().getAgentTokenBudget();
        if (limit <= 0)
            return;

        budgetManager.consumeTokens("test-model", limit, 0, 0);

        assertThatCode(budgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldThrow_whenOverLimit() {
        int limit = new AgentConfig().getAgentTokenBudget();
        if (limit <= 0)
            return;

        try {
            budgetManager.consumeTokens("test-model", limit + 1, 0, 0);
        } catch (RuntimeException e) {
            // Expected
        }

        assertThatThrownBy(budgetManager::checkTokenBudget)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token budget exceeded");
    }

    @Test
    void consumeTokens_AndCheckBudget_shouldTrackDetailedUsage() {
        budgetManager.consumeTokens("test-model", 50, 30, 20);

        assertThat(budgetManager.getAccumulatedTotalTokens()).isEqualTo(100);
        assertThat(budgetManager.getAccumulatedInputTokens()).isEqualTo(50);
        assertThat(budgetManager.getAccumulatedOutputTokens()).isEqualTo(30);
        assertThat(budgetManager.getAccumulatedCachedTokens()).isEqualTo(20);

        assertThat(budgetManager.getAccumulatedTotalTokens("test-model")).isEqualTo(100);
        assertThat(budgetManager.getAccumulatedInputTokens("test-model")).isEqualTo(50);
        assertThat(budgetManager.getAccumulatedOutputTokens("test-model")).isEqualTo(30);
        assertThat(budgetManager.getAccumulatedCachedTokens("test-model")).isEqualTo(20);

        assertThat(budgetManager.getAccumulatedTotalTokens("other-model")).isZero();
    }

    @Test
    void reset_shouldClearDetailedUsage() {
        budgetManager.consumeTokens("test-model", 50, 30, 20);
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

        budgetManager.consumeToolCalls(limit + 1);

        assertThatThrownBy(budgetManager::checkToolCallBudget)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tool call budget exceeded");
    }

    @Test
    void checkToolCallBudget_shouldNotThrow_whenUnderLimit() {
        int limit = new AgentConfig().getAgentToolCallsBudget();
        if (limit <= 0)
            return;

        budgetManager.consumeToolCalls(limit - 1);

        assertThatCode(budgetManager::checkToolCallBudget).doesNotThrowAnyException();
    }

    @Test
    void resetToolCallUsage_shouldResetOnlyToolCalls() {
        budgetManager.consumeTokens("test-model", 50, 30, 20);
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
            budgetManager.consumeToolCalls(toolLimit + 1);
            assertThatThrownBy(budgetManager::checkAllBudgets)
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
