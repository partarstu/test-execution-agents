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
package org.tarik.ta.core.model;

import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.error.RetryState;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import java.util.List;
import java.util.Objects;

import static org.tarik.ta.core.error.ErrorCategory.*;

public class DefaultToolErrorHandler implements ToolExecutionErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultToolErrorHandler.class);
    private static final List<ErrorCategory> terminalErrors = List.of(NON_RETRYABLE_ERROR, TIMEOUT);
    private final RetryPolicy retryPolicy;
    private final ThreadLocal<RetryState> retryState = ThreadLocal.withInitial(RetryState::new);
    private final boolean failOnTimeout;

    public DefaultToolErrorHandler(RetryPolicy retryPolicy) {
        this(retryPolicy, true);
    }

    public DefaultToolErrorHandler(RetryPolicy retryPolicy, boolean failOnTimeout) {
        this.retryPolicy = retryPolicy;
        this.failOnTimeout = failOnTimeout;
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
                return handleRetryableToolError(toolExecutionException.getMessage());
            }
        } else {
            throw new RuntimeException(error);
        }
    }

    protected ToolErrorHandlerResult handleRetryableToolError(String message) throws ToolExecutionException {
        var state = retryState.get();
        state.startIfNotStarted();
        int attempts = state.incrementAttempts();
        long elapsedTime = state.getElapsedTime();
        boolean isTimeout = retryPolicy.timeoutMillis() > 0 && elapsedTime > retryPolicy.timeoutMillis();
        boolean isMaxRetriesReached = attempts > retryPolicy.maxRetries();

        if (isTimeout && failOnTimeout) {
            throw new ToolExecutionException("Retry policy exceeded because of timeout. Original error: " + message, TIMEOUT);
        } else if (isMaxRetriesReached && failOnTimeout) {
            throw new ToolExecutionException("Retry policy exceeded because of max retries. Original error: " + message, TIMEOUT);
        } else {
            LOG.info("Passing the following tool execution error to the agent: '{}'", message);
            return new ToolErrorHandlerResult(message);
        }
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
