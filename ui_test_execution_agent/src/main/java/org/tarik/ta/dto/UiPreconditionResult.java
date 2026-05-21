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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.core.dto.PreconditionResult;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Objects;

public class UiPreconditionResult extends PreconditionResult {
    private final @Nullable @JsonIgnore BufferedImage screenshot;

    public UiPreconditionResult(@NotNull String precondition, boolean success, @Nullable String errorMessage,
            @Nullable BufferedImage screenshot,
            @Nullable Instant executionStartTimestamp, @Nullable Instant executionEndTimestamp) {
        super(precondition, success, errorMessage, executionStartTimestamp, executionEndTimestamp);
        this.screenshot = screenshot;
    }

    public @Nullable BufferedImage getScreenshot() {
        return screenshot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        UiPreconditionResult that = (UiPreconditionResult) o;
        return Objects.equals(screenshot, that.screenshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), screenshot);
    }

    @Override
    public String toString() {
        return "UiPreconditionResult[" +
                "precondition=" + getPrecondition() + ", " +
                "success=" + isSuccess() + ", " +
                "errorMessage=" + getErrorMessage() + ", " +
                "screenshot=" + screenshot + ", " +
                "executionStartTimestamp=" + getExecutionStartTimestamp() + ", " +
                "executionEndTimestamp=" + getExecutionEndTimestamp() + ']';
    }
}
