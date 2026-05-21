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
package org.tarik.ta.agents;


import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.dto.BestUiElementVisualMatchResult;
import org.tarik.ta.core.error.RetryPolicy;

public interface BestUiElementMatchSelectionAgent extends BaseUiAgent<BestUiElementVisualMatchResult> {
    Result<String> selectBestElement(
            @UserMessage String prompt,
            @UserMessage ImageContent screenshot,
            @V("bounding_box_color") String boundingBoxColor);

    @Override
    default String getAgentTaskDescription() {
        return "Selecting the best matching to the description UI element based on the screenshot and found bounding boxes";
    }
}


