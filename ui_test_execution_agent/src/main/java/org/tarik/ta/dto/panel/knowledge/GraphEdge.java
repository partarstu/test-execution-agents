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
package org.tarik.ta.dto.panel.knowledge;

import org.jetbrains.annotations.Nullable;

/**
 * A relationship between two {@link GraphNode}s; {@code sequence} is set for {@code CONTAINS}, the score and the matched phrases
 * for {@code SATISFIES}.
 */
public record GraphEdge(String source, String target, Kind kind, @Nullable Integer sequence, @Nullable Double score,
                        @Nullable String effectPhrase, @Nullable String prerequisitePhrase) {

    public enum Kind { CONTAINS, TARGETS, SATISFIES, USES_PROCEDURE }

    public static GraphEdge of(String source, String target, Kind kind) {
        return new GraphEdge(source, target, kind, null, null, null, null);
    }
}
