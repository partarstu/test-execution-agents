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

import java.awt.*;

/**
 * A rectangular region on the screen in physical pixel coordinates (before any DPI scaling).
 * Callers are responsible for applying DPI scaling when converting to logical coordinates
 * for Robot interaction or screen capture.
 */
@Description("A rectangular region on the screen defined in physical pixel coordinates.")
public record ScreenRegion(
        @Description("The x-coordinate of the top-left corner in physical screen pixels") int x,
        @Description("The y-coordinate of the top-left corner in physical screen pixels") int y,
        @Description("The width of the region in physical screen pixels") int width,
        @Description("The height of the region in physical screen pixels") int height) {

    public Rectangle toRectangle() {
        return new Rectangle(x, y, width, height);
    }
}
