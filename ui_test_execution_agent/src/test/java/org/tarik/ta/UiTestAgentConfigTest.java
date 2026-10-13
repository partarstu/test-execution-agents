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
package org.tarik.ta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

class UiTestAgentConfigTest {

    @Test
    @DisplayName("Verify various config getters")
    void verifyConfigGetters() {
        var config = new UiTestAgentConfig();
        // Just call the getters to ensure they don't throw and to cover the code
        assertThat(config.getScreenshotsSaveFolder()).isNotNull();
        assertThat(config.getExecutionMode()).isNotNull();
        assertThat(config.getSupervisedCountdownSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(config.getAgentToolCallsBudget()).isGreaterThanOrEqualTo(0);
        assertThat(config.getRecordingBitrate()).isGreaterThanOrEqualTo(0);
        assertThat(config.getRecordingFormat()).isNotNull();
        assertThat(config.getRecordingFrameRate()).isGreaterThanOrEqualTo(0);
        assertThat(config.getElementBoundingBoxColorName()).isNotNull();
        assertThat(config.getElementRetrievalMinTargetScore()).isGreaterThanOrEqualTo(0);
        assertThat(config.getElementRetrievalMinGeneralScore()).isGreaterThanOrEqualTo(0);
        assertThat(config.getElementLocatorVisualSimilarityThreshold()).isGreaterThanOrEqualTo(0);
        assertThat(config.getElementLocatorTopVisualMatches()).isGreaterThanOrEqualTo(0);
        assertThat(config.getFoundMatchesDimensionDeviationRatio()).isGreaterThanOrEqualTo(0);
        assertThat(config.getElementLocatorVisualGroundingVoteCount()).isGreaterThanOrEqualTo(0);
        assertThat(config.getElementLocatorValidationVoteCount()).isGreaterThanOrEqualTo(0);
        assertThat(config.getBboxClusteringMinIntersectionRatio()).isGreaterThanOrEqualTo(0);
        assertThat(config.getBboxScreenshotLongestAllowedDimensionPixels()).isGreaterThanOrEqualTo(0);
        assertThat(config.getBboxScreenshotMaxSizeMegapixels()).isGreaterThanOrEqualTo(0);
        assertThat(config.getVerificationModelMaxRetries()).isGreaterThanOrEqualTo(0);
        assertThat(config.getNeo4jUsername()).isNotNull();
        assertThat(config.getNeo4jDatabase()).isNotNull();
        assertThat(config.getKnowledgeMaxDepth()).isGreaterThanOrEqualTo(0);
        assertThat(config.getKnowledgeEmbeddingBatchSize()).isGreaterThanOrEqualTo(0);
        assertThat(config.getKnowledgeMatchConfidenceHigh()).isGreaterThanOrEqualTo(0);
        assertThat(config.getKnowledgeMatchConfidenceLow()).isGreaterThanOrEqualTo(0);
        assertThat(config.getKnowledgeMatchTopN()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Similarity thresholds are on Neo4j's normalised scale and identical in the code and in config.properties")
    void similarityThresholds_shouldMatchTheConfiguredValues() {
        var config = new UiTestAgentConfig();

        assertThat(config.getKnowledgeMatchConfidenceLow()).isEqualTo(0.88);
        assertThat(config.getKnowledgeMatchConfidenceHigh()).isEqualTo(0.95);
        assertThat(config.getSatisfiesSimilarityThreshold()).isEqualTo(0.90);
        assertThat(config.getElementRetrievalMinTargetScore()).isEqualTo(0.93);
        assertThat(config.getElementRetrievalMinGeneralScore()).isEqualTo(0.87);
        assertThat(config.getKnowledgeMatchConfidenceLow()).isLessThan(config.getKnowledgeMatchConfidenceHigh());
    }

    @Test
    @DisplayName("The interactive element resolution budget and the failure-context countdown match config.properties")
    void interactiveDialogBudgets_shouldMatchTheConfiguredValues() {
        var config = new UiTestAgentConfig();

        assertThat(config.getElementResolutionMaxDurationMillis()).isEqualTo(300_000);
        assertThat(config.getFailureContextDialogCountdownSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("The action and verification agents use the prompts that report missing indirectly referenced data")
    void actionAndVerificationAgents_shouldUseTheCurrentPromptVersions() {
        var config = new UiTestAgentConfig();

        assertThat(config.getPreconditionAgentPromptVersion()).isEqualTo("v1.1.0");
        assertThat(config.getPreconditionVerificationAgentPromptVersion()).isEqualTo("v1.1.0");
        assertThat(config.getTestStepActionAgentPromptVersion()).isEqualTo("v1.1.0");
        assertThat(config.getTestStepVerificationAgentPromptVersion()).isEqualTo("v1.1.0");
    }

    @Test
    @DisplayName("The configured action and verification prompts explain which context entries belong to the current step")
    void configuredActionAndVerificationPrompts_shouldExplainTheStepAttributionOfTheContextData() {
        var config = new UiTestAgentConfig();

        var prompts = List.of(
                loadSystemPrompt("test_step/executor", config.getTestStepActionAgentPromptVersion(),
                        "test_step_action_agent_system_prompt.md"),
                loadSystemPrompt("precondition/executor", config.getPreconditionAgentPromptVersion(),
                        "precondition_action_agent_system_prompt.md"),
                loadSystemPrompt("test_step/verifier", config.getTestStepVerificationAgentPromptVersion(),
                        "verification_execution_prompt.md"),
                loadSystemPrompt("precondition/verifier", config.getPreconditionVerificationAgentPromptVersion(),
                        "precondition_verification_prompt.md"));

        assertThat(prompts).allSatisfy(prompt -> assertThat(prompt)
                .contains("## Test execution context data")
                .contains("tells whether that is the one being executed right now")
                .contains("Never resolve an indirect reference of the current precondition or test step")
                // LangChain4j renders a system prompt as a template, so any double curly brace would demand a template variable.
                .doesNotContain("{{"));
    }

    @Test
    @DisplayName("The configured action prompts forbid substituting tools and using coordinates without a target element")
    void configuredActionPrompts_shouldEnforceToolFit() {
        var config = new UiTestAgentConfig();

        var testStepPrompt = loadSystemPrompt("test_step/executor", config.getTestStepActionAgentPromptVersion(),
                "test_step_action_agent_system_prompt.md");
        var preconditionPrompt = loadSystemPrompt("precondition/executor", config.getPreconditionAgentPromptVersion(),
                "precondition_action_agent_system_prompt.md");

        assertThat(List.of(testStepPrompt, preconditionPrompt)).allSatisfy(prompt -> assertThat(prompt)
                .contains("NEVER substitute it with a tool which performs a different kind of interaction")
                .contains("you may NEVER call any tool which expects screen coordinates")
                .doesNotContain("{{>"));
    }

    @Test
    @DisplayName("The knowledge-graph memory settings match config.properties")
    void knowledgeGraphMemorySettings_shouldMatchTheConfiguredValues() {
        var config = new UiTestAgentConfig();

        assertThat(config.getElementAppearanceHistorySize()).isEqualTo(3);
        assertThat(config.getFailureHintsStaleDays()).isEqualTo(90);
        assertThat(config.getFailureHintsMaxCount()).isEqualTo(5);
        assertThat(config.isAnomalyRecordingEnabled()).isTrue();
        assertThat(config.getAnomalyHistorySize()).isEqualTo(20);
        assertThat(config.getAnomalyTimingOutlierFactor()).isEqualTo(3.0);
        assertThat(config.getRunHistorySize()).isEqualTo(500);
        assertThat(config.getHealthRecentWindowDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("A knowledge-graph count or day setting below 1 is rejected with its key and value")
    void requirePositive_shouldRejectTheValue_whenItIsNotPositive() {
        assertThatThrownBy(() -> UiTestAgentConfig.requirePositive(0, "anomaly.history.size"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("anomaly.history.size must be a positive integer, but is 0");
        assertThatThrownBy(() -> UiTestAgentConfig.requirePositive(-1, "failure.hints.max.count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failure.hints.max.count must be a positive integer, but is -1");
    }

    @Test
    void requirePositive_shouldReturnTheValue_whenItIsPositive() {
        assertThat(UiTestAgentConfig.requirePositive(1, "anomaly.history.size")).isEqualTo(1);
    }

    @Test
    @DisplayName("A timing outlier factor of 1 or less is rejected, since it would flag every step")
    void requireOutlierFactor_shouldRejectTheFactor_whenItIsNotGreaterThanOne() {
        assertThatThrownBy(() -> UiTestAgentConfig.requireOutlierFactor(1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("anomaly.timing.outlier.factor must be greater than 1, but is 1.0");
        assertThatThrownBy(() -> UiTestAgentConfig.requireOutlierFactor(0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("anomaly.timing.outlier.factor must be greater than 1, but is 0.5");
    }

    @Test
    void requireOutlierFactor_shouldReturnTheFactor_whenItIsGreaterThanOne() {
        assertThat(UiTestAgentConfig.requireOutlierFactor(1.5)).isEqualTo(1.5);
    }

    @Test
    @DisplayName("The mouse scroll notches per attempt match config.properties")
    void mouseScrollNotchesPerAttempt_shouldMatchTheConfiguredValue() {
        assertThat(new UiTestAgentConfig().getMouseScrollNotchesPerAttempt()).isEqualTo(3);
    }

    @Test
    @DisplayName("The operator panel settings match config.properties, and the secrets, the TLS pair and the VNC socket default to absent")
    void operatorPanelSettings_shouldMatchTheConfiguredValues() {
        var config = new UiTestAgentConfig();

        assertThat(config.getPanelPort()).isEqualTo(8443);
        assertThat(config.getPanelHost()).isEqualTo("127.0.0.1");
        assertThat(config.getPanelSessionIdleTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(config.getPanelSessionAbsoluteTimeout()).isEqualTo(Duration.ofHours(8));
        assertThat(config.getPanelPromptMaxWait()).isEqualTo(Duration.ofMinutes(120));
        assertThat(config.isPanelLiveControlEnabled()).isFalse();
        assertThat(config.getPanelPassword()).isEmpty();
        assertThat(config.getPanelTlsCert()).isEmpty();
        assertThat(config.getPanelTlsKey()).isEmpty();
        assertThat(config.isPanelTlsConfigured()).isFalse();
        assertThat(config.getPanelExternalUrl()).isEmpty();
        assertThat(config.getVncSocketPath()).isEmpty();
    }
}
