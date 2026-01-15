package org.tarik.ta.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.*;
import java.awt.event.InputEvent;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MouseToolsTest {

    @Mock
    private Robot mockRobot;

    private MouseTools mouseTools;

    @BeforeEach
    void setUp() throws Exception {
        mouseTools = new MouseTools(mock(UiStateCheckAgent.class));
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
        verify(mockRobot, org.mockito.Mockito.times(2)).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(mockRobot, org.mockito.Mockito.times(2)).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    void moveMouseTo_shouldMove_whenCoordinatesAreValid() {
        int x = 500;
        int y = 500;

        mouseTools.moveMouseTo(x, y);

        verify(mockRobot).mouseMove(x, y);
    }
}