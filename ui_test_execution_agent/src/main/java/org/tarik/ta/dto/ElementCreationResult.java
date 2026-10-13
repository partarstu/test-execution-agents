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

import dev.langchain4j.model.output.structured.Description;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Description("Result of creating a UI element in the database")
public record ElementCreationResult(
        @Description("Whether the UI element was created successfully") boolean success,
        @Description("Short alias of the created UI element, or null when creation failed") @Nullable String elementId,
        @Description("Name of the created UI element, or null when creation failed") @Nullable String elementName,
        @Description("Short message explaining the creation result") @NotNull String message) {
}
