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

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import org.jetbrains.annotations.NotNull;

import java.awt.Rectangle;
import java.util.List;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;
import static org.tarik.ta.utils.NormalizedCoordinates.NORMALIZED_RANGE;

/**
 * A single located UI element as the position to click plus its size, where a zero width or height means nothing was identified.
 * Grounding models localize one target at a time, so the locator wraps it into the list its pipeline works with.
 */
@Description("the identified UI element: the position to click plus the size of that element")
public record ElementRegion(
        @Description("The x-coordinate of the position to click on the element") int x,
        @Description("The y-coordinate of the position to click on the element") int y,
        @Description("The width of the element, 0 if no element was identified") int width,
        @Description("The height of the element, 0 if no element was identified") int height) implements IdentifiedRegions {

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE_IF_LAST)
    public static ElementRegion endExecutionAndGetFinalResult(@P(FINAL_RESULT_PARAM_DESCRIPTION) ElementRegion result) {
        return result;
    }

    @Override
    public @NotNull List<Rectangle> toRectangles(int imageWidth, int imageHeight, boolean alreadyNormalized) {
        if (width <= 0 || height <= 0) {
            return List.of();
        }
        if (!alreadyNormalized) {
            return List.of(centeredRectangle(x, y, width, height));
        }
        return List.of(centeredRectangle(x * imageWidth / NORMALIZED_RANGE, y * imageHeight / NORMALIZED_RANGE,
                width * imageWidth / NORMALIZED_RANGE, height * imageHeight / NORMALIZED_RANGE));
    }

    private static @NotNull Rectangle centeredRectangle(int centerX, int centerY, int width, int height) {
        return new Rectangle(centerX - width / 2, centerY - height / 2, width, height);
    }
}
