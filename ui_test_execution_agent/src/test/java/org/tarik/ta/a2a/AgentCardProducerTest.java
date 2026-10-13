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

import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCardProducerTest {

    @Test
    void unattendedModeAdvertisesUnattendedSkill() {
        AgentCard card = cardForMode(ExecutionMode.UNATTENDED);

        assertThat(card.name()).isEqualTo("UI Test Execution Agent (unattended)");
        assertThat(card.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.id()).isEqualTo("ui_test_execution_unattended");
            assertThat(skill.name()).isEqualTo("Unattended UI Test Execution");
            assertThat(skill.tags()).contains("unattended");
        });
    }

    @Test
    void supervisedModeAdvertisesSupervisedSkill() {
        AgentCard card = cardForMode(ExecutionMode.SUPERVISED);

        assertThat(card.name()).isEqualTo("UI Test Execution Agent (supervised)");
        assertThat(card.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.id()).isEqualTo("ui_test_execution_supervised");
            assertThat(skill.name()).isEqualTo("Supervised UI Test Execution");
            assertThat(skill.tags()).contains("supervised");
            assertThat(skill.description()).contains("not suitable for CI/CD");
        });
    }

    @Test
    void descriptionListsSubAgentModelsAndVersionComesFromConfig() {
        UiTestAgentConfig config = mock(UiTestAgentConfig.class);
        when(config.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        when(config.getTestStepActionAgentModelName()).thenReturn("test-step-model");
        when(config.getTestCaseExtractionAgentModelName()).thenReturn("extraction-model");
        when(config.getAgentVersion()).thenReturn("1.0.0");

        AgentCard card = new AgentCardProducer("https://agent.example.test", config).agentCard();

        assertThat(card.version()).isEqualTo("1.0.0");
        assertThat(card.description())
                .startsWith("Uses the following sub-agents:<br>")
                .contains("<ol>")
                .contains("<li>Test Step Action Agent — test-step-model</li>")
                .contains("<li>Test Case Extraction Agent — extraction-model</li>")
                .contains("</ol>");
    }

    @Test
    void descriptionEndsWithThePanelUrlAsPlainText_whenAnExternalPanelUrlIsConfigured() {
        UiTestAgentConfig config = mock(UiTestAgentConfig.class);
        when(config.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        when(config.getAgentVersion()).thenReturn("1.0.0");
        when(config.getPanelExternalUrl()).thenReturn(Optional.of("https://203.0.113.7:8443"));

        AgentCard card = new AgentCardProducer("https://agent.example.test", config).agentCard();

        assertThat(card.description()).endsWith("</ol>\nOperator panel: https://203.0.113.7:8443");
        assertThat(card.skills()).singleElement().satisfies(skill -> assertThat(skill.description()).contains("operator panel"));
    }

    @Test
    void descriptionHasNoPanelLine_whenNoExternalPanelUrlIsConfigured() {
        assertThat(cardForMode(ExecutionMode.UNATTENDED).description()).endsWith("</ol>").doesNotContain("Operator panel");
    }

    private AgentCard cardForMode(ExecutionMode mode) {
        UiTestAgentConfig config = mock(UiTestAgentConfig.class);
        when(config.getExecutionMode()).thenReturn(mode);
        when(config.getAgentVersion()).thenReturn("1.0.0");
        return new AgentCardProducer("https://agent.example.test", config).agentCard();
    }
}
