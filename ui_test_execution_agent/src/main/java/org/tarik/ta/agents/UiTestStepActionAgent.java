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
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

/**
 * Agent responsible for executing UI actions (test step actions or precondition actions).
 */
public interface UiTestStepActionAgent extends BaseUiAgent<EmptyExecutionResult> {
    @Override
    default String getAgentTaskDescription() {
        return "Executing action";
    }

    Result<String> execute(
            @UserMessage String userMessage,
            @UserMessage ImageContent screenshot);
}
