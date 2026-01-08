/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.dto.DbUiElementSelectionResult;

/**
 * Agent responsible for selecting the best matching UI element from a list of candidates
 * retrieved from the database, based on the current screenshot of the screen.
 */
public interface DbUiElementSelectionAgent extends BaseUiAgent<DbUiElementSelectionResult> {
    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    Result<String> selectBestElementFromCandidates(@UserMessage ImageContent screenshot,
                                                   @UserMessage String candidatesInfo);

    @Override
    default String getAgentTaskDescription() {
        return "Selects the best matching UI element from found in DB candidates based on the screenshot";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
