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
 * A recorded procedure with all its data and its children in their recorded order, down to the atomic procedures; {@code
 * childrenTruncated} marks a composite whose children were cut off by the depth or size cap of the tree.
 */
public record ProcedureNode(String id,
                            String description,
                            boolean atomic,
                            @JsonProperty("is_precondition") boolean isPrecondition,
                            boolean optional,
                            List<String> testData,
                            String expectedResults,
                            List<String> prerequisites,
                            List<String> effects,
                            List<String> negatedEffects,
                            @Nullable String additionalInfo,
                            long successCount,
                            @Nullable UiElementView uiElement,
                            List<ProcedureNode> children,
                            boolean childrenTruncated) {
    public ProcedureNode {
        testData = List.copyOf(testData);
        prerequisites = List.copyOf(prerequisites);
        effects = List.copyOf(effects);
        negatedEffects = List.copyOf(negatedEffects);
        children = List.copyOf(children);
    }
}
