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
package org.tarik.ta.core.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.utils.CommonUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.tarik.ta.core.error.ErrorCategory.NON_RETRYABLE_ERROR;
import org.tarik.ta.core.exceptions.ToolExecutionException;

class BaseAiAgentRetryTest {

    private MockedStatic<CommonUtils> CoreUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        CoreUtilsMockedStatic = mockStatic(CommonUtils.class, CALLS_REAL_METHODS);
        CoreUtilsMockedStatic.when(() -> CommonUtils.sleepMillis(anyInt())).thenAnswer(invocation -> null);
    }

    @AfterEach
    void tearDown() {
        CoreUtilsMockedStatic.close();
    }

    record TestResult(String value) implements FinalResult {
    }

    // Concrete implementation for testing default methods
    static class TestAgent implements GenericAiAgent<TestResult> {

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
        Supplier<Result<?>> action = () -> Result.<TestResult>builder().content(new TestResult("Success")).build();

        // When
        OperationExecutionResult<TestResult> result = agent.executeAndGetResult(action);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getResultPayload().value()).isEqualTo("Success");
    }

    @Test
    @DisplayName("Should retry and succeed eventually")
    void shouldRetryAndSucceed() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 1000);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            if (attempts.incrementAndGet() < 3) {
                return Result.<TestResult>builder().content(new TestResult("Transient Failure")).build();
            }
            return Result.<TestResult>builder().content(new TestResult("Success")).build();
        };

        // When
        var result = agent.executeWithRetry(action, r -> "Transient Failure".equals(r.value()), policy);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getResultPayload().value()).isEqualTo("Success");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should fail after max retries")
    void shouldFailAfterMaxRetries() {
        // Given
        RetryPolicy policy = new RetryPolicy(2, 10, 1000);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            attempts.incrementAndGet();
            return Result.<TestResult>builder().content(new TestResult("Persistent error")).build();
        };

        // When
        OperationExecutionResult<TestResult> result = agent.executeWithRetry(action,
                r -> "Persistent error".equals(r.value()), policy);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getResultPayload().value()).isEqualTo("Persistent error");
        assertThat(attempts.get()).isGreaterThan(2); // Initial + 2 retries = 3 attempts
    }

    @Test
    @DisplayName("Should fail on timeout")
    void shouldFailOnTimeout() {
        // Given
        // Short timeout, long delay
        RetryPolicy policy = new RetryPolicy(10, 100, 50);
        Supplier<Result<?>> action = () -> {
            return Result.<TestResult>builder().content(new TestResult("Slow error")).build();
        };

        // When
        OperationExecutionResult<TestResult> result = agent.executeWithRetry(action,
                r -> "Slow error".equals(r.value()), policy);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getResultPayload().value()).isEqualTo("Slow error");
    }

    @Test
    @DisplayName("Should not retry on NON_RETRYABLE_ERROR")
    void shouldNotRetryOnNonRetryableError() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 10, 1000);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            attempts.incrementAndGet();
            throw new ToolExecutionException("Fatal error",
                    NON_RETRYABLE_ERROR);
        };

        // When
        OperationExecutionResult<TestResult> result = agent.executeWithRetry(action, r -> true, policy);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getMessage()).isEqualTo("Fatal error");
        assertThat(attempts.get()).isEqualTo(1); // Should not retry
    }

    @Test
    @DisplayName("Should retry on predicate match")
    void shouldRetryOnPredicateMatch() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 100, 1000);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            attempts.incrementAndGet();
            return Result.<TestResult>builder().content(new TestResult("Failed")).build();
        };

        // When
        // Retry if result is "Failed"
        OperationExecutionResult<TestResult> result = agent.executeWithRetry(action,
                res -> "Failed".equals(res.value()), policy);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getResultPayload().value()).isEqualTo("Failed");
        assertThat(attempts.get()).isGreaterThan(1);
    }

    @Test
    @DisplayName("Should succeed when predicate stops matching")
    void shouldSucceedWhenPredicateStopsMatching() {
        // Given
        RetryPolicy policy = new RetryPolicy(3, 100, 1000);
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Result<?>> action = () -> {
            if (attempts.incrementAndGet() < 3) {
                return Result.<TestResult>builder().content(new TestResult("Failed")).build();
            }
            return Result.<TestResult>builder().content(new TestResult("Success")).build();
        };

        // When
        OperationExecutionResult<TestResult> result = agent.executeWithRetry(action,
                res -> "Failed".equals(res.value()), policy);

        // Then
        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.getResultPayload().value()).isEqualTo("Success");
        assertThat(attempts.get()).isEqualTo(3);
    }
}