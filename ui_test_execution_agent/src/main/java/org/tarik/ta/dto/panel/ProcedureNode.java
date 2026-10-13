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
package org.tarik.ta.dto.panel;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A procedure of the current item's decomposition at its {@code depth} below the root; {@code durationMillis} is the wall time from
 * its start to its end, set once it has ended.
 */
public record ProcedureNode(UUID id, String description, @Nullable String expectedResults, boolean atomic, boolean optional,
                            int depth, Status status, @Nullable Long durationMillis) {

    public static ProcedureNode pending(UUID id, String description, @Nullable String expectedResults, boolean atomic, boolean optional,
                                        int depth) {
        return new ProcedureNode(id, description, expectedResults, atomic, optional, depth, Status.PENDING, null);
    }

    public ProcedureNode withTexts(String newDescription, @Nullable String newExpectedResults) {
        return new ProcedureNode(id, newDescription, newExpectedResults, atomic, optional, depth, status, durationMillis);
    }

    public ProcedureNode withStatus(Status newStatus, @Nullable Long newDurationMillis) {
        return new ProcedureNode(id, description, expectedResults, atomic, optional, depth, newStatus, newDurationMillis);
    }

    public enum Status {
        PENDING, RUNNING, COMPLETED, SKIPPED, FAILED, ABANDONED;

        public static Status of(ProcedureOutcome outcome) {
            return switch (outcome) {
                case COMPLETED -> COMPLETED;
                case SKIPPED -> SKIPPED;
                case FAILED -> FAILED;
                case ABANDONED -> ABANDONED;
            };
        }
    }
}
