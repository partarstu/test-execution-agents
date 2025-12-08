package org.tarik.ta.core.agents;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.dto.AgentExecutionResult;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus;

import static java.time.Instant.now;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.SUCCESS;

public interface BaseAiAgent<T extends FinalResult<T>> extends GenericAiAgent<T, AgentExecutionResult<T>> {
    Logger LOG = LoggerFactory.getLogger(BaseAiAgent.class);

    @Override
    default AgentExecutionResult<T> createSuccessResult(T result) {
        return new AgentExecutionResult<>(SUCCESS, "Execution successful", true, result, now());
    }

    @Override
    default AgentExecutionResult<T> createErrorResult(ExecutionStatus status, String message, @Nullable Throwable t) {
        return new AgentExecutionResult<>(status, message, false, null, now());
    }
}
