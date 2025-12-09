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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.dto.AgentExecutionResult;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.System.currentTimeMillis;
import static java.time.Instant.now;
import static org.tarik.ta.core.AgentConfig.isUnattendedMode;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.*;
import static org.tarik.ta.core.error.ErrorCategory.TERMINATION_BY_USER;
import static org.tarik.ta.core.error.ErrorCategory.VERIFICATION_FAILED;
import static org.tarik.ta.core.manager.BudgetManager.checkAllBudgets;
import static org.tarik.ta.core.utils.CoreUtils.sleepMillis;

public interface GenericAiAgent<T extends FinalResult<T>, R extends AgentExecutionResult<T>> {
    Logger LOG = LoggerFactory.getLogger(GenericAiAgent.class);

    static void checkBudgetIfUnattended() {
        if (isUnattendedMode()) {
            checkAllBudgets();
        }
    }

    R createSuccessResult(T result);

    R createErrorResult(AgentExecutionResult.ExecutionStatus status, String message, @Nullable Throwable t);

    RetryPolicy getRetryPolicy();

    String getAgentTaskDescription();

    @NotNull
    default R executeAndGetResult(Supplier<Result<?>> action) {
        checkBudgetIfUnattended();
        try {
            Result<?> resultWrapper = action.get();
            T result = extractResult(resultWrapper);
            return createSuccessResult(result);
        } catch (Throwable e) {
            LOG.error("Error executing agent action", e);
            return createErrorResult(ERROR, e.getMessage(), e);
        }
    }

    @NotNull
    default R executeWithRetry(Supplier<Result<?>> action, Predicate<T> retryCondition) {
        RetryPolicy policy = getRetryPolicy();
        int attempt = 0;
        long startTime = currentTimeMillis();
        String taskDescription = getAgentTaskDescription();

        while (true) {
            attempt++;
            checkBudgetIfUnattended();
            try {
                Result<?> resultWrapper = action.get();
                T result = extractResult(resultWrapper);

                if (retryCondition != null && retryCondition.test(result)) {
                    String message = "Retry explicitly requested by the task because it has the following result: " + result;
                    R errorResult = handleRetry(attempt, startTime, policy, message, taskDescription);
                    if (errorResult != null) {
                        return errorResult;
                    }
                    continue;
                }
                return createSuccessResult(result);
            } catch (Throwable e) {
                switch (e) {
                    case ToolExecutionException tee when tee.getErrorCategory() == TERMINATION_BY_USER -> {
                        LOG.error("User decided to interrupt execution");
                        return createErrorResult(INTERRUPTED_BY_USER, e.getMessage(), e);
                    }
                    case ToolExecutionException tee when tee.getErrorCategory() == VERIFICATION_FAILED -> {
                        return createErrorResult(VERIFICATION_FAILURE, e.getMessage(), e);
                    }
                    case ToolExecutionException _ -> {
                        String message = "Agent execution failed: %s".formatted(e.getMessage());
                        LOG.error(message, e);
                        return createErrorResult(ERROR, e.getMessage(), e);
                    }
                    default -> {
                    }
                }

                LOG.error("Got error while executing action for task: {}. Retrying...", taskDescription, e);
                R errorResult = handleRetry(attempt, startTime, policy, e.getMessage(), taskDescription);
                if (errorResult != null) {
                    return errorResult;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    default T extractResult(Result<?> resultWrapper) {
        if (resultWrapper == null) {
            return null;
        }
        if (resultWrapper.content() != null) {
            try {
                return (T) resultWrapper.content();
            } catch (ClassCastException e) {
                LOG.warn("Result content is of unexpected type: {}", resultWrapper.content());
            }
        }

        if (resultWrapper.toolExecutions() != null && !resultWrapper.toolExecutions().isEmpty()) {
            Object executionResult = resultWrapper.toolExecutions().getLast().resultObject();
            try {
                return (T) executionResult;
            } catch (ClassCastException e) {
                LOG.warn("Could not cast tool execution result '{}' to the expected type.", executionResult);
            }
        }
        return null;
    }

    @Nullable
    default R handleRetry(int attempt, long startTime, RetryPolicy policy, String message,
                                                String taskDescription) {
        long elapsedTime = currentTimeMillis() - startTime;
        boolean isTimeout = policy.timeoutMillis() > 0 && elapsedTime > policy.timeoutMillis();
        boolean isMaxRetriesReached = attempt > policy.maxRetries();

        if (isTimeout || isMaxRetriesReached) {
            LOG.error("Operation for task '{}' failed after {} attempts (elapsed: {}ms). Last error: {}", taskDescription, attempt,
                    elapsedTime, message);
            return createErrorResult(ERROR, message, null);
        }

        long delayMillis = (long) (policy.initialDelayMillis() * Math.pow(policy.backoffMultiplier(), attempt - 1));
        delayMillis = Math.min(delayMillis, policy.maxDelayMillis());
        LOG.warn("Attempt {} for task '{}' failed: {}. Retrying in {}ms...", attempt, taskDescription, message, delayMillis);
        sleepMillis((int) delayMillis);
        return null;
    }

    default R executeWithRetry(Supplier<Result<?>> action) {
        return executeWithRetry(action, null);
    }
}
