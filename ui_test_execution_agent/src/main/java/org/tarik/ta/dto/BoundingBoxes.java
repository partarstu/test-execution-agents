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

import org.tarik.ta.core.dto.FinalResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

@Description("the list of all identified bounding boxes")
public record BoundingBoxes(List<BoundingBox> boundingBoxes) implements FinalResult {
    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE_IF_LAST)
    public static BoundingBoxes endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) BoundingBoxes result) {
        return result;
    }
}