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
package org.tarik.ta.dto;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import org.tarik.ta.core.dto.FinalResult;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;

@Description("The result of target element identification.")
public record UiElementIdentificationResult(
        @Description("Flag which defines if the target UI element was actually located on the screenshot.")
        boolean targetElementIdentified,
        @Description("Flag which defines if multiple instances of the target UI element were found on the screenshot.")
        boolean multipleElementsIdentified,
        @Description(value = "Extracted information about the located UI element. Must be null, if the target UI element was not found on " +
                "the screenshot or if multiple instances of the target UI element were found.")
        UiElementDescription elementDescription)
        implements FinalResult {

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE)
    public static UiElementIdentificationResult endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) UiElementIdentificationResult result) {
        return result;
    }
}