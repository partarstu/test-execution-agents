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

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentExtension;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.KnowledgeAgentConfig;

import java.util.List;
import java.util.Map;

import static org.a2aproject.sdk.spec.TransportProtocol.JSONRPC;

/**
 * Builds the agent card: the capability the orchestrator routes requests by, and the task capacity extension that tells it how many
 * tasks to send at the same time.
 */
public class AgentCardProducer {
    static final String TASK_CAPACITY_EXTENSION_URI = "urn:quaia:extensions:task-capacity:v1";
    static final String TASK_CAPACITY_PARAM = "capacity";
    static final String CAPABILITY_DESCRIPTION = "Provides the recorded procedures (knowledge items) that best match each precondition "
            + "and test step of a test case, and those that combine consecutive ones into one workflow.";
    private static final String AGENT_NAME = "Procedure Knowledge Agent";
    private static final String JSON_MODE = "application/json";

    private final String agentUrl;
    private final KnowledgeAgentConfig config;

    public AgentCardProducer(@NotNull String agentUrl, @NotNull KnowledgeAgentConfig config) {
        this.agentUrl = agentUrl;
        this.config = config;
    }

    public AgentCard agentCard() {
        return AgentCard.builder()
                .name(AGENT_NAME)
                .description(CAPABILITY_DESCRIPTION)
                .version(config.getAgentVersion())
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .extensions(List.of(taskCapacityExtension()))
                        .build())
                .defaultInputModes(List.of(JSON_MODE))
                .defaultOutputModes(List.of(JSON_MODE))
                .skills(List.of(procedureSelectionSkill()))
                .documentationUrl("https://github.com/partarstu/test-execution-agents")
                .supportedInterfaces(List.of(new AgentInterface(JSONRPC.asString(), agentUrl)))
                .build();
    }

    private AgentExtension taskCapacityExtension() {
        return AgentExtension.builder()
                .uri(TASK_CAPACITY_EXTENSION_URI)
                .description("The number of tasks the agent runs at the same time")
                .required(false)
                .params(Map.of(TASK_CAPACITY_PARAM, config.getTaskCapacity()))
                .build();
    }

    private static AgentSkill procedureSelectionSkill() {
        return AgentSkill.builder()
                .id("procedure_knowledge_selection")
                .name("Procedure Knowledge Selection")
                .description(CAPABILITY_DESCRIPTION)
                .tags(List.of("knowledge", "procedures", "test design"))
                .build();
    }
}
