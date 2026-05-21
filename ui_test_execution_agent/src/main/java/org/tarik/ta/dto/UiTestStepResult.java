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
package org.tarik.ta.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Objects;

public class UiTestStepResult extends TestStepResult {
    private final @Nullable @JsonIgnore BufferedImage screenshot;

    public UiTestStepResult(
            @NotNull TestStep testStep,
            TestStepResultStatus executionStatus,
            @Nullable String errorMessage,
            @Nullable String actualResult,
            @Nullable BufferedImage screenshot,
            @Nullable Instant executionStartTimestamp,
            @Nullable Instant executionEndTimestamp) {
        super(testStep, executionStatus, errorMessage, actualResult, executionStartTimestamp, executionEndTimestamp);
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
        UiTestStepResult that = (UiTestStepResult) o;
        return Objects.equals(screenshot, that.screenshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), screenshot);
    }

    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TestStepResult:\n");
        sb.append("  - Step: ").append(getTestStep()).append("\n");
        sb.append("  - Status: ").append(getExecutionStatus()).append("\n");

        if (getExecutionStatus() != TestStepResultStatus.SUCCESS && getErrorMessage() != null
                && !getErrorMessage().trim().isEmpty()) {
            sb.append("  - Error: ").append(getErrorMessage()).append("\n");
        }

        boolean screenshotExists = screenshot != null;
        sb.append("  - Screenshot: ").append(screenshotExists ? "Available" : "Not Available").append("\n");
        sb.append("  - Start Time: ")
                .append(getExecutionStartTimestamp() != null ? getExecutionStartTimestamp().toString() : "N/A")
                .append("\n");
        sb.append("  - End Time: ")
                .append(getExecutionEndTimestamp() != null ? getExecutionEndTimestamp().toString() : "N/A");

        return sb.toString();
    }
}
