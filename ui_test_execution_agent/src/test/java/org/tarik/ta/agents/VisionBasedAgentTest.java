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
package org.tarik.ta.agents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.model.ImageContentCreator;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.LOW;
import static dev.langchain4j.data.message.ImageContent.DetailLevel.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.AgentConfig.ModelProvider.GOOGLE;
import static org.tarik.ta.core.AgentConfig.ModelProvider.QWEN;

/**
 * Every agent which is sent a screenshot has to answer with its own image settings. Two agents are enough to prove the
 * contract picks up the settings of the agent it was asked, rather than a fixed set.
 */
class VisionBasedAgentTest {
    private static final int BUDGET = 8294400;

    @Test
    @DisplayName("An agent reports the image settings configured for itself")
    void getImageContentCreator_shouldReportTheAgentsOwnSettings() {
        var config = mock(UiTestAgentConfig.class);
        when(config.getUiStateCheckAgentImageDetailLevel()).thenReturn(LOW);
        when(config.getUiStateCheckAgentModelProvider()).thenReturn(QWEN);
        when(config.getMaxModelImagePixels()).thenReturn(BUDGET);

        var creator = agent(UiStateCheckAgent.class).getImageContentCreator(config);

        assertThat(creator).isEqualTo(new ImageContentCreator(LOW, QWEN, BUDGET));
    }

    @Test
    @DisplayName("A different agent reports different settings from the same configuration")
    void getImageContentCreator_shouldReportEachAgentsSettingsSeparately() {
        var config = mock(UiTestAgentConfig.class);
        when(config.getUiStateCheckAgentImageDetailLevel()).thenReturn(LOW);
        when(config.getUiStateCheckAgentModelProvider()).thenReturn(QWEN);
        when(config.getTestStepVerificationAgentImageDetailLevel()).thenReturn(MEDIUM);
        when(config.getTestStepVerificationAgentModelProvider()).thenReturn(GOOGLE);
        when(config.getMaxModelImagePixels()).thenReturn(BUDGET);

        var uiStateCheck = agent(UiStateCheckAgent.class).getImageContentCreator(config);
        var verification = agent(UiTestStepVerificationAgent.class).getImageContentCreator(config);

        assertThat(uiStateCheck).isEqualTo(new ImageContentCreator(LOW, QWEN, BUDGET));
        assertThat(verification).isEqualTo(new ImageContentCreator(MEDIUM, GOOGLE, BUDGET));
    }

    /** In production these are langchain4j proxies, which run the interface's default methods as written. */
    private static <T extends VisionBasedAgent> T agent(Class<T> agentType) {
        return mock(agentType, CALLS_REAL_METHODS);
    }
}
