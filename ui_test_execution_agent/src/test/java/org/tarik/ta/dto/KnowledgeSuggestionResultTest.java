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

import dev.langchain4j.internal.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSuggestionResultTest {
    @Test
    void jsonDeserialization_shouldAcceptExpectedResultsArray() {
        var json = suggestionJson("[\"First result\", \"Second result\"]");

        var result = Json.fromJson(json, KnowledgeSuggestionResult.class);

        assertThat(result.suggestedExpectedResults()).containsExactly("First result", "Second result");
        assertThat(result.suggestedExpectedResultsText()).isEqualTo("First result%nSecond result".formatted());
    }

    @Test
    void jsonDeserialization_shouldAcceptExpectedResultsString() {
        var json = suggestionJson("\"Single result\"");

        var result = Json.fromJson(json, KnowledgeSuggestionResult.class);

        assertThat(result.suggestedExpectedResults()).containsExactly("Single result");
        assertThat(result.suggestedExpectedResultsText()).isEqualTo("Single result");
    }

    @Test
    void jsonDeserialization_shouldReadTheSuggestedNegatedEffects() {
        var json = """
                {
                  "suggestedPreconditions": ["the dialog is open"],
                  "suggestedEffects": ["the form is shown"],
                  "suggestedNegatedEffects": ["the dialog is open"],
                  "suggestedExpectedResults": [],
                  "suggestedChildSteps": [],
                  "isAtomicSuggestion": true
                }
                """;

        var result = Json.fromJson(json, KnowledgeSuggestionResult.class);

        assertThat(result.suggestedNegatedEffects()).containsExactly("the dialog is open");
    }

    private static String suggestionJson(String expectedResultsJson) {
        return """
                {
                  "suggestedPreconditions": [],
                  "suggestedEffects": [],
                  "suggestedExpectedResults": %s,
                  "suggestedChildSteps": [],
                  "isAtomicSuggestion": true
                }
                """.formatted(expectedResultsJson);
    }
}
