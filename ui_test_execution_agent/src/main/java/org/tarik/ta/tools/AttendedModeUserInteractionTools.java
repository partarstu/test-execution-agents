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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.dto.BoundingBox;
import org.tarik.ta.dto.LocationConfirmationResult;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.user_dialogs.LocatedElementConfirmationDialog;
import org.tarik.ta.dto.NewElementCreationResult;
import org.tarik.ta.user_dialogs.BoundingBoxCaptureNeededPopup;
import org.tarik.ta.user_dialogs.UiElementScreenshotCaptureWindow;
import org.tarik.ta.user_dialogs.UiElementInfoPopup;

import java.awt.*;

import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.utils.UiCommonUtils.getPhysicalBoundingBox;
import static dev.langchain4j.service.AiServices.builder;
import static org.tarik.ta.core.model.ModelFactory.getModel;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

import org.tarik.ta.agents.UiElementDescriptionAgent;
import org.tarik.ta.dto.UiElementDescriptionResult;
import org.tarik.ta.user_dialogs.UiElementScreenshotCaptureWindow.UiElementCaptureResult;
import org.tarik.ta.UiTestAgentConfig;

import static org.tarik.ta.utils.ImageUtils.singleImageContent;
import static org.tarik.ta.utils.UiCommonUtils.getColorName;

/**
 * User interaction tools for ATTENDED execution mode.
 */
public class AttendedModeUserInteractionTools extends UserInteractionToolsBase {
    private static final Logger LOG = LoggerFactory.getLogger(AttendedModeUserInteractionTools.class);

    private final UiElementDescriptionAgent uiElementDescriptionAgent;

    /**
     * Constructs AttendedModeUserInteractionTools.
     *
     * @param uiElementRetriever The retriever for persisting and querying UI elements
     * @param executionContext   The current UI test execution context
     */
    public AttendedModeUserInteractionTools(UiElementRetriever uiElementRetriever, UiTestExecutionContext executionContext) {
        super(uiElementRetriever, executionContext);
        this.uiElementDescriptionAgent = getUiElementDescriptionAgent();
    }

    @Tool("Asks the user to confirm that a located element is correct. Use this tool when you have located an element but want to ensure " +
            "it is the correct one before proceeding.")
    public LocationConfirmationResult confirmLocatedElement(
            @P("Description of the element being confirmed") String elementDescription,
            @P("The bounding box of the located element") BoundingBox boundingBox) {
        try {
            if (isBlank(elementDescription)) {
                throw new ToolExecutionException("Element description cannot be empty", TRANSIENT_TOOL_ERROR);
            }
            if (boundingBox == null) {
                throw new ToolExecutionException("Bounding box cannot be null", TRANSIENT_TOOL_ERROR);
            }

            var screenshot = captureScreen();
            Rectangle boundingBoxRectangle = getPhysicalBoundingBox(getBoundingBoxRectangle(boundingBox));
            LOG.info("Prompting user to confirm located element: {}", elementDescription);
            var choice =
                    LocatedElementConfirmationDialog.displayAndGetUserChoice(null, screenshot, boundingBoxRectangle, BOUNDING_BOX_COLOR,
                            elementDescription);

            return switch (choice) {
                case CORRECT -> {
                    LOG.info("User confirmed element location as correct, returning the result after {} millis",
                            USER_DIALOG_DISMISS_DELAY_MILLIS);
                    sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);
                    yield LocationConfirmationResult.correct();
                }
                case INCORRECT -> {
                    LOG.info("User marked element location as incorrect.");
                    yield LocationConfirmationResult.incorrect();
                }
                case INTERRUPTED -> {
                    LOG.info("User interrupted location confirmation.");
                    yield LocationConfirmationResult.interrupted();
                }
            };
        } catch (Exception e) {
            throw rethrowAsToolException(e, "confirming located element correctness");
        }
    }

    @Tool("Prompts the user to create a new UI element. Use this tool when you need to create a new " +
            "UI element which is not present in the database")
    public NewElementCreationResult promptUserToCreateNewElement(
            @P("Initial description of the target element, not its name.") String elementDescription,
            @P("Relevant test data that helps identify the element but should NOT be part of the element metadata")
            String relevantTestData) {
        if (isBlank(elementDescription)) {
            throw new ToolExecutionException("Element description cannot be empty", TRANSIENT_TOOL_ERROR);
        }

        try {
            LOG.info("Starting new element creation workflow for: {}", elementDescription);

            // Step 1: Inform user that bounding box capture is needed
            BoundingBoxCaptureNeededPopup.display(null);
            sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);

            // Step 2: Capture bounding box
            LOG.debug("Prompting user to capture element screenshot");
            var captureResult = UiElementScreenshotCaptureWindow.displayAndGetResult(null, BOUNDING_BOX_COLOR);
            if (captureResult.isEmpty()) {
                var message = "User cancelled screenshot capture";
                LOG.info(message);
                return NewElementCreationResult.interrupted(message);
            }

            // Step 3: Prompt the model to suggest the new element info based on the element
            // position on the screenshot
            var capture = captureResult.get();
            var describedUiElement = getUiElementInfoSuggestionFromModel(elementDescription, relevantTestData, capture);

            // Step 4: Prompt user to refine the suggested by the model element info
            var uiElementInfo = getUiElementInfo(describedUiElement);
            return UiElementInfoPopup.displayAndGetUpdatedElementInfo(null, uiElementInfo)
                    .map(clarifiedByUserElement -> {
                        LOG.debug("Persisting new element to database");
                        saveNewUiElementIntoDb(capture.elementScreenshot(), clarifiedByUserElement);
                        LOG.info("Successfully created new element: {}", clarifiedByUserElement.name());
                        return NewElementCreationResult.asSuccess();
                    })
                    .orElseGet(() -> {
                        var message = "User interrupted element creation by closing the element creation popup";
                        LOG.info(message);
                        return NewElementCreationResult.interrupted(message);
                    });
        } catch (Exception e) {
            throw rethrowAsToolException(e, "creating a new UI element");
        }
    }

    protected UiElementDescriptionResult getUiElementInfoSuggestionFromModel(String elementDescription,
                                                                             String relevantTestData,
                                                                             UiElementCaptureResult capture) {
        var screenshot = singleImageContent(capture.wholeScreenshotWithBoundingBox());
        var boundingBoxColorName = getColorName(BOUNDING_BOX_COLOR).toLowerCase();
        return uiElementDescriptionAgent.executeAndGetResult(() ->
                        uiElementDescriptionAgent.describeUiElement(elementDescription, boundingBoxColorName, relevantTestData, screenshot))
                .getResultPayload();
    }

    private static UiElementDescriptionAgent getUiElementDescriptionAgent() {
        var model = getModel(UiTestAgentConfig.getUiElementDescriptionAgentModelName(),
                UiTestAgentConfig.getUiElementDescriptionAgentModelProvider());
        var prompt = loadSystemPrompt("element_describer/screenshot_based", UiTestAgentConfig.getUiElementDescriptionAgentPromptVersion(),
                "element_description_prompt.txt");
        return builder(UiElementDescriptionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(ignored -> prompt)
                .tools(new UiElementDescriptionResult("", "", "", "", false))
                .build();
    }
}
