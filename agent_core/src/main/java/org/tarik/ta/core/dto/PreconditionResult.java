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

public class PreconditionResult {
    private final @NotNull String precondition;
    private final boolean success;
    private final @Nullable String errorMessage;
    private final @Nullable Instant executionStartTimestamp;
    private final @Nullable Instant executionEndTimestamp;

    public PreconditionResult(@NotNull String precondition, boolean success, @Nullable String errorMessage, @Nullable Instant executionStartTimestamp, @Nullable Instant executionEndTimestamp) {
        this.precondition = precondition;
        this.success = success;
        this.errorMessage = errorMessage;
        this.executionStartTimestamp = executionStartTimestamp;
        this.executionEndTimestamp = executionEndTimestamp;
    }

    public @NotNull String precondition() {
        return precondition;
    }

    public boolean success() {
        return success;
    }

    public @Nullable String errorMessage() {
        return errorMessage;
    }

    public @Nullable Instant executionStartTimestamp() {
        return executionStartTimestamp;
    }

    public @Nullable Instant executionEndTimestamp() {
        return executionEndTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PreconditionResult that = (PreconditionResult) o;
        return success == that.success && Objects.equals(precondition, that.precondition) && Objects.equals(errorMessage, that.errorMessage) && Objects.equals(executionStartTimestamp, that.executionStartTimestamp) && Objects.equals(executionEndTimestamp, that.executionEndTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(precondition, success, errorMessage, executionStartTimestamp, executionEndTimestamp);
    }

    @Override
    public String toString() {
        return "PreconditionResult[" +
                "precondition=" + precondition + ", " +
                "success=" + success + ", " +
                "errorMessage=" + errorMessage + ", " +
                "executionStartTimestamp=" + executionStartTimestamp + ", " +
                "executionEndTimestamp=" + executionEndTimestamp + ']';
    }
}