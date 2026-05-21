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
import org.tarik.ta.core.dto.SystemInfo;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStepResult;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class UiTestExecutionResult extends TestExecutionResult {
    private final @Nullable @JsonIgnore BufferedImage screenshot;
    private final @Nullable String videoPath;

    public UiTestExecutionResult(
            @NotNull String testCaseName,
            @NotNull TestExecutionStatus testExecutionStatus,
            @NotNull List<PreconditionResult> preconditionResults,
            @NotNull List<TestStepResult> stepResults,
            @Nullable BufferedImage screenshot,
            @Nullable SystemInfo systemInfo,
            @Nullable String videoPath,
            @Nullable List<String> logs,
            @Nullable Instant executionStartTimestamp,
            @Nullable Instant executionEndTimestamp,
            @Nullable String generalErrorMessage) {
        super(testCaseName, testExecutionStatus, preconditionResults, stepResults, executionStartTimestamp,
                executionEndTimestamp, generalErrorMessage, systemInfo, logs);
        this.screenshot = screenshot;
        this.videoPath = videoPath;
    }

    @Nullable
    public BufferedImage getScreenshot() {
        return screenshot;
    }

    @Nullable
    public String getVideoPath() {
        return videoPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        UiTestExecutionResult that = (UiTestExecutionResult) o;
        return Objects.equals(screenshot, that.screenshot) &&
                Objects.equals(videoPath, that.videoPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), screenshot, videoPath);
    }
}
