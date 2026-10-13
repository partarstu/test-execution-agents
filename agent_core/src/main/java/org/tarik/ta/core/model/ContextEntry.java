/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.model;

import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A value in the test execution context together with where it came from and the precondition or test step it belongs to.
 * The step is null for the data table of the test case, and the name is null for a data line which names no key.
 */
public record ContextEntry(@Nullable String name, @Nullable Object value, @NotNull Provenance source, @Nullable Step step) {

    public enum Provenance {
        DATA_TABLE("test case data table"),
        TEST_STEP("test step data"),
        TOOL("stored during execution");

        private final String description;

        Provenance(String description) {
            this.description = description;
        }

        @JsonValue
        public String description() {
            return description;
        }
    }

    public enum StepType {
        PRECONDITION("precondition"),
        TEST_STEP("test step");

        private final String description;

        StepType(String description) {
            this.description = description;
        }

        @JsonValue
        public String description() {
            return description;
        }
    }

    /**
     * A precondition or test step of the run, numbered in execution order starting with 1.
     */
    public record Step(int number, @NotNull StepType type, @NotNull String description) {
    }
}
