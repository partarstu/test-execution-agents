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
import dev.langchain4j.service.V;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

/**
 * Agent responsible for executing test case preconditions.
 */
public interface UiPreconditionActionAgent extends BaseUiAgent<EmptyExecutionResult> {
    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    Result<String> execute(
            @UserMessage("""
                    The precondition you need to execute: {{precondition}}.
                    
                    Test context execution data: {{sharedData}}.
                    
                    The screenshot follows.
                    """)
            @V("precondition") String precondition,
            @V("sharedData") String sharedData,
            @UserMessage ImageContent screenshot);

    @Override
    default String getAgentTaskDescription() {
        return "Executing precondition action related to UI";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
