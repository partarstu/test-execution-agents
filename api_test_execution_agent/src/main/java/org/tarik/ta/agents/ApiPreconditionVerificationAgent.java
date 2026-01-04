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
 * Agent responsible for verifying API test case preconditions.
 * <p>
 * This agent verifies that precondition setup operations completed successfully
 * by:
 * <ul>
 * <li>Checking API response status codes and bodies</li>
 * <li>Validating that expected resources were created</li>
 * <li>Confirming authentication tokens are valid</li>
 * <li>Verifying data state matches expectations</li>
 * </ul>
 */
public interface ApiPreconditionVerificationAgent extends BaseAiAgent<VerificationExecutionResult> {
    RetryPolicy RETRY_POLICY = getVerificationRetryPolicy();

    @UserMessage("""
            Verify that the following API precondition was executed successfully: {{precondition}}
            
            The precondition action that was executed: {{actionDescription}}
            
            Last API response information:
            - Status Code: {{lastResponseStatus}}
            - Response Body: {{lastResponseBody}}
            
            Test context execution data: {{sharedData}}
            
            Determine if the precondition was successful based on the API response and context state.
            """)
    Result<String> verify(
            @V("precondition") String precondition,
            @V("actionDescription") String actionDescription,
            @V("lastResponseStatus") String lastResponseStatus,
            @V("lastResponseBody") String lastResponseBody,
            @V("sharedData") String sharedData);

    @Override
    default String getAgentTaskDescription() {
        return "Verifying API precondition";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
