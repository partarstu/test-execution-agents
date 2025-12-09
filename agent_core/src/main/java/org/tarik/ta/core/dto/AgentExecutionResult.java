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

import java.time.Instant;
import java.util.Objects;

import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.SUCCESS;

@Description("Result of a tool execution containing status, message, optional screenshot, typed payload, and timestamp")
public class AgentExecutionResult<T> {
    @Description("Execution status indicating success, error, or user interruption")
    protected final ExecutionStatus executionStatus;
    @Description("Human-readable message describing the execution result")
    protected final String message;
    @Description("Indicates whether retrying this operation makes sense")
    protected final boolean retryMakesSense;
    @Description("Strongly-typed payload containing the specific result data (nullable)")
    protected final @Nullable T resultPayload;
    @Description("Timestamp when the tool execution completed")
    protected final Instant timestamp;

    public AgentExecutionResult(ExecutionStatus executionStatus, String message, boolean retryMakesSense, @Nullable T resultPayload,
                                Instant timestamp) {
        this.executionStatus = executionStatus;
        this.message = message;
        this.retryMakesSense = retryMakesSense;
        this.resultPayload = resultPayload;
        this.timestamp = timestamp;
    }

    public AgentExecutionResult(ExecutionStatus executionStatus, String message, boolean retryMakesSense, Instant timestamp) {
        this(executionStatus, message, retryMakesSense, null, timestamp);
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

    public boolean isRetryMakingSense() {
        return retryMakesSense;
    }

    public @Nullable T getResultPayload() {
        return resultPayload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (AgentExecutionResult<?>) obj;
        return Objects.equals(this.executionStatus, that.executionStatus) &&
                Objects.equals(this.message, that.message) &&
                this.retryMakesSense == that.retryMakesSense &&
                Objects.equals(this.resultPayload, that.resultPayload) &&
                Objects.equals(this.timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionStatus, message, retryMakesSense, resultPayload, timestamp);
    }

    @Override
    public String toString() {
        return "AgentExecutionResult[" +
                "executionStatus=" + executionStatus + ", " +
                "message=" + message + ", " +
                "retryMakesSense=" + retryMakesSense + ", " +
                "resultPayload=" + resultPayload + ", " +
                "timestamp=" + timestamp + ']';
    }

    public enum ExecutionStatus {
        SUCCESS, ERROR, VERIFICATION_FAILURE, INTERRUPTED_BY_USER
    }
}
