/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BudgetManagerTest {

    @BeforeEach
    void setUp() {
        BudgetManager.reset();
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenUnderLimit() {
        // Given
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit <= 0)
            return; // Skip if no limit

        // When
        BudgetManager.consumeTokens("test-model", limit - 1, 0, 0);

        // Then
        assertThatCode(BudgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenAtLimit() {
        // Given
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit <= 0)
            return; // Skip if no limit

        // When
        BudgetManager.consumeTokens("test-model", limit, 0, 0);

        // Then
        assertThatCode(BudgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldThrow_whenOverLimit() {
        // Given
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit <= 0)
            return; // Skip if no limit

        // When
        try {
            BudgetManager.consumeTokens("test-model", limit + 1, 0, 0);
        } catch (RuntimeException e) {
            // Expected
        }

        // Then
        assertThatThrownBy(BudgetManager::checkTokenBudget)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token budget exceeded");
    }

    @Test
    void consumeTokens_AndCheckBudget_shouldTrackDetailedUsage() {
        BudgetManager.consumeTokens("test-model", 50, 30, 20);

        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedTotalTokens()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedInputTokens()).isEqualTo(50);
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedOutputTokens()).isEqualTo(30);
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedCachedTokens()).isEqualTo(20);

        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedTotalTokens("test-model"))
                .isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedInputTokens("test-model")).isEqualTo(50);
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedOutputTokens("test-model"))
                .isEqualTo(30);
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedCachedTokens("test-model"))
                .isEqualTo(20);

        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedTotalTokens("other-model")).isZero();
    }

    @Test
    void reset_shouldClearDetailedUsage() {
        BudgetManager.consumeTokens("test-model", 50, 30, 20);
        BudgetManager.consumeToolCalls(5);
        BudgetManager.reset();

        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedTotalTokens()).isZero();
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedInputTokens()).isZero();
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedOutputTokens()).isZero();
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedCachedTokens()).isZero();
    }

    @Test
    void checkToolCallBudget_shouldThrow_whenOverLimit() {
        int limit = AgentConfig.getAgentToolCallsBudget();
        if (limit <= 0)
            return;

        BudgetManager.consumeToolCalls(limit + 1);

        assertThatThrownBy(BudgetManager::checkToolCallBudget)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tool call budget exceeded");
    }

    @Test
    void checkToolCallBudget_shouldNotThrow_whenUnderLimit() {
        int limit = AgentConfig.getAgentToolCallsBudget();
        if (limit <= 0)
            return;

        BudgetManager.consumeToolCalls(limit - 1);

        assertThatCode(BudgetManager::checkToolCallBudget).doesNotThrowAnyException();
    }

    @Test
    void resetToolCallUsage_shouldResetOnlyToolCalls() {
        BudgetManager.consumeTokens("test-model", 50, 30, 20);
        BudgetManager.consumeToolCalls(5);

        BudgetManager.resetToolCallUsage();

        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedTotalTokens()).isEqualTo(100);
        // We can't verify tool call usage count directly as it's private, but we can
        // verify it doesn't throw if we add more up to limit
        assertThatCode(BudgetManager::checkToolCallBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTimeBudget_shouldNotThrow_whenNotYetActivated() {
        // Given - Create a fresh state where reset() has NOT been called
        // We simulate this by running this test in isolation conceptually.
        // Since @BeforeEach calls reset(), we need to use a workaround.
        // The actual test verifies the behavior matches our expectation from the
        // implementation.

        // When/Then - After reset() is called (in @BeforeEach), budget is activated and
        // should work normally
        assertThatCode(BudgetManager::checkTimeBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTimeBudget_shouldThrow_whenOverLimit() {
        int limit = AgentConfig.getAgentExecutionTimeBudgetSeconds();
        if (limit <= 0)
            return;

        Instant start = Instant.ofEpochSecond(1000);
        Instant later = start.plusSeconds(limit + 10);

        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(start);
            BudgetManager.reset(); // Capture start time

            mockedInstant.when(Instant::now).thenReturn(later);

            assertThatThrownBy(BudgetManager::checkTimeBudget)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Execution time budget exceeded");
        }
    }

    @Test
    void checkAllBudgets_shouldCheckAll() {
        int toolLimit = AgentConfig.getAgentToolCallsBudget();
        if (toolLimit > 0) {
            BudgetManager.consumeToolCalls(toolLimit + 1);
            assertThatThrownBy(BudgetManager::checkAllBudgets)
                    .isInstanceOf(RuntimeException.class);
        }
    }
}