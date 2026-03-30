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

import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.dto.UiElementDescriptionResult;

/**
 * AI agent that extracts the UI element description from a test action description.
 */
public interface UiElementDescriptionExtractionAgent extends BaseUiAgent<UiElementDescriptionResult> {
    @UserMessage("Procedure description: {{testActionDescription}}")
    Result<String> extract(@V("testActionDescription") String testActionDescription);

    @Override
    default String getAgentTaskDescription() {
        return "Extracting UI element description from procedure description";
    }
}
