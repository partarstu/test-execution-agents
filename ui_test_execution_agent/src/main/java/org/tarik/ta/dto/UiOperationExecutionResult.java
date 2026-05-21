/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.dto;

import dev.langchain4j.model.output.structured.Description;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.core.dto.OperationExecutionResult;

import java.awt.image.BufferedImage;
import java.util.Objects;

@Description("Result of a tool execution containing status, message, optional screenshot, typed payload, and timestamp")
public final class UiOperationExecutionResult<T> extends OperationExecutionResult<T> {
    private final @Nullable BufferedImage screenshot;

    public UiOperationExecutionResult(ExecutionStatus executionStatus, String message, @Nullable T resultPayload,
                                      @Nullable BufferedImage screenshot) {
        super(executionStatus, message, resultPayload);
        this.screenshot = screenshot;
    }

    public @Nullable BufferedImage screenshot() {
        return screenshot;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UiOperationExecutionResult<?> that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        return Objects.equals(screenshot, that.screenshot);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(screenshot);
        return result;
    }
}
