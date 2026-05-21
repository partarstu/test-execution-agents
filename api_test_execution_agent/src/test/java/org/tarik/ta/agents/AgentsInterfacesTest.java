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
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.error.RetryPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class AgentsInterfacesTest {

    @Test
    void testApiPreconditionActionAgentDefaults() {
        ApiPreconditionActionAgent agent = new ApiPreconditionActionAgent() {
            @Override
            public Result<String> execute(String precondition, String sharedData) {
                return null;
            }
        };
        
        assertThat(agent.getAgentTaskDescription()).isEqualTo("Executing and verifying API test preconditions");
    }

    @Test
    void testApiTestStepActionAgentDefaults() {
        ApiTestStepActionAgent agent = new ApiTestStepActionAgent() {
            @Override
            public Result<String> execute(String testStep, String expectedResults, String testData, String sharedData) {
                return null;
            }
        };
        
        assertThat(agent.getAgentTaskDescription()).isEqualTo("Executing and verifying API test step actions");
    }
}
