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
package org.tarik.ta.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.dto.FinalResult;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;

/**
 * AI suggestions the procedure editor pre-populates a new procedure with, where {@code isAtomicSuggestion} marks a
 * procedure the AI deems indecomposable. Test data is excluded because it comes from the test step at execution time.
 */
@Description("AI-suggested preconditions, effects, negated effects, expected results, and child steps for a new procedure")
public record KnowledgeSuggestionResult(
        @Description("List of suggested precondition phrases that must be true before this procedure can execute")
        List<String> suggestedPreconditions,
        @Description("List of suggested effect phrases that will be true after this procedure executes successfully")
        List<String> suggestedEffects,
        @Description("List of suggested negated-effect phrases: states that were true before this procedure executes and stop being "
                + "true after it executes successfully, phrased like effects")
        List<String> suggestedNegatedEffects,
        @JsonFormat(with = ACCEPT_SINGLE_VALUE_AS_ARRAY)
        @Description("List of suggested expected-result descriptions after execution")
        @NotNull List<String> suggestedExpectedResults,
        @Description("List of suggested child steps if this should be a composite procedure")
        List<SuggestedStep> suggestedChildSteps,
        @Description("True if this should be an atomic (indecomposable) step, false if it should be composite")
        boolean isAtomicSuggestion
) implements FinalResult {

    /**
     * Represents a suggested child step within a composite procedure.
     * Atomicity and expected results are determined when the user edits the child step.
     *
     * @param description the suggested description of what the child step does
     */
    @Description("A suggested child step within a composite procedure")
    public record SuggestedStep(
            @Description("The suggested description of what the child step does")
            String description
    ) {
    }

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE_IF_LAST)
    public static KnowledgeSuggestionResult endExecutionAndGetFinalResult(
            @P(FINAL_RESULT_PARAM_DESCRIPTION) KnowledgeSuggestionResult result) {
        return result;
    }

    public @NotNull String suggestedExpectedResultsText() {
        return suggestedExpectedResults == null ? "" : String.join(System.lineSeparator(), suggestedExpectedResults);
    }

    public static KnowledgeSuggestionResult empty() {
        return new KnowledgeSuggestionResult(List.of(), List.of(), List.of(), List.of(), List.of(), true);
    }
}
