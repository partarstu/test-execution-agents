/*
 * agent-core - ${project.description}
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
 * Configuration for retry logic.
 *
 * @param maxRetries    Maximum number of retry attempts.
 * @param delayMillis   Delay before the next retry in milliseconds.
 * @param timeoutMillis Total timeout for the operation including retries.
 */
public record RetryPolicy(int maxRetries, long delayMillis, long timeoutMillis) {
}