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
package org.tarik.ta.core.agents;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.dto.AgentExecutionResult;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus;

import static java.time.Instant.now;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.SUCCESS;

public interface BaseAiAgent<T extends FinalResult> extends GenericAiAgent<T, AgentExecutionResult<T>> {
    Logger LOG = LoggerFactory.getLogger(BaseAiAgent.class);

    @Override
    default AgentExecutionResult<T> createSuccessResult(T result) {
        return new AgentExecutionResult<>(SUCCESS, "Execution successful", true, result, now());
    }

    @Override
    default AgentExecutionResult<T> createErrorResult(ExecutionStatus status, String message, @Nullable Throwable t) {
        return new AgentExecutionResult<>(status, message, false, null, now());
    }
}
