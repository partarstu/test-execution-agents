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
        SUCCESS, ERROR, INTERRUPTED_BY_USER
    }
}
