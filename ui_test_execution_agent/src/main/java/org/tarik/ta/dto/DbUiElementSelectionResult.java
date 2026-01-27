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

import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;
import org.tarik.ta.core.dto.FinalResult;

/**
 * Represents the result of selecting the best matching UI element from a list of candidates.
 */
@Description("The result of selecting the best matching UI element from a list of candidates based on the screenshot")
public record DbUiElementSelectionResult(
        @Description("Indicates whether a matching element was found. Must be \"false\" if none of the " +
                "candidate elements match what is visible on the screenshot, \"true\" otherwise.")
        boolean success,
        @Description("contains the unique ID of the selected element candidate. If the value of \"success\" field " +
                "is \"false\", this field must be an empty string, \"\".")
        String selectedElementId,
        @Description("contains comments explaining the selection decision. If \"success\" is \"true\", explain " +
                "why this element was selected over others, focusing on matching candidate's info and visual " +
                "characteristics. If \"success\" is \"false\", explain why none of the candidates matched.")
        String message) implements FinalResult {
    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE)
    public static DbUiElementSelectionResult endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) DbUiElementSelectionResult result) {
        return result;
    }
}