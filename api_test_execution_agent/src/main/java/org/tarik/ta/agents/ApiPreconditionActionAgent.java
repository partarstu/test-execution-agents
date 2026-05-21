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
 * Agent responsible for executing and verifying API test case preconditions.
 * <p>
 * This agent handles setup operations such as:
 * <ul>
 * <li>Creating test data via API calls</li>
 * <li>Setting up authentication tokens</li>
 * <li>Initializing session state</li>
 * <li>Creating required resources before test execution</li>
 * </ul>
 * <p>
 * After execution, it also verifies that the precondition was successfully met by:
 * <ul>
 * <li>Checking API response status codes and bodies</li>
 * <li>Validating that expected resources were created</li>
 * <li>Confirming authentication tokens are valid</li>
 * <li>Verifying data state matches expectations</li>
 * </ul>
 */
public interface ApiPreconditionActionAgent extends GenericAiAgent<VerificationExecutionResult> {
    @UserMessage("""
            Precondition: {{precondition}}
            
            Test context data from previous operations: {{sharedData}}.
            """)
    Result<String> execute(
            @V("precondition") String precondition,
            @V("sharedData") String sharedData);

    @Override
    default String getAgentTaskDescription() {
        return "Executing and verifying API test preconditions";
    }
}
