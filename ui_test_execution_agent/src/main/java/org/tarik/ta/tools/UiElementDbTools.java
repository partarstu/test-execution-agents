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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.jetbrains.annotations.NotNull;
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
import org.tarik.ta.knowledge_graph.service.UiElementAliasRegistry;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.MarkdownSections.SECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.SUBSECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.heading;
import static org.tarik.ta.core.utils.MarkdownSections.joinLines;
import static org.tarik.ta.core.utils.MarkdownSections.joinSections;
import static org.tarik.ta.core.utils.MarkdownSections.listItem;
import static org.tarik.ta.core.utils.MarkdownSections.parentSection;
import static org.tarik.ta.core.utils.MarkdownSections.section;
import static org.tarik.ta.core.utils.MarkdownSections.userMessageContents;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.utils.UserMessageSections.SCREENSHOT_LABEL;
import static java.util.UUID.randomUUID;
import static java.util.Objects.requireNonNull;

@UiAgentRequestScope
public class UiElementDbTools extends UiAbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementDbTools.class);
    private static final String TARGET_ELEMENT_DESCRIPTION_LABEL = "Target element description";
    private final UiElementRepository elementRepository;
    private final UiElementExtendedDescriptionAgent uiElementExtendedDescriptionAgent;
    private final DbUiElementSelectionAgent dbUiElementSelectionAgent;
    private final UiElementRefinementHelper uiElementRefinementHelper;
    private final UiTestAgentConfig uiTestAgentConfig;
    private final UiElementAliasRegistry uiElementAliasRegistry;

    @Inject
    public UiElementDbTools(UiElementRepository uiElementRepository, UiStateCheckAgent uiStateCheckAgent,
                             UiElementExtendedDescriptionAgent uiElementExtendedDescriptionAgent,
                             DbUiElementSelectionAgent dbUiElementSelectionAgent,
                             UiElementRefinementHelper uiElementRefinementHelper,
                             UiTestAgentConfig uiTestAgentConfig,
                             @NotNull UiElementAliasRegistry uiElementAliasRegistry) {
        super(uiStateCheckAgent);
        this.elementRepository = requireNonNull(uiElementRepository, "uiElementRepository");
        this.uiElementExtendedDescriptionAgent = requireNonNull(uiElementExtendedDescriptionAgent, "uiElementExtendedDescriptionAgent");
        this.dbUiElementSelectionAgent = requireNonNull(dbUiElementSelectionAgent, "dbUiElementSelectionAgent");
        this.uiElementRefinementHelper = requireNonNull(uiElementRefinementHelper, "uiElementRefinementHelper");
        this.uiTestAgentConfig = requireNonNull(uiTestAgentConfig, "uiTestAgentConfig");
        this.uiElementAliasRegistry = uiElementAliasRegistry;
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
                    .map(elem -> new DbElementSearchResult(true, uiElementAliasRegistry.aliasFor(elem.id()), elem.name(),
                            elem.description()))
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
            BufferedImage screenshot = captureScreen();
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
            return new ElementCreationResult(true, uiElementAliasRegistry.aliasFor(uuid), descriptionResult.name(),
                    "Element created successfully");
        } catch (Exception e) {
            throw rethrowAsToolException(e, "creating a new UI element automatically");
        }
    }

    private UiElementIdentificationResult getElementIdentification(String elementDescription, String relevantTestData,
                                                                   BufferedImage screenshot) {
        var imageContent = uiElementExtendedDescriptionAgent.getImageContentCreator(uiTestAgentConfig).createImageContent(screenshot);
        var userMessage = userMessageContents(joinSections(
                section(SECTION_LEVEL, TARGET_ELEMENT_DESCRIPTION_LABEL, elementDescription),
                section(SECTION_LEVEL, "Relevant data context", relevantTestData),
                heading(SECTION_LEVEL, SCREENSHOT_LABEL)));
        var executionResult = uiElementExtendedDescriptionAgent
                .executeAndGetResult(() -> uiElementExtendedDescriptionAgent.describeUiElement(userMessage, imageContent));
        var identificationResult = executionResult.getResultPayload();
        if (!executionResult.isSuccess() || identificationResult == null) {
            throw new ToolExecutionException("The UI element could not be described: %s".formatted(executionResult.getMessage()),
                    TRANSIENT_TOOL_ERROR);
        }
        return identificationResult;
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
        var userMessage = userMessageContents(getDbElementBestMatchSelectionUserMessage(candidatesById, elementDescription,
                elementSpecificData));
        BufferedImage screenshot = captureScreen();
        try {
            var result = dbUiElementSelectionAgent.executeAndGetResult(() ->
                            dbUiElementSelectionAgent.selectBestElementFromCandidates(userMessage,
                                    dbUiElementSelectionAgent.getImageContentCreator(uiTestAgentConfig).createImageContent(screenshot)))
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

    private String getDbElementBestMatchSelectionUserMessage(Map<String, UiElement> candidatesById,
                                                             String elementDescription,
                                                             String elementSpecificData) {
        var candidateSections = candidatesById.entrySet().stream()
                .map(candidateById -> renderCandidate(candidateById.getKey(), candidateById.getValue()))
                .toArray(String[]::new);

        return joinSections(
                section(SECTION_LEVEL, TARGET_ELEMENT_DESCRIPTION_LABEL, elementDescription),
                section(SECTION_LEVEL, "Data related to this element", elementSpecificData),
                parentSection(SECTION_LEVEL, "Candidates", candidateSections),
                heading(SECTION_LEVEL, SCREENSHOT_LABEL));
    }

    private static String renderCandidate(String candidateId, UiElement candidate) {
        return joinLines(
                heading(SUBSECTION_LEVEL, "Candidate %s".formatted(candidateId)),
                listItem("Name", candidate.name()),
                listItem("Description", candidate.description()),
                listItem("Location details", candidate.locationDetails()),
                listItem("Parent element info", candidate.parentElementSummary()));
    }
}
