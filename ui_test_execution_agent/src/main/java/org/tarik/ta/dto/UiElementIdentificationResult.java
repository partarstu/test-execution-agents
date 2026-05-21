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
import dev.langchain4j.model.output.structured.Description;
import org.tarik.ta.core.dto.FinalResult;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;

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

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE_IF_LAST)
    public static UiElementIdentificationResult endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) UiElementIdentificationResult result) {
        return result;
    }
}