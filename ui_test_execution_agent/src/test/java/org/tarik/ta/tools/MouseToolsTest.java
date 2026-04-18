/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.*;
import java.awt.event.InputEvent;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class MouseToolsTest {

    @Mock
    private Robot mockRobot;
    @Mock
    private UiTestAgentConfig mockConfig;

    private MouseTools mouseTools;

    @BeforeEach
    void setUp() throws Exception {
        mouseTools = new MouseTools(mock(UiStateCheckAgent.class), mockConfig);
        setMockRobot(mockRobot);
    }

    @AfterEach
    void tearDown() throws Exception {
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