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
import org.tarik.ta.core.AgentConfig;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetManagerTest {

    @BeforeEach
    void setUp() {
        BudgetManager.reset();
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenUnderLimit() {
        // Given
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit <= 0) return; // Skip if no limit

        // When
        BudgetManager.consumeTokens("test-model", limit - 1, 0, 0);

        // Then
        assertThatCode(BudgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldNotThrow_whenAtLimit() {
        // Given
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit <= 0) return; // Skip if no limit

        // When
        BudgetManager.consumeTokens("test-model", limit, 0, 0);

        // Then
        assertThatCode(BudgetManager::checkTokenBudget).doesNotThrowAnyException();
    }

    @Test
    void checkTokenBudget_shouldThrow_whenOverLimit() {
        // Given
        int limit = AgentConfig.getAgentTokenBudget();
        if (limit <= 0) return; // Skip if no limit

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
    }

    @Test
    void reset_shouldClearDetailedUsage() {
        BudgetManager.consumeTokens("test-model", 50, 30, 20);
        BudgetManager.reset();

        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedTotalTokens()).isZero();
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedInputTokens()).isZero();
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedOutputTokens()).isZero();
        org.assertj.core.api.Assertions.assertThat(BudgetManager.getAccumulatedCachedTokens()).isZero();
    }
}
