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
