/*
 * Copyright (c) 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.tarik.ta.dto;

import dev.langchain4j.model.output.structured.Description;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.core.dto.AgentExecutionResult;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Objects;

@Description("Result of a tool execution containing status, message, optional screenshot, typed payload, and timestamp")
public final class UiAgentExecutionResult<T> extends AgentExecutionResult<T> {
    @Description("Optional screenshot captured during execution (nullable)")
    private final @Nullable BufferedImage screenshot;

    public UiAgentExecutionResult(ExecutionStatus executionStatus, String message, boolean retryMakesSense, @Nullable T resultPayload,
                                 @Nullable BufferedImage screenshot,  Instant timestamp) {
        super(executionStatus, message, retryMakesSense, resultPayload, timestamp);
        this.screenshot = screenshot;
    }

    public @Nullable BufferedImage screenshot() {
        return screenshot;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UiAgentExecutionResult<?> that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        return Objects.equals(screenshot, that.screenshot);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(screenshot);
        return result;
    }
}
