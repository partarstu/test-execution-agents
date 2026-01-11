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
package org.tarik.ta.agents;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.agents.GenericAiAgent;
import org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.dto.UiAgentExecutionResult;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;

import static java.time.Instant.now;
import static org.tarik.ta.UiTestAgentConfig.isUnattendedMode;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.SUCCESS;

public interface BaseUiAgent<T extends FinalResult> extends GenericAiAgent<T, UiAgentExecutionResult<T>> {
    Logger LOG = LoggerFactory.getLogger(BaseUiAgent.class);

    default BufferedImage captureErrorScreenshot() {
        return UiCommonUtils.captureScreen();
    }

    @Override
    default UiAgentExecutionResult<T> createSuccessResult(T result) {
        return new UiAgentExecutionResult<>(SUCCESS, "Execution successful", true, result, null, now());
    }

    @Override
    default UiAgentExecutionResult<T> createErrorResult(ExecutionStatus status, String message, @Nullable Throwable t) {
        return new UiAgentExecutionResult<>(status, message, false, null, captureErrorScreenshot(), now());
    }

    @Override
    default void checkBudget() {
        if (isUnattendedMode()) {
            GenericAiAgent.super.checkBudget();
        }
    }
}