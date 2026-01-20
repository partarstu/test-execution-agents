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

import static org.tarik.ta.UiTestAgentConfig.getSemiAttendedCountdownSeconds;
import static org.tarik.ta.user_dialogs.CountdownHaltPopup.Result.HALTED;

/**
 * User interaction tools for SEMI_ATTENDED execution mode.
 */
public class SemiAttendedModeUserInteractionTools extends UserInteractionToolsBase {
    private static final Logger LOG = LoggerFactory.getLogger(SemiAttendedModeUserInteractionTools.class);

    /**
     * Constructs SemiAttendedModeUserInteractionTools.
     *
     * @param uiElementRetriever The retriever for persisting and querying UI elements
     * @param executionContext   The current UI test execution context
     */
    public SemiAttendedModeUserInteractionTools(UiElementRetriever uiElementRetriever, UiTestExecutionContext executionContext) {
        super(uiElementRetriever, executionContext);
    }

    @Tool("Pauses execution with a countdown popup, allowing the operator to halt if needed. " +
            "Call this after completing each test step action to give the operator a chance to intervene.")
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
            @P("Description of the error that occurred") String errorDescription,
            @P("Screenshot at the time of error (optional)") BufferedImage screenshot) {
        try {
            LOG.warn("Reporting error to operator: {}", errorDescription);
            displayInformationalPopup("Error During Execution", errorDescription, screenshot, PopupType.ERROR);
            return promptUserForNextAction("Error occurred: " + errorDescription);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "reporting error and prompting for next action");
        }
    }
}
