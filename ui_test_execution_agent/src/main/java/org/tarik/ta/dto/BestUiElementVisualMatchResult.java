/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
import org.tarik.ta.core.dto.FinalResult;

import dev.langchain4j.model.output.structured.Description;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;

@Description("the identified best match of the bounding box for a target UI element")
public record BestUiElementVisualMatchResult(
        @Description("indicates whether there is a match. Must be \"false\", if you're sure that there are" +
                " no bounding boxes which correctly mark the target UI element based on its info and visual characteristics," +
                " \"true\" otherwise.")
        boolean success,
        @Description("contains the ID of the identified bounding box. If the value of \"success\" field " +
                "is \"false\", this field must be an empty string, \"\".")
        String boundingBoxId,
        @Description("contains any comments regarding the results of identification. If the value of \"success\" field is " +
                "\"true\", this field should have your comments clarifying why a specific bounding box was identified comparing to " +
                "others. If the value of \"success\" field is \"false\", this field should have your comments " +
                "clarifying why you found no good match at all.")
        String message) implements FinalResult {

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE_IF_LAST)
    public static BestUiElementVisualMatchResult endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) BestUiElementVisualMatchResult result) {
        return result;
    }
}