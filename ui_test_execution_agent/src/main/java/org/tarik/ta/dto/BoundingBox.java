/*
 * ui-test-execution-agent - ${project.description}
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

@Description("a single bounding box with coordinates")
public record BoundingBox(
        @Description("The y-coordinate of the top-left corner (y_min)") int y1,
        @Description("The x-coordinate of the top-left corner (x_min)") int x1,
        @Description("The y-coordinate of the bottom-right corner (y_max)") int y2,
        @Description("The x-coordinate of the bottom-right corner (x_max)") int x2) {
    public Rectangle getActualBoundingBox(int actualImageWidth, int actualImageHeight, boolean isAlreadyNormalized) {
        if (isAlreadyNormalized) {
            // Coordinates are normalized between 0 and 1000
            var actualX1 = x1 * actualImageWidth / 1000;
            var actualY1 = y1 * actualImageHeight / 1000;
            var actualX2 = x2 * actualImageWidth / 1000;
            var actualY2 = y2 * actualImageHeight / 1000;
            return new Rectangle(actualX1, actualY1, actualX2 - actualX1, actualY2 - actualY1);
        } else {
            // Coordinates are absolute
            return new Rectangle(x1, y1, x2 - x1, y2 - y1);
        }
    }
}