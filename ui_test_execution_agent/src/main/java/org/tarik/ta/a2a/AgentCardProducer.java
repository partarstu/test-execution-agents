/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.UiTestAgentConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentCardProducer {
    private static final String AGENT_NAME = "UI Test Execution Agent";

    private final String agentUrl;
    private final UiTestAgentConfig config;

    public AgentCardProducer(@NotNull String agentUrl, @NotNull UiTestAgentConfig config) {
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
                .skills(List.of(uiTestExecutionSkill()))
                .documentationUrl("https://github.com/partarstu/test-execution-agents")
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), agentUrl)))
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
        models.put("Knowledge Collection Element Resolution Agent", config.getKnowledgeCollectionElementResolutionAgentModelName());
        models.put("Knowledge Suggestion Agent", config.getKnowledgeSuggestionAgentModelName());
        models.put("UI State Check Agent", config.getUiStateCheckAgentModelName());
        models.put("Test Step Verification Agent", config.getTestStepVerificationAgentModelName());
        models.put("Precondition Verification Agent", config.getPreconditionVerificationAgentModelName());
        models.put("Test Step Action Agent", config.getTestStepActionAgentModelName());
        models.put("Precondition Action Agent", config.getPreconditionActionAgentModelName());
        models.put("Element Bounding Box Agent", config.getElementBoundingBoxAgentModelName());
        models.put("UI Element Visual Match Agent", config.getUiElementVisualMatchAgentModelName());
        models.put("UI Element Description Extraction Agent", config.getUiElementDescriptionExtractionAgentModelName());
        models.put("UI Element Description Matcher Agent", config.getUiElementDescriptionMatcherAgentModelName());
        models.put("DB Element Selection Agent", config.getDbElementCandidateSelectionAgentModelName());
        return models;
    }

    private AgentSkill uiTestExecutionSkill() {
        return switch (config.getExecutionMode()) {
            case UNATTENDED -> AgentSkill.builder()
                    .id("ui_test_execution_unattended")
                    .name("Unattended UI Test Execution")
                    .description("Can execute UI tests in a fully automated (unattended) mode, suitable for CI/CD pipelines")
                    .tags(List.of("testing", "ui", "unattended"))
                    .build();
            case SUPERVISED -> AgentSkill.builder()
                    .id("ui_test_execution_supervised")
                    .name("Supervised UI Test Execution")
                    .description("Can execute UI tests in supervised mode, allowing operator interaction during execution")
                    .tags(List.of("testing", "ui", "supervised"))
                    .build();
        };
    }
}
