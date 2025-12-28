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

    public UiTestStepResult(@NotNull TestStep testStep, TestStepResultStatus executionStatus,
            @Nullable String errorMessage, @Nullable String actualResult, @Nullable BufferedImage screenshot,
            @Nullable Instant executionStartTimestamp, @Nullable Instant executionEndTimestamp) {
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
