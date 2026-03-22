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
package org.tarik.ta;

import io.a2a.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.a2a.AgentExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class ServerTest {

    @Test
    void testServerMethods() {
        Server server = new Server();
        
        AgentExecutor executor = server.createAgentExecutor();
        assertThat(executor).isInstanceOf(org.tarik.ta.a2a.ApiAgentExecutor.class);
        
        AgentCard card = server.createAgentCard();
        assertThat(card.name()).isEqualTo("API Test Execution Agent");
        
        String logMessage = server.getStartupLogMessage("localhost", 8080);
        assertThat(logMessage).contains("localhost").contains("8080");
    }
}
