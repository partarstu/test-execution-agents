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

import io.avaje.inject.Singleton;

import org.tarik.ta.core.AgentConfig;

@Singleton
public class UiTestAgentConfig extends AgentConfig {

    public UiTestAgentConfig() {
        super();
        this.SCREENSHOTS_SAVE_FOLDER = loadProperty("screenshots.save.folder",
            "SCREENSHOTS_SAVE_FOLDER", "screens", s -> s, false);
        this.EXECUTION_MODE = loadProperty(
            "execution.mode", "EXECUTION_MODE", "SUPERVISED",
            s -> ExecutionMode.valueOf(s.toUpperCase()), false);
        this.SUPERVISED_COUNTDOWN_SECONDS = loadPropertyAsInteger(
            "supervised.countdown.seconds", "SUPERVISED_COUNTDOWN_SECONDS", "5", false);
        this.SCREEN_RECORDING_ENABLED = loadProperty("screen.recording.active",
            "SCREEN_RECORDING_ENABLED", "false", Boolean::parseBoolean, false);
        this.SCREEN_RECORDING_FOLDER = loadProperty("screen.recording.output.dir",
            "SCREEN_RECORDING_FOLDER", "videos", s -> s, false);
        this.VIDEO_BITRATE = loadPropertyAsInteger("recording.bit.rate",
            "VIDEO_BITRATE", "2000000", false);
        this.SCREEN_RECORDING_FORMAT = loadProperty("recording.file.format",
            "SCREEN_RECORDING_FORMAT", "mp4", s -> s, false);
        this.SCREEN_RECORDING_FRAME_RATE = loadPropertyAsInteger("recording.fps",
            "SCREEN_RECORDING_FRAME_RATE", "10", false);
        this.ELEMENT_BOUNDING_BOX_COLOR_NAME = getRequiredProperty(
            "element.bounding.box.color", "BOUNDING_BOX_COLOR", false);
        this.ELEMENT_RETRIEVAL_MIN_TARGET_SCORE = loadPropertyAsDouble(
            "element.retrieval.min.target.score", "ELEMENT_RETRIEVAL_MIN_TARGET_SCORE", "0.85", false);
        this.ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE = loadPropertyAsDouble(
            "element.retrieval.min.general.score", "ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE", "0.85", false);
        this.ELEMENT_LOCATOR_VISUAL_SIMILARITY_THRESHOLD = loadPropertyAsDouble(
            "element.locator.visual.similarity.threshold", "VISUAL_SIMILARITY_THRESHOLD", "0.8", false);
        this.ELEMENT_LOCATOR_TOP_VISUAL_MATCHES = loadPropertyAsInteger(
            "element.locator.top.visual.matches",
            "TOP_VISUAL_MATCHES_TO_FIND",
            "3", false);
        this.FOUND_MATCHES_DIMENSION_DEVIATION_RATIO = loadPropertyAsDouble(
            "element.locator.found.matches.dimension.deviation.ratio", "FOUND_MATCHES_DIMENSION_DEVIATION_RATIO", "0.3",
            false);
        this.ELEMENT_LOCATOR_VISUAL_GROUNDING_VOTE_COUNT = loadPropertyAsInteger(
            "element.locator.visual.grounding.model.vote.count", "VISUAL_GROUNDING_MODEL_VOTE_COUNT", "5", false);
        this.ELEMENT_LOCATOR_VALIDATION_VOTE_COUNT = loadPropertyAsInteger(
            "element.locator.validation.model.vote.count", "VALIDATION_MODEL_VOTE_COUNT", "3", false);
        this.BBOX_CLUSTERING_MIN_INTERSECTION_RATIO = loadPropertyAsDouble(
            "element.locator.bbox.clustering.min.intersection.ratio", "BBOX_CLUSTERING_MIN_INTERSECTION_RATIO", "0.7",
            false);
        this.BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS = loadPropertyAsInteger(
            "bbox.screenshot.longest.allowed.dimension.pixels", "BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS",
            "1568", false);
        this.BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS = loadPropertyAsDouble(
            "bbox.screenshot.max.size.megapixels", "BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS", "1.15", false);
        this.BOUNDING_BOX_ALREADY_NORMALIZED = loadProperty(
            "bounding.box.already.normalized", "BOUNDING_BOX_ALREADY_NORMALIZED", "false", Boolean::parseBoolean,
            false);
        this.ALGORITHMIC_SEARCH_ENABLED = loadProperty(
            "element.locator.algorithmic.search.enabled", "ALGORITHMIC_SEARCH_ENABLED", "true", Boolean::parseBoolean,
            false);
        this.VERIFICATION_MODEL_MAX_RETRIES = loadProperty(
            "verification.model.max.retries", "VERIFICATION_MODEL_MAX_RETRIES", "3", Integer::parseInt, false);
        this.SKIP_UI_ELEMENT_SELECTION_FOR_VISION = loadProperty(
            "element.locator.skip.model.selection.vision.only", "SKIP_UI_ELEMENT_SELECTION_FOR_VISION", "false",
            Boolean::parseBoolean, false);
        this.DIALOG_DEFAULT_HORIZONTAL_GAP = loadPropertyAsInteger(
            "dialog.default.horizontal.gap", "DIALOG_DEFAULT_HORIZONTAL_GAP", "10", false);
        this.DIALOG_DEFAULT_VERTICAL_GAP = loadPropertyAsInteger(
            "dialog.default.vertical.gap", "DIALOG_DEFAULT_VERTICAL_GAP", "10", false);
        this.DIALOG_DEFAULT_FONT_TYPE = getProperty("dialog.default.font.type",
            "DIALOG_DEFAULT_FONT_TYPE", "Dialog", s -> s, false);
        this.DIALOG_DEFAULT_FONT_SIZE = loadPropertyAsInteger(
            "dialog.default.font.size", "DIALOG_DEFAULT_FONT_SIZE", "13", false);
        this.DIALOG_HOVER_AS_CLICK = loadProperty("dialog.hover.as.click",
            "DIALOG_HOVER_AS_CLICK", "false", Boolean::parseBoolean, false);
        this.NEO4J_USERNAME = loadProperty("neo4j.username", "NEO4J_USERNAME",
            "neo4j", s -> s, false);
        this.NEO4J_DATABASE = loadProperty("neo4j.database", "NEO4J_DATABASE",
            "neo4j", s -> s, false);
        this.KNOWLEDGE_MAX_DEPTH = loadPropertyAsInteger("knowledge.max.depth",
            "KNOWLEDGE_MAX_DEPTH", "3", false);
        this.KNOWLEDGE_EMBEDDING_BATCH_SIZE = loadPropertyAsInteger(
            "knowledge.embedding.batch.size", "KNOWLEDGE_EMBEDDING_BATCH_SIZE", "10", false);
        this.KNOWLEDGE_MATCH_CONFIDENCE_HIGH = loadPropertyAsDouble(
            "knowledge.match.confidence.high", "KNOWLEDGE_MATCH_CONFIDENCE_HIGH", "0.85", false);
        this.KNOWLEDGE_MATCH_CONFIDENCE_LOW = loadPropertyAsDouble(
            "knowledge.match.confidence.low", "KNOWLEDGE_MATCH_CONFIDENCE_LOW", "0.5", false);
        this.KNOWLEDGE_MATCH_TOP_N = loadPropertyAsInteger(
            "knowledge.match.top.n", "KNOWLEDGE_MATCH_TOP_N", "5", false);
        this.PROCEDURE_LOOKUP_DELAY_MS = loadPropertyAsInteger(
            "procedure.lookup.delay.ms", "PROCEDURE_LOOKUP_DELAY_MS", "2000", false);
        this.UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME = loadProperty(
            "ui.element.description.matcher.agent.model.name", "UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME", "gemini-3-flash-preview",
            s -> s, false);
        this.UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER = getProperty(
            "ui.element.description.matcher.agent.model.provider", "UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER", "google",
            this::getModelProvider, false);
        this.UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION = loadProperty(
            "ui.element.description.matcher.agent.prompt.version", "UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION", "v1.0.0",
            s -> s, false);
        this.UI_STATE_CHECK_AGENT_MODEL_NAME = loadProperty(
            "ui.state.check.agent.model.name", "UI_STATE_CHECK_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.UI_STATE_CHECK_AGENT_MODEL_PROVIDER = getProperty(
            "ui.state.check.agent.model.provider", "UI_STATE_CHECK_AGENT_MODEL_PROVIDER", "google",
            this::getModelProvider, false);
        this.UI_STATE_CHECK_AGENT_PROMPT_VERSION = loadProperty(
            "ui.state.check.agent.prompt.version", "UI_STATE_CHECK_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);
        this.ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME = loadProperty(
            "element.bounding.box.agent.model.name", "ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME", "gemini-3-flash-preview",
            s -> s, false);
        this.ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER = getProperty(
            "element.bounding.box.agent.model.provider", "ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER", "google",
            this::getModelProvider, false);
        this.ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION = loadProperty(
            "element.bounding.box.agent.prompt.version", "ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION", "v1.0.0", s -> s,
            false);
        this.UI_ELEMENT_VISUAL_MATCH_AGENT_MODEL_NAME = loadProperty(
            "element.selection.agent.model.name", "ELEMENT_SELECTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.UI_ELEMENT_VISUAL_MATCH_AGENT_MODEL_PROVIDER = getProperty(
            "element.selection.agent.model.provider", "ELEMENT_SELECTION_AGENT_MODEL_PROVIDER", "google", this::getModelProvider,
            false);
        this.ELEMENT_SELECTION_AGENT_PROMPT_VERSION = loadProperty(
            "element.selection.agent.prompt.version", "ELEMENT_SELECTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);
        this.DB_ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION = loadProperty(
            "db.element.selection.agent.prompt.version", "ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s,
            false);
        this.DB_ELEMENT_SELECTION_AGENT_MODEL_NAME = loadProperty(
            "db.element.selection.agent.model.name", "DB_ELEMENT_SELECTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER = getProperty(
            "db.element.selection.agent.model.provider", "DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER", "google",
            this::getModelProvider, false);
        this.PRECONDITION_VERIFICATION_AGENT_MODEL_NAME = loadProperty(
            "precondition.verification.agent.model.name", "PRECONDITION_VERIFICATION_AGENT_MODEL_NAME",
            "gemini-3-flash-preview", s -> s, false);
        this.PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER = getProperty(
            "precondition.verification.agent.model.provider", "precondition_VERIFICATION_AGENT_MODEL_PROVIDER",
            "google", this::getModelProvider, false);
        this.PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION = loadProperty(
            "precondition.verification.agent.prompt.version", "PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION",
            "v1.0.0", s -> s, false);
        this.TEST_STEP_VERIFICATION_AGENT_MODEL_NAME = loadProperty(
            "test.step.verification.agent.model.name", "TEST_STEP_VERIFICATION_AGENT_MODEL_NAME", "gemini-3-flash-preview",
            s -> s, false);
        this.TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER = getProperty(
            "test.step.verification.agent.model.provider", "TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER", "google",
            this::getModelProvider, false);
        this.TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION = loadProperty(
            "test.step.verification.agent.prompt.version", "TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION", "v1.0.0",
            s -> s, false);
        this.KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME = loadProperty(
            "knowledge.suggestion.agent.model.name", "KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME", "gemini-3-flash-preview",
            s -> s, false);
        this.KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER = getProperty(
            "knowledge.suggestion.agent.model.provider", "KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER", "google",
            this::getModelProvider, false);
        this.KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION = loadProperty(
            "knowledge.suggestion.agent.prompt.version", "KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION", "v1.0.0",
            s -> s, false);
        this.KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME = loadProperty(
            "knowledge.collection.element.resolution.agent.model.name", "KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME",
            "gemini-3-flash-preview", s -> s, false);
        this.KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER = getProperty(
            "knowledge.collection.element.resolution.agent.model.provider", "KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER",
            "google", this::getModelProvider, false);
        this.KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION = loadProperty(
            "knowledge.collection.element.resolution.agent.prompt.version", "KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION",
            "v1.0.0", s -> s, false);
        this.TIMING_EWMA_ALPHA = loadPropertyAsDouble(
            "timing.ewma.alpha", "TIMING_EWMA_ALPHA", "0.2", false);
        this.STABILITY_EWMA_ALPHA = loadPropertyAsDouble(
            "stability.ewma.alpha", "STABILITY_EWMA_ALPHA", "0.3", false);
        this.TIMING_VERIFICATION_MIN_DELAY_MS = loadPropertyAsInteger(
            "timing.verification.min.delay.ms", "TIMING_VERIFICATION_MIN_DELAY_MS", "500", false);
        this.SATISFIES_SIMILARITY_THRESHOLD = loadPropertyAsDouble(
            "satisfies.similarity.threshold", "SATISFIES_SIMILARITY_THRESHOLD", "0.85", false);
        this.ANCESTRY_WINDOW_SIZE = loadPropertyAsInteger(
            "ancestry.window.size", "ANCESTRY_WINDOW_SIZE", "5", false);
        this.SATISFIES_STALE_DAYS = loadPropertyAsInteger(
            "satisfies.stale.days", "SATISFIES_STALE_DAYS", "30", false);
        this.STABILITY_PENALTY_THRESHOLD = loadPropertyAsDouble(
            "stability.penalty.threshold", "STABILITY_PENALTY_THRESHOLD", "0.5", false);
        this.HEALTH_REPORT_OUTPUT_PATH = loadProperty(
            "health.report.output.path", "HEALTH_REPORT_OUTPUT_PATH", "reports/graph-health-report.html", s -> s, false);
        this.HEALTH_WARNING_THRESHOLD = loadPropertyAsInteger(
            "health.warning.threshold", "HEALTH_WARNING_THRESHOLD", "3", false);
        this.HEALTH_CRITICAL_THRESHOLD = loadPropertyAsInteger(
            "health.critical.threshold", "HEALTH_CRITICAL_THRESHOLD", "10", false);
    }


    private final ConfigProperty<String> SCREENSHOTS_SAVE_FOLDER;

    public String getScreenshotsSaveFolder() {
        return SCREENSHOTS_SAVE_FOLDER.value();
    }

    // -----------------------------------------------------
    // Execution Mode Configuration
    private final ConfigProperty<ExecutionMode> EXECUTION_MODE;

    /**
     * Returns the current execution mode.
     */
    public ExecutionMode getExecutionMode() {
        return EXECUTION_MODE.value();
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

    
    private final ConfigProperty<Integer> SUPERVISED_COUNTDOWN_SECONDS;

    /**
     * Returns the countdown duration in seconds for supervised mode halt popup.
     */
    public int getSupervisedCountdownSeconds() {
        return SUPERVISED_COUNTDOWN_SECONDS.value();
    }

    public int getAgentToolCallsBudget() {
        return super.getAgentToolCallsBudget();
    }

    // -----------------------------------------------------
    // Video Recording
    private final ConfigProperty<Boolean> SCREEN_RECORDING_ENABLED;
    private final ConfigProperty<String> SCREEN_RECORDING_FOLDER;
    private final ConfigProperty<Integer> VIDEO_BITRATE;
    private final ConfigProperty<String> SCREEN_RECORDING_FORMAT;
    private final ConfigProperty<Integer> SCREEN_RECORDING_FRAME_RATE;

    public boolean getScreenRecordingEnabled() {
        return SCREEN_RECORDING_ENABLED.value();
    }

    public String getScreenRecordingFolder() {
        return SCREEN_RECORDING_FOLDER.value();
    }

    public int getRecordingBitrate() {
        return VIDEO_BITRATE.value();
    }

    public String getRecordingFormat() {
        return SCREEN_RECORDING_FORMAT.value();
    }

    public int getRecordingFrameRate() {
        if (SCREEN_RECORDING_FRAME_RATE.value() <= 0) {
            throw new IllegalArgumentException("Video recording frame rate must be a positive integer.");
        }
        return SCREEN_RECORDING_FRAME_RATE.value();
    }

    // -----------------------------------------------------
    // Element Config
    private final ConfigProperty<String> ELEMENT_BOUNDING_BOX_COLOR_NAME;

    public String getElementBoundingBoxColorName() {
        return ELEMENT_BOUNDING_BOX_COLOR_NAME.value();
    }

    private final ConfigProperty<Double> ELEMENT_RETRIEVAL_MIN_TARGET_SCORE;

    public double getElementRetrievalMinTargetScore() {
        return ELEMENT_RETRIEVAL_MIN_TARGET_SCORE.value();
    }

    private final ConfigProperty<Double> ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE;

    public double getElementRetrievalMinGeneralScore() {
        return ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE.value();
    }

    private final ConfigProperty<Double> ELEMENT_LOCATOR_VISUAL_SIMILARITY_THRESHOLD;

    public double getElementLocatorVisualSimilarityThreshold() {
        return ELEMENT_LOCATOR_VISUAL_SIMILARITY_THRESHOLD.value();
    }

    private final ConfigProperty<Integer> ELEMENT_LOCATOR_TOP_VISUAL_MATCHES;

    public int getElementLocatorTopVisualMatches() {
        return ELEMENT_LOCATOR_TOP_VISUAL_MATCHES.value();
    }

    private final ConfigProperty<Double> FOUND_MATCHES_DIMENSION_DEVIATION_RATIO;

    public double getFoundMatchesDimensionDeviationRatio() {
        return FOUND_MATCHES_DIMENSION_DEVIATION_RATIO.value();
    }

    private final ConfigProperty<Integer> ELEMENT_LOCATOR_VISUAL_GROUNDING_VOTE_COUNT;

    public int getElementLocatorVisualGroundingVoteCount() {
        return ELEMENT_LOCATOR_VISUAL_GROUNDING_VOTE_COUNT.value();
    }

    private final ConfigProperty<Integer> ELEMENT_LOCATOR_VALIDATION_VOTE_COUNT;

    public int getElementLocatorValidationVoteCount() {
        return ELEMENT_LOCATOR_VALIDATION_VOTE_COUNT.value();
    }

    private final ConfigProperty<Double> BBOX_CLUSTERING_MIN_INTERSECTION_RATIO;

    public double getBboxClusteringMinIntersectionRatio() {
        return BBOX_CLUSTERING_MIN_INTERSECTION_RATIO.value();
    }

    private final ConfigProperty<Integer> BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS;

    public int getBboxScreenshotLongestAllowedDimensionPixels() {
        return BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS.value();
    }

    private final ConfigProperty<Double> BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS;

    public double getBboxScreenshotMaxSizeMegapixels() {
        return BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS.value();
    }

    private final ConfigProperty<Boolean> BOUNDING_BOX_ALREADY_NORMALIZED;

    public boolean isBoundingBoxAlreadyNormalized() {
        return BOUNDING_BOX_ALREADY_NORMALIZED.value();
    }

    private final ConfigProperty<Boolean> ALGORITHMIC_SEARCH_ENABLED;

    private final ConfigProperty<Integer> VERIFICATION_MODEL_MAX_RETRIES;

    public int getVerificationModelMaxRetries() {
        return VERIFICATION_MODEL_MAX_RETRIES.value();
    }

    public boolean isAlgorithmicSearchEnabled() {
        return ALGORITHMIC_SEARCH_ENABLED.value();
    }

    private final ConfigProperty<Boolean> SKIP_UI_ELEMENT_SELECTION_FOR_VISION;

    public boolean skipBestUiElementMatchSelection() {
        return SKIP_UI_ELEMENT_SELECTION_FOR_VISION.value();
    }

    // -----------------------------------------------------
    // User UI dialogs
    private final ConfigProperty<Integer> DIALOG_DEFAULT_HORIZONTAL_GAP;

    public int getDialogDefaultHorizontalGap() {
        return DIALOG_DEFAULT_HORIZONTAL_GAP.value();
    }

    private final ConfigProperty<Integer> DIALOG_DEFAULT_VERTICAL_GAP;

    public int getDialogDefaultVerticalGap() {
        return DIALOG_DEFAULT_VERTICAL_GAP.value();
    }

    private final ConfigProperty<String> DIALOG_DEFAULT_FONT_TYPE;

    public String getDialogDefaultFontType() {
        return DIALOG_DEFAULT_FONT_TYPE.value();
    }

    private final ConfigProperty<Integer> DIALOG_DEFAULT_FONT_SIZE;

    public int getDialogDefaultFontSize() {
        return DIALOG_DEFAULT_FONT_SIZE.value();
    }

    private final ConfigProperty<Boolean> DIALOG_HOVER_AS_CLICK;

    public boolean isDialogHoverAsClick() {
        return DIALOG_HOVER_AS_CLICK.value();
    }

    // -----------------------------------------------------
    // Knowledge Persistence Configuration (Neo4j)
    private final ConfigProperty<String> NEO4J_USERNAME;
    private final ConfigProperty<String> NEO4J_DATABASE;
    private final ConfigProperty<Integer> KNOWLEDGE_MAX_DEPTH;
    private final ConfigProperty<Integer> KNOWLEDGE_EMBEDDING_BATCH_SIZE;
    private final ConfigProperty<Double> KNOWLEDGE_MATCH_CONFIDENCE_HIGH;
    private final ConfigProperty<Double> KNOWLEDGE_MATCH_CONFIDENCE_LOW;
    private final ConfigProperty<Integer> KNOWLEDGE_MATCH_TOP_N;
    private final ConfigProperty<Integer> PROCEDURE_LOOKUP_DELAY_MS;

    public String getNeo4jUsername() {
        return NEO4J_USERNAME.value();
    }

    public String getNeo4jDatabase() {
        return NEO4J_DATABASE.value();
    }

    public int getKnowledgeMaxDepth() {
        return KNOWLEDGE_MAX_DEPTH.value();
    }

    public int getKnowledgeEmbeddingBatchSize() {
        return KNOWLEDGE_EMBEDDING_BATCH_SIZE.value();
    }

    public double getKnowledgeMatchConfidenceHigh() {
        return KNOWLEDGE_MATCH_CONFIDENCE_HIGH.value();
    }

    public double getKnowledgeMatchConfidenceLow() {
        return KNOWLEDGE_MATCH_CONFIDENCE_LOW.value();
    }

    public int getKnowledgeMatchTopN() {
        return KNOWLEDGE_MATCH_TOP_N.value();
    }

    public int getProcedureLookupDelayMs() {
        return PROCEDURE_LOOKUP_DELAY_MS.value();
    }

    /**
     * Returns true if Neo4j authentication credentials are fully configured
     * (both username and password are non-blank).
     */
    public boolean isNeo4jAuthConfigured() {
        return !getNeo4jUsername().isBlank() && !getVectorDbToken().isBlank();
    }

    // UI Element Description Matcher Agent
    private final ConfigProperty<String> UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME;

    public String getUiElementDescriptionMatcherAgentModelName() {
        return UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_NAME.value();
    }

    private final ConfigProperty<ModelProvider> UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER;

    public ModelProvider getUiElementDescriptionMatcherAgentModelProvider() {
        return UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_MODEL_PROVIDER.value();
    }

    private final ConfigProperty<String> UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION;

    public String getUiElementDescriptionMatcherAgentPromptVersion() {
        return UI_ELEMENT_DESCRIPTION_MATCHER_AGENT_PROMPT_VERSION.value();
    }

    // UI State Check Agent
    private final ConfigProperty<String> UI_STATE_CHECK_AGENT_MODEL_NAME;

    public String getUiStateCheckAgentModelName() {
        return UI_STATE_CHECK_AGENT_MODEL_NAME.value();
    }

    private final ConfigProperty<ModelProvider> UI_STATE_CHECK_AGENT_MODEL_PROVIDER;

    public ModelProvider getUiStateCheckAgentModelProvider() {
        return UI_STATE_CHECK_AGENT_MODEL_PROVIDER.value();
    }

    private final ConfigProperty<String> UI_STATE_CHECK_AGENT_PROMPT_VERSION;

    public String getUiStateCheckAgentPromptVersion() {
        return UI_STATE_CHECK_AGENT_PROMPT_VERSION.value();
    }

    // Element Bounding Box Agent
    private final ConfigProperty<String> ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME;

    public String getElementBoundingBoxAgentModelName() {
        return ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME.value();
    }

    private final ConfigProperty<ModelProvider> ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER;

    public ModelProvider getElementBoundingBoxAgentModelProvider() {
        return ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER.value();
    }

    private final ConfigProperty<String> ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION;

    public String getElementBoundingBoxAgentPromptVersion() {
        return ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION.value();
    }

    // Element Selection Agent
    private final ConfigProperty<String> UI_ELEMENT_VISUAL_MATCH_AGENT_MODEL_NAME;

    public String getUiElementVisualMatchAgentModelName() {
        return UI_ELEMENT_VISUAL_MATCH_AGENT_MODEL_NAME.value();
    }

    private final ConfigProperty<ModelProvider> UI_ELEMENT_VISUAL_MATCH_AGENT_MODEL_PROVIDER;

    public ModelProvider getUiElementVisualMatchAgentModelProvider() {
        return UI_ELEMENT_VISUAL_MATCH_AGENT_MODEL_PROVIDER.value();
    }

    private final ConfigProperty<String> ELEMENT_SELECTION_AGENT_PROMPT_VERSION;

    public String getElementSelectionAgentPromptVersion() {
        return ELEMENT_SELECTION_AGENT_PROMPT_VERSION.value();
    }

    // DB Element Selection Agent
    private final ConfigProperty<String> DB_ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION;

    public String getDbElementCandidateSelectionAgentPromptVersion() {
        return DB_ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION.value();
    }

    // Element Selection Agent
    private final ConfigProperty<String> DB_ELEMENT_SELECTION_AGENT_MODEL_NAME;


    public String getDbElementCandidateSelectionAgentModelName() {
        return DB_ELEMENT_SELECTION_AGENT_MODEL_NAME.value();
    }

    private final ConfigProperty<ModelProvider> DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER;

    public ModelProvider getDbElementCandidateSelectionAgentModelProvider() {
        return DB_ELEMENT_SELECTION_AGENT_MODEL_PROVIDER.value();
    }

    // Precondition Verification Agent
    private final ConfigProperty<String> PRECONDITION_VERIFICATION_AGENT_MODEL_NAME;

    public String getPreconditionVerificationAgentModelName() {
        return PRECONDITION_VERIFICATION_AGENT_MODEL_NAME.value();
    }

    private final ConfigProperty<ModelProvider> PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER;

    public ModelProvider getPreconditionVerificationAgentModelProvider() {
        return PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER.value();
    }

    private final ConfigProperty<String> PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION;

    public String getPreconditionVerificationAgentPromptVersion() {
        return PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION.value();
    }

    // Test Step Verification Agent
    private final ConfigProperty<String> TEST_STEP_VERIFICATION_AGENT_MODEL_NAME;

    public String getTestStepVerificationAgentModelName() {
        return TEST_STEP_VERIFICATION_AGENT_MODEL_NAME.value();
    }

    private final ConfigProperty<ModelProvider> TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER;

    public ModelProvider getTestStepVerificationAgentModelProvider() {
        return TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER.value();
    }

    private final ConfigProperty<String> TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION;

    public String getTestStepVerificationAgentPromptVersion() {
        return TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION.value();
    }

    // Knowledge Suggestion Agent
    private final ConfigProperty<String> KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME;

    public String getKnowledgeSuggestionAgentModelName() {
        return KNOWLEDGE_SUGGESTION_AGENT_MODEL_NAME.value();
    }

    private final ConfigProperty<ModelProvider> KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER;

    public ModelProvider getKnowledgeSuggestionAgentModelProvider() {
        return KNOWLEDGE_SUGGESTION_AGENT_MODEL_PROVIDER.value();
    }

    private final ConfigProperty<String> KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION;

    public String getKnowledgeSuggestionAgentPromptVersion() {
        return KNOWLEDGE_SUGGESTION_AGENT_PROMPT_VERSION.value();
    }

    // Collecting knowledge Element Resolution Agent
    private final ConfigProperty<String> KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME;

    public String getKnowledgeCollectionElementResolutionAgentModelName() {
        return KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_NAME.value();
    }

    private final ConfigProperty<ModelProvider> KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER;

    public ModelProvider getKnowledgeCollectionElementResolutionAgentModelProvider() {
        return KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_MODEL_PROVIDER.value();
    }

    private final ConfigProperty<String> KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION;

    public String getKnowledgeCollectionElementResolutionAgentPromptVersion() {
        return KNOWLEDGE_COLLECTION_ELEMENT_RESOLUTION_AGENT_PROMPT_VERSION.value();
    }

    // -----------------------------------------------------
    // Knowledge Graph Enhancements Tunable Parameters
    private final ConfigProperty<Double> TIMING_EWMA_ALPHA;

    public double getTimingEwmaAlpha() {
        return TIMING_EWMA_ALPHA.value();
    }

    private final ConfigProperty<Double> STABILITY_EWMA_ALPHA;

    public double getStabilityEwmaAlpha() {
        return STABILITY_EWMA_ALPHA.value();
    }

    private final ConfigProperty<Integer> TIMING_VERIFICATION_MIN_DELAY_MS;

    public int getTimingVerificationMinDelayMs() {
        return TIMING_VERIFICATION_MIN_DELAY_MS.value();
    }

    private final ConfigProperty<Double> SATISFIES_SIMILARITY_THRESHOLD;

    public double getSatisfiesSimilarityThreshold() {
        return SATISFIES_SIMILARITY_THRESHOLD.value();
    }

    private final ConfigProperty<Integer> ANCESTRY_WINDOW_SIZE;

    public int getAncestryWindowSize() {
        return ANCESTRY_WINDOW_SIZE.value();
    }

    private final ConfigProperty<Integer> SATISFIES_STALE_DAYS;

    public int getSatisfiesStaleDays() {
        return SATISFIES_STALE_DAYS.value();
    }

    private final ConfigProperty<Double> STABILITY_PENALTY_THRESHOLD;

    public double getStabilityPenaltyThreshold() {
        return STABILITY_PENALTY_THRESHOLD.value();
    }

    private final ConfigProperty<String> HEALTH_REPORT_OUTPUT_PATH;

    public String getHealthReportOutputPath() {
        return HEALTH_REPORT_OUTPUT_PATH.value();
    }

    private final ConfigProperty<Integer> HEALTH_WARNING_THRESHOLD;

    public int getHealthWarningThreshold() {
        return HEALTH_WARNING_THRESHOLD.value();
    }

    private final ConfigProperty<Integer> HEALTH_CRITICAL_THRESHOLD;

    public int getHealthCriticalThreshold() {
        return HEALTH_CRITICAL_THRESHOLD.value();
    }
}