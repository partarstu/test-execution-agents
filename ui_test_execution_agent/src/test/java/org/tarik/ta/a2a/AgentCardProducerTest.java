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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCardProducerTest {

    @Test
    void unattendedModeAdvertisesUnattendedSkill() {
        AgentCard card = cardForMode(ExecutionMode.UNATTENDED);

        assertThat(card.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.id()).isEqualTo("ui_test_execution_unattended");
            assertThat(skill.name()).isEqualTo("Unattended UI Test Execution");
            assertThat(skill.tags()).contains("unattended");
        });
    }

    @Test
    void supervisedModeAdvertisesSupervisedSkill() {
        AgentCard card = cardForMode(ExecutionMode.SUPERVISED);

        assertThat(card.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.id()).isEqualTo("ui_test_execution_supervised");
            assertThat(skill.name()).isEqualTo("Supervised UI Test Execution");
            assertThat(skill.tags()).contains("supervised");
        });
    }

    @Test
    void descriptionListsSubAgentModels() {
        UiTestAgentConfig config = mock(UiTestAgentConfig.class);
        when(config.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        when(config.getTestStepActionAgentModelName()).thenReturn("test-step-model");

        AgentCard card = new AgentCardProducer("https://agent.example.test", config).agentCard();

        assertThat(card.description())
                .startsWith("UI Test Execution Agent using the following sub-agents:")
                .contains("Test Step Action Agent — test-step-model");
    }

    private AgentCard cardForMode(ExecutionMode mode) {
        UiTestAgentConfig config = mock(UiTestAgentConfig.class);
        when(config.getExecutionMode()).thenReturn(mode);
        return new AgentCardProducer("https://agent.example.test", config).agentCard();
    }
}
