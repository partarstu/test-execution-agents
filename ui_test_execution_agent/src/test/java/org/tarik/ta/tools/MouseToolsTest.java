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
package org.tarik.ta.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.event.InputEvent;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.lenient;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class MouseToolsTest {

    @Mock
    private Robot mockRobot;
    @Mock
    private UiTestAgentConfig mockConfig;
    @Mock
    private GraphicsEnvironment mockGraphicsEnvironment;
    @Mock
    private GraphicsDevice mockGraphicsDevice;
    @Mock
    private GraphicsConfiguration mockGraphicsConfiguration;
    @Mock
    private AffineTransform mockAffineTransform;

    private MouseTools mouseTools;
    private MockedStatic<GraphicsEnvironment> graphicsEnvironmentMockedStatic;

    @BeforeEach
    void setUp() throws Exception {
        graphicsEnvironmentMockedStatic = mockStatic(GraphicsEnvironment.class);
        graphicsEnvironmentMockedStatic.when(GraphicsEnvironment::getLocalGraphicsEnvironment).thenReturn(mockGraphicsEnvironment);
        lenient().when(mockGraphicsEnvironment.getDefaultScreenDevice()).thenReturn(mockGraphicsDevice);
        lenient().when(mockGraphicsDevice.getDefaultConfiguration()).thenReturn(mockGraphicsConfiguration);
        lenient().when(mockGraphicsConfiguration.getDefaultTransform()).thenReturn(mockAffineTransform);
        lenient().when(mockAffineTransform.getScaleX()).thenReturn(1.0);
        lenient().when(mockAffineTransform.getScaleY()).thenReturn(1.0);

        mouseTools = new MouseTools(mock(UiStateCheckAgent.class), mockConfig);
        setMockRobot(mockRobot);
    }

    @AfterEach
    void tearDown() throws Exception {
        graphicsEnvironmentMockedStatic.close();
        setMockRobot(null);
    }

    private void setMockRobot(Robot robot) throws Exception {
        Field robotField = UiCommonUtils.class.getDeclaredField("robot");
        robotField.setAccessible(true);
        robotField.set(null, robot);
    }

    @Test
    void rightMouseClick_shouldClick_whenCoordinatesAreValid() {
        int x = 100;
        int y = 200;

        mouseTools.rightMouseClick(x, y);

        verify(mockRobot).mouseMove(x, y);
        verify(mockRobot).mousePress(InputEvent.BUTTON3_DOWN_MASK);
        verify(mockRobot).mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    @Test
    void rightMouseClick_shouldThrowException_whenCoordinatesAreInvalid() {
        assertThatThrownBy(() -> mouseTools.rightMouseClick(-1, 100))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Invalid coordinates");
    }

    @Test
    void leftMouseClick_shouldClick_whenCoordinatesAreValid() {
        int x = 150;
        int y = 250;

        mouseTools.leftMouseClick(x, y);

        verify(mockRobot).mouseMove(x, y);
        verify(mockRobot).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(mockRobot).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    void leftMouseDoubleClick_shouldDoubleClick_whenCoordinatesAreValid() {
        int x = 10;
        int y = 20;

        mouseTools.leftMouseDoubleClick(x, y);

        verify(mockRobot).mouseMove(x, y);
        // Double click involves press-release twice
        verify(mockRobot, Mockito.times(2)).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(mockRobot, Mockito.times(2)).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    void moveMouseTo_shouldMove_whenCoordinatesAreValid() {
        int x = 500;
        int y = 500;

        mouseTools.moveMouseTo(x, y);

        verify(mockRobot).mouseMove(x, y);
    }
}