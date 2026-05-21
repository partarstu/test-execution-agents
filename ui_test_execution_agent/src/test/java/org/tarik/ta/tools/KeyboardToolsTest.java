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
package org.tarik.ta.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeyboardToolsTest {

    private Robot robot;
    private MockedStatic<UiCommonUtils> commonUtilsMockedStatic;
    private MockedStatic<CommonUtils> coreUtilsMockedStatic;
    private MockedStatic<Toolkit> toolkitMockedStatic;
    private Clipboard clipboard;
    private KeyboardTools keyboardTools;

    @Mock
    private UiStateCheckAgent uiStateCheckAgent;

    @BeforeEach
    void setUp() {
        robot = mock(Robot.class);
        clipboard = mock(Clipboard.class);
        uiStateCheckAgent = mock(UiStateCheckAgent.class);
        keyboardTools = new KeyboardTools(uiStateCheckAgent);

        commonUtilsMockedStatic = mockStatic(UiCommonUtils.class);
        commonUtilsMockedStatic.when(UiCommonUtils::getRobot).thenReturn(robot);

        coreUtilsMockedStatic = mockStatic(CommonUtils.class);
        coreUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
        coreUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(null)).thenReturn(false);
        coreUtilsMockedStatic.when(() -> CommonUtils.isBlank(anyString())).thenCallRealMethod();
        coreUtilsMockedStatic.when(() -> CommonUtils.sleepMillis(anyInt())).thenAnswer(_ -> null);

        Toolkit toolkit = mock(Toolkit.class);
        when(toolkit.getSystemClipboard()).thenReturn(clipboard);

        toolkitMockedStatic = mockStatic(Toolkit.class);
        toolkitMockedStatic.when(Toolkit::getDefaultToolkit).thenReturn(toolkit);
    }

    @AfterEach
    void tearDown() {
        commonUtilsMockedStatic.close();
        coreUtilsMockedStatic.close();
        toolkitMockedStatic.close();
    }

    @Test
    @DisplayName("typeText should type text")
    void typeTextShouldType() {
        String text = "abc";

        keyboardTools.typeText(text, "true");

        // Verify clear actions (Ctrl+A, Backspace) - VK_A is used for both Ctrl+A and
        // typing 'a'
        verify(robot, times(1)).keyPress(KeyEvent.VK_CONTROL);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot, times(1)).keyPress(KeyEvent.VK_BACK_SPACE);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_BACK_SPACE);

        // Verify typing 'a', 'b', 'c' (VK_A pressed twice: once for Ctrl+A, once for
        // 'a')
        verify(robot, times(2)).keyPress(KeyEvent.VK_A);
        verify(robot, times(2)).keyRelease(KeyEvent.VK_A);
        verify(robot, times(1)).keyPress(KeyEvent.VK_B);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_B);
        verify(robot, times(1)).keyPress(KeyEvent.VK_C);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_C);
    }

    @Test
    @DisplayName("clearData should clear")
    void clearDataShouldClear() {
        keyboardTools.clearData();

        verify(robot).keyPress(KeyEvent.VK_CONTROL);
        verify(robot).keyPress(KeyEvent.VK_A);
        verify(robot).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot).keyRelease(KeyEvent.VK_A);
        verify(robot).keyPress(KeyEvent.VK_BACK_SPACE);
        verify(robot).keyRelease(KeyEvent.VK_BACK_SPACE);
    }

    @Test
    @DisplayName("pressKey should press and release a single key")
    void pressKeyShouldPressAndReleaseSingleKey() {
        keyboardTools.pressKey("A");
        verify(robot).keyPress(KeyEvent.VK_A);
        verify(robot).keyRelease(KeyEvent.VK_A);
    }

    @Test
    @DisplayName("pressKeys should press and release multiple keys")
    void pressKeysShouldPressAndReleaseMultipleKeys() {
        keyboardTools.pressKeys("Ctrl", "C");
        verify(robot).keyPress(KeyEvent.VK_CONTROL);
        verify(robot).keyPress(KeyEvent.VK_C);
        verify(robot).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot).keyRelease(KeyEvent.VK_C);
    }

    @Test
    @DisplayName("typeText should handle non-ASCII characters with copy-paste")
    void typeTextShouldHandleNonAsciiWithCopyPaste() {
        String text = "你好";

        keyboardTools.typeText(text, "true");

        // Verify clipboard was set for each non-ASCII character
        verify(clipboard, times(2)).setContents(any(StringSelection.class), any());

        // Verify Ctrl+V was pressed for each character (plus once for Ctrl+A during
        // clear)
        // Clear: Ctrl+A, Backspace, then two characters: Ctrl+V (twice)
        verify(robot, times(3)).keyPress(KeyEvent.VK_CONTROL); // 1x for clear (Ctrl+A), 2x for paste
        verify(robot, times(2)).keyPress(KeyEvent.VK_V);
        verify(robot, times(3)).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot, times(2)).keyRelease(KeyEvent.VK_V);
    }
}
