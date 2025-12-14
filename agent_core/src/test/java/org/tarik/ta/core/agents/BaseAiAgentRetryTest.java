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
package org.tarik.ta.core.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.dto.AgentExecutionResult;
import org.tarik.ta.core.utils.CommonUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.SUCCESS;

class BaseAiAgentRetryTest {

    private MockedStatic<CommonUtils> CoreUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        CoreUtilsMockedStatic = mockStatic(CommonUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        CoreUtilsMockedStatic.when(() -> CommonUtils.sleepMillis(anyInt())).thenAnswer(invocation -> null);
    }

    @AfterEach
    void tearDown() {
        CoreUtilsMockedStatic.close();
    }

    record TestResult(String value) implements FinalResult<TestResult> {}

    // Concrete implementation for testing default methods
    static class TestAgent implements BaseAiAgent<TestResult> {
        private RetryPolicy retryPolicy;

        public void setRetryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
        }

        @Override
        public RetryPolicy getRetryPolicy() {
            return retryPolicy;
        }

        @Override
        public String getAgentTaskDescription() {
            return "Test Task";
        }
    }

    private final TestAgent agent = new TestAgent();

    @Test
    @DisplayName("Should succeed on first attempt without retries")
    void shouldSucceedOnFirstAttempt() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        agent.setRetryPolicy(policy);
        Supplier<Result<?>> action = () -> Result.<TestResult>builder().content(new TestResult("Success")).build();

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getResultPayload().value()).isEqualTo("Success");
    }

    @Test
    @DisplayName("Should retry and succeed eventually")
    void shouldRetryAndSucceed() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("Transient error");
            }
            return Result.<TestResult>builder().content(new TestResult("Success")).build();
        };

        // When
        var result = agent.executeWithRetry(action);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getResultPayload().value()).isEqualTo("Success");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should fail after max retries")
    void shouldFailAfterMaxRetries() {
        // Given
        RetryPolicy policy = new RetryPolicy(2, 10, 100, 2.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("Persistent error");
        };

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getMessage()).isEqualTo("Persistent error");
        assertThat(attempts.get()).isGreaterThan(2); // Initial + 2 retries = 3 attempts
    }

    @Test
    @DisplayName("Should fail on timeout")
    void shouldFailOnTimeout() {
        // Given
        // Short timeout, long delay
        RetryPolicy policy = new RetryPolicy(10, 100, 100, 1.0, 50);
        agent.setRetryPolicy(policy);
        Supplier<Result<?>> action = () -> {
            throw new RuntimeException("Slow error");
        };

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getMessage()).isEqualTo("Slow error");
    }

    @Test
    @DisplayName("Should not retry on NON_RETRYABLE_ERROR")
    void shouldNotRetryOnNonRetryableError() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            attempts.incrementAndGet();
            throw new org.tarik.ta.core.exceptions.ToolExecutionException("Fatal error",
                    org.tarik.ta.core.error.ErrorCategory.NON_RETRYABLE_ERROR);
        };

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getMessage()).isEqualTo("Fatal error");
        assertThat(attempts.get()).isEqualTo(1); // Should not retry
    }

    @Test
    @DisplayName("Should retry on predicate match")
    void shouldRetryOnPredicateMatch() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 100, 100, 1.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            attempts.incrementAndGet();
            return Result.<TestResult>builder().content(new TestResult("Failed")).build();
        };

        // When
        // Retry if result is "Failed"
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action, res -> "Failed".equals(res.value()));

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getMessage()).contains("Retry explicitly requested by the task");
        assertThat(attempts.get()).isGreaterThan(1);
    }

    @Test
    @DisplayName("Should succeed when predicate stops matching")
    void shouldSucceedWhenPredicateStopsMatching() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 100, 100, 1.0, 1000);
        agent.setRetryPolicy(policy);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            if (attempts.incrementAndGet() < 3) {
                return Result.<TestResult>builder().content(new TestResult("Failed")).build();
            }
            return Result.<TestResult>builder().content(new TestResult("Success")).build();
        };

        // When
        AgentExecutionResult<TestResult> result = agent.executeWithRetry(action, res -> "Failed".equals(res.value()));

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getResultPayload().value()).isEqualTo("Success");
        assertThat(attempts.get()).isEqualTo(3);
    }
}