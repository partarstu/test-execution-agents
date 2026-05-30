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
package org.tarik.ta.core.dto;

import org.jetbrains.annotations.NotNull;

/**
 * Identifies the test step or precondition that is about to be executed. It is streamed as the message of a
 * {@code working} task status update right before the item runs, so that an observing client (e.g. a live dashboard)
 * can show what is currently being executed.
 *
 * @param activityType the kind of item being executed, either {@link #TEST_STEP} or {@link #PRECONDITION}
 * @param description   the human-readable description of the item being executed
 */
public record ExecutionActivity(@NotNull String activityType, @NotNull String description) {
    public static final String TEST_STEP = "test_step";
    public static final String PRECONDITION = "precondition";
}
