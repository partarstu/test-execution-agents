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
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.System.currentTimeMillis;

import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.*;
import static org.tarik.ta.core.error.ErrorCategory.TERMINATION_BY_USER;
import static org.tarik.ta.core.error.ErrorCategory.VERIFICATION_FAILED;
import static org.tarik.ta.core.manager.BudgetManager.checkAllBudgets;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;

public interface GenericAiAgent<T extends FinalResult<T>> {
    Logger LOG = LoggerFactory.getLogger(GenericAiAgent.class);

    default void checkBudget() {
        checkAllBudgets();
    }

    default OperationExecutionResult<T> createSuccessResult(T result) {
        return new OperationExecutionResult<>(SUCCESS, "Execution successful", result);
    }

    default OperationExecutionResult<T> createErrorResult(ExecutionStatus status, String message, T result) {
        return new OperationExecutionResult<>(status, message, result);
    }

    RetryPolicy getRetryPolicy();

    String getAgentTaskDescription();

    /**
     * Executes an operation and returns the execution result. Any exception thrown during this execution will result in the immediate
     * interruption of the execution, no retries will be applied. The concept of each agent is to execute everything using tools,
     * including returning a result. Any exception thrown by {@link org.tarik.ta.core.model.DefaultToolErrorHandler} is treated as the
     * one that implies no retries.
     *
     * @param operation - operation to execute
     * @return - execution result containing the optional operation result
     */
    @NotNull
    default OperationExecutionResult<T> executeAndGetResult(Supplier<Result<?>> operation) {
        checkBudget();
        try {
            Result<?> resultWrapper = operation.get();
            T result = extractResult(resultWrapper);
            return createSuccessResult(result);
        } catch (Throwable e) {
            String taskDescription = getAgentTaskDescription();
            switch (e) {
                case ToolExecutionException tee when tee.getErrorCategory() == TERMINATION_BY_USER -> {
                    LOG.error("User decided to interrupt execution while agent was {}", taskDescription);
                    return createErrorResult(INTERRUPTED_BY_USER, e.getMessage(), null);
                }
                case ToolExecutionException tee when tee.getErrorCategory() == VERIFICATION_FAILED -> {
                    return createErrorResult(VERIFICATION_FAILURE, e.getMessage(), null);
                }
                case ToolExecutionException _ -> {
                    String message = "Got tool error while %s : %s".formatted(taskDescription, e.getMessage());
                    LOG.error(message, e);
                    return createErrorResult(ERROR, e.getMessage(), null);
                }
                default -> {
                    String message = "Error while %s".formatted(taskDescription);
                    LOG.error(message, e);
                    return createErrorResult(ERROR, e.getMessage(), null);
                }
            }
        }
    }


    /**
     * This method doesn't handle any exceptions - the concept of each agent is to execute everything using tools, including returning a
     * result. That's why any exception thrown during execution will be either an unexpected Exception or a {@link ToolExecutionException}
     * thrown by {@link org.tarik.ta.core.model.DefaultToolErrorHandler}. Because the latter already handles exceptions related to the
     * retry, none of those two exceptions need a retry.
     */
    @NotNull
    default OperationExecutionResult<T> executeWithRetry(Supplier<Result<?>> action, @NotNull Predicate<T> shouldRetry) {
        RetryPolicy policy = getRetryPolicy();
        int attempts = 0;
        long startTime = currentTimeMillis();
        String taskDescription = getAgentTaskDescription();

        long elapsedTime = currentTimeMillis() - startTime;
        OperationExecutionResult<T> operationResult;
        do {
            operationResult = executeAndGetResult(action);
            ++attempts;
            if (!operationResult.isSuccess()) {
                LOG.warn("Got error while {}, no retries will be made.", taskDescription);
                return operationResult;
            }
            var result = operationResult.getResultPayload();
            if (!shouldRetry.test(result)) {
                return operationResult;
            } else {
                var message = "Retry needs to be done after %d attempt(s) because %s has the following result: '%s'".formatted(
                        attempts, taskDescription, result);
                LOG.info(message);
                sleepMillis(policy.delayMillis());
            }
        } while (elapsedTime < policy.timeoutMillis() && (attempts - 1) <= policy.maxRetries());
        LOG.warn("{} failed after {} attempts (elapsed: {}ms). Last result: {}",
                taskDescription, attempts, elapsedTime, operationResult.getResultPayload());
        return operationResult;
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
}
