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
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStepResult;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class UiTestExecutionResult extends TestExecutionResult {
    private final @Nullable @JsonIgnore BufferedImage screenshot;

    public UiTestExecutionResult(String testCaseName, @NotNull TestExecutionStatus testExecutionStatus,
                                 @NotNull List<PreconditionResult> preconditionResults,
                                 @NotNull List<TestStepResult> stepResults,
                                 @Nullable BufferedImage screenshot,
                                 @Nullable Instant executionStartTimestamp,
                                 @Nullable Instant executionEndTimestamp,
                                 @Nullable String generalErrorMessage) {
        super(testCaseName, testExecutionStatus, preconditionResults, stepResults, executionStartTimestamp, executionEndTimestamp, generalErrorMessage);
        this.screenshot = screenshot;
    }

    public @Nullable BufferedImage screenshot() {
        return screenshot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        UiTestExecutionResult that = (UiTestExecutionResult) o;
        return Objects.equals(screenshot, that.screenshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), screenshot);
    }
}
