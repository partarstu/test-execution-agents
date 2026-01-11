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
package org.tarik.ta.core.dto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the result of a single test step execution.
 */
public class TestStepResult {
    private final @NotNull TestStep testStep;
    private final TestStepResultStatus executionStatus;
    private final @Nullable String errorMessage;
    private final @Nullable String actualResult;
    private final @Nullable Instant executionStartTimestamp;
    private final @Nullable Instant executionEndTimestamp;

    public TestStepResult(@NotNull TestStep testStep, TestStepResultStatus executionStatus,
                          @Nullable String errorMessage, @Nullable String actualResult, @Nullable Instant executionStartTimestamp,
                          @Nullable Instant executionEndTimestamp) {
        this.testStep = testStep;
        this.executionStatus = executionStatus;
        this.errorMessage = errorMessage;
        this.actualResult = actualResult;
        this.executionStartTimestamp = executionStartTimestamp;
        this.executionEndTimestamp = executionEndTimestamp;
    }

    public @NotNull TestStep getTestStep() {
        return testStep;
    }

    public TestStepResultStatus getExecutionStatus() {
        return executionStatus;
    }

    public @Nullable String getErrorMessage() {
        return errorMessage;
    }

    public @Nullable String getActualResult() {
        return actualResult;
    }

    public @Nullable Instant getExecutionStartTimestamp() {
        return executionStartTimestamp;
    }

    public @Nullable Instant getExecutionEndTimestamp() {
        return executionEndTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TestStepResult that = (TestStepResult) o;
        return Objects.equals(testStep, that.testStep) && executionStatus == that.executionStatus
                && Objects.equals(errorMessage, that.errorMessage) && Objects.equals(actualResult, that.actualResult)
                && Objects.equals(executionStartTimestamp, that.executionStartTimestamp)
                && Objects.equals(executionEndTimestamp, that.executionEndTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testStep, executionStatus, errorMessage, actualResult, executionStartTimestamp, executionEndTimestamp);
    }

    /**
     * Provides a human-friendly string representation of the TestStepResult
     * instance.
     * The output is formatted for console readability.
     *
     * @return A formatted string representing the test step result.
     */
    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TestStepResult:\n");
        sb.append("  - Step: ").append(testStep).append("\n");
        sb.append("  - Status: ").append(executionStatus).append("\n");

        if (executionStatus != TestStepResultStatus.SUCCESS && errorMessage != null && !errorMessage.trim().isEmpty()) {
            sb.append("  - Error: ").append(errorMessage).append("\n");
        }

        sb.append("  - Start Time: ")
                .append(executionStartTimestamp != null ? executionStartTimestamp.toString() : "N/A")
                .append("\n");
        sb.append("  - End Time: ").append(executionEndTimestamp != null ? executionEndTimestamp.toString() : "N/A");

        return sb.toString();
    }

    public enum TestStepResultStatus {
        SUCCESS, FAILURE, ERROR
    }
}