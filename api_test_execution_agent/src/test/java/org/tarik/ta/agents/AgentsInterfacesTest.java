/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
