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
package org.tarik.ta.dto;

/**
 * Options of the post-failure or post-halt rerun flow. {@code isPrecondition} renames the rerun-step action, {@code
 * atomicRerunAvailable} is false when no atomic procedure has executed, e.g. after a decomposition failure, and {@code halt} tells
 * where the operator halted, which decides whether the run can go on without a rerun.
 */
public record RerunOptions(boolean isPrecondition, boolean atomicRerunAvailable, Halt halt) {

    public enum Halt {
        NONE,
        BEFORE_EXECUTION,
        AFTER_SUCCESS
    }
}
