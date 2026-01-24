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
import org.tarik.ta.dto.CountdownResult;
import org.tarik.ta.dto.NextActionResult;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.user_dialogs.CountdownHaltPopup;

import java.awt.image.BufferedImage;

import org.tarik.ta.agents.UiElementExtendedDescriptionAgent;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult;
import org.tarik.ta.dto.NewElementCreationResult;
import org.tarik.ta.dto.UiElementDescriptionResult;
import org.tarik.ta.user_dialogs.SemiAttendedModeElementLocationConfirmationPopup;
import org.tarik.ta.user_dialogs.UiElementInfoPopup.UiElementInfo;

import static dev.langchain4j.service.AiServices.builder;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.tarik.ta.UiTestAgentConfig.getSemiAttendedCountdownSeconds;
import static org.tarik.ta.UiTestAgentConfig.getUiElementDescriptionMatcherAgentModelName;
import static org.tarik.ta.UiTestAgentConfig.getUiElementDescriptionMatcherAgentModelProvider;
import static org.tarik.ta.UiTestAgentConfig.getUiElementDescriptionMatcherAgentPromptVersion;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.model.ModelFactory.getModel;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;
import static org.tarik.ta.user_dialogs.CountdownHaltPopup.Result.HALTED;
import static org.tarik.ta.utils.ImageUtils.singleImageContent;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

/**
 * User interaction tools for SEMI_ATTENDED execution mode.
 */
public class SemiAttendedModeCommonUserInteractionTools extends CommonUserInteractionTools {
    private static final Logger LOG = LoggerFactory.getLogger(SemiAttendedModeCommonUserInteractionTools.class);
    private final UiElementExtendedDescriptionAgent uiElementExtendedDescriptionAgent;

    /**
     * Constructs SemiAttendedModeCommonUserInteractionTools.
     *
     * @param uiElementRetriever The retriever for persisting and querying UI elements
     * @param executionContext   The current UI test execution context
     */
    public SemiAttendedModeCommonUserInteractionTools(UiElementRetriever uiElementRetriever, UiTestExecutionContext executionContext) {
        this(uiElementRetriever, executionContext, createUiElementDescriptionMatcherAgent());
    }

    /**
     * Constructs SemiAttendedModeCommonUserInteractionTools with injected agent.
     *
     * @param uiElementRetriever                The retriever for persisting and querying UI elements
     * @param executionContext                  The current UI test execution context
     * @param uiElementExtendedDescriptionAgent The agent for element description
     */
    public SemiAttendedModeCommonUserInteractionTools(UiElementRetriever uiElementRetriever, UiTestExecutionContext executionContext,
                                                      UiElementExtendedDescriptionAgent uiElementExtendedDescriptionAgent) {
        super(uiElementRetriever, executionContext);
        this.uiElementExtendedDescriptionAgent = uiElementExtendedDescriptionAgent;
    }

    @Tool("Creates a new UI element record in DB based on its description.")
    public NewElementCreationResult createNewElementInDb(
            @P("Original description of UI element. If any related to this element data is provided, don't use " +
                    "that data as a part of its description")
            String elementDescription,
            @P(value = "Any data related to this element or the action involving this element", required = false)
            String relevantTestData) {
        if (isBlank(elementDescription)) {
            throw new ToolExecutionException("Element description cannot be empty", TRANSIENT_TOOL_ERROR);
        }

        try {
            LOG.info("Starting new element creation workflow for: {}", elementDescription);
            var screenshot = captureScreen();
            var descriptionResult = getElementDescription(elementDescription, relevantTestData, screenshot);
            LOG.info("Automatically identified element '{}'. Proceeding with creation.", elementDescription);
            return processElementCreation(descriptionResult);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "creating a new UI element automatically");
        }
    }

    @Tool("Pauses execution with a countdown popup, allowing the operator to halt if needed.")
    public CountdownResult pauseWithCountdown(
            @P("Description of the completed operation") String operationDescription) {
        try {
            LOG.info("Displaying countdown popup after: {}", operationDescription);
            var result = CountdownHaltPopup.displayWithCountdown(getSemiAttendedCountdownSeconds(), operationDescription);
            if (result == HALTED) {
                LOG.info("Operator halted execution, prompting for next action");
                return new CountdownResult(false);
            } else {
                LOG.info("Countdown completed, proceeding with execution");
                return new CountdownResult(true);
            }
        } catch (Exception e) {
            throw rethrowAsToolException(e, "displaying countdown popup");
        }
    }

    @Tool("Reports an error to the operator and prompts for the next action. " +
            "Use this tool when an error occurs during execution to notify the operator.")
    public NextActionResult reportErrorAndPrompt(
            @P("Description of the error that occurred") String errorDescription) {
        try {
            LOG.warn("Reporting error to operator: {}", errorDescription);
            var screenshot = captureScreen();
            displayInformationalPopup("Error During Execution", errorDescription, screenshot, PopupType.ERROR);
            return promptUserForNextAction("Error occurred: " + errorDescription);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "reporting error and prompting for next action");
        }
    }

    @Tool("Confirms the located element with the operator in semi-attended mode. Displays a countdown popup allowing intervention.")
    public SemiAttendedModeElementLocationConfirmationResult confirmElementSelection(
            @P("The original description of the located element") String elementDescription,
            @P("The exact name of the located element") String elementName,
            @P("The description of intended action with this element") String intendedAction) {
        try {
            LOG.info("Requesting operator confirmation for element: {} with action: {}", elementName, intendedAction);
            return displayConfirmationPopup(elementDescription, elementName, intendedAction);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "confirming element selection");
        }
    }

    protected SemiAttendedModeElementLocationConfirmationResult displayConfirmationPopup(String elementDescription, String elementName,
                                                                                         String intendedAction) {
        return SemiAttendedModeElementLocationConfirmationPopup.displayAndGetUserDecision(elementDescription, elementName, intendedAction,
                getSemiAttendedCountdownSeconds());
    }

    private UiElementDescriptionResult getElementDescription(String elementDescription, String relevantTestData,
                                                             BufferedImage screenshot) {
        var imageContent = singleImageContent(screenshot);
        var relevantDataString = relevantTestData == null ? "" : relevantTestData;
        return uiElementExtendedDescriptionAgent.executeAndGetResult(() ->
                        uiElementExtendedDescriptionAgent.describeUiElement(elementDescription, relevantDataString, imageContent))
                .getResultPayload();
    }

    private NewElementCreationResult processElementCreation(UiElementDescriptionResult result) {
        var uiElementInfo = new UiElementInfo(result.name(), result.ownDescription(), result.locationDescription(),
                result.pageSummary(), result.elementIsDataDependent());
        saveNewUiElementIntoDb(null, uiElementInfo);
        return NewElementCreationResult.asSuccess();
    }

    private static UiElementExtendedDescriptionAgent createUiElementDescriptionMatcherAgent() {
        var model = getModel(getUiElementDescriptionMatcherAgentModelName(),
                getUiElementDescriptionMatcherAgentModelProvider());
        var prompt = loadSystemPrompt("element_describer/description_based",
                getUiElementDescriptionMatcherAgentPromptVersion(),
                "description_matcher_prompt.txt");
        return builder(UiElementExtendedDescriptionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(ignored -> prompt)
                .tools(new UiElementDescriptionResult("", "", "", "", false))
                .build();
    }
}
