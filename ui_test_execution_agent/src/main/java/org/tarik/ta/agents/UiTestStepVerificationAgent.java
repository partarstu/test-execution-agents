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
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

/**
 * Agent responsible for verifying test step expected results for UI tests.
 * It uses tools to perform the actual verification.
 */
public interface UiTestStepVerificationAgent extends BaseUiAgent<VerificationExecutionResult> {
    RetryPolicy RETRY_POLICY = AgentConfig.getVerificationRetryPolicy();

    @Override
    default String getAgentTaskDescription() {
        return "Verifying test step actual results";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }

    @UserMessage("""
            Verify that: {{verificationDescription}}.
            
            The test case action executed before this verification: {{actionDescription}}.
            
            The test data for this action was: {{actionTestData}}.
            
            Test context data: {{sharedData}}.
            """)
    Result<String> verify(
            @V("verificationDescription") String verificationDescription,
            @V("actionDescription") String actionDescription,
            @V("actionTestData") String actionTestData,
            @V("sharedData") String sharedData);
}
