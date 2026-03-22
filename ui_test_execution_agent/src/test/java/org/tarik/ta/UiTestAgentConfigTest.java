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
        // Just call the getters to ensure they don't throw and to cover the code
        assertThat(UiTestAgentConfig.getScreenshotsSaveFolder()).isNotNull();
        assertThat(UiTestAgentConfig.getExecutionMode()).isNotNull();
        assertThat(UiTestAgentConfig.getSupervisedCountdownSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getAgentToolCallsBudget()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getRecordingBitrate()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getRecordingFormat()).isNotNull();
        assertThat(UiTestAgentConfig.getRecordingFrameRate()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getElementBoundingBoxColorName()).isNotNull();
        assertThat(UiTestAgentConfig.getElementRetrievalMinTargetScore()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getElementRetrievalMinGeneralScore()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getElementLocatorVisualSimilarityThreshold()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getElementLocatorTopVisualMatches()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getFoundMatchesDimensionDeviationRatio()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getElementLocatorVisualGroundingVoteCount()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getElementLocatorValidationVoteCount()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getBboxClusteringMinIntersectionRatio()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getBboxScreenshotLongestAllowedDimensionPixels()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getBboxScreenshotMaxSizeMegapixels()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getVerificationModelMaxRetries()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getDialogDefaultHorizontalGap()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getDialogDefaultVerticalGap()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getDialogDefaultFontType()).isNotNull();
        assertThat(UiTestAgentConfig.getDialogDefaultFontSize()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getNeo4jUsername()).isNotNull();
        assertThat(UiTestAgentConfig.getNeo4jDatabase()).isNotNull();
        assertThat(UiTestAgentConfig.getKnowledgeMaxDepth()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getKnowledgeEmbeddingBatchSize()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getKnowledgeMatchConfidenceHigh()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getKnowledgeMatchConfidenceLow()).isGreaterThanOrEqualTo(0);
        assertThat(UiTestAgentConfig.getKnowledgeMatchTopN()).isGreaterThanOrEqualTo(0);
    }
}
