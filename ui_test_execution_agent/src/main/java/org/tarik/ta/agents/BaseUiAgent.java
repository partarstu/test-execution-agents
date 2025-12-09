package org.tarik.ta.agents;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.agents.GenericAiAgent;
import org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.dto.UiAgentExecutionResult;
import org.tarik.ta.utils.CommonUtils;

import java.awt.image.BufferedImage;

import static java.time.Instant.now;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.SUCCESS;

public interface BaseUiAgent<T extends FinalResult<T>> extends GenericAiAgent<T, UiAgentExecutionResult<T>> {
    Logger LOG = LoggerFactory.getLogger(BaseUiAgent.class);

    default BufferedImage captureErrorScreenshot() {
        return CommonUtils.captureScreen();
    }

    @Override
    default UiAgentExecutionResult<T> createSuccessResult(T result) {
        return new UiAgentExecutionResult<>(SUCCESS, "Execution successful", true, result, null, now());
    }

    @Override
    default UiAgentExecutionResult<T> createErrorResult(ExecutionStatus status, String message, @Nullable Throwable t) {
        return new UiAgentExecutionResult<>(status, message, false, null, captureErrorScreenshot(), now());
    }
}