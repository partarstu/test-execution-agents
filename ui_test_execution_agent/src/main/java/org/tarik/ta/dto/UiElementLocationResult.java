/*
 * ui-test-execution-agent - ${project.description}
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
package org.tarik.ta.dto;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import org.tarik.ta.core.dto.FinalResult;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;

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

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE_IF_LAST)
    public static UiElementLocationResult endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) UiElementLocationResult result) {
        return result;
    }
}
