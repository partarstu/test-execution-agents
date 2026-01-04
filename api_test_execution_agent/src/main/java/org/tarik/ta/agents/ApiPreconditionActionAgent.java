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

import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.core.agents.BaseAiAgent;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

import static org.tarik.ta.core.AgentConfig.getActionRetryPolicy;

/**
 * Agent responsible for executing API test case preconditions.
 * <p>
 * This agent handles setup operations such as:
 * <ul>
 * <li>Creating test data via API calls</li>
 * <li>Setting up authentication tokens</li>
 * <li>Initializing session state</li>
 * <li>Creating required resources before test execution</li>
 * </ul>
 */
public interface ApiPreconditionActionAgent extends BaseAiAgent<EmptyExecutionResult> {
    RetryPolicy RETRY_POLICY = getActionRetryPolicy();

    @UserMessage("""
            Execute the following API precondition: {{precondition}}

            Shared data from previous operations: {{sharedData}}

            Use the available API tools to execute this precondition.
            Store any values needed for later steps in context variables.
            """)
    Result<String> execute(
            @V("precondition") String precondition,
            @V("sharedData") String sharedData);

    @Override
    default String getAgentTaskDescription() {
        return "Executing API precondition action";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
