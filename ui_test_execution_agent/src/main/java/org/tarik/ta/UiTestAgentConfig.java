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


import jakarta.inject.Singleton;
import org.tarik.ta.core.AgentConfig;

@Singleton
public class UiTestAgentConfig extends AgentConfig {

    public UiTestAgentConfig() {
        super();
        this.screenshotsSaveFolder = loadProperty("screenshots.save.folder",
                "SCREENSHOTS_SAVE_FOLDER", "screens", s -> s, false);
        this.executionMode = loadProperty(
                "execution.mode", "EXECUTION_MODE", "SUPERVISED",
                s -> parseEnum(ExecutionMode.class, s), false);
        this.supervisedCountdownSeconds = loadPropertyAsInteger(
                "supervised.countdown.seconds", "SUPERVISED_COUNTDOWN_SECONDS", "5", false);
        this.screenRecordingEnabled = loadProperty("screen.recording.active",
                "SCREEN_RECORDING_ENABLED", "false", Boolean::parseBoolean, false);
        this.screenRecordingFolder = loadProperty("screen.recording.output.dir",
                "SCREEN_RECORDING_FOLDER", "videos", s -> s, false);
        this.videoBitrate = loadPropertyAsInteger("recording.bit.rate",
                "VIDEO_BITRATE", "2000000", false);
        this.screenRecordingFormat = loadProperty("recording.file.format",
                "SCREEN_RECORDING_FORMAT", "mp4", s -> s, false);
        this.screenRecordingFrameRate = loadPropertyAsInteger("recording.fps",
                "SCREEN_RECORDING_FRAME_RATE", "10", false);
        this.elementBoundingBoxColorName = getRequiredProperty(
                "element.bounding.box.color", "BOUNDING_BOX_COLOR", false);
        this.elementRetrievalMinTargetScore = loadPropertyAsDouble(
                "element.retrieval.min.target.score", "ELEMENT_RETRIEVAL_MIN_TARGET_SCORE", "0.85", false);
        this.elementRetrievalMinGeneralScore = loadPropertyAsDouble(
                "element.retrieval.min.general.score", "ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE", "0.85", false);
        this.elementLocatorVisualSimilarityThreshold = loadPropertyAsDouble(
                "element.locator.visual.similarity.threshold", "VISUAL_SIMILARITY_THRESHOLD", "0.8", false);
        this.elementLocatorTopVisualMatches = loadPropertyAsInteger(
                "element.locator.top.visual.matches",
                "TOP_VISUAL_MATCHES_TO_FIND",
                "3", false);
        this.foundMatchesDimensionDeviationRatio = loadPropertyAsDouble(
                "element.locator.found.matches.dimension.deviation.ratio", "FOUND_MATCHES_DIMENSION_DEVIATION_RATIO", "0.3",
                false);
        this.elementLocatorVisualGroundingVoteCount = loadPropertyAsInteger(
                "element.locator.visual.grounding.model.vote.count", "VISUAL_GROUNDING_MODEL_VOTE_COUNT", "5", false);
        this.elementLocatorValidationVoteCount = loadPropertyAsInteger(
                "element.locator.validation.model.vote.count", "VALIDATION_MODEL_VOTE_COUNT", "3", false);
        this.bboxClusteringMinIntersectionRatio = loadPropertyAsDouble(
                "element.locator.bbox.clustering.min.intersection.ratio", "BBOX_CLUSTERING_MIN_INTERSECTION_RATIO", "0.7",
                false);
        this.bboxScreenshotLongestAllowedDimensionPixels = loadPropertyAsInteger(
                "bbox.screenshot.longest.allowed.dimension.pixels", "BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS",
                "1568", false);
        this.bboxScreenshotMaxSizeMegapixels = loadPropertyAsDouble(
                "bbox.screenshot.max.size.megapixels", "BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS", "1.15", false);
        this.boundingBoxAlreadyNormalized = loadProperty(
                "bounding.box.already.normalized", "BOUNDING_BOX_ALREADY_NORMALIZED", "false", Boolean::parseBoolean,
                false);
        this.algorithmicSearchEnabled = loadProperty(
                "element.locator.algorithmic.search.enabled", "ALGORITHMIC_SEARCH_ENABLED", "true", Boolean::parseBoolean,
                false);
        this.verificationModelMaxRetries = loadProperty(
                "verification.model.max.retries", "VERIFICATION_MODEL_MAX_RETRIES", "3", Integer::parseInt, false);
        this.skipUiElementSelectionForVision = loadProperty(
                "element.locator.skip.model.selection.vision.only", "SKIP_UI_ELEMENT_SELECTION_FOR_VISION", "false",
                Boolean::parseBoolean, false);
        this.dialogDefaultHorizontalGap = loadPropertyAsInteger(
                "dialog.default.horizontal.gap", "DIALOG_DEFAULT_HORIZONTAL_GAP", "10", false);
        this.dialogDefaultVerticalGap = loadPropertyAsInteger(
                "dialog.default.vertical.gap", "DIALOG_DEFAULT_VERTICAL_GAP", "10", false);
        this.dialogDefaultFontType = getProperty("dialog.default.font.type",
                "DIALOG_DEFAULT_FONT_TYPE", "Dialog", s -> s, false);
        this.dialogDefaultFontSize = loadPropertyAsInteger(
                "dialog.default.font.size", "DIALOG_DEFAULT_FONT_SIZE", "13", false);
        this.dialogHoverAsClick = loadProperty("dialog.hover.as.click",
                "DIALOG_HOVER_AS_CLICK", "false", Boolean::parseBoolean, false);
        this.neo4jUsername = loadProperty("neo4j.username", "NEO4J_USERNAME",
                "neo4j", s -> s, false);
        this.neo4jDatabase = loadProperty("neo4j.database", "NEO4J_DATABASE",
                "neo4j", s -> s, false);
        this.neo4jMaxConnectionPoolSize = loadPropertyAsInteger(
                "neo4j.max.connection.pool.size", "NEO4J_MAX_CONNECTION_POOL_SIZE", "20", false);
        this.neo4jConnectionAcquisitionTimeoutSeconds = loadPropertyAsInteger(
                "neo4j.connection.acquisition.timeout.seconds", "NEO4J_CONNECTION_ACQUISITION_TIMEOUT_SECONDS", "30", false);
        this.neo4jConnectionTimeoutSeconds = loadPropertyAsInteger(
                "neo4j.connection.timeout.seconds", "NEO4J_CONNECTION_TIMEOUT_SECONDS", "10", false);
        this.neo4jMaxTransactionRetryTimeSeconds = loadPropertyAsInteger(
                "neo4j.max.transaction.retry.time.seconds", "NEO4J_MAX_TRANSACTION_RETRY_TIME_SECONDS", "120", false);
        this.knowledgeMaxDepth = loadPropertyAsInteger("knowledge.max.depth",
                "KNOWLEDGE_MAX_DEPTH", "3", false);
        this.knowledgeEmbeddingBatchSize = loadPropertyAsInteger(
                "knowledge.embedding.batch.size", "KNOWLEDGE_EMBEDDING_BATCH_SIZE", "10", false);
        this.knowledgeMatchConfidenceHigh = loadPropertyAsDouble(
                "knowledge.match.confidence.high", "KNOWLEDGE_MATCH_CONFIDENCE_HIGH", "0.85", false);
        this.knowledgeMatchConfidenceLow = loadPropertyAsDouble(
                "knowledge.match.confidence.low", "KNOWLEDGE_MATCH_CONFIDENCE_LOW", "0.5", false);
        this.knowledgeMatchTopN = loadPropertyAsInteger(
                "knowledge.match.top.n", "KNOWLEDGE_MATCH_TOP_N", "5", false);
        this.procedureLookupDelayMs = loadPropertyAsInteger(
                "procedure.lookup.delay.ms", "PROCEDURE_LOOKUP_DELAY_MS", "2000", false);
        this.uiElementDescriptionMatcherAgentModelName = loadProperty(
                "ui.element.description.matcher.agent.model.name", "UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME",
                "gemini-3-flash-preview",
                s -> s, false);
        this.uiElementDescriptionMatcherAgentModelProvider = getProperty(
                "ui.element.description.matcher.agent.model.provider", "UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.uiElementDescriptionMatcherAgentPromptVersion = loadProperty(
                "ui.element.description.matcher.agent.prompt.version", "UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION", "v1.0.0",
                s -> s, false);
        this.uiElementDescriptionExtractionAgentModelName = loadProperty(
                "ui.element.description.extraction.agent.model.name", "UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_NAME",
                "gemini-3-flash-preview", s -> s, false);
        this.uiElementDescriptionExtractionAgentModelProvider = getProperty(
                "ui.element.description.extraction.agent.model.provider", "UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_MODEL_PROVIDER",
                "google", this::getModelProvider, false);
        this.uiElementDescriptionExtractionAgentPromptVersion = loadProperty(
                "ui.element.description.extraction.agent.prompt.version", "UI_ELEMENT_DESCRIPTION_EXTRACTION_AGENT_PROMPT_VERSION",
                "v1.0.0", s -> s, false);
        this.uiStateCheckAgentModelName = loadProperty(
                "ui.state.check.agent.model.name", "UI_STATE_CHECK_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.uiStateCheckAgentModelProvider = getProperty(
                "ui.state.check.agent.model.provider", "UI_STATE_CHECK_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.uiStateCheckAgentPromptVersion = loadProperty(
                "ui.state.check.agent.prompt.version", "UI_STATE_CHECK_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);
        this.elementBoundingBoxAgentModelName = loadProperty(
                "element.bounding.box.agent.model.name", "ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME", "gemini-3-flash-preview",
                s -> s, false);
        this.elementBoundingBoxAgentModelProvider = getProperty(
                "element.bounding.box.agent.model.provider", "ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.elementBoundingBoxAgentPromptVersion = loadProperty(
                "element.bounding.box.agent.prompt.version", "ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION", "v1.0.0", s -> s,
                false);
        this.uiElementVisualMatchAgentModelName = loadProperty(
                "element.selection.agent.model.name", "ELEMENT_SELECTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.uiElementVisualMatchAgentModelProvider = getProperty(
                "element.selection.agent.model.provider", "ELEMENT_SELECTION_AGENT_MODEL_PROVIDER", "google", this::getModelProvider,
                false);
        this.elementSelectionAgentPromptVersion = loadProperty(
                "element.selection.agent.prompt.version", "ELEMENT_SELECTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);
        this.dbElementCandidateSelectionAgentPromptVersion = loadProperty(
                "db.element.selection.agent.prompt.version", "ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s,
                false);
        this.dbElementSelectionAgentModelName = loadProperty(
                "db.element.selection.agent.model.name", "DB_ELEMENT_SELECTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.dbElementSelectionAgentModelProvider = getProperty(
                "db.element.selection.agent.model.provider", "DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.preconditionVerificationAgentModelName = loadProperty(
                "precondition.verification.agent.model.name", "PRECONDITION_VERIFICATION_AGENT_MODEL_NAME",
                "gemini-3-flash-preview", s -> s, false);
        this.preconditionVerificationAgentModelProvider = getProperty(
                "precondition.verification.agent.model.provider", "precondition_VERIFICATION_AGENT_MODEL_PROVIDER",
                "google", this::getModelProvider, false);
        this.preconditionVerificationAgentPromptVersion = loadProperty(
                "precondition.verification.agent.prompt.version", "PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION",
                "v1.0.0", s -> s, false);
        this.testStepVerificationAgentModelName = loadProperty(
                "test.step.verification.agent.model.name", "TEST_STEP_VERIFICATION_AGENT_MODEL_NAME", "gemini-3-flash-preview",
                s -> s, false);
        this.testStepVerificationAgentModelProvider = getProperty(
                "test.step.verification.agent.model.provider", "TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.testStepVerificationAgentPromptVersion = loadProperty(
                "test.step.verification.agent.prompt.version", "TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION", "v1.0.0",
                s -> s, false);
        this.knowledgeSuggestionAgentModelName = loadProperty(
                "knowledge.suggestion.agent.model.name", "KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME", "gemini-3-flash-preview",
                s -> s, false);
        this.knowledgeSuggestionAgentModelProvider = getProperty(
                "knowledge.suggestion.agent.model.provider", "KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.knowledgeSuggestionAgentPromptVersion = loadProperty(
                "knowledge.suggestion.agent.prompt.version", "KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION", "v1.0.0",
                s -> s, false);
        this.knowledgeCollectionElementResolutionAgentModelName = loadProperty(
                "knowledge.collection.element.resolution.agent.model.name", "KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME",
                "gemini-3-flash-preview", s -> s, false);
        this.knowledgeCollectionElementResolutionAgentModelProvider = getProperty(
                "knowledge.collection.element.resolution.agent.model.provider",
                "KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER",
                "google", this::getModelProvider, false);
        this.knowledgeCollectionElementResolutionAgentPromptVersion = loadProperty(
                "knowledge.collection.element.resolution.agent.prompt.version",
                "KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION",
                "v1.0.0", s -> s, false);
        this.timingEwmaAlpha = loadPropertyAsDouble(
                "timing.ewma.alpha", "TIMING_EWMA_ALPHA", "0.2", false);
        this.stabilityEwmaAlpha = loadPropertyAsDouble(
                "stability.ewma.alpha", "STABILITY_EWMA_ALPHA", "0.3", false);
        this.timingVerificationMinDelayMs = loadPropertyAsInteger(
                "timing.verification.min.delay.ms", "TIMING_VERIFICATION_MIN_DELAY_MS", "500", false);
        this.satisfiesSimilarityThreshold = loadPropertyAsDouble(
                "satisfies.similarity.threshold", "SATISFIES_SIMILARITY_THRESHOLD", "0.85", false);
        this.ancestryWindowSize = loadPropertyAsInteger(
                "ancestry.window.size", "ANCESTRY_WINDOW_SIZE", "5", false);
        this.satisfiesStaleDays = loadPropertyAsInteger(
                "satisfies.stale.days", "SATISFIES_STALE_DAYS", "30", false);
        this.stabilityPenaltyThreshold = loadPropertyAsDouble(
                "stability.penalty.threshold", "STABILITY_PENALTY_THRESHOLD", "0.5", false);
        this.healthReportOutputPath = loadProperty(
                "health.report.output.path", "HEALTH_REPORT_OUTPUT_PATH", "reports/graph-health-report.html", s -> s, false);
        this.healthWarningThreshold = loadPropertyAsInteger(
                "health.warning.threshold", "HEALTH_WARNING_THRESHOLD", "3", false);
        this.healthCriticalThreshold = loadPropertyAsInteger(
                "health.critical.threshold", "HEALTH_CRITICAL_THRESHOLD", "10", false);
    }


    private final ConfigProperty<String> screenshotsSaveFolder;

    public String getScreenshotsSaveFolder() {
        return screenshotsSaveFolder.value();
    }

    // -----------------------------------------------------
    // Execution Mode Configuration
    private final ConfigProperty<ExecutionMode> executionMode;

    /**
     * Returns the current execution mode.
     */
    public ExecutionMode getExecutionMode() {
        return executionMode.value();
    }

    /**
     * Returns true if the agent is running in fully unattended mode (no operator interaction).
     */
    public boolean isFullyUnattended() {
        return getExecutionMode() == ExecutionMode.UNATTENDED;
    }

    /**
     * Returns true if the agent is running in supervised mode (autonomous with halt option).
     */
    public boolean isSupervised() {
        return getExecutionMode() == ExecutionMode.SUPERVISED;
    }


    private final ConfigProperty<Integer> supervisedCountdownSeconds;

    /**
     * Returns the countdown duration in seconds for supervised mode halt popup.
     */
    public int getSupervisedCountdownSeconds() {
        return supervisedCountdownSeconds.value();
    }

    public int getAgentToolCallsBudget() {
        return super.getAgentToolCallsBudget();
    }

    // -----------------------------------------------------
    // Video Recording
    private final ConfigProperty<Boolean> screenRecordingEnabled;
    private final ConfigProperty<String> screenRecordingFolder;
    private final ConfigProperty<Integer> videoBitrate;
    private final ConfigProperty<String> screenRecordingFormat;
    private final ConfigProperty<Integer> screenRecordingFrameRate;

    public boolean getScreenRecordingEnabled() {
        return screenRecordingEnabled.value();
    }

    public String getScreenRecordingFolder() {
        return screenRecordingFolder.value();
    }

    public int getRecordingBitrate() {
        return videoBitrate.value();
    }

    public String getRecordingFormat() {
        return screenRecordingFormat.value();
    }

    public int getRecordingFrameRate() {
        if (screenRecordingFrameRate.value() <= 0) {
            throw new IllegalArgumentException("Video recording frame rate must be a positive integer.");
        }
        return screenRecordingFrameRate.value();
    }

    // -----------------------------------------------------
    // Element Config
    private final ConfigProperty<String> elementBoundingBoxColorName;

    public String getElementBoundingBoxColorName() {
        return elementBoundingBoxColorName.value();
    }

    private final ConfigProperty<Double> elementRetrievalMinTargetScore;

    public double getElementRetrievalMinTargetScore() {
        return elementRetrievalMinTargetScore.value();
    }

    private final ConfigProperty<Double> elementRetrievalMinGeneralScore;

    public double getElementRetrievalMinGeneralScore() {
        return elementRetrievalMinGeneralScore.value();
    }

    private final ConfigProperty<Double> elementLocatorVisualSimilarityThreshold;

    public double getElementLocatorVisualSimilarityThreshold() {
        return elementLocatorVisualSimilarityThreshold.value();
    }

    private final ConfigProperty<Integer> elementLocatorTopVisualMatches;

    public int getElementLocatorTopVisualMatches() {
        return elementLocatorTopVisualMatches.value();
    }

    private final ConfigProperty<Double> foundMatchesDimensionDeviationRatio;

    public double getFoundMatchesDimensionDeviationRatio() {
        return foundMatchesDimensionDeviationRatio.value();
    }

    private final ConfigProperty<Integer> elementLocatorVisualGroundingVoteCount;

    public int getElementLocatorVisualGroundingVoteCount() {
        return elementLocatorVisualGroundingVoteCount.value();
    }

    private final ConfigProperty<Integer> elementLocatorValidationVoteCount;

    public int getElementLocatorValidationVoteCount() {
        return elementLocatorValidationVoteCount.value();
    }

    private final ConfigProperty<Double> bboxClusteringMinIntersectionRatio;

    public double getBboxClusteringMinIntersectionRatio() {
        return bboxClusteringMinIntersectionRatio.value();
    }

    private final ConfigProperty<Integer> bboxScreenshotLongestAllowedDimensionPixels;

    public int getBboxScreenshotLongestAllowedDimensionPixels() {
        return bboxScreenshotLongestAllowedDimensionPixels.value();
    }

    private final ConfigProperty<Double> bboxScreenshotMaxSizeMegapixels;

    public double getBboxScreenshotMaxSizeMegapixels() {
        return bboxScreenshotMaxSizeMegapixels.value();
    }

    private final ConfigProperty<Boolean> boundingBoxAlreadyNormalized;

    public boolean isBoundingBoxAlreadyNormalized() {
        return boundingBoxAlreadyNormalized.value();
    }

    private final ConfigProperty<Boolean> algorithmicSearchEnabled;

    private final ConfigProperty<Integer> verificationModelMaxRetries;

    public int getVerificationModelMaxRetries() {
        return verificationModelMaxRetries.value();
    }

    public boolean isAlgorithmicSearchEnabled() {
        return algorithmicSearchEnabled.value();
    }

    private final ConfigProperty<Boolean> skipUiElementSelectionForVision;

    public boolean skipBestUiElementMatchSelection() {
        return skipUiElementSelectionForVision.value();
    }

    // -----------------------------------------------------
    // User UI dialogs
    private final ConfigProperty<Integer> dialogDefaultHorizontalGap;

    public int getDialogDefaultHorizontalGap() {
        return dialogDefaultHorizontalGap.value();
    }

    private final ConfigProperty<Integer> dialogDefaultVerticalGap;

    public int getDialogDefaultVerticalGap() {
        return dialogDefaultVerticalGap.value();
    }

    private final ConfigProperty<String> dialogDefaultFontType;

    public String getDialogDefaultFontType() {
        return dialogDefaultFontType.value();
    }

    private final ConfigProperty<Integer> dialogDefaultFontSize;

    public int getDialogDefaultFontSize() {
        return dialogDefaultFontSize.value();
    }

    private final ConfigProperty<Boolean> dialogHoverAsClick;

    public boolean isDialogHoverAsClick() {
        return dialogHoverAsClick.value();
    }

    // -----------------------------------------------------
    // Knowledge Persistence Configuration (Neo4j)
    private final ConfigProperty<String> neo4jUsername;
    private final ConfigProperty<String> neo4jDatabase;
    private final ConfigProperty<Integer> neo4jMaxConnectionPoolSize;
    private final ConfigProperty<Integer> neo4jConnectionAcquisitionTimeoutSeconds;
    private final ConfigProperty<Integer> neo4jConnectionTimeoutSeconds;
    private final ConfigProperty<Integer> neo4jMaxTransactionRetryTimeSeconds;
    private final ConfigProperty<Integer> knowledgeMaxDepth;
    private final ConfigProperty<Integer> knowledgeEmbeddingBatchSize;
    private final ConfigProperty<Double> knowledgeMatchConfidenceHigh;
    private final ConfigProperty<Double> knowledgeMatchConfidenceLow;
    private final ConfigProperty<Integer> knowledgeMatchTopN;
    private final ConfigProperty<Integer> procedureLookupDelayMs;

    public String getNeo4jUsername() {
        return neo4jUsername.value();
    }

    public String getNeo4jDatabase() {
        return neo4jDatabase.value();
    }

    public int getNeo4jMaxConnectionPoolSize() {
        return neo4jMaxConnectionPoolSize.value();
    }

    public int getNeo4jConnectionAcquisitionTimeoutSeconds() {
        return neo4jConnectionAcquisitionTimeoutSeconds.value();
    }

    public int getNeo4jConnectionTimeoutSeconds() {
        return neo4jConnectionTimeoutSeconds.value();
    }

    public int getNeo4jMaxTransactionRetryTimeSeconds() {
        return neo4jMaxTransactionRetryTimeSeconds.value();
    }

    public int getKnowledgeMaxDepth() {
        return knowledgeMaxDepth.value();
    }

    public int getKnowledgeEmbeddingBatchSize() {
        return knowledgeEmbeddingBatchSize.value();
    }

    public double getKnowledgeMatchConfidenceHigh() {
        return knowledgeMatchConfidenceHigh.value();
    }

    public double getKnowledgeMatchConfidenceLow() {
        return knowledgeMatchConfidenceLow.value();
    }

    public int getKnowledgeMatchTopN() {
        return knowledgeMatchTopN.value();
    }

    public int getProcedureLookupDelayMs() {
        return procedureLookupDelayMs.value();
    }

    /**
     * Returns true if Neo4j authentication credentials are fully configured
     * (both username and password are non-blank).
     */
    public boolean isNeo4jAuthConfigured() {
        return !getNeo4jUsername().isBlank() && !getVectorDbToken().isBlank();
    }

    // UI Element Description Extraction Agent
    private final ConfigProperty<String> uiElementDescriptionExtractionAgentModelName;

    public String getUiElementDescriptionExtractionAgentModelName() {
        return uiElementDescriptionExtractionAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> uiElementDescriptionExtractionAgentModelProvider;

    public ModelProvider getUiElementDescriptionExtractionAgentModelProvider() {
        return uiElementDescriptionExtractionAgentModelProvider.value();
    }

    private final ConfigProperty<String> uiElementDescriptionExtractionAgentPromptVersion;

    public String getUiElementDescriptionExtractionAgentPromptVersion() {
        return uiElementDescriptionExtractionAgentPromptVersion.value();
    }

    // UI Element Description Matcher Agent
    private final ConfigProperty<String> uiElementDescriptionMatcherAgentModelName;

    public String getUiElementDescriptionMatcherAgentModelName() {
        return uiElementDescriptionMatcherAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> uiElementDescriptionMatcherAgentModelProvider;

    public ModelProvider getUiElementDescriptionMatcherAgentModelProvider() {
        return uiElementDescriptionMatcherAgentModelProvider.value();
    }

    private final ConfigProperty<String> uiElementDescriptionMatcherAgentPromptVersion;

    public String getUiElementDescriptionMatcherAgentPromptVersion() {
        return uiElementDescriptionMatcherAgentPromptVersion.value();
    }

    // UI State Check Agent
    private final ConfigProperty<String> uiStateCheckAgentModelName;

    public String getUiStateCheckAgentModelName() {
        return uiStateCheckAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> uiStateCheckAgentModelProvider;

    public ModelProvider getUiStateCheckAgentModelProvider() {
        return uiStateCheckAgentModelProvider.value();
    }

    private final ConfigProperty<String> uiStateCheckAgentPromptVersion;

    public String getUiStateCheckAgentPromptVersion() {
        return uiStateCheckAgentPromptVersion.value();
    }

    // Element Bounding Box Agent
    private final ConfigProperty<String> elementBoundingBoxAgentModelName;

    public String getElementBoundingBoxAgentModelName() {
        return elementBoundingBoxAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> elementBoundingBoxAgentModelProvider;

    public ModelProvider getElementBoundingBoxAgentModelProvider() {
        return elementBoundingBoxAgentModelProvider.value();
    }

    private final ConfigProperty<String> elementBoundingBoxAgentPromptVersion;

    public String getElementBoundingBoxAgentPromptVersion() {
        return elementBoundingBoxAgentPromptVersion.value();
    }

    // Element Selection Agent
    private final ConfigProperty<String> uiElementVisualMatchAgentModelName;

    public String getUiElementVisualMatchAgentModelName() {
        return uiElementVisualMatchAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> uiElementVisualMatchAgentModelProvider;

    public ModelProvider getUiElementVisualMatchAgentModelProvider() {
        return uiElementVisualMatchAgentModelProvider.value();
    }

    private final ConfigProperty<String> elementSelectionAgentPromptVersion;

    public String getElementSelectionAgentPromptVersion() {
        return elementSelectionAgentPromptVersion.value();
    }

    // DB Element Selection Agent
    private final ConfigProperty<String> dbElementCandidateSelectionAgentPromptVersion;

    public String getDbElementCandidateSelectionAgentPromptVersion() {
        return dbElementCandidateSelectionAgentPromptVersion.value();
    }

    // Element Selection Agent
    private final ConfigProperty<String> dbElementSelectionAgentModelName;


    public String getDbElementCandidateSelectionAgentModelName() {
        return dbElementSelectionAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> dbElementSelectionAgentModelProvider;

    public ModelProvider getDbElementCandidateSelectionAgentModelProvider() {
        return dbElementSelectionAgentModelProvider.value();
    }

    // Precondition Verification Agent
    private final ConfigProperty<String> preconditionVerificationAgentModelName;

    public String getPreconditionVerificationAgentModelName() {
        return preconditionVerificationAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> preconditionVerificationAgentModelProvider;

    public ModelProvider getPreconditionVerificationAgentModelProvider() {
        return preconditionVerificationAgentModelProvider.value();
    }

    private final ConfigProperty<String> preconditionVerificationAgentPromptVersion;

    public String getPreconditionVerificationAgentPromptVersion() {
        return preconditionVerificationAgentPromptVersion.value();
    }

    // Test Step Verification Agent
    private final ConfigProperty<String> testStepVerificationAgentModelName;

    public String getTestStepVerificationAgentModelName() {
        return testStepVerificationAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> testStepVerificationAgentModelProvider;

    public ModelProvider getTestStepVerificationAgentModelProvider() {
        return testStepVerificationAgentModelProvider.value();
    }

    private final ConfigProperty<String> testStepVerificationAgentPromptVersion;

    public String getTestStepVerificationAgentPromptVersion() {
        return testStepVerificationAgentPromptVersion.value();
    }

    // Knowledge Suggestion Agent
    private final ConfigProperty<String> knowledgeSuggestionAgentModelName;

    public String getKnowledgeSuggestionAgentModelName() {
        return knowledgeSuggestionAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> knowledgeSuggestionAgentModelProvider;

    public ModelProvider getKnowledgeSuggestionAgentModelProvider() {
        return knowledgeSuggestionAgentModelProvider.value();
    }

    private final ConfigProperty<String> knowledgeSuggestionAgentPromptVersion;

    public String getKnowledgeSuggestionAgentPromptVersion() {
        return knowledgeSuggestionAgentPromptVersion.value();
    }

    // Collecting knowledge Element Resolution Agent
    private final ConfigProperty<String> knowledgeCollectionElementResolutionAgentModelName;

    public String getKnowledgeCollectionElementResolutionAgentModelName() {
        return knowledgeCollectionElementResolutionAgentModelName.value();
    }

    private final ConfigProperty<ModelProvider> knowledgeCollectionElementResolutionAgentModelProvider;

    public ModelProvider getKnowledgeCollectionElementResolutionAgentModelProvider() {
        return knowledgeCollectionElementResolutionAgentModelProvider.value();
    }

    private final ConfigProperty<String> knowledgeCollectionElementResolutionAgentPromptVersion;

    public String getKnowledgeCollectionElementResolutionAgentPromptVersion() {
        return knowledgeCollectionElementResolutionAgentPromptVersion.value();
    }

    // -----------------------------------------------------
    // Knowledge Graph Enhancements Tunable Parameters
    private final ConfigProperty<Double> timingEwmaAlpha;

    public double getTimingEwmaAlpha() {
        return timingEwmaAlpha.value();
    }

    private final ConfigProperty<Double> stabilityEwmaAlpha;

    public double getStabilityEwmaAlpha() {
        return stabilityEwmaAlpha.value();
    }

    private final ConfigProperty<Integer> timingVerificationMinDelayMs;

    public int getTimingVerificationMinDelayMs() {
        return timingVerificationMinDelayMs.value();
    }

    private final ConfigProperty<Double> satisfiesSimilarityThreshold;

    public double getSatisfiesSimilarityThreshold() {
        return satisfiesSimilarityThreshold.value();
    }

    private final ConfigProperty<Integer> ancestryWindowSize;

    public int getAncestryWindowSize() {
        return ancestryWindowSize.value();
    }

    private final ConfigProperty<Integer> satisfiesStaleDays;

    public int getSatisfiesStaleDays() {
        return satisfiesStaleDays.value();
    }

    private final ConfigProperty<Double> stabilityPenaltyThreshold;

    public double getStabilityPenaltyThreshold() {
        return stabilityPenaltyThreshold.value();
    }

    private final ConfigProperty<String> healthReportOutputPath;

    public String getHealthReportOutputPath() {
        return healthReportOutputPath.value();
    }

    private final ConfigProperty<Integer> healthWarningThreshold;

    public int getHealthWarningThreshold() {
        return healthWarningThreshold.value();
    }

    private final ConfigProperty<Integer> healthCriticalThreshold;

    public int getHealthCriticalThreshold() {
        return healthCriticalThreshold.value();
    }
}