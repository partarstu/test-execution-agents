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
import org.tarik.ta.core.agents.GenericAiAgent;
import org.tarik.ta.dto.UiElementLocationResult;

/**
 * AI agent that resolves (locates and, if needed, creates in DB) a UI element for an atomic collecting knowledge procedure.
 *
 * <p>This agent is a one-shot sub-agent scoped to a single element resolution operation.
 * It uses {@link UiElementLocationResult#endExecutionAndGetFinalResult} as its terminal tool,
 * which signals completion with the resolved element's UUID, name, and bounding box.
 */
public interface UiElementResolutionAgent extends GenericAiAgent<UiElementLocationResult> {
    @UserMessage("""
            Action actionDescription: {{actionDescription}}
            
            Data related to the element: {{relatedData}}
            """)
    Result<String> resolveForAction(
            @V("actionDescription") String actionDescription,
            @V("relatedData") String relatedData);

    @Override
    default String getAgentTaskDescription() {
        return "Resolves UI element for specific test action";
    }
}
