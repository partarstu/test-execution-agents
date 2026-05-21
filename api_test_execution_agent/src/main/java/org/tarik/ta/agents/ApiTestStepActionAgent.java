/*
 * api-test-execution-agent - ${project.description}
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

import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.core.agents.GenericAiAgent;
import org.tarik.ta.core.dto.VerificationExecutionResult;

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
}
