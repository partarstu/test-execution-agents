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

@Description("A rectangular region on the screen, normalized to [0, 1000] of the screen width and height.")
public record ScreenRegion(
        @Description("The x-coordinate of the top-left corner, normalized to the screen width in [0, 1000]") int x,
        @Description("The y-coordinate of the top-left corner, normalized to the screen height in [0, 1000]") int y,
        @Description("The width of the region, normalized to the screen width in [0, 1000]") int width,
        @Description("The height of the region, normalized to the screen height in [0, 1000]") int height) {
}
