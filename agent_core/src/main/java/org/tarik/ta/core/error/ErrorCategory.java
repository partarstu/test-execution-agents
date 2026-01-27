/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
