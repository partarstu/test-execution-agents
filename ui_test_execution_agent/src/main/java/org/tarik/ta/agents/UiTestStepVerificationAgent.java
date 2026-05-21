/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tarik.ta.agents;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

/**
 * Agent responsible for verifying test step expected results for UI tests.
 * It uses tools to perform the actual verification.
 */
public interface UiTestStepVerificationAgent extends BaseUiAgent<VerificationExecutionResult> {
    @Override
    default String getAgentTaskDescription() {
        return "Verifying test step actual results";
    }

    @UserMessage("""
            Verify that: {{verificationDescription}}.
            
            The test case action executed before this verification: {{actionDescription}}.
            
            The test data for this action was: {{actionTestData}}.
            
            Test context data: {{sharedData}}.
            
            Screenshot attached.
            """)
    Result<String> verify(
            @V("verificationDescription") String verificationDescription,
            @V("actionDescription") String actionDescription,
            @V("actionTestData") String actionTestData,
            @V("sharedData") String sharedData,
            @UserMessage ImageContent imageContent);
}
