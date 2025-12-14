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

public class DefaultErrorHandler implements ToolExecutionErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultErrorHandler.class);
    private static final List<ErrorCategory> terminalErrors = List.of(NON_RETRYABLE_ERROR, TIMEOUT,
            VERIFICATION_FAILED);
    private final RetryPolicy retryPolicy;
    private final RetryState retryState;
    private final boolean failOnTimeout;

    public DefaultErrorHandler(RetryPolicy retryPolicy, RetryState retryState) {
        this(retryPolicy, retryState, true);
    }

    public DefaultErrorHandler(RetryPolicy retryPolicy, RetryState retryState, boolean failOnTimeout) {
        this.retryPolicy = retryPolicy;
        this.retryState = retryState;
        this.failOnTimeout = failOnTimeout;
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
        retryState.startIfNotStarted();
        int attempts = retryState.incrementAttempts();
        long elapsedTime = retryState.getElapsedTime();
        boolean isTimeout = retryPolicy.timeoutMillis() > 0
                && elapsedTime > retryPolicy.timeoutMillis();
        boolean isMaxRetriesReached = attempts > retryPolicy.maxRetries();

        if (isTimeout && failOnTimeout) {
            throw new ToolExecutionException(
                    "Retry policy exceeded because of timeout. Original error: " + message,
                    TIMEOUT);
        } else if (isMaxRetriesReached && failOnTimeout) {
            throw new ToolExecutionException(
                    "Retry policy exceeded because of max retries. Original error: "
                            + message,
                    TIMEOUT);
        } else {
            LOG.info("Passing the following tool execution error to the agent: '{}'", message);
            return new ToolErrorHandlerResult(message);
        }
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public RetryState retryState() {
        return retryState;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (DefaultErrorHandler) obj;
        return Objects.equals(this.retryPolicy, that.retryPolicy) &&
                Objects.equals(this.retryState, that.retryState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retryPolicy, retryState);
    }

    @Override
    public String toString() {
        return "DefaultErrorHandler[" +
                "retryPolicy=" + retryPolicy + ", " +
                "retryState=" + retryState + ']';
    }

}
