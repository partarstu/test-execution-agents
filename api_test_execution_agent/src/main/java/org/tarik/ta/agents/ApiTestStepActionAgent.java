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
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.agents.BaseAiAgent;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

/**
 * Agent responsible for executing API test step actions.
 * <p>
 * This agent handles the execution of individual API test steps including:
 * <ul>
 * <li>Sending HTTP requests with various methods and authentication</li>
 * <li>Processing request/response data</li>
 * <li>Storing extracted values in context for later use</li>
 * <li>Handling data-driven test scenarios</li>
 * </ul>
 */
public interface ApiTestStepActionAgent extends BaseAiAgent<EmptyExecutionResult> {

    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    @Override
    default String getAgentTaskDescription() {
        return "Executing API test step action";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }

    @UserMessage("""
            Execute the following API test step: {{testStep}}

            Data related to the test step: {{testData}}

            Shared data: {{sharedData}}
            """)
    Result<String> execute(
            @V("testStep") String testStep,
            @V("testData") String testData,
            @V("sharedData") String sharedData);
}
