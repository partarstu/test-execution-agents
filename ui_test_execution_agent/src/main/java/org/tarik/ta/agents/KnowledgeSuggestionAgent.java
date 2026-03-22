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