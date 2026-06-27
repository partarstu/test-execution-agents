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

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.ApiTestAgentConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.a2aproject.sdk.spec.TransportProtocol.JSONRPC;

public class AgentCardProducer {
    private static final String AGENT_NAME = "API Test Execution Agent";

    private final String agentUrl;
    private final ApiTestAgentConfig config;

    public AgentCardProducer(@NotNull String agentUrl, @NotNull ApiTestAgentConfig config) {
        this.agentUrl = agentUrl;
        this.config = config;
    }

    public AgentCard agentCard() {
        return AgentCard.builder()
                .name(AGENT_NAME)
                .description(buildDescription())
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(apiTestExecutionSkill()))
                .documentationUrl("https://github.com/partarstu/test-execution-agents")
                .supportedInterfaces(List.of(new AgentInterface(JSONRPC.asString(), agentUrl)))
                .build();
    }

    private String buildDescription() {
        var lines = new ArrayList<String>();
        lines.add("%s using the following sub-agents:".formatted(AGENT_NAME));
        subAgentModels().forEach((subAgent, modelName) -> lines.add("%s — %s".formatted(subAgent, modelName)));
        return String.join("\n", lines);
    }

    private Map<String, String> subAgentModels() {
        var models = new LinkedHashMap<String, String>();
        models.put("Test Step Action Agent", config.getTestStepActionAgentModelName());
        models.put("Precondition Action Agent", config.getPreconditionActionAgentModelName());
        return models;
    }

    private AgentSkill apiTestExecutionSkill() {
        return AgentSkill.builder()
                .id("api_test_execution")
                .name("API Test Execution")
                .description("Can execute API tests in a fully automated mode")
                .tags(List.of("testing", "api"))
                .build();
    }
}
