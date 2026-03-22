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
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.dto.NewActionResult;

import static org.tarik.ta.core.AgentConfig.getActionRetryPolicy;

/**
 * Agent responsible for [specific task description].
 */
public interface NewActionAgent extends GenericAiAgent<NewActionResult> {
    RetryPolicy RETRY_POLICY = getActionRetryPolicy();

    @UserMessage("""
            Action to execute: {{action}}
            
            Context data: {{context}}
            """)
    Result<String> execute(
            @V("action") String action,
            @V("context") String context);

    @Override
    default String getAgentTaskDescription() {
        return "Executing new action";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
