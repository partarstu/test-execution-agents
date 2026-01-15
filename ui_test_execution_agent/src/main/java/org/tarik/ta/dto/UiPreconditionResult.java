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

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Objects;

public class UiPreconditionResult extends PreconditionResult {
    private final @Nullable @JsonIgnore BufferedImage screenshot;

    public UiPreconditionResult(@NotNull String precondition, boolean success, @Nullable String errorMessage,
            @Nullable BufferedImage screenshot,
            @Nullable Instant executionStartTimestamp, @Nullable Instant executionEndTimestamp) {
        super(precondition, success, errorMessage, executionStartTimestamp, executionEndTimestamp);
        this.screenshot = screenshot;
    }

    public @Nullable BufferedImage getScreenshot() {
        return screenshot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        UiPreconditionResult that = (UiPreconditionResult) o;
        return Objects.equals(screenshot, that.screenshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), screenshot);
    }

    @Override
    public String toString() {
        return "UiPreconditionResult[" +
                "precondition=" + getPrecondition() + ", " +
                "success=" + isSuccess() + ", " +
                "errorMessage=" + getErrorMessage() + ", " +
                "screenshot=" + screenshot + ", " +
                "executionStartTimestamp=" + getExecutionStartTimestamp() + ", " +
                "executionEndTimestamp=" + getExecutionEndTimestamp() + ']';
    }
}
