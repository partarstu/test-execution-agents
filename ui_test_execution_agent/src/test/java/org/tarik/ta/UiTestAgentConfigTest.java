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

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(config.getDialogDefaultHorizontalGap()).isGreaterThanOrEqualTo(0);
        assertThat(config.getDialogDefaultVerticalGap()).isGreaterThanOrEqualTo(0);
        assertThat(config.getDialogDefaultFontType()).isNotNull();
        assertThat(config.getDialogDefaultFontSize()).isGreaterThanOrEqualTo(0);
        assertThat(config.getNeo4jUsername()).isNotNull();
        assertThat(config.getNeo4jDatabase()).isNotNull();
        assertThat(config.getKnowledgeMaxDepth()).isGreaterThanOrEqualTo(0);
        assertThat(config.getKnowledgeEmbeddingBatchSize()).isGreaterThanOrEqualTo(0);
        assertThat(config.getKnowledgeMatchConfidenceHigh()).isGreaterThanOrEqualTo(0);
        assertThat(config.getKnowledgeMatchConfidenceLow()).isGreaterThanOrEqualTo(0);
        assertThat(config.getKnowledgeMatchTopN()).isGreaterThanOrEqualTo(0);
    }
}
