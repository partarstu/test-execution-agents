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

/**
 * A procedure loaded into the procedure editor: {@code id} is null for one not persisted yet, and {@code parents} and {@code
 * testCases} name what uses it, for the warning before its deletion.
 */
public record EditorNode(@Nullable UUID id, boolean precondition, EditorFields fields, @Nullable ElementCard targetElement,
                         List<ChildRow> children, List<String> parents, List<String> testCases) {
    public EditorNode {
        children = List.copyOf(children);
        parents = List.copyOf(parents);
        testCases = List.copyOf(testCases);
    }

    /** A child step; {@code id} is null while it is only a suggested description that the operator still has to edit and save. */
    public record ChildRow(@Nullable UUID id, String description, boolean atomic, boolean precondition, List<String> effects,
                           List<String> negatedEffects, @Nullable String screenshotId) {
        public ChildRow {
            effects = List.copyOf(effects);
            negatedEffects = List.copyOf(negatedEffects);
        }
    }
}
