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
package org.tarik.ta.knowledge_graph.model.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * An unexpected event observed on a UI element or a procedure during a run. The test case name is a property rather than an edge
 * to the {@code TestCase} node, whose usage edges are written asynchronously and may not exist yet.
 */
public record ExecutionAnomaly(@NotNull UUID id, @NotNull Kind kind, @NotNull UUID subjectId, @NotNull Instant occurredAt,
                               @Nullable String testCaseName, @NotNull String details, @NotNull String mode) {
    public static final String LABEL = "ExecutionAnomaly";
    public static final String PROP_KIND = "kind";
    public static final String PROP_SUBJECT_ID = "subjectId";
    public static final String PROP_OCCURRED_AT = "occurredAt";
    public static final String PROP_TEST_CASE_NAME = "testCaseName";
    public static final String PROP_DETAILS = "details";

    public enum Kind {
        ELEMENT_LOCATION_FAILED(UiElement.LABEL),
        STEP_FAILED(Procedure.LABEL),
        TIMING_OUTLIER(Procedure.LABEL);

        private final String subjectLabel;

        Kind(String subjectLabel) {
            this.subjectLabel = subjectLabel;
        }

        public String subjectLabel() {
            return subjectLabel;
        }
    }

    public ExecutionAnomaly {
        requireNonNull(id, "id");
        requireNonNull(kind, "kind");
        requireNonNull(subjectId, "subjectId");
        requireNonNull(occurredAt, "occurredAt");
        requireNonNull(details, "details");
        requireNonNull(mode, "mode");
    }
}
