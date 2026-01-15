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

import dev.langchain4j.model.output.structured.Description;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;

@Description("Result of executing a single operation")
public class OperationExecutionResult<T> {
    @Description("Execution status indicating success, error, or user interruption")
    protected final ExecutionStatus executionStatus;
    @Description("Human-readable message describing the execution result")
    protected final String message;
    @Description("Strongly-typed payload containing the specific result data (nullable)")
    protected final @Nullable T resultPayload;

    public OperationExecutionResult(ExecutionStatus executionStatus, String message, @Nullable T resultPayload) {
        this.executionStatus = executionStatus;
        this.message = message;
        this.resultPayload = resultPayload;
    }

    public OperationExecutionResult(ExecutionStatus executionStatus, String message) {
        this(executionStatus, message, null);
    }

    /**
     * Returns true if the execution was successful.
     */
    public boolean isSuccess() {
        return executionStatus == SUCCESS;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public String getMessage() {
        return message;
    }

    public @Nullable T getResultPayload() {
        return resultPayload;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (OperationExecutionResult<?>) obj;
        return Objects.equals(this.executionStatus, that.executionStatus) &&
                Objects.equals(this.message, that.message) &&
                Objects.equals(this.resultPayload, that.resultPayload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionStatus, message, resultPayload);
    }

    @Override
    public String toString() {
        return "OperationExecutionResult[" +
                "executionStatus=" + executionStatus + ", " +
                "message=" + message + ", " +
                "resultPayload=" + resultPayload + ", ";
    }

    public enum ExecutionStatus {
        SUCCESS, ERROR, VERIFICATION_FAILURE, INTERRUPTED_BY_USER
    }
}
