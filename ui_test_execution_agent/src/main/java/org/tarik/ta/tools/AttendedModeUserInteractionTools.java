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

import java.awt.*;

import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.utils.UiCommonUtils.getPhysicalBoundingBox;

/**
 * User interaction tools for ATTENDED execution mode.
 */
public class AttendedModeUserInteractionTools extends UserInteractionToolsBase {
    private static final Logger LOG = LoggerFactory.getLogger(AttendedModeUserInteractionTools.class);

    /**
     * Constructs AttendedModeUserInteractionTools.
     *
     * @param uiElementRetriever The retriever for persisting and querying UI elements
     * @param executionContext   The current UI test execution context
     */
    public AttendedModeUserInteractionTools(UiElementRetriever uiElementRetriever, UiTestExecutionContext executionContext) {
        super(uiElementRetriever, executionContext);
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
}
