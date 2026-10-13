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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tarik.ta.dto.ScreenRegion;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.tarik.ta.utils.NormalizedCoordinates.*;

class NormalizedCoordinatesTest {
    // A 4K screenshot of a screen scaled by 150%, whose logical size is what the Robot works in
    private static final int SCREENSHOT_WIDTH = 3840;
    private static final int SCREENSHOT_HEIGHT = 2160;
    private static final Dimension LOGICAL_SCREEN_SIZE = new Dimension(2560, 1440);

    private MockedStatic<UiCommonUtils> uiCommonUtils;

    @BeforeEach
    void setUp() {
        uiCommonUtils = mockStatic(UiCommonUtils.class);
        uiCommonUtils.when(UiCommonUtils::getScreenSize).thenReturn(LOGICAL_SCREEN_SIZE);
    }

    @AfterEach
    void tearDown() {
        uiCommonUtils.close();
    }

    @Test
    void normalize_shouldRoundToTheNearestThousandth() {
        assertThat(normalize(2294, SCREENSHOT_WIDTH)).isEqualTo(597);
        assertThat(normalize(2025, SCREENSHOT_WIDTH)).isEqualTo(527);
        assertThat(normalize(0, SCREENSHOT_WIDTH)).isZero();
        assertThat(normalize(SCREENSHOT_WIDTH, SCREENSHOT_WIDTH)).isEqualTo(NORMALIZED_RANGE);
    }

    @Test
    void toNormalizedRegion_shouldNormalizeEveryEdgeAgainstItsScreenshotExtent() {
        var region = toNormalizedRegion(new Rectangle(1920, 540, 384, 216), SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT);

        assertThat(region).isEqualTo(new ScreenRegion(500, 250, 100, 100));
    }

    @Test
    void toLogicalScreenPoint_shouldMapToTheLogicalScreen() {
        assertThat(toLogicalScreenPoint(527, 362)).isEqualTo(new Point(1349, 521));
        assertThat(toLogicalScreenPoint(0, 0)).isEqualTo(new Point(0, 0));
    }

    @Test
    void toLogicalScreenPoint_shouldStayOnTheLastPixel_whenPointIsTheFarEdge() {
        assertThat(toLogicalScreenPoint(1000, 1000)).isEqualTo(new Point(2559, 1439));
    }

    @Test
    void toLogicalScreenPoint_shouldReturnTheLocatedCentre_whenCentreWasNormalizedFromTheScreenshot() {
        int centreX = normalize(2294, SCREENSHOT_WIDTH);
        int centreY = normalize(785, SCREENSHOT_HEIGHT);

        // 2294 x 785 physical pixels are 1529 x 523 logical ones at 150%; one normalized step is 2.56 logical pixels wide
        assertThat(toLogicalScreenPoint(centreX, centreY)).isEqualTo(new Point(1528, 523));
    }

    @Test
    void toLogicalScreenOffset_shouldKeepTheDirection() {
        assertThat(toLogicalScreenOffset(-250, 500)).isEqualTo(new Point(-640, 720));
    }

    @Test
    void toLogicalScreenRectangle_shouldMapPositionAndSize() {
        var rectangle = toLogicalScreenRectangle(new ScreenRegion(500, 250, 100, 100));

        assertThat(rectangle).isEqualTo(new Rectangle(1280, 360, 256, 144));
    }
}
