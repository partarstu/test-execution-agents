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
package org.tarik.ta.agents;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.core.agents.GenericAiAgent;
import org.tarik.ta.dto.KnowledgeSuggestionResult;

/**
 * AI agent that suggests preconditions, effects, and child steps for new procedures whose knowledge is being collected.
 */
public interface KnowledgeSuggestionAgent extends GenericAiAgent<KnowledgeSuggestionResult> {
    @UserMessage("""
            Test case scenario: {{description}}

            Context:
            {{context}}

            Test Data: {{testData}}

            Expected Results: {{expectedResults}}
            """)
    Result<String> suggest(
            @V("description") String description,
            @V("context") String context,
            @V("testData") String testData,
            @V("expectedResults") String expectedResults,
            @UserMessage ImageContent screenshot);

    @Override
    default String getAgentTaskDescription() {
        return "Suggesting information about the procedure (decomposition, effects/preconditions, test data etc.)";
    }
}