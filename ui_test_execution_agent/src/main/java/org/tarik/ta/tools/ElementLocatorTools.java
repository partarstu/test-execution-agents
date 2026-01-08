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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.UiElementBoundingBoxAgent;
import org.tarik.ta.agents.DbUiElementSelectionAgent;
import org.tarik.ta.agents.BestUiElementMatchSelectionAgent;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.dto.*;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.exceptions.ElementLocationException.ElementLocationStatus;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.UiElementRetriever.RetrievedUiElementItem;
import org.tarik.ta.rag.model.UiElement;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static dev.langchain4j.service.AiServices.builder;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import static java.time.Duration.between;
import static java.util.Collections.max;
import static java.util.Comparator.comparingDouble;
import static java.util.Optional.*;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.stream.Collectors.*;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static org.tarik.ta.UiTestAgentConfig.*;
import static org.tarik.ta.core.error.ErrorCategory.*;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;
import static org.tarik.ta.exceptions.ElementLocationException.ElementLocationStatus.NO_ELEMENTS_FOUND_IN_DB;
import static org.tarik.ta.exceptions.ElementLocationException.ElementLocationStatus.SIMILAR_ELEMENTS_IN_DB_BUT_SCORE_TOO_LOW;
import static org.tarik.ta.exceptions.ElementLocationException.ElementLocationStatus.MODEL_COULD_NOT_SELECT_FROM_DB_CANDIDATES;
import static org.tarik.ta.core.model.ModelFactory.getModel;
import static org.tarik.ta.utils.BoundingBoxUtil.*;
import static org.tarik.ta.utils.UiCommonUtils.*;
import static org.tarik.ta.core.utils.CommonUtils.*;
import static org.tarik.ta.utils.ImageMatchingUtil.findMatchingRegionsWithORB;
import static org.tarik.ta.utils.ImageMatchingUtil.findMatchingRegionsWithTemplateMatching;
import static org.tarik.ta.utils.ImageUtils.*;

public class ElementLocatorTools extends UiAbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ElementLocatorTools.class);
    private static final double MIN_TARGET_RETRIEVAL_SCORE = UiTestAgentConfig.getElementRetrievalMinTargetScore();
    private static final double MIN_GENERAL_RETRIEVAL_SCORE = UiTestAgentConfig.getElementRetrievalMinGeneralScore();
    private static final String BOUNDING_BOX_COLOR_NAME = UiTestAgentConfig.getElementBoundingBoxColorName();
    private static final Color BOUNDING_BOX_COLOR = getColorByName(BOUNDING_BOX_COLOR_NAME);
    private static final int TOP_N_ELEMENTS_TO_RETRIEVE = AgentConfig.getRetrieverTopN();
    private static final int VISUAL_GROUNDING_MODEL_VOTE_COUNT = UiTestAgentConfig.getElementLocatorVisualGroundingVoteCount();
    private static final int VALIDATION_MODEL_VOTE_COUNT = UiTestAgentConfig.getElementLocatorValidationVoteCount();
    private static final double BBOX_CLUSTERING_MIN_INTERSECTION_RATIO = UiTestAgentConfig.getBboxClusteringMinIntersectionRatio();
    private static final double ZOOM_IN_EXTENSION_RATIO_PROPORTIONAL_TO_ELEMENT = 15.0;
    private static final int BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS =
            UiTestAgentConfig.getBboxScreenshotLongestAllowedDimensionPixels();
    private static final double BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS = UiTestAgentConfig.getBboxScreenshotMaxSizeMegapixels();
    private static final boolean DEBUG_MODE = AgentConfig.isDebugMode();

    private final UiElementRetriever elementRetriever;
    private final UiElementBoundingBoxAgent uiElementBoundingBoxAgent;
    private final BestUiElementMatchSelectionAgent bestUiElementMatchSelectionAgent;
    private final DbUiElementSelectionAgent dbUiElementSelectionAgent;

    public ElementLocatorTools() {
        super();
        this.elementRetriever = RetrieverFactory.getUiElementRetriever();
        this.uiElementBoundingBoxAgent = createElementBoundingBoxAgent();
        this.bestUiElementMatchSelectionAgent = createElementSelectionAgent();
        this.dbUiElementSelectionAgent = createDbElementSelectionAgent();
    }

    public ElementLocatorTools(UiStateCheckAgent uiStateCheckAgent) {
        super(uiStateCheckAgent);
        this.elementRetriever = RetrieverFactory.getUiElementRetriever();
        this.uiElementBoundingBoxAgent = createElementBoundingBoxAgent();
        this.bestUiElementMatchSelectionAgent = createElementSelectionAgent();
        this.dbUiElementSelectionAgent = createDbElementSelectionAgent();
    }

    @Tool(value = "Locates the UI element on the screen based on its description and returns its coordinates.")
    public ElementLocation locateElementOnTheScreen(
            @P("A very brief description of UI element to locate. If any related to this element data is provided, don't use " +
                    "that data as a part of its description")
            String elementDescription,
            @P(value = "Any data related to this element or the action involving this element.", required = false)
            String elementSpecificData) {
        if (isBlank(elementDescription)) {
            throw new ToolExecutionException("Element description cannot be empty", TRANSIENT_TOOL_ERROR);
        }
        try {
            var retrievedElements = elementRetriever.retrieveUiElements(elementDescription, TOP_N_ELEMENTS_TO_RETRIEVE,
                    MIN_GENERAL_RETRIEVAL_SCORE);
            var matchingByDescriptionUiElements = retrievedElements.stream()
                    .filter(retrievedUiElementItem -> retrievedUiElementItem
                            .mainScore() >= MIN_TARGET_RETRIEVAL_SCORE)
                    .sorted(comparingDouble(RetrievedUiElementItem::mainScore).reversed())
                    .map(RetrievedUiElementItem::element)
                    .toList();
            if (matchingByDescriptionUiElements.isEmpty() && !retrievedElements.isEmpty()) {
                throw processNoElementsFoundInDbWithSimilarCandidatesPresentCase(elementDescription, retrievedElements);
            } else if (matchingByDescriptionUiElements.isEmpty()) {
                throw processNoElementsFoundInDbCase(elementDescription);
            } else {
                LOG.info("Found {} UI element(s) in DB corresponding to the description of '{}'. Element names: {}",
                        matchingByDescriptionUiElements.size(), elementDescription,
                        matchingByDescriptionUiElements.stream().map(UiElement::name).toList());
                UiElement bestMatchingElement;
                if (matchingByDescriptionUiElements.size() > 1) {
                    LOG.info("{} UI elements found in vector DB which semantically match the description '{}'. " +
                                    "Using model to select the best matching element based on current screenshot.",
                            matchingByDescriptionUiElements.size(), elementDescription);
                    bestMatchingElement = selectBestMatchingDbElement(matchingByDescriptionUiElements, elementDescription,
                            elementSpecificData)
                            .orElseThrow(() -> processNoMatchingDbElementCandidateIdentifiedByModel(
                                    elementDescription, retrievedElements));
                } else {
                    bestMatchingElement = matchingByDescriptionUiElements.getFirst();
                }

                return findElementAndProcessLocationResult(() -> getFinalElementLocation(bestMatchingElement, elementSpecificData),
                        elementDescription);
            }
        } catch (Exception e) {
            throw rethrowAsToolException(e, "locating a UI element on the screen");
        }
    }

    private UiElementBoundingBoxAgent createElementBoundingBoxAgent() {
        var model = getModel(getElementBoundingBoxAgentModelName(), getElementBoundingBoxAgentModelProvider());
        var prompt = loadSystemPrompt("element_locator/bounding_box", getElementBoundingBoxAgentPromptVersion(),
                "element_bounding_box_prompt.txt");
        return builder(UiElementBoundingBoxAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new BoundingBoxes(List.of()))
                .build();
    }

    private BestUiElementMatchSelectionAgent createElementSelectionAgent() {
        var model = getModel(getUiElementVisualMatchAgentModelName(), getUiElementVisualMatchAgentModelProvider());
        var prompt = loadSystemPrompt("element_locator/best_ui_match_selection", getElementSelectionAgentPromptVersion(),
                "find_best_matching_ui_element_id.txt");
        return builder(BestUiElementMatchSelectionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new BestUiElementVisualMatchResult(false, "", ""))
                .build();
    }

    private String getElementBoundingBoxUserMessage(UiElement uiElement, String elementTestData) {
        if (isNotBlank(elementTestData) && uiElement.isDataDependent()) {
            return """
                    The target element:
                    "%s. %s %s"
                    
                    This element is data-dependent.
                    Available specific data for this element: "%s"
                    """.formatted(uiElement.name(), uiElement.description(), uiElement.locationDetails(),
                    elementTestData);
        } else {
            return """
                    The target element:
                    "%s. %s %s"
                    """.formatted(uiElement.name(), uiElement.description(), uiElement.locationDetails());
        }
    }

    private String getBestElementVisualMatchUserMessage(UiElement uiElement, String elementTestData, List<String> boundingBoxIds) {
        String boundingBoxIdsString = "Bounding box IDs: %s.".formatted(String.join(", ", boundingBoxIds));
        if (isNotBlank(elementTestData) && uiElement.isDataDependent()) {
            return """
                    The target element:
                    "%s. %s %s"
                    
                    This element is data-dependent.
                    Available specific data for this element: "%s"
                    
                    %s
                    """.formatted(uiElement.name(), uiElement.description(), uiElement.locationDetails(),
                    elementTestData, boundingBoxIdsString);
        } else {
            return """
                    The target element: "%s. %s %s"
                    
                    %s
                    """.formatted(uiElement.name(), uiElement.description(), uiElement.locationDetails(),
                    boundingBoxIdsString);
        }
    }

    private DbUiElementSelectionAgent createDbElementSelectionAgent() {
        var model = getModel(getDbElementCandidateSelectionAgentModelName(), getDbElementCandidateSelectionAgentModelProvider());
        var prompt = loadSystemPrompt("element_locator/db_element_selector",
                getDbElementCandidateSelectionAgentPromptVersion(), "select_best_db_search_result_prompt.txt");
        return builder(DbUiElementSelectionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new DbUiElementSelectionResult(false, "", ""))
                .build();
    }


    private Optional<UiElement> selectBestMatchingDbElement(List<UiElement> candidates, String elementDescription,
                                                            String elementSpecificData) {
        if (candidates.isEmpty()) {
            return empty();
        }
        if (candidates.size() == 1) {
            return of(candidates.getFirst());
        }

        BufferedImage screenshot = captureScreen();
        Map<String, UiElement> candidatesById = range(0, candidates.size())
                .boxed()
                .collect(toMap(index -> "element_" + index, candidates::get));
        var userMessage = getDbElementBestMatchSelectionUserMessage(candidatesById, elementDescription, elementSpecificData);
        try {
            var result = dbUiElementSelectionAgent.executeAndGetResult(() ->
                            dbUiElementSelectionAgent.selectBestElementFromCandidates(userMessage, singleImageContent(screenshot)))
                    .getResultPayload();
            if (result != null && result.success() && isNotBlank(result.selectedElementId())) {
                String selectedId = result.selectedElementId().toLowerCase().trim();
                UiElement selectedElement = candidatesById.get(selectedId);
                if (selectedElement != null) {
                    LOG.info("Model selected element '{}' from {} candidates.", selectedElement.name(), candidates.size());
                    return of(selectedElement);
                } else {
                    LOG.warn("Model returned unknown element ID '{}'. Available IDs: {}. Falling back to first candidate.",
                            selectedId, candidatesById.keySet());
                }
            } else {
                LOG.warn("Model could not select a matching element from candidates. Reasoning: {}. Falling back to first candidate.",
                        result != null ? result.message() : "No result");
                return empty();
            }
        } catch (Exception e) {
            throw rethrowAsToolException(e, "selecting the best UI element fetched from DB based on the screen state");
        }

        return empty();
    }

    private String getDbElementBestMatchSelectionUserMessage(Map<String, UiElement> candidatesById, String elementDescription,
                                                             String elementSpecificData) {
        String candidatesString = candidatesById.entrySet().stream()
                .map((candidateById) -> {
                    var candidate = candidateById.getValue();
                    return "  - Candidate ID: %s, Name: '%s', Description: '%s', Location Details: '%s', Parent Element Info: '%s'"
                            .formatted(candidateById.getKey(), candidate.name(), candidate.description(), candidate.locationDetails(),
                                    candidate.parentElementSummary());
                })
                .collect(joining("\n"));

        return """
                The target element description: '%s'.
                
                Available data related to this element: '%s'
                
                Candidates:
                %s
                """.formatted(elementDescription, elementSpecificData, candidatesString);
    }

    private ElementLocationException processNoElementsFoundInDbWithSimilarCandidatesPresentCase(
            String elementDescription, List<RetrievedUiElementItem> retrievedElements) {
        var retrievedElementsString = retrievedElements.stream()
                .map(el -> "%s --> %.1f".formatted(el.element().name(), el.mainScore()))
                .collect(joining(", "));
        var failureReason = String.format("No UI elements found in vector DB which semantically match the description '%s' with the " +
                        "similarity mainScore > %.1f. The most similar element names by similarity mainScore are: %s",
                elementDescription, MIN_TARGET_RETRIEVAL_SCORE, retrievedElementsString);
        LOG.info(failureReason);
        var message = ("No UI elements found in DB matching the description '%s'. Similar candidates exist but their " +
                "similarity scores are below threshold.").formatted(elementDescription);
        return new ElementLocationException(message, SIMILAR_ELEMENTS_IN_DB_BUT_SCORE_TOO_LOW);
    }

    private ElementLocationException processNoElementsFoundInDbCase(String elementDescription) {
        var failureReason = String.format("No UI elements found in vector DB which semantically match the description '%s' with the " +
                "similarity mainScore > %.1f.", elementDescription, MIN_GENERAL_RETRIEVAL_SCORE);
        LOG.info(failureReason);
        var message = "No UI elements found in DB matching the description '%s'.".formatted(elementDescription);
        return new ElementLocationException(message, NO_ELEMENTS_FOUND_IN_DB);
    }

    private ElementLocationException processNoMatchingDbElementCandidateIdentifiedByModel(
            String elementDescription, List<RetrievedUiElementItem> retrievedElements) {
        var candidateElementsString = retrievedElements.stream()
                .map(el -> "%s (score: %.2f)".formatted(el.element().name(), el.mainScore()))
                .collect(joining(", "));
        var failureReason = ("Model could not select a matching element from DB candidates for the description '%s'. Candidates " +
                "considered: [%s]").formatted(elementDescription, candidateElementsString);
        LOG.info(failureReason);
        var message = ("Model could not select a matching element for '%s' from multiple DB candidates. " +
                "None of the candidates appear to match the current screen state.").formatted(elementDescription);
        return new ElementLocationException(message, MODEL_COULD_NOT_SELECT_FROM_DB_CANDIDATES);
    }

    private ElementLocation findElementAndProcessLocationResult(Supplier<UiElementLocationInternalResult> resultSupplier,
                                                                String elementDescription) {
        var locationResult = resultSupplier.get();
        return ofNullable(locationResult.boundingBox())
                .map(_ -> processSuccessfulMatchCase(locationResult, elementDescription))
                .orElseThrow(() -> processNoVisualMatchCase(locationResult, elementDescription));
    }

    private ElementLocation processSuccessfulMatchCase(UiElementLocationInternalResult locationResult, String elementDescription) {
        var boundingBox = locationResult.boundingBox();
        LOG.info("The best visual match for the description '{}' has been located at: {}", elementDescription, boundingBox);
        var scaledBoundingBox = getScaledBoundingBox(boundingBox);
        var center = new Point((int) scaledBoundingBox.getCenterX(), (int) scaledBoundingBox.getCenterY());
        var bbox = new BoundingBox(scaledBoundingBox.x, scaledBoundingBox.y, scaledBoundingBox.x + scaledBoundingBox.width,
                scaledBoundingBox.y + scaledBoundingBox.height);
        return new ElementLocation(center.x, center.y, bbox);
    }

    private ElementLocationException processNoVisualMatchCase(UiElementLocationInternalResult locationResult, String elementDescription) {
        String rootCause;
        ElementLocationStatus status;

        if (!locationResult.algorithmicMatchFound() && !locationResult.visualGroundingMatchFound()) {
            rootCause = "Neither visual grounding nor algorithmic matching provided any results";
            status = ElementLocationStatus.ELEMENT_NOT_FOUND_ON_SCREEN_VISUAL_AND_ALGORITHMIC_FAILED;
        } else {
            if (locationResult.algorithmicMatchFound() && locationResult.visualGroundingMatchFound()) {
                rootCause = "Both visual grounding and algorithmic matching provided results, but the validation model decided that none " +
                        "of them are valid";
            } else if (locationResult.algorithmicMatchFound()) {
                rootCause = "Only algorithmic matching provided results, but the validation model decided that none of them are valid";
            } else {
                rootCause = "Only visual grounding provided results, but the validation model decided that none of them are valid";
            }
            status = ElementLocationStatus.ELEMENT_NOT_FOUND_ON_SCREEN_VALIDATION_FAILED;
        }

        var failureReason = String.format("Element with description '%s' was not found on the screen. %s.", elementDescription, rootCause);
        return new ElementLocationException(failureReason, status);
    }

    private UiElementLocationInternalResult getFinalElementLocation(UiElement elementRetrievedFromMemory,
                                                                    String elementTestData) {
        var elementScreenshot = elementRetrievedFromMemory.screenshot().toBufferedImage();
        BufferedImage wholeScreenshot = captureScreen();
        if (elementRetrievedFromMemory.zoomInRequired()) {
            LOG.info("Zoom-in is needed for element '{}'. Performing initial wide-area search.",
                    elementRetrievedFromMemory.name());
            List<Rectangle> initialCandidates = identifyBoundingBoxesUsingVision(elementRetrievedFromMemory,
                    wholeScreenshot, elementTestData);
            if (initialCandidates.isEmpty()) {
                return new UiElementLocationInternalResult(false, false, null,
                        elementRetrievedFromMemory,
                        wholeScreenshot);
            }

            var zoomInOriginalRegion = getCommonArea(initialCandidates);
            var zoomInExtendedRegion = extendZoomInRegion(zoomInOriginalRegion, elementScreenshot,
                    wholeScreenshot);
            var zoomInImage = cloneImage(
                    wholeScreenshot.getSubimage(zoomInExtendedRegion.x, zoomInExtendedRegion.y,
                            zoomInExtendedRegion.width, zoomInExtendedRegion.height));
            var scaleFactor = min(wholeScreenshot.getWidth() / ((double) zoomInImage.getWidth()),
                    UiTestAgentConfig.getElementLocatorZoomScaleFactor());
            var zoomedInScreenshot = getScaledUpImage(zoomInImage, scaleFactor);
            var elementLocationResult = getUiElementLocationResult(elementRetrievedFromMemory,
                    elementTestData,
                    zoomedInScreenshot, elementScreenshot, false);
            if (elementLocationResult.boundingBox() != null) {
                var finalBox = getActualBox(elementLocationResult.boundingBox(), zoomInExtendedRegion,
                        scaleFactor);
                return new UiElementLocationInternalResult(
                        elementLocationResult.algorithmicMatchFound(),
                        elementLocationResult.visualGroundingMatchFound(), finalBox,
                        elementLocationResult.elementUsedForLocation(),
                        wholeScreenshot);
            } else {
                return elementLocationResult;
            }
        } else {
            boolean useAlgorithmicSearch = UiTestAgentConfig.isAlgorithmicSearchEnabled()
                    && !(elementRetrievedFromMemory.isDataDependent());
            return getUiElementLocationResult(elementRetrievedFromMemory, elementTestData, wholeScreenshot,
                    elementScreenshot,
                    useAlgorithmicSearch);
        }
    }

    @NotNull
    private Rectangle getActualBox(Rectangle scaledBox, Rectangle zoomInExtendedRegion, double scaleFactor) {
        var rescaledBoundingBox = getRescaledBox(scaledBox, scaleFactor);
        var actualX = zoomInExtendedRegion.x + rescaledBoundingBox.x;
        var actualY = zoomInExtendedRegion.y + rescaledBoundingBox.y;
        return new Rectangle(actualX, actualY, rescaledBoundingBox.width, rescaledBoundingBox.height);
    }

    @NotNull
    private Rectangle getRescaledBox(Rectangle scaledBox, double scaleFactor) {
        int rescaledX = (int) (scaledBox.x / scaleFactor);
        int rescaledY = (int) (scaledBox.y / scaleFactor);
        int rescaledWidth = (int) (scaledBox.width / scaleFactor);
        int rescaledHeight = (int) (scaledBox.height / scaleFactor);
        return new Rectangle(rescaledX, rescaledY, rescaledWidth, rescaledHeight);
    }

    @NotNull
    private Rectangle extendZoomInRegion(Rectangle zoomInOriginalRegion, BufferedImage elementScreenshot, BufferedImage wholeScreenshot) {
        var extensionRatio = (elementScreenshot.getWidth() * ZOOM_IN_EXTENSION_RATIO_PROPORTIONAL_TO_ELEMENT) / zoomInOriginalRegion.width;
        if (extensionRatio >= 1.0) {
            int newWidth = (int) (zoomInOriginalRegion.width * extensionRatio);
            int newHeight = (int) (zoomInOriginalRegion.height * extensionRatio);
            newWidth = min(newWidth, wholeScreenshot.getWidth() / 2);
            newHeight = min(newHeight, wholeScreenshot.getHeight() / 2);
            int newLeftX = Math.max(0, zoomInOriginalRegion.x - (newWidth - zoomInOriginalRegion.width) / 2);
            int newTopY = Math.max(0, zoomInOriginalRegion.y - (newHeight - zoomInOriginalRegion.height) / 2);
            int newRightX = min(wholeScreenshot.getWidth() - 1, newLeftX + newWidth);
            int newBottomY = min(wholeScreenshot.getHeight() - 1, newTopY + newHeight);
            zoomInOriginalRegion = new Rectangle(newLeftX, newTopY, newRightX - newLeftX, newBottomY - newTopY);
        }
        return zoomInOriginalRegion;
    }

    private UiElementLocationInternalResult getUiElementLocationResult(UiElement elementRetrievedFromMemory,
                                                                       String elementTestData,
                                                                       BufferedImage wholeScreenshot,
                                                                       BufferedImage elementScreenshot,
                                                                       boolean useAlgorithmicSearch) {
        var identifiedByVisionBoundingBoxes = identifyBoundingBoxesUsingVision(elementRetrievedFromMemory,
                wholeScreenshot, elementTestData);
        List<Rectangle> featureMatchedBoundingBoxes = new LinkedList<>();
        List<Rectangle> templateMatchedBoundingBoxes = new LinkedList<>();
        if (useAlgorithmicSearch) {
            var featureMatchedBoundingBoxesByElementFuture = supplyAsync(
                    () -> findMatchingRegionsWithORB(wholeScreenshot, elementScreenshot));
            var templateMatchedBoundingBoxesByElementFuture = supplyAsync(() -> mergeOverlappingRectangles(
                    findMatchingRegionsWithTemplateMatching(wholeScreenshot, elementScreenshot)));
            featureMatchedBoundingBoxes = featureMatchedBoundingBoxesByElementFuture.join();
            templateMatchedBoundingBoxes = templateMatchedBoundingBoxesByElementFuture.join();
            if (DEBUG_MODE) {
                markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot),
                        getElementToPlot(elementRetrievedFromMemory, featureMatchedBoundingBoxes), "opencv_features_original");
                markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot),
                        getElementToPlot(elementRetrievedFromMemory, templateMatchedBoundingBoxes), "opencv_template_original");
            }
        }

        return getUiElementLocationResult(elementRetrievedFromMemory, elementTestData, wholeScreenshot, identifiedByVisionBoundingBoxes,
                featureMatchedBoundingBoxes, templateMatchedBoundingBoxes);
    }

    private UiElementLocationInternalResult getUiElementLocationResult(UiElement elementRetrievedFromMemory,
                                                                       String elementTestData,
                                                                       BufferedImage wholeScreenshot,
                                                                       List<Rectangle> identifiedByVisionBoundingBoxes,
                                                                       List<Rectangle> featureMatchedBoundingBoxes,
                                                                       List<Rectangle> templateMatchedBoundingBoxes) {
        if (identifiedByVisionBoundingBoxes.isEmpty() && featureMatchedBoundingBoxes.isEmpty() &&
                templateMatchedBoundingBoxes.isEmpty()) {
            return new UiElementLocationInternalResult(false, false, null, elementRetrievedFromMemory,
                    wholeScreenshot);
        } else if (identifiedByVisionBoundingBoxes.isEmpty()) {
            LOG.info("Vision model provided no detection results, proceeding with algorithmic matches");
            return chooseBestAlgorithmicMatch(elementRetrievedFromMemory, elementTestData, wholeScreenshot, featureMatchedBoundingBoxes,
                    templateMatchedBoundingBoxes);
        } else {
            if (featureMatchedBoundingBoxes.isEmpty() && templateMatchedBoundingBoxes.isEmpty()) {
                if (UiTestAgentConfig.isSkipModelSelectionForVisionOnly()) {
                    LOG.info("Skipping model selection for vision-only results as per configuration. Returning the first " +
                            "identified element out of {} elements.", identifiedByVisionBoundingBoxes.size());
                    return new UiElementLocationInternalResult(false, true, identifiedByVisionBoundingBoxes.getFirst(),
                            elementRetrievedFromMemory, wholeScreenshot);
                } else {
                    return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, elementTestData,
                            identifiedByVisionBoundingBoxes, wholeScreenshot, "vision_only", false, true);
                }
            } else {
                return chooseBestCommonMatch(elementRetrievedFromMemory, elementTestData, identifiedByVisionBoundingBoxes, wholeScreenshot,
                        featureMatchedBoundingBoxes, templateMatchedBoundingBoxes)
                        .orElseGet(() -> {
                            var algorithmicIntersections = getIntersections(featureMatchedBoundingBoxes, templateMatchedBoundingBoxes);
                            if (!algorithmicIntersections.isEmpty()) {
                                var boxes = concat(identifiedByVisionBoundingBoxes.stream(), algorithmicIntersections.stream()).toList();
                                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, elementTestData, boxes,
                                        wholeScreenshot, "vision_and_algorithmic_only_intersections", true, true);
                            } else {
                                var boxes = Stream.of(identifiedByVisionBoundingBoxes, featureMatchedBoundingBoxes,
                                                templateMatchedBoundingBoxes)
                                        .flatMap(Collection::stream)
                                        .toList();
                                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, elementTestData, boxes,
                                        wholeScreenshot, "vision_and_algorithmic_regions_separately", true, true);
                            }
                        });
            }
        }
    }

    @NotNull
    private Optional<UiElementLocationInternalResult> chooseBestCommonMatch(UiElement matchingUiElement,
                                                                            String elementTestData,
                                                                            List<Rectangle> identifiedByVisionBoundingBoxes,
                                                                            BufferedImage wholeScreenshot,
                                                                            List<Rectangle> featureRects,
                                                                            List<Rectangle> templateRects) {
        LOG.info("Mapping provided by vision model results to the algorithmic ones");
        var visionAndFeatureIntersections = getIntersections(identifiedByVisionBoundingBoxes, featureRects);
        var visionAndTemplateIntersections = getIntersections(identifiedByVisionBoundingBoxes, templateRects);
        var bestIntersections = getIntersections(visionAndFeatureIntersections, visionAndTemplateIntersections);

        if (!bestIntersections.isEmpty()) {
            if (bestIntersections.size() > 1) {
                LOG.info("Found {} common vision model and algorithmic regions, using them for further refinement by " +
                        "the model.", bestIntersections.size());
                return of(selectBestMatchingUiElementUsingModel(matchingUiElement, elementTestData, bestIntersections, wholeScreenshot,
                        "intersection_all", true, true));
            } else {
                LOG.info("Found a single common vision model and common algorithmic region, returning it");
                return of(new UiElementLocationInternalResult(true, true, bestIntersections.getFirst(),
                        matchingUiElement, wholeScreenshot));
            }
        } else {
            var goodIntersections = Stream
                    .of(visionAndFeatureIntersections.stream(),
                            visionAndTemplateIntersections.stream())
                    .flatMap(Stream::distinct)
                    .toList();
            if (!goodIntersections.isEmpty()) {
                LOG.info("Found {} common regions between vision model and either template or feature matching algorithms, " +
                        "using them for further refinement by the model.", goodIntersections.size());
                return of(selectBestMatchingUiElementUsingModel(matchingUiElement, elementTestData, goodIntersections, wholeScreenshot,
                        "intersection_vision_and_one_algorithm", true, true));
            } else {
                LOG.info("Found no common regions between vision model and either template or feature matching algorithms");
                return empty();
            }
        }
    }

    private UiElementLocationInternalResult chooseBestAlgorithmicMatch(UiElement matchingUiElement,
                                                                       String elementTestData,
                                                                       BufferedImage wholeScreenshot,
                                                                       List<Rectangle> featureMatchedBoxes,
                                                                       List<Rectangle> templateMatchedBoxes) {
        if (templateMatchedBoxes.isEmpty() && featureMatchedBoxes.isEmpty()) {
            LOG.info("No algorithmic matches provided for selection");
            return new UiElementLocationInternalResult(false, false, null, matchingUiElement, wholeScreenshot);
        }

        var algorithmicIntersections = getIntersections(templateMatchedBoxes, featureMatchedBoxes);
        if (!algorithmicIntersections.isEmpty()) {
            LOG.info("Found {} common detection regions between algorithmic matches, using them for further refinement by the model.",
                    algorithmicIntersections.size());
            return selectBestMatchingUiElementUsingModel(matchingUiElement, elementTestData, algorithmicIntersections, wholeScreenshot,
                    "intersection_feature_and_template", true, false);
        } else {
            LOG.info("Found no common detection regions between algorithmic matches, using all originally detected regions for " +
                    "further refinement by the model.");
            var combinedBoundingBoxes = concat(featureMatchedBoxes.stream(), templateMatchedBoxes.stream()).toList();
            return selectBestMatchingUiElementUsingModel(matchingUiElement, elementTestData, combinedBoundingBoxes, wholeScreenshot,
                    "all_feature_and_template", true, false);
        }
    }

    private List<Rectangle> identifyBoundingBoxesUsingVision(UiElement element, BufferedImage wholeScreenshot,
                                                             String elementTestData) {
        var startTime = Instant.now();
        LOG.info("Asking sub-agent to identify bounding boxes for element '{}'.", element.name());
        try {
            var scalingRatio = getScalingRatio(wholeScreenshot);
            var imageToSend = scalingRatio < 1.0 ? scaleImage(wholeScreenshot, scalingRatio) : wholeScreenshot;
            var prompt = getElementBoundingBoxUserMessage(element, elementTestData);
            try (var executor = newVirtualThreadPerTaskExecutor()) {
                List<Callable<List<BoundingBox>>> tasks = range(0, VISUAL_GROUNDING_MODEL_VOTE_COUNT)
                        .mapToObj(_ -> (Callable<List<BoundingBox>>) () -> Objects.requireNonNull(
                                uiElementBoundingBoxAgent.executeAndGetResult(
                                        () -> uiElementBoundingBoxAgent.identifyBoundingBoxes(prompt, singleImageContent(imageToSend))
                                ).getResultPayload()).boundingBoxes())
                        .toList();
                List<Rectangle> allBoundingBoxes = executor.invokeAll(tasks).stream()
                        .map(future -> getFutureResult(future, "getting bounding boxes from vision model"))
                        .flatMap(Optional::stream)
                        .flatMap(Collection::stream)
                        .map(bb -> {
                            Rectangle rectOnScaledImage = bb.getActualBoundingBox(imageToSend.getWidth(), imageToSend.getHeight());
                            return scalingRatio < 1.0 ? getRescaledBox(rectOnScaledImage, scalingRatio) : rectOnScaledImage;
                        })
                        .filter(bb -> bb.width > 0 && bb.height > 0)
                        .toList();

                if (DEBUG_MODE) {
                    var imageWithAllBoxes = cloneImage(wholeScreenshot);
                    allBoundingBoxes.forEach(box -> drawBoundingBox(imageWithAllBoxes, box, BOUNDING_BOX_COLOR));
                    saveImage(imageWithAllBoxes, "vision_identified_boxes_before_clustering");
                }

                if (allBoundingBoxes.isEmpty()) {
                    return List.of();
                }

                if (VISUAL_GROUNDING_MODEL_VOTE_COUNT > 1) {
                    DBSCANClusterer<RectangleAdapter> clusterer =
                            new DBSCANClusterer<>(BBOX_CLUSTERING_MIN_INTERSECTION_RATIO, 0, new IoUDistance());
                    List<RectangleAdapter> points = allBoundingBoxes.stream().map(RectangleAdapter::new).toList();
                    List<Cluster<RectangleAdapter>> clusters = clusterer.cluster(points);
                    var result = clusters.stream()
                            .map(cluster -> {
                                List<Rectangle> clusterBoxes = cluster.getPoints()
                                        .stream()
                                        .map(RectangleAdapter::getRectangle)
                                        .toList();
                                return calculateAverageBoundingBox(clusterBoxes);
                            })
                            .toList();
                    if (DEBUG_MODE) {
                        var imageWithAllBoxes = cloneImage(wholeScreenshot);
                        result.forEach(box -> drawBoundingBox(imageWithAllBoxes, box, BOUNDING_BOX_COLOR));
                        saveImage(imageWithAllBoxes, "vision_identified_boxes_after_clustering");
                    }
                    LOG.info("Model identified {} bounding boxes with {} votes, resulting in {} common regions", allBoundingBoxes.size(),
                            VISUAL_GROUNDING_MODEL_VOTE_COUNT, result.size());
                    return result;
                } else {
                    LOG.info("Model identified {} bounding boxes", allBoundingBoxes.size());
                    return allBoundingBoxes;
                }
            } catch (InterruptedException e) {
                currentThread().interrupt();
                LOG.error("Got interrupted while collecting bounding boxes from the model", e);
                return List.of();
            }
        } finally {
            LOG.info("Finished identifying bounding boxes using vision in {} ms", between(startTime, Instant.now()).toMillis());
        }
    }

    private Rectangle calculateAverageBoundingBox(List<Rectangle> boxes) {
        if (boxes.isEmpty()) {
            return new Rectangle();
        }
        int x = (int) boxes.stream().mapToDouble(Rectangle::getX).average().orElse(0);
        int y = (int) boxes.stream().mapToDouble(Rectangle::getY).average().orElse(0);
        int width = (int) boxes.stream().mapToDouble(Rectangle::getWidth).average().orElse(0);
        int height = (int) boxes.stream().mapToDouble(Rectangle::getHeight).average().orElse(0);
        return new Rectangle(x, y, width, height);
    }

    @NotNull
    private List<Rectangle> getIntersections(List<Rectangle> firstSet, List<Rectangle> secondSet) {
        return firstSet.stream()
                .flatMap(r1 -> secondSet.stream()
                        .map(r1::intersection)
                        .filter(intersection -> !intersection.isEmpty()))
                .toList();
    }

    private UiElementLocationInternalResult selectBestMatchingUiElementUsingModel(UiElement uiElement,
                                                                                  String elementTestData,
                                                                                  List<Rectangle> matchedBoundingBoxes,
                                                                                  BufferedImage screenshot, String matchAlgorithm,
                                                                                  boolean algorithmicSearchDone,
                                                                                  boolean visualGroundingDone) {
        var startTime = Instant.now();
        LOG.info("Selecting the best visual match for UI element '{}'", uiElement.name());
        try {
            var boxedAmount = matchedBoundingBoxes.size();
            checkArgument(boxedAmount > 0, "Amount of bounding boxes to plot must be > 0");
            Map<String, Rectangle> boxesWithIds = getBoxesWithIds(matchedBoundingBoxes);
            var resultingScreenshot = cloneImage(screenshot);
            drawBoundingBoxes(resultingScreenshot, boxesWithIds);
            if (DEBUG_MODE) {
                saveImage(resultingScreenshot, "model_selection_%s".formatted(matchAlgorithm));
            }

            var successfulIdentificationResults = getValidSuccessfulIdentificationResultsFromModelUsingQuorum(
                    uiElement, elementTestData, resultingScreenshot, new ArrayList<>(boxesWithIds.keySet()));
            LOG.info("Model provided {} successful identification results for the element '{}' with {} vote(s).",
                    successfulIdentificationResults.size(), uiElement.name(), VALIDATION_MODEL_VOTE_COUNT);
            if (successfulIdentificationResults.isEmpty()) {
                return new UiElementLocationInternalResult(algorithmicSearchDone, visualGroundingDone, null, uiElement, screenshot);
            }
            var votesById = successfulIdentificationResults.stream()
                    .collect(groupingBy(r -> r.boundingBoxId().toLowerCase(), counting()));
            var maxVotes = max(votesById.values());
            var winners = votesById.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(maxVotes))
                    .map(Map.Entry::getKey)
                    .toList();
            if (winners.size() > 1) {
                LOG.warn("Found multiple winners with {} votes for element '{}': {}. Selecting the one with the largest bounding box area.",
                        maxVotes, uiElement.name(), winners);
                return winners.stream()
                        .map(boxesWithIds::get)
                        .max(comparingDouble(box -> box.getWidth() * box.getHeight()))
                        .map(box -> new UiElementLocationInternalResult(true, true, box, uiElement, screenshot))
                        .orElseGet(() -> new UiElementLocationInternalResult(true, false, null, uiElement, screenshot));
            } else {
                return new UiElementLocationInternalResult(true, true, boxesWithIds.get(winners.getFirst()), uiElement, screenshot);
            }
        } finally {
            LOG.info("Finished selecting best matching UI element using model in {} ms", between(startTime, Instant.now()).toMillis());
        }
    }

    @NotNull
    private List<BestUiElementVisualMatchResult> getValidSuccessfulIdentificationResultsFromModelUsingQuorum(
            @NotNull UiElement uiElement,
            String elementTestData,
            @NotNull BufferedImage resultingScreenshot,
            @NotNull List<String> boxIds) {
        try (var executor = newVirtualThreadPerTaskExecutor()) {
            var prompt = getBestElementVisualMatchUserMessage(uiElement, elementTestData, boxIds);
            var boundingBoxColorName = UiCommonUtils.getColorName(BOUNDING_BOX_COLOR).toLowerCase();

            List<Callable<BestUiElementVisualMatchResult>> tasks = range(0, VALIDATION_MODEL_VOTE_COUNT)
                    .mapToObj(_ -> (Callable<BestUiElementVisualMatchResult>) () -> bestUiElementMatchSelectionAgent.executeAndGetResult(
                            () -> bestUiElementMatchSelectionAgent.selectBestElement(boundingBoxColorName, prompt,
                                    singleImageContent(resultingScreenshot))
                    ).getResultPayload())
                    .toList();
            return executor.invokeAll(tasks).stream()
                    .map(future -> getFutureResult(future, "UI element identification by the model"))
                    .flatMap(Optional::stream)
                    .filter(r -> r.success() && boxIds.contains(r.boundingBoxId()))
                    .toList();
        } catch (InterruptedException e) {
            currentThread().interrupt();
            LOG.error("Got interrupted while collecting UI element identification results by the model", e);
            return List.of();
        }
    }

    private Map<String, Rectangle> getBoxesWithIds(List<Rectangle> boundingBoxes) {
        Map<String, Rectangle> boxesWithIds = new LinkedHashMap<>();
        for (Rectangle box : boundingBoxes) {
            String id;
            do {
                id = randomUUID().toString().substring(0, 4);
            } while (boxesWithIds.containsKey(id));
            boxesWithIds.put(id, box);
        }
        return boxesWithIds;
    }

    @NotNull
    private PlottedUiElement getElementToPlot(UiElement element, List<Rectangle> matchedBoundingBoxes) {
        return new PlottedUiElement(element.name(), element, getBoxesWithIds(matchedBoundingBoxes));
    }

    private void markElementsToPlotWithBoundingBoxes(BufferedImage resultingScreenshot,
                                                     PlottedUiElement elementToPlot,
                                                     String postfix) {
        var elementBoundingBoxesByLabel = elementToPlot.boundingBoxesByIds();
        drawBoundingBoxes(resultingScreenshot, elementBoundingBoxesByLabel);
        if (DEBUG_MODE) {
            saveImage(resultingScreenshot, postfix);
        }
    }

    private record PlottedUiElement(String id, UiElement uiElement, Map<String, Rectangle> boundingBoxesByIds) {
    }

    private record UiElementLocationInternalResult(boolean algorithmicMatchFound, boolean visualGroundingMatchFound,
                                                   Rectangle boundingBox, UiElement elementUsedForLocation, BufferedImage screenshot) {
    }

    private static class RectangleAdapter implements Clusterable {
        private final Rectangle rectangle;
        private final double[] points;

        public RectangleAdapter(Rectangle rectangle) {
            this.rectangle = rectangle;
            this.points = new double[]{rectangle.x, rectangle.y, rectangle.width, rectangle.height};
        }

        public Rectangle getRectangle() {
            return rectangle;
        }

        @Override
        public double[] getPoint() {
            return points;
        }
    }

    private static class IoUDistance implements DistanceMeasure {
        @Override
        public double compute(double[] a, double[] b) {
            Rectangle r1 = new Rectangle((int) a[0], (int) a[1], (int) a[2], (int) a[3]);
            Rectangle r2 = new Rectangle((int) b[0], (int) b[1], (int) b[2], (int) b[3]);
            return 1 - calculateIoU(r1, r2);
        }
    }

    private static double getScalingRatio(BufferedImage image) {
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        int longestSide = Math.max(originalWidth, originalHeight);
        double downscaleRatio = 1.0;
        if (longestSide > BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS) {
            downscaleRatio = ((double) BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS) / longestSide;
        }

        double originalSizeMegapixels = originalWidth * originalHeight / 1_000_000d;
        if (originalSizeMegapixels > BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS) {
            downscaleRatio = min(downscaleRatio, Math.sqrt(BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS / originalSizeMegapixels));
        }
        return downscaleRatio;
    }
}



