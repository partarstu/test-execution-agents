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
package org.tarik.ta.dto.panel.prompt;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** The requests the browser sends to the actions of an open prompt and what the actions answer. */
public final class PromptActionMessages {
    private PromptActionMessages() {
    }

    /**
     * Searches procedures by description; the procedure editor names the procedure whose child is looked up, its type and its
     * current children, which a search for the next action leaves out.
     */
    public record ProcedureSearch(String query, boolean showAllTypes, @Nullable UUID procedureId, boolean precondition,
                                  @Nullable List<UUID> childIds) {
    }

    public record ProcedureHit(UUID id, String description, boolean atomic, boolean precondition, int satisfiedPrerequisites,
                               int totalPrerequisites) {
    }

    public record ProcedureHits(List<ProcedureHit> hits) {
        public ProcedureHits {
            hits = List.copyOf(hits);
        }
    }

    /** A child step in the editor: a persisted procedure by its id, or a suggested step known only by its description. */
    public record ChildRef(@Nullable UUID id, String description) {
    }

    /** The preceding siblings at each nesting level, from the edited procedure down to the child a prerequisite is picked for. */
    public record EffectsBeforeChild(List<List<ChildRef>> levels) {
    }

    public record Effects(List<String> effects) {
        public Effects {
            effects = List.copyOf(effects);
        }
    }

    /** {@code precedingSteps} are the steps executed before the described one, across every nesting level, in execution order. */
    public record SuggestionRequest(String description, List<ChildRef> precedingSteps) {
    }

    public record Suggestions(boolean atomic, List<String> childSteps, List<String> prerequisites, List<String> effects,
                              List<String> negatedEffects, String expectedResults) {
        public Suggestions {
            childSteps = List.copyOf(childSteps);
            prerequisites = List.copyOf(prerequisites);
            effects = List.copyOf(effects);
            negatedEffects = List.copyOf(negatedEffects);
        }
    }

    public record SimilarChildren(@Nullable UUID procedureId, List<ChildRef> rows) {
    }

    /** The indices of the rows for which the knowledge graph already holds a similar procedure. */
    public record SimilarRows(List<Integer> indices) {
        public SimilarRows {
            indices = List.copyOf(indices);
        }
    }

    public record ElementLocation(String description, List<String> testData) {
    }

    /** The located or created element, or the reason it could not be found, which the editor shows. */
    public record ElementResolution(@Nullable ElementCard element, @Nullable String failureReason) {
    }

    public record DescriptionText(String description) {
    }

    public record ElementQuery(String query) {
    }

    public record ElementCards(List<ElementCard> elements) {
        public ElementCards {
            elements = List.copyOf(elements);
        }
    }

    public record ElementInfoUpdate(UUID elementId, String name, String description, String locationDetails, String pageSummary,
                                    boolean dataDependent) {
    }

    public record NoRequest() {
    }

    public record CapturedScreen(String screenshotId, int width, int height) {
    }

    /** Replaces the element's screenshot with the rectangle of the captured screen, in the capture's pixels. */
    public record ScreenshotCrop(UUID elementId, String screenshotId, int x, int y, int width, int height) {
    }

    public record ElementRef(UUID elementId) {
    }

    public record ProcedureRef(UUID procedureId) {
    }

    public record ParentSummary(UUID id, String description) {
    }

    public record Parents(List<ParentSummary> parents) {
        public Parents {
            parents = List.copyOf(parents);
        }
    }

    public record Done() {
    }
}
