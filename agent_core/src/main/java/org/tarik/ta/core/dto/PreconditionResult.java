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

    public PreconditionResult(@NotNull String precondition, boolean success, @Nullable String errorMessage,
            @Nullable Instant executionStartTimestamp, @Nullable Instant executionEndTimestamp) {
        this.precondition = precondition;
        this.success = success;
        this.errorMessage = errorMessage;
        this.executionStartTimestamp = executionStartTimestamp;
        this.executionEndTimestamp = executionEndTimestamp;
    }

    public @NotNull String getPrecondition() {
        return precondition;
    }

    public boolean isSuccess() {
        return success;
    }

    public @Nullable String getErrorMessage() {
        return errorMessage;
    }

    public @Nullable Instant getExecutionStartTimestamp() {
        return executionStartTimestamp;
    }

    public @Nullable Instant getExecutionEndTimestamp() {
        return executionEndTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PreconditionResult that = (PreconditionResult) o;
        return success == that.success && Objects.equals(precondition, that.precondition)
                && Objects.equals(errorMessage, that.errorMessage)
                && Objects.equals(executionStartTimestamp, that.executionStartTimestamp)
                && Objects.equals(executionEndTimestamp, that.executionEndTimestamp);
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