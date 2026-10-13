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
package org.tarik.ta.core.model;

import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.error.RetryState;
import org.tarik.ta.core.exceptions.BudgetExceededException;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.tarik.ta.core.error.ErrorCategory.*;

public class DefaultToolErrorHandler implements ToolExecutionErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultToolErrorHandler.class);
    private static final List<ErrorCategory> terminalErrors = List.of(NON_RETRYABLE_ERROR, TIMEOUT);
    private static final String INVALID_ARGUMENTS_MESSAGE = "The arguments of this tool call could not be parsed: %s. Call the tool " +
            "again with arguments which are valid JSON matching the tool's parameter schema, passing every object parameter as a JSON " +
            "object, not as a string.";
    private final RetryPolicy retryPolicy;
    private final ThreadLocal<InvocationRetryState> retryState =
            ThreadLocal.withInitial(() -> new InvocationRetryState(null, new RetryState()));
    private final boolean failOnTimeout;

    // The retry budget belongs to one AI service invocation: a budget used up by an earlier step must not fail the next one.
    private record InvocationRetryState(@Nullable UUID invocationId, RetryState state) {
    }

    public DefaultToolErrorHandler(RetryPolicy retryPolicy) {
        this(retryPolicy, true);
    }

    public DefaultToolErrorHandler(RetryPolicy retryPolicy, boolean failOnTimeout) {
        this.retryPolicy = retryPolicy;
        this.failOnTimeout = failOnTimeout;
    }

    /**
     * Returns malformed tool-call arguments to the model, which can correct them itself, until the retry policy is exhausted.
     */
    public static @NotNull ToolArgumentsErrorHandler forToolArguments(@NotNull RetryPolicy retryPolicy) {
        var handler = new DefaultToolErrorHandler(retryPolicy);
        return (error, context) -> handler.handleRetryableToolError(context, INVALID_ARGUMENTS_MESSAGE.formatted(error.getMessage()), true);
    }

    public void reset() {
        retryState.remove();
    }

    protected List<ErrorCategory> getTerminalErrors() {
        return terminalErrors;
    }

    @Override
    public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
        if (error instanceof ToolExecutionException toolExecutionException) {
            if (getTerminalErrors().contains(toolExecutionException.getErrorCategory())) {
                throw toolExecutionException;
            } else {
                return handleRetryableToolError(context, toolExecutionException.getMessage());
            }
        } else {
            throw unhandledError(error);
        }
    }

    /**
     * Ends the agent invocation with an error which is not given back to the model, keeping an exhausted budget's own message.
     */
    protected static RuntimeException unhandledError(@Nullable Throwable error) {
        return error instanceof BudgetExceededException budgetExceeded ? budgetExceeded : new RuntimeException(error);
    }

    protected ToolErrorHandlerResult handleRetryableToolError(ToolErrorContext context, String message) throws ToolExecutionException {
        return handleRetryableToolError(context, message, failOnTimeout);
    }

    protected ToolErrorHandlerResult handleRetryableToolError(ToolErrorContext context, String message, boolean failWhenExhausted)
            throws ToolExecutionException {
        var state = retryStateOf(context);
        state.startIfNotStarted();
        int attempts = state.incrementAttempts();
        long elapsedTime = state.getElapsedTime();
        boolean isTimeout = retryPolicy.timeoutMillis() > 0 && elapsedTime > retryPolicy.timeoutMillis();
        boolean isMaxRetriesReached = attempts > retryPolicy.maxRetries();

        if (isTimeout && failWhenExhausted) {
            throw new ToolExecutionException("Retry policy exceeded because of timeout. Original error: " + message, TIMEOUT);
        } else if (isMaxRetriesReached && failWhenExhausted) {
            throw new ToolExecutionException("Retry policy exceeded because of max retries. Original error: " + message, TIMEOUT);
        } else {
            LOG.info("Passing the following tool execution error to the agent: '{}'", message);
            return new ToolErrorHandlerResult(message);
        }
    }

    private RetryState retryStateOf(ToolErrorContext context) {
        var invocationId = context.invocationContext() != null ? context.invocationContext().invocationId() : null;
        var current = retryState.get();
        if (!Objects.equals(current.invocationId(), invocationId)) {
            current = new InvocationRetryState(invocationId, new RetryState());
            retryState.set(current);
        }
        return current.state();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (DefaultToolErrorHandler) obj;
        return Objects.equals(this.retryPolicy, that.retryPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retryPolicy);
    }

    @Override
    public String toString() {
        return "DefaultToolErrorHandler[" + "retryPolicy=" + retryPolicy + ']';
    }
}
