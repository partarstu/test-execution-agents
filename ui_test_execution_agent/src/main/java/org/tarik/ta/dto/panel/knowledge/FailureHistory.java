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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The failure hints of a procedure, current ones first; hints that expired more than {@code staleDays} ago are deleted at startup
 * and so are missing.
 */
public record FailureHistory(ProcedureRef procedure, int staleDays, List<Hint> hints) {

    public FailureHistory {
        hints = List.copyOf(hints);
    }

    /** A hint with an empty resolution is unresolved; {@code expiredAt} is null while it is current. */
    public record Hint(UUID id, String category, String symptom, String resolution, int occurrences, Instant lastOccurred,
                       @Nullable Instant expiredAt, String mode) {
    }
}
