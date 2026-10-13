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

@Description("Represents the location of UI element on the screen.")
public record LocatedElementInfo(
        @Description("The name of the element retrieved from DB and used during location.") String name,
        @Description("The ID of the UI element, in the same short form it was provided in.") String elementId,
        @Description("The X coordinate of the center point of UI element, normalized to the screen width in [0, 1000].")
        int centerXCoordinate,
        @Description("The Y coordinate of the center point of UI element, normalized to the screen height in [0, 1000].")
        int centerYCoordinate,
        @Description("The screen region where the UI element was identified, normalized to [0, 1000].") ScreenRegion elementScreenRegion) {
}
