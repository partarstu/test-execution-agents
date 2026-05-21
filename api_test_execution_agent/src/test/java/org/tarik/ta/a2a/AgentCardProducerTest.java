/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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
package org.tarik.ta.a2a;

import io.a2a.spec.AgentCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCardProducerTest {

    @Test
    void testAgentCard() {
        String agentUrl = "https://agent.example.test";
        AgentCard card = new AgentCardProducer().agentCard(agentUrl);

        assertThat(card.name()).isEqualTo("API Test Execution Agent");
        assertThat(card.url()).isEqualTo(agentUrl);
        assertThat(card.preferredTransport()).isEqualTo("JSONRPC");
    }
}
