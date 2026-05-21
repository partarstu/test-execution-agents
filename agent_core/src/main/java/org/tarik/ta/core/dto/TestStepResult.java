/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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