/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The own data of a recorded procedure, without its children, as the exploration tools return it; the score only for a search hit.
 */
public record ProcedureSummary(String id,
                               @Nullable Double score,
                               String description,
                               boolean atomic,
                               @JsonProperty("is_precondition") boolean isPrecondition,
                               @Nullable UiElementView uiElement,
                               @Nullable String additionalInfo,
                               List<String> prerequisites,
                               List<String> effects,
                               List<String> negatedEffects) {
    public ProcedureSummary {
        prerequisites = List.copyOf(prerequisites);
        effects = List.copyOf(effects);
        negatedEffects = List.copyOf(negatedEffects);
    }
}
