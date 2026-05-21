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
package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.core.agents.GenericAiAgent;
import org.tarik.ta.dto.UiElementLocationResult;

/**
 * AI agent that resolves (locates and, if needed, creates in DB) a UI element for an atomic collecting knowledge procedure.
 *
 * <p>This agent is a one-shot sub-agent scoped to a single element resolution operation.
 * It uses {@link UiElementLocationResult#endExecutionAndGetFinalResult} as its terminal tool,
 * which signals completion with the resolved element's UUID, name, and bounding box.
 */
public interface UiElementResolutionAgent extends GenericAiAgent<UiElementLocationResult> {
    String DESCRIPTION_EXTRACTION_WORKFLOW_PART =
            "Extract the UI element description from the provided action description. If any relevant for the element data is provided, " +
                    "never include it in the element description - this data must be provided as a separate argument to each tool " +
                    "which requires it.";
    String CREATE_UI_ELEMENT_IN_DB_WORKFLOW_PART = """
            a. Show the spinner with the message "Creating element in DB...".
            b. Create the element in the database using the corresponding tool, passing the extracted element description and any provided to you relevant element data.
            c. Hide the spinner.
            d. If creation in DB failed, end execution immediately and return a failure result with info that the element was not found in DB.""";
    String VISUAL_GROUNDING_WORKFLOW_PART = """
            a. Show the spinner with the message "Locating UI element on the screen...".
            b. Locate the (found or created in DB) UI element on the screen based on its ID using the corresponding tool, passing any provided to you relevant element data.
            c. Hide the spinner.
            b. Return the final result. If the element was not located successfully, always provide a short and user-friendly explanation of why it happened.""";

    String RESOLVE_WORKFLOW = """
            1. %s
            2. Show the spinner with the message "Searching for element in DB..." using the spinner tool.
            3. Search for the element in the database using the corresponding tool with the extracted element description.
            4. If the element was not found in the database, do the following:
               %s
            5. Do the following:
               %s
            """.formatted(DESCRIPTION_EXTRACTION_WORKFLOW_PART, CREATE_UI_ELEMENT_IN_DB_WORKFLOW_PART, VISUAL_GROUNDING_WORKFLOW_PART);

    String CREATE_WORKFLOW = """
            1. %s
            2. Do the following:
               %s
            """.formatted(CREATE_UI_ELEMENT_IN_DB_WORKFLOW_PART, VISUAL_GROUNDING_WORKFLOW_PART);

    @UserMessage("""
            {{descriptionLabel}}: {{description}}

            Data related to the element: {{relatedData}}
            """)
    Result<String> resolveForAction(
            @V("descriptionLabel") String descriptionLabel,
            @V("description") String description,
            @V("relatedData") String relatedData,
            // This one is a placeholder in the system prompt
            @V("execution_workflow") String executionWorkflow);

    @Override
    default String getAgentTaskDescription() {
        return "Resolves UI element for specific test action";
    }
}