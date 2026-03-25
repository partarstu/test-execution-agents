/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
