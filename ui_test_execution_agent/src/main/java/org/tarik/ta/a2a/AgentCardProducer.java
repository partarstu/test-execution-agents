/*
 * ui-test-execution-agent - ${project.description}
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
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.TransportProtocol;

import java.util.List;

public class AgentCardProducer {
    private final String agentUrl;

    public AgentCardProducer(String agentUrl) {
        this.agentUrl = agentUrl;
    }

    public AgentCard agentCard() {
        return AgentCard.builder()
                .name("UI Test Execution Agent")
                .description("Can execute UI tests in a fully automated mode")
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .documentationUrl("https://github.com/partarstu/test-execution-agents")
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), agentUrl)))
                .build();
    }
}
