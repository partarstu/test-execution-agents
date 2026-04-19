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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.DbUiElementSelectionAgent;
import org.tarik.ta.agents.UiElementExtendedDescriptionAgent;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.dto.DbElementSearchResult;
import org.tarik.ta.dto.ElementCreationResult;
import org.tarik.ta.dto.UiElementIdentificationResult;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.user_dialogs.SpinnerManager;
import org.tarik.ta.utils.ImageUtils;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static java.util.UUID.randomUUID;
import static java.util.Objects.requireNonNull;

@Singleton
public class UiElementDbTools extends UiAbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementDbTools.class);
    private final UiElementRepository elementRepository;
    private final UiElementExtendedDescriptionAgent uiElementExtendedDescriptionAgent;
    private final DbUiElementSelectionAgent dbUiElementSelectionAgent;
    private final UiElementRefinementHelper uiElementRefinementHelper;
    private final UiTestAgentConfig uiTestAgentConfig;

    @Inject
    public UiElementDbTools(UiElementRepository uiElementRepository, UiStateCheckAgent uiStateCheckAgent,
                             UiElementExtendedDescriptionAgent uiElementExtendedDescriptionAgent,
                             DbUiElementSelectionAgent dbUiElementSelectionAgent,
                             UiElementRefinementHelper uiElementRefinementHelper,
                             UiTestAgentConfig uiTestAgentConfig) {
        super(uiStateCheckAgent);
        this.elementRepository = requireNonNull(uiElementRepository, "uiElementRepository");
        this.uiElementExtendedDescriptionAgent = requireNonNull(uiElementExtendedDescriptionAgent, "uiElementExtendedDescriptionAgent");
        this.dbUiElementSelectionAgent = requireNonNull(dbUiElementSelectionAgent, "dbUiElementSelectionAgent");
        this.uiElementRefinementHelper = requireNonNull(uiElementRefinementHelper, "uiElementRefinementHelper");
        this.uiTestAgentConfig = requireNonNull(uiTestAgentConfig, "uiTestAgentConfig");
    }

    @Tool("Searches for a UI element in the database using vector similarity and selects the best candidate.")
    public DbElementSearchResult searchElementInDb(
            @P("The description of the UI element to search for") String elementDescription,
            @P("Any data related to this UI element") String elementSpecificData) {
        if (isBlank(elementDescription)) {
            throw new ToolExecutionException("Element description cannot be empty", TRANSIENT_TOOL_ERROR);
        }
        try {
            var retrievedElements = uiElementRefinementHelper.retrieveUiElementsWithTargetSimilarity(elementRepository, elementDescription);
            var candidates = retrievedElements.stream()
                    .map(UiElementRepository.UiElementMatch::element)
                    .toList();

            if (candidates.isEmpty()) {
                LOG.info("No UI elements found in DB matching the description '{}'", elementDescription);
                return new DbElementSearchResult(false, null, null, null);
            }

            return selectBestMatchingDbElement(candidates, elementDescription, elementSpecificData)
                    .map(elem -> new DbElementSearchResult(true, elem.id(), elem.name(), elem.description()))
                    .orElseGet(() -> new DbElementSearchResult(false, null, null, null));
        } catch (Exception e) {
            throw rethrowAsToolException(e, "searching element in DB");
        }
    }

    @Tool("Creates a new UI element record in DB based on the description of this element.")
    public ElementCreationResult createElementInDb(
            @P("Original description of UI element, extracted from the test step action.") String elementDescription,
            @P(value = "Any data related to this element or the action involving this element", required = false) String relevantTestData) {
        if (isBlank(elementDescription)) {
            throw new ToolExecutionException("Element description cannot be empty", TRANSIENT_TOOL_ERROR);
        }

        try {
            LOG.info("Starting new element creation workflow for: {}", elementDescription);
            BufferedImage screenshot = getScreenshotTogglingSpinner();
            var identificationResult = getElementIdentification(elementDescription, relevantTestData, screenshot);
            if (!identificationResult.targetElementIdentified()) {
                LOG.warn("Target element '{}' was not found on the screenshot.", elementDescription);
                return new ElementCreationResult(false, null, null, "Target element was not found on the screenshot.");
            }

            if (identificationResult.multipleElementsIdentified()) {
                LOG.warn("Multiple instances of '{}' were found.", elementDescription);
                return new ElementCreationResult(false, null, null, "Multiple instances of target element were found.");
            }

            var descriptionResult = identificationResult.elementDescription();
            LOG.info("Automatically identified element '{}'. Proceeding with creation.", elementDescription);
            var uuid = randomUUID();
            UiElement uiElementToStore = new UiElement(uuid, descriptionResult.name(), descriptionResult.ownDescription(),
                    descriptionResult.locationDescription(), descriptionResult.parentSummary(), null,
                    descriptionResult.elementIsDataDependent());
            elementRepository.create(uiElementToStore);
            return new ElementCreationResult(true, uuid, descriptionResult.name(), "Element created successfully");
        } catch (Exception e) {
            throw rethrowAsToolException(e, "creating a new UI element automatically");
        }
    }

    private UiElementIdentificationResult getElementIdentification(String elementDescription, String relevantTestData,
                                                                   BufferedImage screenshot) {
        var imageContent = ImageUtils.singleImageContent(screenshot, uiTestAgentConfig.getUiElementDescriptionMatcherAgentImageDetailLevel());
        var relevantDataString = relevantTestData == null ? "" : relevantTestData;
        return uiElementExtendedDescriptionAgent
                .executeAndGetResult(() -> uiElementExtendedDescriptionAgent.describeUiElement(elementDescription,
                        relevantDataString, imageContent))
                .getResultPayload();
    }

    private Optional<UiElement> selectBestMatchingDbElement(List<UiElement> candidates, String elementDescription,
                                                            String elementSpecificData) {
        if (candidates.isEmpty()) {
            return empty();
        }
        if (candidates.size() == 1) {
            return of(candidates.getFirst());
        }

        Map<String, UiElement> candidatesById = IntStream.range(0, candidates.size())
                .boxed()
                .collect(toMap(index -> "element_" + index, candidates::get));
        var userMessage = getDbElementBestMatchSelectionUserMessage(candidatesById, elementDescription, elementSpecificData);
        BufferedImage screenshot = getScreenshotTogglingSpinner();
        try {
            var result = dbUiElementSelectionAgent.executeAndGetResult(() ->
                            dbUiElementSelectionAgent.selectBestElementFromCandidates(userMessage, ImageUtils.singleImageContent(screenshot, uiTestAgentConfig.getDbElementCandidateSelectionAgentImageDetailLevel())))
                    .getResultPayload();
            if (result == null) {
                LOG.warn("Model returned null result. Returning empty.");
                return empty();
            }

            if (!result.targetElementIdentified()) {
                LOG.warn("Model could not identify the target element on the screen. Reasoning: {}.", result.message());
                return empty();
            }

            if (!result.atLeastOneCandidateMatches()) {
                LOG.warn("Model could not select a matching element from candidates. Reasoning: {}.", result.message());
                return empty();
            }

            if (isNotBlank(result.selectedElementId())) {
                String selectedId = result.selectedElementId().toLowerCase().trim();
                UiElement selectedElement = candidatesById.get(selectedId);
                if (selectedElement != null) {
                    LOG.info("Model selected element '{}' from {} candidates.", selectedElement.name(), candidates.size());
                    return of(selectedElement);
                } else {
                    LOG.warn("Model returned unknown element ID '{}'. Available IDs: {}.", selectedId, candidatesById.keySet());
                }
            } else {
                LOG.warn("Model did not provide a selected element ID. Reasoning: {}.", result.message());
                return empty();
            }
        } catch (Exception e) {
            throw rethrowAsToolException(e, "selecting the best UI element fetched from DB based on the screen state");
        }

        return empty();
    }

    private static @NonNull BufferedImage getScreenshotTogglingSpinner() {
        var previousState = SpinnerManager.hideIfVisible();
        BufferedImage screenshot;
        try {
            screenshot = captureScreen();
        } finally {
            previousState.restoreIfWasVisible();
        }
        return screenshot;
    }

    private String getDbElementBestMatchSelectionUserMessage(Map<String, UiElement> candidatesById,
                                                             String elementDescription,
                                                             String elementSpecificData) {
        String candidatesString = candidatesById.entrySet().stream()
                .map((candidateById) -> {
                    var candidate = candidateById.getValue();
                    return "  - Candidate ID: %s, Name: '%s', Description: '%s', Location Details: '%s', Parent Element Info: '%s'"
                            .formatted(candidateById.getKey(), candidate.name(), candidate.description(),
                                    candidate.locationDetails(), candidate.parentElementSummary());
                })
                .collect(joining("\n"));

        return """
                The target element description: '%s'.
                
                Available data related to this element: '%s'
                
                Candidates:
                %s
                
                Screenshot follows.
                """.formatted(elementDescription, elementSpecificData != null ? elementSpecificData : "",
                candidatesString);
    }
}
