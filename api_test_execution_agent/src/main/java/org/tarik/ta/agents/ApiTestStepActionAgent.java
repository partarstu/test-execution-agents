/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

import static org.tarik.ta.core.AgentConfig.getActionRetryPolicy;

/**
 * Agent responsible for executing and verifying API test step actions.
 * <p>
 * This agent handles the execution of individual API test steps including:
 * <ul>
 * <li>Sending HTTP requests with various methods and authentication</li>
 * <li>Processing request/response data</li>
 * <li>Storing extracted values in context for later use</li>
 * <li>Handling data-driven test scenarios</li>
 * </ul>
 * <p>
 * After execution, it also verifies test step expected results by:
 * <ul>
 * <li>Validating API response status codes match expectations</li>
 * <li>Checking response body content against expected values</li>
 * <li>Verifying JSON path values and structure</li>
 * <li>Validating response schema compliance</li>
 * </ul>
 */
public interface ApiTestStepActionAgent extends GenericAiAgent<VerificationExecutionResult> {
    RetryPolicy RETRY_POLICY = getActionRetryPolicy();

    @UserMessage("""
            Test step action: {{testStep}}
            
            Expected results to verify: {{expectedResults}}
            
            Data related to the test step: {{testData}}
            
            Test context data: {{sharedData}}
            """)
    Result<String> execute(
            @V("testStep") String testStep,
            @V("expectedResults") String expectedResults,
            @V("testData") String testData,
            @V("sharedData") String sharedData);


    @Override
    default String getAgentTaskDescription() {
        return "Executing and verifying API test step actions";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
