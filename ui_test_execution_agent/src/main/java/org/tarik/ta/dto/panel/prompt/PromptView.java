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

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * The open prompt as the browser receives it: {@code payload} is the redacted {@link PromptPayload} of {@code type}, and {@code
 * remainingMillis} and {@code waitedMillis} the time to its deadline (a countdown's, the frozen time left of a paused one or, once
 * held or without one, the prompt upper bound's) and the time since it opened, both when it was sent; {@code countdownMillis} is
 * the whole countdown, or 0 without one.
 */
public record PromptView(UUID id, String type, JsonNode payload, long remainingMillis, long waitedMillis, boolean countdown,
                         long countdownMillis, boolean held, boolean paused, boolean hasDefault) {
}
