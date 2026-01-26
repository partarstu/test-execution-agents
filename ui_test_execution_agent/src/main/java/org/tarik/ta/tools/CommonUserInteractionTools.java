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
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;

import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.dto.*;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.UiElementRetriever.RetrievedUiElementItem;
import org.tarik.ta.rag.model.UiElement;
import org.tarik.ta.user_dialogs.*;
import org.tarik.ta.user_dialogs.UiElementInfoPopup.UiElementInfo;
import org.tarik.ta.user_dialogs.UiElementScreenshotCaptureWindow.UiElementCaptureResult;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Comparator.comparingDouble;

import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.tarik.ta.UiTestAgentConfig.getElementRetrievalMinGeneralScore;
import static org.tarik.ta.core.error.ErrorCategory.*;
import static org.tarik.ta.core.utils.CommonUtils.*;
import static org.tarik.ta.dto.ElementRefinementOperation.Operation.DONE;
import static org.tarik.ta.rag.model.UiElement.Screenshot.fromBufferedImage;
import static org.tarik.ta.utils.UiCommonUtils.*;

/**
 * Abstract base class for user interaction tools that coordinates UI dialogs.
 * This class provides common functionality for element creation, refinement,
 * and operator interaction that is shared across all execution modes.
 */
public abstract class CommonUserInteractionTools extends UiAbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(CommonUserInteractionTools.class);
    protected static final String BOUNDING_BOX_COLOR_NAME = UiTestAgentConfig.getElementBoundingBoxColorName();
    protected static final Color BOUNDING_BOX_COLOR = getColorByName(BOUNDING_BOX_COLOR_NAME);
    protected static final int USER_DIALOG_DISMISS_DELAY_MILLIS = 2000;
    protected final UiElementRetriever uiElementRetriever;
    protected final UiTestExecutionContext executionContext;


    /**
     * Constructs a new CommonUserInteractionTools.
     *
     * @param uiElementRetriever The retriever for persisting and querying UI elements
     * @param executionContext   The current UI test execution context
     */
    public CommonUserInteractionTools(UiElementRetriever uiElementRetriever, UiTestExecutionContext executionContext) {
        this.uiElementRetriever = uiElementRetriever;
        this.executionContext = executionContext;

    }

    protected static @NonNull UiElementInfo getUiElementInfo(UiElementDescriptionResult describedUiElement) {
        return new UiElementInfo(describedUiElement.name(), describedUiElement.ownDescription(),
                describedUiElement.locationDescription(), describedUiElement.pageSummary(), describedUiElement.elementIsDataDependent());
    }

    @Tool("Prompts the user to refine existing UI elements.")
    public ElementRefinementResult promptUserToRefineExistingElements(
            @P("Initial description or hint about the element") String elementDescription) {
        List<UiElement> elementsToRefine = uiElementRetriever.retrieveUiElements(elementDescription, AgentConfig.getRetrieverTopN(),
                        getElementRetrievalMinGeneralScore())
                .stream()
                .sorted(comparingDouble(RetrievedUiElementItem::mainScore).reversed())
                .map(RetrievedUiElementItem::element)
                .toList();

        if (elementsToRefine.isEmpty()) {
            throw new ToolExecutionException("No candidate elements found for refinement", TRANSIENT_TOOL_ERROR);
        }

        try {
            Set<UiElement> updatedElementsCollector = new HashSet<>();
            List<UiElement> deletedElementsCollector = new ArrayList<>();
            LOG.info("Starting element refinement workflow with {} candidates", elementsToRefine.size());
            boolean changesMade = false;
            var message = "Please refine the following elements which are the best matches to %s".formatted(elementDescription);
            while (true) {
                var choiceOptional = UiElementRefinementPopup.displayAndGetChoice(null, message, elementsToRefine);
                if (choiceOptional.isEmpty()) {
                    var cause = "User interrupted element refinement by closing the corresponding popup";
                    LOG.info(cause);
                    return ElementRefinementResult.wasInterrupted(cause);
                }

                ElementRefinementOperation operation = choiceOptional.get();
                if (operation.operation() == DONE) {
                    LOG.info("User finished element refinement");
                    break;
                }

                changesMade = true;
                UUID elementId = operation.elementId();
                switch (operation.operation()) {
                    case UPDATE_SCREENSHOT -> updateElementScreenshot(elementsToRefine, elementId).ifPresent(updatedElementsCollector::add);
                    case UPDATE_ELEMENT -> updateElementInfo(elementsToRefine, elementId).ifPresent(updatedElementsCollector::add);
                    case DELETE_ELEMENT -> {
                        var deletedElement = deleteElement(elementsToRefine, elementId);
                        deletedElementsCollector.add(deletedElement);
                    }
                    default -> throw new IllegalStateException("Unexpected value for element operation type: " + operation.operation());
                }
                elementsToRefine = elementsToRefine.stream()
                        .filter(elementToRefine -> !deletedElementsCollector.contains(elementToRefine))
                        .map(elementToRefine -> updatedElementsCollector.stream()
                                .filter(updated -> elementToRefine.uuid().equals(updated.uuid()))
                                .findFirst()
                                .orElse(elementToRefine))
                        .toList();
            }

            LOG.info("Element refinement workflow completed");
            return changesMade
                    ? ElementRefinementResult.success(List.copyOf(updatedElementsCollector), deletedElementsCollector)
                    : ElementRefinementResult.noChanges();
        } catch (Exception e) {
            throw rethrowAsToolException(e, "refinement most matching elements");
        }
    }

    @Tool("Prompts the user to choose the next action.")
    public NextActionResult promptUserForNextAction(
            @P("Description of the reason of prompting the user") String reason) {
        try {
            if (isBlank(reason)) {
                throw new ToolExecutionException("Reason for prompting the user cannot be empty", TRANSIENT_TOOL_ERROR);
            }

            LOG.info("Prompting user for next action, root cause: {}", reason);
            reason = "%s\nPlease choose one of the following actions you'd like to do:".formatted(reason);
            var decision = NextActionPopup.displayAndGetUserDecision(null, reason);
            return switch (decision) {
                case CREATE_NEW_ELEMENT -> {
                    LOG.info("User chose to create a new element");
                    yield NextActionResult.createNewElement();
                }
                case REFINE_EXISTING_ELEMENT -> {
                    LOG.info("User chose to refine an existing element");
                    yield NextActionResult.refineExistingElement();
                }
                case RETRY_SEARCH -> {
                    LOG.info("User chose to retry search");
                    sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);
                    yield NextActionResult.retrySearch();
                }
                case TERMINATE -> {
                    LOG.info("User chose to terminate");
                    throw new ToolExecutionException("User chose to terminate", TERMINATION_BY_USER);
                }
            };
        } catch (Exception e) {
            throw rethrowAsToolException(e, "prompting for next action");
        }
    }

    @Tool("Displays an informational popup to the user.")
    public String displayInformationalPopup(
            @P("The title of the popup window") String title,
            @P("The message content to display") String message,
            @P("The severity level of the popup (INFO, WARNING, ERROR)") PopupType popupType) {
        displayInformationalPopup(title, message, null, popupType);
        return "Popup displayed successfully.";
    }

    @Tool("Informs the user about the verification failure.")
    public void displayVerificationFailure(
            @P("Description of the verification") String verificationDescription,
            @P("Short explanation of why the verification failed") String failureReason) {
        try {
            LOG.info("Displaying verification failure for: {}", verificationDescription);
            var screenshot = executionContext.getVisualState().screenshot();
            VerificationFailurePopup.display(verificationDescription, failureReason, screenshot);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "notifying operator that the verification failed");
        }
    }

    protected void displayInformationalPopup(String title, String message, BufferedImage screenshot, PopupType popupType) {
        try {
            LOG.debug("Displaying informational popup: {}", title);
            InformationalPopup.display(title, message, screenshot, popupType);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "displaying informational popup");
        }
    }

    protected void saveNewUiElementIntoDb(BufferedImage elementScreenshot, UiElementInfo uiElement) {
        var screenshot = elementScreenshot == null ? null : fromBufferedImage(elementScreenshot, "png");
        UiElement uiElementToStore = new UiElement(randomUUID(), uiElement.name(), uiElement.description(),
                uiElement.locationDetails(), uiElement.pageSummary(), screenshot, uiElement.isDataDependent());
        uiElementRetriever.storeElement(uiElementToStore);
    }

    protected Optional<UiElement> updateElementScreenshot(List<UiElement> elements, UUID elementId) {
        UiElement elementToUpdate = findElementById(elements, elementId);
        LOG.info("User chose to update screenshot for element: {}", elementToUpdate.name());

        BoundingBoxCaptureNeededPopup.display(null);
        sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);

        return UiElementScreenshotCaptureWindow.displayAndGetResult(null, Color.GREEN)
                .filter(UiElementCaptureResult::success)
                .map(captureResult -> {
                    var newScreenshot = fromBufferedImage(captureResult.elementScreenshot(), "png");
                    var elementWithNewScreenshot = new UiElement(
                            elementToUpdate.uuid(), elementToUpdate.name(), elementToUpdate.description(),
                            elementToUpdate.locationDetails(), elementToUpdate.parentElementSummary(), newScreenshot,
                            elementToUpdate.isDataDependent());
                    uiElementRetriever.updateElement(elementToUpdate, elementWithNewScreenshot);
                    LOG.debug("Persisted updated screenshot for element: {}", elementToUpdate.name());
                    return elementWithNewScreenshot;
                });
    }

    protected Optional<UiElement> updateElementInfo(List<UiElement> elements, UUID elementId) {
        UiElement elementToUpdate = findElementById(elements, elementId);
        LOG.info("User chose to update info for element: {}", elementToUpdate.name());

        var currentInfo = new UiElementInfo(elementToUpdate.name(), elementToUpdate.description(),
                elementToUpdate.locationDetails(), elementToUpdate.parentElementSummary(),
                elementToUpdate.isDataDependent());

        return UiElementInfoPopup.displayAndGetUpdatedElementInfo(null, currentInfo)
                .map(newInfo -> {
                    var updatedElement = new UiElement(elementToUpdate.uuid(), newInfo.name(), newInfo.description(),
                            newInfo.locationDetails(), newInfo.pageSummary(), elementToUpdate.screenshot(),
                            newInfo.isDataDependent());
                    uiElementRetriever.updateElement(elementToUpdate, updatedElement);
                    LOG.debug("Persisted updated info for element: {}", updatedElement.name());
                    return updatedElement;
                });
    }

    protected UiElement deleteElement(List<UiElement> elements, UUID elementId) {
        UiElement elementToDelete = findElementById(elements, elementId);
        LOG.info("User chose to delete element: {}", elementToDelete.name());
        uiElementRetriever.removeElement(elementToDelete);
        LOG.debug("Deleted element from storage: {}", elementToDelete.name());
        return elementToDelete;
    }

    protected UiElement findElementById(List<UiElement> selection, UUID elementId) {
        return selection.stream()
                .filter(el -> el.uuid().equals(elementId))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("Element with ID " + elementId + " not found in the list."));
    }

    @NotNull
    protected static Rectangle getBoundingBoxRectangle(@NotNull BoundingBox boundingBox) {
        return new Rectangle(boundingBox.x1(), boundingBox.y1(), boundingBox.x2() - boundingBox.x1(),
                boundingBox.y2() - boundingBox.y1());
    }



    /**
     * Enum representing the type of informational popup to display.
     */
    public enum PopupType {
        INFO,
        WARNING,
        ERROR
    }
}
