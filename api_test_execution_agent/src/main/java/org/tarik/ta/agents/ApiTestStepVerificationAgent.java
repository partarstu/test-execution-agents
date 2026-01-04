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
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

import static org.tarik.ta.core.AgentConfig.getVerificationRetryPolicy;

/**
 * Agent responsible for verifying API test step expected results.
 * <p>
 * This agent verifies test step outcomes by:
 * <ul>
 * <li>Validating API response status codes match expectations</li>
 * <li>Checking response body content against expected values</li>
 * <li>Verifying JSON path values and structure</li>
 * <li>Validating response schema compliance</li>
 * <li>Checking extracted variable values</li>
 * </ul>
 */
public interface ApiTestStepVerificationAgent extends BaseAiAgent<VerificationExecutionResult> {
    RetryPolicy RETRY_POLICY = getVerificationRetryPolicy();

    @UserMessage("""
            Verify the following expected result: {{verificationDescription}}

            The test step action that was executed: {{actionDescription}}
            Test data used for the action: {{actionTestData}}

            Last API response information:
            - Status Code: {{lastResponseStatus}}
            - Response Body: {{lastResponseBody}}
            - Response Headers: {{lastResponseHeaders}}

            Test context execution data: {{sharedData}}

            Use the assertion tools if needed to verify the expected results.
            Determine if the verification passed or failed based on the response and context.
            """)
    Result<String> verify(
            @V("verificationDescription") String verificationDescription,
            @V("actionDescription") String actionDescription,
            @V("actionTestData") String actionTestData,
            @V("lastResponseStatus") String lastResponseStatus,
            @V("lastResponseBody") String lastResponseBody,
            @V("lastResponseHeaders") String lastResponseHeaders,
            @V("sharedData") String sharedData);

    @Override
    default String getAgentTaskDescription() {
        return "Verifying API test step actual results";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
