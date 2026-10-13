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
package org.tarik.ta.utils;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.ScreenRegion;

import java.awt.Point;
import java.awt.Rectangle;

import static java.lang.Math.min;
import static java.lang.Math.round;
import static org.tarik.ta.utils.UiCommonUtils.getScreenSize;

/**
 * Converts between the pixels of a whole-screen screenshot, the {@code [0, 1000]} range every model reads and writes screen
 * coordinates in, and the logical screen coordinates the {@link java.awt.Robot} works in.
 */
public final class NormalizedCoordinates {
    public static final int NORMALIZED_RANGE = 1000;

    private NormalizedCoordinates() {
    }

    public static @NotNull ScreenRegion toNormalizedRegion(@NotNull Rectangle screenshotBox, int screenshotWidth,
                                                           int screenshotHeight) {
        return new ScreenRegion(normalize(screenshotBox.x, screenshotWidth), normalize(screenshotBox.y, screenshotHeight),
                normalize(screenshotBox.width, screenshotWidth), normalize(screenshotBox.height, screenshotHeight));
    }

    public static int normalize(int screenshotPixel, int screenshotExtent) {
        return (int) round((double) screenshotPixel * NORMALIZED_RANGE / screenshotExtent);
    }

    public static @NotNull Point toLogicalScreenPoint(int normalizedX, int normalizedY) {
        var screenSize = getScreenSize();
        return new Point(toLogicalPosition(normalizedX, screenSize.width), toLogicalPosition(normalizedY, screenSize.height));
    }

    public static @NotNull Point toLogicalScreenOffset(int normalizedXOffset, int normalizedYOffset) {
        var screenSize = getScreenSize();
        return new Point(toLogicalLength(normalizedXOffset, screenSize.width), toLogicalLength(normalizedYOffset, screenSize.height));
    }

    public static @NotNull Rectangle toLogicalScreenRectangle(@NotNull ScreenRegion normalizedRegion) {
        var screenSize = getScreenSize();
        return new Rectangle(toLogicalPosition(normalizedRegion.x(), screenSize.width),
                toLogicalPosition(normalizedRegion.y(), screenSize.height),
                toLogicalLength(normalizedRegion.width(), screenSize.width),
                toLogicalLength(normalizedRegion.height(), screenSize.height));
    }

    private static int toLogicalPosition(int normalizedPosition, int screenExtent) {
        // 1000 is the far edge of the screen, one pixel past the last one the pointer can reach
        return min(toLogicalLength(normalizedPosition, screenExtent), screenExtent - 1);
    }

    private static int toLogicalLength(int normalizedLength, int screenExtent) {
        return (int) round((double) normalizedLength * screenExtent / NORMALIZED_RANGE);
    }
}
