/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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

import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.KnowledgeAgentConfig;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentCardProducerTest {

    @Mock
    private KnowledgeAgentConfig config;

    @Test
    void agentCard_shouldDescribeTheCapabilityAndDeclareTheTaskCapacity() {
        when(config.getAgentVersion()).thenReturn("1.0.0");
        when(config.getTaskCapacity()).thenReturn(4);

        var card = new AgentCardProducer("http://knowledge-agent:8007", config).agentCard();

        assertThat(card.description()).isEqualTo(AgentCardProducer.CAPABILITY_DESCRIPTION).contains("precondition and test step");
        assertThat(card.version()).isEqualTo("1.0.0");
        assertThat(card.skills()).extracting(AgentSkill::description).containsExactly(AgentCardProducer.CAPABILITY_DESCRIPTION);
        assertThat(card.supportedInterfaces()).extracting(AgentInterface::url).containsExactly("http://knowledge-agent:8007");
        assertThat(card.capabilities().extensions()).singleElement().satisfies(extension -> {
            assertThat(extension.uri()).isEqualTo("urn:quaia:extensions:task-capacity:v1");
            assertThat(extension.required()).isFalse();
            assertThat(extension.params()).isEqualTo(Map.of("capacity", 4));
        });
    }
}
