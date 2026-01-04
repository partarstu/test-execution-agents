/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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

import org.tarik.ta.core.AgentConfig;

public class UiTestAgentConfig extends AgentConfig {

    private static final ConfigProperty<String> SCREENSHOTS_SAVE_FOLDER = loadProperty("screenshots.save.folder",
            "SCREENSHOTS_SAVE_FOLDER", "screens", s -> s, false);

    public static String getScreenshotsSaveFolder() {
        return SCREENSHOTS_SAVE_FOLDER.value();
    }

    private static final ConfigProperty<Boolean> UNATTENDED_MODE = loadProperty("unattended.mode", "UNATTENDED_MODE",
            "false", Boolean::parseBoolean, false);

    public static boolean isUnattendedMode() {
        return UNATTENDED_MODE.value();
    }

    // -----------------------------------------------------
    // Video Recording
    private static final ConfigProperty<Boolean> SCREEN_RECORDING_ENABLED = loadProperty("screen.recording.active",
            "SCREEN_RECORDING_ENABLED", "false", Boolean::parseBoolean, false);
    private static final ConfigProperty<String> SCREEN_RECORDING_FOLDER = loadProperty("screen.recording.output.dir",
            "SCREEN_RECORDING_FOLDER", "videos", s -> s, false);
    private static final ConfigProperty<Integer> VIDEO_BITRATE = loadPropertyAsInteger("recording.bit.rate",
            "VIDEO_BITRATE", "2000000", false);
    private static final ConfigProperty<String> SCREEN_RECORDING_FORMAT = loadProperty("recording.file.format",
            "SCREEN_RECORDING_FORMAT", "mp4", s -> s, false);
    private static final ConfigProperty<Integer> SCREEN_RECORDING_FRAME_RATE = loadPropertyAsInteger("recording.fps",
            "SCREEN_RECORDING_FRAME_RATE", "10", false);

    public static boolean getScreenRecordingEnabled() {
        return SCREEN_RECORDING_ENABLED.value();
    }

    public static String getScreenRecordingFolder() {
        return SCREEN_RECORDING_FOLDER.value();
    }

    public static int getRecordingBitrate() {
        return VIDEO_BITRATE.value();
    }

    public static String getRecordingFormat() {
        return SCREEN_RECORDING_FORMAT.value();
    }

    public static int getRecordingFrameRate() {
        if (SCREEN_RECORDING_FRAME_RATE.value() <= 0) {
            throw new IllegalArgumentException("Video recording frame rate must be a positive integer.");
        }
        return SCREEN_RECORDING_FRAME_RATE.value();
    }

    // -----------------------------------------------------
    // Element Config
    private static final ConfigProperty<String> ELEMENT_BOUNDING_BOX_COLOR_NAME = getRequiredProperty(
            "element.bounding.box.color", "BOUNDING_BOX_COLOR", false);

    public static String getElementBoundingBoxColorName() {
        return ELEMENT_BOUNDING_BOX_COLOR_NAME.value();
    }

    private static final ConfigProperty<Double> ELEMENT_RETRIEVAL_MIN_TARGET_SCORE = loadPropertyAsDouble(
            "element.retrieval.min.target.score", "ELEMENT_RETRIEVAL_MIN_TARGET_SCORE", "0.85", false);

    public static double getElementRetrievalMinTargetScore() {
        return ELEMENT_RETRIEVAL_MIN_TARGET_SCORE.value();
    }

    private static final ConfigProperty<Double> ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE = loadPropertyAsDouble(
            "element.retrieval.min.general.score", "ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE", "0.4", false);

    public static double getElementRetrievalMinGeneralScore() {
        return ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE.value();
    }

    private static final ConfigProperty<Double> ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE = loadPropertyAsDouble(
            "element.retrieval.min.page.relevance.score", "ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE", "0.5", false);

    public static double getElementRetrievalMinPageRelevanceScore() {
        return ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE.value();
    }

    private static final ConfigProperty<Double> ELEMENT_LOCATOR_VISUAL_SIMILARITY_THRESHOLD = loadPropertyAsDouble(
            "element.locator.visual.similarity.threshold", "VISUAL_SIMILARITY_THRESHOLD", "0.8", false);

    public static double getElementLocatorVisualSimilarityThreshold() {
        return ELEMENT_LOCATOR_VISUAL_SIMILARITY_THRESHOLD.value();
    }

    private static final ConfigProperty<Integer> ELEMENT_LOCATOR_TOP_VISUAL_MATCHES = loadPropertyAsInteger(
            "element.locator.top.visual.matches",
            "TOP_VISUAL_MATCHES_TO_FIND",
            "3", false);

    public static int getElementLocatorTopVisualMatches() {
        return ELEMENT_LOCATOR_TOP_VISUAL_MATCHES.value();
    }

    private static final ConfigProperty<Double> FOUND_MATCHES_DIMENSION_DEVIATION_RATIO = loadPropertyAsDouble(
            "element.locator.found.matches.dimension.deviation.ratio", "FOUND_MATCHES_DIMENSION_DEVIATION_RATIO", "0.3",
            false);

    public static double getFoundMatchesDimensionDeviationRatio() {
        return FOUND_MATCHES_DIMENSION_DEVIATION_RATIO.value();
    }

    private static final ConfigProperty<Integer> ELEMENT_LOCATOR_VISUAL_GROUNDING_VOTE_COUNT = loadPropertyAsInteger(
            "element.locator.visual.grounding.model.vote.count", "VISUAL_GROUNDING_MODEL_VOTE_COUNT", "5", false);

    public static int getElementLocatorVisualGroundingVoteCount() {
        return ELEMENT_LOCATOR_VISUAL_GROUNDING_VOTE_COUNT.value();
    }

    private static final ConfigProperty<Integer> ELEMENT_LOCATOR_VALIDATION_VOTE_COUNT = loadPropertyAsInteger(
            "element.locator.validation.model.vote.count", "VALIDATION_MODEL_VOTE_COUNT", "3", false);

    public static int getElementLocatorValidationVoteCount() {
        return ELEMENT_LOCATOR_VALIDATION_VOTE_COUNT.value();
    }

    private static final ConfigProperty<Double> BBOX_CLUSTERING_MIN_INTERSECTION_RATIO = loadPropertyAsDouble(
            "element.locator.bbox.clustering.min.intersection.ratio", "BBOX_CLUSTERING_MIN_INTERSECTION_RATIO", "0.7",
            false);

    public static double getBboxClusteringMinIntersectionRatio() {
        return BBOX_CLUSTERING_MIN_INTERSECTION_RATIO.value();
    }

    private static final ConfigProperty<Integer> ELEMENT_LOCATOR_ZOOM_SCALE_FACTOR = loadPropertyAsInteger(
            "element.locator.zoom.scale.factor", "ELEMENT_LOCATOR_ZOOM_SCALE_FACTOR", "2", false);

    public static int getElementLocatorZoomScaleFactor() {
        return ELEMENT_LOCATOR_ZOOM_SCALE_FACTOR.value();
    }

    private static final ConfigProperty<Integer> BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS = loadPropertyAsInteger(
            "bbox.screenshot.longest.allowed.dimension.pixels", "BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS",
            "1568", false);

    public static int getBboxScreenshotLongestAllowedDimensionPixels() {
        return BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS.value();
    }

    private static final ConfigProperty<Double> BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS = loadPropertyAsDouble(
            "bbox.screenshot.max.size.megapixels", "BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS", "1.15", false);

    public static double getBboxScreenshotMaxSizeMegapixels() {
        return BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS.value();
    }

    private static final ConfigProperty<Boolean> BOUNDING_BOX_ALREADY_NORMALIZED = loadProperty(
            "bounding.box.already.normalized", "BOUNDING_BOX_ALREADY_NORMALIZED", "false", Boolean::parseBoolean,
            false);

    public static boolean isBoundingBoxAlreadyNormalized() {
        return BOUNDING_BOX_ALREADY_NORMALIZED.value();
    }

    private static final ConfigProperty<Boolean> ALGORITHMIC_SEARCH_ENABLED = loadProperty(
            "element.locator.algorithmic.search.enabled", "ALGORITHMIC_SEARCH_ENABLED", "true", Boolean::parseBoolean,
            false);

    public static boolean isAlgorithmicSearchEnabled() {
        return ALGORITHMIC_SEARCH_ENABLED.value();
    }

    // -----------------------------------------------------
    // User UI dialogs
    private static final ConfigProperty<Integer> DIALOG_DEFAULT_HORIZONTAL_GAP = loadPropertyAsInteger(
            "dialog.default.horizontal.gap", "DIALOG_DEFAULT_HORIZONTAL_GAP", "10", false);

    public static int getDialogDefaultHorizontalGap() {
        return DIALOG_DEFAULT_HORIZONTAL_GAP.value();
    }

    private static final ConfigProperty<Integer> DIALOG_DEFAULT_VERTICAL_GAP = loadPropertyAsInteger(
            "dialog.default.vertical.gap", "DIALOG_DEFAULT_VERTICAL_GAP", "10", false);

    public static int getDialogDefaultVerticalGap() {
        return DIALOG_DEFAULT_VERTICAL_GAP.value();
    }

    private static final ConfigProperty<String> DIALOG_DEFAULT_FONT_TYPE = getProperty("dialog.default.font.type",
            "DIALOG_DEFAULT_FONT_TYPE", "Dialog", s -> s, false);

    public static String getDialogDefaultFontType() {
        return DIALOG_DEFAULT_FONT_TYPE.value();
    }

    private static final ConfigProperty<Integer> DIALOG_DEFAULT_FONT_SIZE = loadPropertyAsInteger(
            "dialog.default.font.size", "DIALOG_DEFAULT_FONT_SIZE", "13", false);

    public static int getDialogDefaultFontSize() {
        return DIALOG_DEFAULT_FONT_SIZE.value();
    }

    private static final ConfigProperty<Boolean> DIALOG_HOVER_AS_CLICK = loadProperty("dialog.hover.as.click",
            "DIALOG_HOVER_AS_CLICK", "false", Boolean::parseBoolean, false);

    public static boolean isDialogHoverAsClick() {
        return DIALOG_HOVER_AS_CLICK.value();
    }

    // Prefetching
    private static final ConfigProperty<Boolean> PREFETCHING_ENABLED = loadProperty("prefetching.enabled",
            "PREFETCHING_ENABLED", "true", Boolean::parseBoolean, false);

    public static boolean isElementLocationPrefetchingEnabled() {
        return PREFETCHING_ENABLED.value() && isUnattendedMode();
    }

    // UI Element Description Agent
    private static final ConfigProperty<String> UI_ELEMENT_DESCRIPTION_AGENT_MODEL_NAME = loadProperty(
            "ui.element.description.agent.model.name", "UI_ELEMENT_DESCRIPTION_AGENT_MODEL_NAME", "gemini-3-flash-preview",
            s -> s, false);

    public static String getUiElementDescriptionAgentModelName() {
        return UI_ELEMENT_DESCRIPTION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> UI_ELEMENT_DESCRIPTION_AGENT_MODEL_PROVIDER = getProperty(
            "ui.element.description.agent.model.provider", "UI_ELEMENT_DESCRIPTION_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getUiElementDescriptionAgentModelProvider() {
        return UI_ELEMENT_DESCRIPTION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> UI_ELEMENT_DESCRIPTION_AGENT_PROMPT_VERSION = loadProperty(
            "ui.element.description.agent.prompt.version", "UI_ELEMENT_DESCRIPTION_AGENT_PROMPT_VERSION", "v1.0.0",
            s -> s, false);

    public static String getUiElementDescriptionAgentPromptVersion() {
        return UI_ELEMENT_DESCRIPTION_AGENT_PROMPT_VERSION.value();
    }

    // UI State Check Agent
    private static final ConfigProperty<String> UI_STATE_CHECK_AGENT_MODEL_NAME = loadProperty(
            "ui.state.check.agent.model.name", "UI_STATE_CHECK_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);

    public static String getUiStateCheckAgentModelName() {
        return UI_STATE_CHECK_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> UI_STATE_CHECK_AGENT_MODEL_PROVIDER = getProperty(
            "ui.state.check.agent.model.provider", "UI_STATE_CHECK_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getUiStateCheckAgentModelProvider() {
        return UI_STATE_CHECK_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> UI_STATE_CHECK_AGENT_PROMPT_VERSION = loadProperty(
            "ui.state.check.agent.prompt.version", "UI_STATE_CHECK_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);

    public static String getUiStateCheckAgentPromptVersion() {
        return UI_STATE_CHECK_AGENT_PROMPT_VERSION.value();
    }

    // Element Bounding Box Agent
    private static final ConfigProperty<String> ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME = loadProperty(
            "element.bounding.box.agent.model.name", "ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME", "gemini-3-flash-preview",
            s -> s, false);

    public static String getElementBoundingBoxAgentModelName() {
        return ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER = getProperty(
            "element.bounding.box.agent.model.provider", "ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getElementBoundingBoxAgentModelProvider() {
        return ELEMENT_BOUNDING_BOX_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION = loadProperty(
            "element.bounding.box.agent.prompt.version", "ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION", "v1.0.0", s -> s,
            false);

    public static String getElementBoundingBoxAgentPromptVersion() {
        return ELEMENT_BOUNDING_BOX_AGENT_PROMPT_VERSION.value();
    }

    // Element Selection Agent
    private static final ConfigProperty<String> ELEMENT_SELECTION_AGENT_MODEL_NAME = loadProperty(
            "element.selection.agent.model.name", "ELEMENT_SELECTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s,
            false);

    public static String getElementSelectionAgentModelName() {
        return ELEMENT_SELECTION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> ELEMENT_SELECTION_AGENT_MODEL_PROVIDER = getProperty(
            "element.selection.agent.model.provider", "ELEMENT_SELECTION_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getElementSelectionAgentModelProvider() {
        return ELEMENT_SELECTION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> ELEMENT_SELECTION_AGENT_PROMPT_VERSION = loadProperty(
            "element.selection.agent.prompt.version", "ELEMENT_SELECTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s,
            false);

    public static String getElementSelectionAgentPromptVersion() {
        return ELEMENT_SELECTION_AGENT_PROMPT_VERSION.value();
    }

    // Page Description Agent
    private static final ConfigProperty<String> PAGE_DESCRIPTION_AGENT_MODEL_NAME = loadProperty(
            "page.description.agent.model.name", "PAGE_DESCRIPTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s,
            false);

    public static String getPageDescriptionAgentModelName() {
        return PAGE_DESCRIPTION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> PAGE_DESCRIPTION_AGENT_MODEL_PROVIDER = getProperty(
            "page.description.agent.model.provider", "PAGE_DESCRIPTION_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getPageDescriptionAgentModelProvider() {
        return PAGE_DESCRIPTION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> PAGE_DESCRIPTION_AGENT_PROMPT_VERSION = loadProperty(
            "page.description.agent.prompt.version", "PAGE_DESCRIPTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);

    public static String getPageDescriptionAgentPromptVersion() {
        return PAGE_DESCRIPTION_AGENT_PROMPT_VERSION.value();
    }
}