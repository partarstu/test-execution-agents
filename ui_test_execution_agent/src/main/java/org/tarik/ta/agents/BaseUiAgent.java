/*
 * ui-test-execution-agent - ${project.description}
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
        return new UiOperationExecutionResult<>(status, message, result, captureErrorScreenshot());
    }

    @Override
    default void checkBudget() {
        GenericAiAgent.super.checkBudget();
    }
}