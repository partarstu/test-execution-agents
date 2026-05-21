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
import org.tarik.ta.dto.UiStateCheckResult;

public interface UiStateCheckAgent extends BaseUiAgent<UiStateCheckResult> {
    @UserMessage("""
            The expected state of the screen: {{expectedStateDescription}}
            
            The action performed was: {{actionDescription}}
            
            Any related to the expected state data: {{relevantData}}
            
            Screenshot attached.
            """)
    Result<String> check(
            @V("expectedStateDescription") String expectedStateDescription,
            @V("actionDescription") String actionDescription,
            @V("relevantData") String relevantData,
            @UserMessage ImageContent screenshot);

    @Override
    default String getAgentTaskDescription() {
        return "Checking UI state";
    }
}