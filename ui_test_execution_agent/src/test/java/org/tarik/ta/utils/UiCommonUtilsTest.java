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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.dto.ScreenRegion;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.PointerInfo;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import static org.tarik.ta.utils.UiCommonUtils.captureElementScreenshot;
import static org.tarik.ta.utils.UiCommonUtils.captureScreenPart;
import static org.tarik.ta.utils.UiCommonUtils.getColorByName;
import static org.tarik.ta.utils.UiCommonUtils.getColorName;
import static org.tarik.ta.utils.UiCommonUtils.getMouseLocation;
import static org.tarik.ta.utils.UiCommonUtils.getScreenSize;
import static org.tarik.ta.utils.UiCommonUtils.getStaticFieldValue;

@SuppressWarnings("ALL")
@ExtendWith(MockitoExtension.class)
@DisplayName("CommonUtils Tests")
class UiCommonUtilsTest {
    @Mock
    private Toolkit mockToolkit;
    @Mock
    private Dimension mockScreenSize;
    @Mock
    private PointerInfo mockPointerInfo;


    // Static mocks
    private MockedStatic<Toolkit> toolkitMockedStatic;
    private MockedStatic<MouseInfo> mouseInfoMockedStatic;
    private MockedConstruction<Robot> robotMockedConstruction;

    // Constants
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;

    @BeforeEach
    void setUp() {
        robotMockedConstruction = mockConstruction(Robot.class);
        toolkitMockedStatic = mockStatic(Toolkit.class);
        mouseInfoMockedStatic = mockStatic(MouseInfo.class);

        // Toolkit
        lenient().when(Toolkit.getDefaultToolkit()).thenReturn(mockToolkit);
        lenient().when(mockToolkit.getScreenSize()).thenReturn(mockScreenSize);
        lenient().when(mockScreenSize.getWidth()).thenReturn((double) SCREEN_WIDTH);
        lenient().when(mockScreenSize.getHeight()).thenReturn((double) SCREEN_HEIGHT);

        // MouseInfo
        lenient().when(MouseInfo.getPointerInfo()).thenReturn(mockPointerInfo);
        lenient().when(mockPointerInfo.getLocation()).thenReturn(new Point(100, 150));
    }

    @AfterEach
    void tearDown() {
        toolkitMockedStatic.close();
        mouseInfoMockedStatic.close();
        robotMockedConstruction.close();
    }

    @Test
    @DisplayName("getStaticFieldValue: Should return static field value")
    void getStaticFieldValueSuccess() throws NoSuchFieldException, IllegalAccessException {
        // Given
        Field colorField = Color.class.getField("RED"); // Use a real static field

        // When
        Object value = getStaticFieldValue(colorField);

        // Then
        assertEquals(Color.RED, value);
    }

    @Test
    @DisplayName("getColorByName: Should return correct Color for valid name")
    void getColorByNameValid() {
        // Given
        String colorName = "BLUE";

        // When
        Color result = getColorByName(colorName);

        // Then
        assertEquals(Color.BLUE, result);
    }

    @Test
    @DisplayName("getColorByName: Should return correct Color for valid lowercase name")
    void getColorByNameValidLowercase() {
        // Given
        String colorName = "green";

        // When
        Color result = getColorByName(colorName);

        // Then
        assertEquals(Color.GREEN, result);
    }

    @Test
    @DisplayName("getColorByName: Should throw exception for invalid name")
    void getColorByNameInvalid() {
        // Given
        String invalidColorName = "NonExistentColor";

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            getColorByName(invalidColorName);
        });
        assertTrue(exception.getMessage().contains(invalidColorName));
    }


    @Test
    @DisplayName("getColorName: Should return correct name for standard Color")
    void getColorNameValid() {
        // Given
        Color color = Color.YELLOW;

        // When
        String name = getColorName(color);

        // Then
        assertEquals("yellow", name.toLowerCase()); // Standard names might be lower/upper case
    }

    @Test
    @DisplayName("getColorName: Should throw exception for non-standard Color")
    void getColorNameNonStandard() {
        // Given
        Color customColor = new Color(1, 2, 3); // A color not defined as a public static final field in Color

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            getColorName(customColor);
        });
        assertTrue(exception.getMessage().contains(customColor.toString()));
    }

    @Test
    @DisplayName("getScreenSize: Should return correct dimension from Toolkit")
    void getScreenSizeReturnsCorrectly() {
        // Given
        // Toolkit mock is set up in @BeforeEach

        // When
        Dimension size = getScreenSize();

        // Then
        assertEquals(mockScreenSize, size);
        verify(mockToolkit).getScreenSize();
    }

    @Test
    @DisplayName("getMouseLocation: Should return correct location from MouseInfo")
    void getMouseLocationReturnsCorrectly() {
        // Given
        Point expectedLocation = new Point(100, 150); // Set in @BeforeEach

        // When
        Point location = getMouseLocation();

        // Then
        assertEquals(expectedLocation, location);
        verify(mockPointerInfo).getLocation();
    }

    @Test
    @DisplayName("captureElementScreenshot: Should capture the logical screen area of the normalized region")
    void captureElementScreenshotCapturesTheLogicalAreaOfTheNormalizedRegion() {
        // Given
        var elementImage = new BufferedImage(256, 144, BufferedImage.TYPE_INT_RGB);
        try (var uiCommonUtils = mockStatic(UiCommonUtils.class, CALLS_REAL_METHODS)) {
            uiCommonUtils.when(UiCommonUtils::getScreenSize).thenReturn(new Dimension(2560, 1440));
            uiCommonUtils.when(() -> captureScreenPart(any(Rectangle.class), anyBoolean())).thenReturn(elementImage);

            // When
            var capturedImage = captureElementScreenshot(new ScreenRegion(500, 250, 100, 100));

            // Then
            assertThat(capturedImage).isSameAs(elementImage);
            uiCommonUtils.verify(() -> captureScreenPart(new Rectangle(1280, 360, 256, 144), true));
        }
    }
}