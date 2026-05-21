/*
 * agent-core - ${project.description}
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents the result of the test execution.
 */
public class TestExecutionResult {
    private final String testCaseName;
    private final @NotNull TestExecutionStatus testExecutionStatus;
    private final @NotNull List<PreconditionResult> preconditionResults;
    private final @NotNull List<TestStepResult> stepResults;
    private final @Nullable Instant executionStartTimestamp;
    private final @Nullable Instant executionEndTimestamp;
    private final @Nullable String generalErrorMessage;
    private final @Nullable SystemInfo systemInfo;
    @JsonIgnore
    private final @Nullable List<String> logs;

    public TestExecutionResult(
            @NotNull String testCaseName,
            @NotNull TestExecutionStatus testExecutionStatus,
            @NotNull List<PreconditionResult> preconditionResults,
            @NotNull List<TestStepResult> stepResults,
            @Nullable Instant executionStartTimestamp,
            @Nullable Instant executionEndTimestamp,
            @Nullable String generalErrorMessage,
            @Nullable SystemInfo systemInfo,
            @Nullable List<String> logs) {
        this.testCaseName = testCaseName;
        this.testExecutionStatus = testExecutionStatus;
        this.preconditionResults = preconditionResults;
        this.stepResults = stepResults;
        this.executionStartTimestamp = executionStartTimestamp;
        this.executionEndTimestamp = executionEndTimestamp;
        this.generalErrorMessage = generalErrorMessage;
        this.systemInfo = systemInfo;
        this.logs = logs;
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    public @NotNull TestExecutionStatus getTestExecutionStatus() {
        return testExecutionStatus;
    }

    public @NotNull List<PreconditionResult> getPreconditionResults() {
        return preconditionResults;
    }

    public @NotNull List<TestStepResult> getStepResults() {
        return stepResults;
    }

    public @Nullable Instant getExecutionStartTimestamp() {
        return executionStartTimestamp;
    }

    public @Nullable Instant getExecutionEndTimestamp() {
        return executionEndTimestamp;
    }

    public @Nullable String getGeneralErrorMessage() {
        return generalErrorMessage;
    }

    public @Nullable SystemInfo getSystemInfo() {
        return systemInfo;
    }

    public @Nullable List<String> getLogs() {
        return logs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TestExecutionResult that = (TestExecutionResult) o;
        return Objects.equals(testCaseName, that.testCaseName) && testExecutionStatus == that.testExecutionStatus
                && Objects.equals(preconditionResults, that.preconditionResults)
                && Objects.equals(stepResults, that.stepResults)
                && Objects.equals(executionStartTimestamp, that.executionStartTimestamp)
                && Objects.equals(executionEndTimestamp, that.executionEndTimestamp)
                && Objects.equals(generalErrorMessage, that.generalErrorMessage)
                && Objects.equals(systemInfo, that.systemInfo)
                && Objects.equals(logs, that.logs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testCaseName, testExecutionStatus, preconditionResults, stepResults,
                executionStartTimestamp, executionEndTimestamp, generalErrorMessage, systemInfo, logs);
    }

    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("Test Case: ").append(testCaseName).append("\n");
        sb.append("Execution Result: ").append(testExecutionStatus).append("\n");
        if (generalErrorMessage != null && !generalErrorMessage.isBlank()) {
            sb.append("Error Message: ").append(generalErrorMessage).append("\n");
        }
        sb.append("Start Time: ").append(executionStartTimestamp != null ? executionStartTimestamp.toString() : "N/A")
                .append("\n");
        sb.append("End Time: ").append(executionEndTimestamp != null ? executionEndTimestamp.toString() : "N/A")
                .append("\n");
        if (systemInfo != null) {
            sb.append("System Info: ").append(systemInfo).append("\n");
        }
        sb.append("============================================================\n");

        if (!preconditionResults.isEmpty()) {
            sb.append("Preconditions:\n");
            for (int i = 0; i < preconditionResults.size(); i++) {
                PreconditionResult result = preconditionResults.get(i);
                sb.append("\n[Precondition ").append(i + 1).append("]\n");
                sb.append("  - Description: ").append(result.getPrecondition()).append("\n");
                sb.append("  - Status: ").append(result.isSuccess() ? "SUCCESS" : "FAILURE").append("\n");
                if (!result.isSuccess() && result.getErrorMessage() != null) {
                    sb.append("  - Error: ").append(result.getErrorMessage()).append("\n");
                }
            }
            sb.append("------------------------------------------------------------\n");
        }

        sb.append("Steps:\n");

        if (stepResults.isEmpty()) {
            sb.append("  - No steps were executed.\n");
        } else {
            for (int i = 0; i < stepResults.size(); i++) {
                TestStepResult result = stepResults.get(i);
                sb.append("\n[Step ").append(i + 1).append("]\n");
                // Indent the output from the TestStepResult.toString() for better hierarchy
                String indentedStepResult = "  " + result.toString().replace("\n", "\n  ");
                sb.append(indentedStepResult).append("\n");
            }
        }

        sb.append("====================== End of Test =======================");

        return sb.toString();
    }

    public enum TestExecutionStatus {
        PASSED, FAILED, ERROR
    }
}