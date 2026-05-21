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

import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;
import org.tarik.ta.core.dto.FinalResult;

/**
 * Represents the result of selecting the best matching UI element from a list of candidates.
 */
@Description("The result of selecting the best matching UI element from a list of candidates based on the screenshot")
public record DbUiElementSelectionResult(
        @Description("Flag which defines if the target element was actually found on the screenshot based on its description.")
        boolean targetElementIdentified,
        @Description("Indicates whether a matching element was found. Must be \"false\" if none of the " +
                "candidate elements match what is visible on the screenshot, \"true\" otherwise.")
        boolean atLeastOneCandidateMatches,
        @Description("contains the unique ID of the selected element candidate. If the value of \"atLeastOneCandidateMatches\" field " +
                "is \"false\", this field must be an empty string, \"\".")
        String selectedElementId,
        @Description("contains comments explaining the selection decision. If \"atLeastOneCandidateMatches\" is \"true\", explain " +
                "why this element was selected over others, focusing on matching candidate's info and visual " +
                "characteristics. If \"atLeastOneCandidateMatches\" is \"false\", explain why none of the candidates matched.")
        String message) implements FinalResult {
    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE_IF_LAST)
    public static DbUiElementSelectionResult endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) DbUiElementSelectionResult result) {
        return result;
    }
}