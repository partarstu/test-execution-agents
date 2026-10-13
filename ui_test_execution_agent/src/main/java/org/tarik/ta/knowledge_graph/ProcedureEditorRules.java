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
package org.tarik.ta.knowledge_graph;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.dto.EditorChild;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static java.util.Optional.ofNullable;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.createAtomic;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.createComposite;

/** Decisions of the procedure editor that do not depend on how the editor is presented. */
public final class ProcedureEditorRules {
    private ProcedureEditorRules() {
    }

    /** Lists the effects a prerequisite may be picked from, the latest one first and each once. */
    public static List<String> distinctNewestFirst(List<String> effectsInExecutionOrder) {
        return List.copyOf(new LinkedHashSet<>(effectsInExecutionOrder.reversed()));
    }

    /**
     * Values a save must not derive from the edited form. The procedure's type is the one it was loaded with, and Test Data has no
     * editor when the type hides it, so taking it from the (then empty) form would wipe what is stored.
     */
    public record PreservedFields(boolean isPrecondition, List<String> testData) {
    }

    public static PreservedFields resolvePreservedFields(@Nullable Procedure loadedProcedure, boolean showTestDataAndExpectedResults,
                                                         List<String> editedTestData) {
        if (loadedProcedure == null) {
            return new PreservedFields(!showTestDataAndExpectedResults, editedTestData);
        }
        return new PreservedFields(loadedProcedure.isPrecondition(),
                showTestDataAndExpectedResults ? editedTestData : loadedProcedure.testData());
    }

    // At runtime a composite sibling's scope drops its children's effects when it closes, so a sibling contributes only its own,
    // and like at runtime it first ends the states it negates.
    public static List<String> effectsBeforeChild(List<String> effectsBeforeParent, List<EditorChild> precedingSiblings) {
        var combined = new ArrayList<>(effectsBeforeParent);
        precedingSiblings.forEach(sibling -> {
            combined.removeAll(sibling.negatedEffects());
            combined.addAll(sibling.effects());
        });
        return List.copyOf(combined);
    }

    /** Builds a transient {@link Procedure} pre-filled with the AI suggestions or the description, or null when there is neither. */
    @Nullable
    public static Procedure buildTransientProcedure(String initialDescription,
                                                    @Nullable KnowledgeSuggestionResult aiSuggestions,
                                                    boolean showTestDataAndExpectedResults) {
        if (initialDescription.isBlank() && aiSuggestions == null) {
            return null;
        }
        var ai = ofNullable(aiSuggestions);
        var prerequisites = ai.map(KnowledgeSuggestionResult::suggestedPreconditions).orElse(List.of());
        var effects = ai.map(KnowledgeSuggestionResult::suggestedEffects).orElse(List.of());
        var negatedEffects = ai.map(KnowledgeSuggestionResult::suggestedNegatedEffects).orElse(List.of());
        var expectedResults = ai.map(KnowledgeSuggestionResult::suggestedExpectedResultsText).orElse("");
        boolean isAtomic = aiSuggestions == null || aiSuggestions.isAtomicSuggestion();
        var procedure = isAtomic
                ? createAtomic(initialDescription, List.of(), expectedResults, prerequisites, effects, !showTestDataAndExpectedResults)
                : createComposite(initialDescription, List.of(), expectedResults, prerequisites, effects, !showTestDataAndExpectedResults);
        return procedure.withNegatedEffects(negatedEffects);
    }
}
