/*
 * Test Execution Agent Parent - ${project.description}
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

public record NewActionResult(
    boolean success,
    String message,
    String details
) implements FinalResult {
    
    @Tool(name = "submitResult", value = FinalResult.TOOL_DESCRIPTION)
    public static NewActionResult submitResult(
            @P(FinalResult.FINAL_RESULT_PARAM_DESCRIPTION) NewActionResult result) {
        return result;
    }
}
