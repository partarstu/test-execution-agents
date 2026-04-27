/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jetbrains.annotations.Nullable;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;

@Description("Result of executing any action which doesn't expect the return value")
public record EmptyExecutionResult(
        @Description("Indicates whether the execution was successful (true) or failed (false).")
        boolean executionSuccess,
        @Description("A message describing the outcome. Required only when execution failed — must explain the root cause.")
        @Nullable String message)
        implements FinalResult {
    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE)
    public static EmptyExecutionResult endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) EmptyExecutionResult result) {
        return result;
    }
}