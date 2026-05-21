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
package org.tarik.ta.core.error;

/**
 * Categories of errors that can occur during agent execution.
 * These categories determine the retry strategy and logging level.
 */
public enum ErrorCategory {
    /**
     * User explicitly interrupted the execution.
     * Retry: NO
     * Severity: INFO
     */
    TERMINATION_BY_USER,

    /**
     * A transient error occurred with a tool or external service (e.g., network glitch).
     * Retry: YES (Exponential backoff)
     * Severity: WARN
     */
    TRANSIENT_TOOL_ERROR,

    /**
     * A fatal error that cannot be recovered from (e.g., invalid configuration).
     * Retry: NO
     * Severity: ERROR
     */
    NON_RETRYABLE_ERROR,

    /**
     * Execution timed out.
     * Retry: YES (Bounded if budget allows)
     * Severity: WARN
     */
    TIMEOUT,

    /**
     * Unknown error category.
     * Retry: NO
     * Severity: ERROR
     */
    UNKNOWN
}
