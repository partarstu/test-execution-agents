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

/**
 * Final result DTO for locating an element on the screen or creating it in DB.
 */
@Description("The result of UI element location or creation during a collecting knowledge procedure resolution.")
public record UiElementLocationResult(
        @Description("Whether the element was successfully located or created.")
        boolean success,
        @Description("The UUID of the located or created UI element in the database. Empty if the operation was not successful.")
        String elementId,
        @Description("The name of the located or created UI element. Empty if the operation was not successful.")
        String elementName,
        @Description("The physical screen region of the located element. Null if the operation was not successful.")
        ScreenRegion elementScreenRegion,
        @Description("A short, user-friendly message explaining the result, especially in case of failure.") String message
)
        implements FinalResult {

    public static UiElementLocationResult empty() {
        return new UiElementLocationResult(false, null, null, null, null);
    }

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE)
    public static UiElementLocationResult endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) UiElementLocationResult result) {
        return result;
    }
}
