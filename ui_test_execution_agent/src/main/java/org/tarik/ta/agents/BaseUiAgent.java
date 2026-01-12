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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.agents.GenericAiAgent;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;

import static org.tarik.ta.UiTestAgentConfig.isUnattendedMode;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;

public interface BaseUiAgent<T extends FinalResult> extends GenericAiAgent<T> {
    Logger LOG = LoggerFactory.getLogger(BaseUiAgent.class);

    default BufferedImage captureErrorScreenshot() {
        return UiCommonUtils.captureScreen();
    }

    @Override
    default UiOperationExecutionResult<T> createSuccessResult(T result) {
        return new UiOperationExecutionResult<>(SUCCESS, "Execution successful", result, null);
    }

    @Override
    default OperationExecutionResult<T> createErrorResult(ExecutionStatus status, String message, T result) {
        return new UiOperationExecutionResult<>(status, message,  result, captureErrorScreenshot());
    }

    @Override
    default void checkBudget() {
        if (isUnattendedMode()) {
            GenericAiAgent.super.checkBudget();
        }
    }
}