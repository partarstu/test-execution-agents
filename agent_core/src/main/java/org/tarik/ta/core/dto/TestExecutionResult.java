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

    public TestExecutionResult(String testCaseName, @NotNull TestExecutionStatus testExecutionStatus, @NotNull List<PreconditionResult> preconditionResults, @NotNull List<TestStepResult> stepResults, @Nullable Instant executionStartTimestamp, @Nullable Instant executionEndTimestamp, @Nullable String generalErrorMessage) {
        this.testCaseName = testCaseName;
        this.testExecutionStatus = testExecutionStatus;
        this.preconditionResults = preconditionResults;
        this.stepResults = stepResults;
        this.executionStartTimestamp = executionStartTimestamp;
        this.executionEndTimestamp = executionEndTimestamp;
        this.generalErrorMessage = generalErrorMessage;
    }

    public String testCaseName() {
        return testCaseName;
    }

    public @NotNull TestExecutionStatus testExecutionStatus() {
        return testExecutionStatus;
    }

    public @NotNull List<PreconditionResult> preconditionResults() {
        return preconditionResults;
    }

    public @NotNull List<TestStepResult> stepResults() {
        return stepResults;
    }

    public @Nullable Instant executionStartTimestamp() {
        return executionStartTimestamp;
    }

    public @Nullable Instant executionEndTimestamp() {
        return executionEndTimestamp;
    }

    public @Nullable String generalErrorMessage() {
        return generalErrorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestExecutionResult that = (TestExecutionResult) o;
        return Objects.equals(testCaseName, that.testCaseName) && testExecutionStatus == that.testExecutionStatus && Objects.equals(preconditionResults, that.preconditionResults) && Objects.equals(stepResults, that.stepResults) && Objects.equals(executionStartTimestamp, that.executionStartTimestamp) && Objects.equals(executionEndTimestamp, that.executionEndTimestamp) && Objects.equals(generalErrorMessage, that.generalErrorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testCaseName, testExecutionStatus, preconditionResults, stepResults, executionStartTimestamp, executionEndTimestamp, generalErrorMessage);
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
        sb.append("Start Time: ").append(executionStartTimestamp != null ? executionStartTimestamp.toString() : "N/A").append("\n");
        sb.append("End Time: ").append(executionEndTimestamp != null ? executionEndTimestamp.toString() : "N/A").append("\n");
        sb.append("============================================================\n");

        if (!preconditionResults.isEmpty()) {
            sb.append("Preconditions:\n");
            for (int i = 0; i < preconditionResults.size(); i++) {
                PreconditionResult result = preconditionResults.get(i);
                sb.append("\n[Precondition ").append(i + 1).append("]\n");
                sb.append("  - Description: ").append(result.precondition()).append("\n");
                sb.append("  - Status: ").append(result.success() ? "SUCCESS" : "FAILURE").append("\n");
                if (!result.success() && result.errorMessage() != null) {
                    sb.append("  - Error: ").append(result.errorMessage()).append("\n");
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
                String indentedStepResult = "  " + result.toString().replaceAll("\n", "\n  ");
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