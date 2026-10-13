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

import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.AgentConfig.ModelProvider;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.dto.UiStateCheckResult;
import org.tarik.ta.model.ImageContentCreator;
import org.tarik.ta.tools.MouseTools.ScrollDirection;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;
import static org.tarik.ta.core.error.ErrorCategory.TIMEOUT;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class MouseToolsTest {
    // Twice as wide as high, so a normalized x maps to twice its value and a normalized y to itself
    private static final Dimension SCREEN_SIZE = new Dimension(2000, 1000);

    @Mock
    private Robot mockRobot;
    @Mock
    private UiTestAgentConfig mockConfig;
    @Mock
    private Toolkit mockToolkit;
    @Mock
    private UiStateCheckAgent stateCheckAgent;

    private MouseTools mouseTools;
    private MockedStatic<Toolkit> toolkitMockedStatic;

    @BeforeEach
    void setUp() throws Exception {
        toolkitMockedStatic = mockStatic(Toolkit.class);
        toolkitMockedStatic.when(Toolkit::getDefaultToolkit).thenReturn(mockToolkit);
        lenient().when(mockToolkit.getScreenSize()).thenReturn(SCREEN_SIZE);

        mouseTools = new MouseTools(stateCheckAgent, mockConfig);
        setMockRobot(mockRobot);
    }

    @AfterEach
    void tearDown() throws Exception {
        toolkitMockedStatic.close();
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

        verify(mockRobot).mouseMove(200, 200);
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

        verify(mockRobot).mouseMove(300, 250);
        verify(mockRobot).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(mockRobot).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    void leftMouseClick_shouldRejectWithoutClicking_whenCoordinatesAreScreenPixels() {
        assertThatThrownBy(() -> mouseTools.leftMouseClick(2294, 785))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Invalid coordinates: x=2294, y=785. Coordinates must be integers in [0, 1000], normalized to the " +
                        "screen, exactly as returned by the UI element location tool.")
                .extracting(e -> ((ToolExecutionException) e).getErrorCategory())
                .isEqualTo(TRANSIENT_TOOL_ERROR);
        verifyNoInteractions(mockRobot);
    }

    @Test
    void leftMouseClick_shouldClickTheLastScreenPixel_whenCoordinatesAreTheFarEdge() {
        mouseTools.leftMouseClick(1000, 1000);

        verify(mockRobot).mouseMove(1999, 999);
    }

    @Test
    void leftMouseDoubleClick_shouldDoubleClick_whenCoordinatesAreValid() {
        int x = 10;
        int y = 20;

        mouseTools.leftMouseDoubleClick(x, y);

        verify(mockRobot).mouseMove(20, 20);
        // Double click involves press-release twice
        verify(mockRobot, Mockito.times(2)).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(mockRobot, Mockito.times(2)).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    void moveMouseTo_shouldMove_whenCoordinatesAreValid() {
        int x = 500;
        int y = 500;

        mouseTools.moveMouseTo(x, y);

        verify(mockRobot).mouseMove(1000, 500);
    }

    @Test
    void clickAndDrag_shouldDragByTheScreenDistanceOfTheNormalizedOffsets() {
        try (var uiCommonUtils = mockScreen()) {
            uiCommonUtils.when(UiCommonUtils::getMouseLocation).thenReturn(new Point(100, 100));

            mouseTools.clickAndDrag(50, -20);
        }

        verify(mockRobot).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(mockRobot).mouseMove(200, 80);
        verify(mockRobot).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    void clickAndDrag_shouldRejectWithoutDragging_whenOffsetExceedsTheScreen() {
        assertThatThrownBy(() -> mouseTools.clickAndDrag(0, -1001))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Offsets must be integers in [-1000, 1000]");
        verifyNoInteractions(mockRobot);
    }

    @Test
    void scrollUntilStateAchieved_shouldScrollDownByConfiguredNotches_untilStateIsReached() {
        stubStateChecks(false, false, true);

        String result;
        try (var ignored = mockScreen()) {
            result = mouseTools.scrollUntilStateAchieved(ScrollDirection.DOWN, "The footer is visible");
        }

        assertThat(result).isEqualTo("The expected state 'The footer is visible' has been reached, the action is complete.");
        verify(mockRobot, times(2)).mouseWheel(3);
        verifyNoMoreInteractions(mockRobot);
    }

    @Test
    void clickElementUntilStateAchieved_shouldClick_untilStateIsReached() {
        stubStateChecks(false, true);

        String result;
        try (var ignored = mockScreen()) {
            result = mouseTools.clickElementUntilStateAchieved(10, 20, "The menu is open");
        }

        assertThat(result).isEqualTo("The expected state 'The menu is open' has been reached, the action is complete.");
        verify(mockRobot).mouseMove(20, 20);
        verify(mockRobot).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(mockRobot).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    void scrollUntilStateAchieved_shouldScrollUpWithNegativeNotches() {
        stubStateChecks(false, true);

        try (var ignored = mockScreen()) {
            mouseTools.scrollUntilStateAchieved(ScrollDirection.UP, "The header is visible");
        }

        verify(mockRobot).mouseWheel(-3);
        verifyNoMoreInteractions(mockRobot);
    }

    @Test
    void scrollUntilStateAchieved_shouldSendTheExpectedStateAndThePerformedActionAsSectionsBeforeTheScreenshot() {
        lenient().when(mockConfig.getMouseScrollNotchesPerAttempt()).thenReturn(3);
        when(mockConfig.getMaxActionExecutionDurationMillis()).thenReturn(10_000);
        when(mockConfig.getActionRetryPolicy()).thenReturn(new RetryPolicy(1, 0, 0));
        when(stateCheckAgent.getImageContentCreator(mockConfig))
                .thenReturn(new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000));
        when(stateCheckAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<Result<?>>>getArgument(0).get();
            return stateCheck(true);
        });

        try (var ignored = mockScreen()) {
            mouseTools.scrollUntilStateAchieved(ScrollDirection.DOWN, "The footer is visible");
        }

        verify(stateCheckAgent).check(eq(List.of(TextContent.from("""
                ## Expected state of the screen
                The footer is visible

                ## Performed action
                Scrolling mouse wheel down until The footer is visible

                ## Screenshot"""))), any());
    }

    @Test
    void scrollUntilStateAchieved_shouldNotScroll_whenStateIsAlreadyReached() {
        stubStateChecks(true);

        String result;
        try (var ignored = mockScreen()) {
            result = mouseTools.scrollUntilStateAchieved(ScrollDirection.DOWN, "The footer is visible");
        }

        assertThat(result).isEqualTo("The expected state 'The footer is visible' has been reached, the action is complete.");
        verify(mockRobot, never()).mouseWheel(anyInt());
    }

    @Test
    void scrollUntilStateAchieved_shouldFailWithTimeout_whenStateIsNeverReached() {
        stubStateChecks(false);
        when(mockConfig.getMaxActionExecutionDurationMillis()).thenReturn(0);

        try (var ignored = mockScreen()) {
            assertThatThrownBy(() -> mouseTools.scrollUntilStateAchieved(ScrollDirection.DOWN, "The footer is visible"))
                    .isInstanceOf(ToolExecutionException.class)
                    .hasMessage("Failed to reach expected state 'The footer is visible' after scrolling down within the timeout " +
                            "of 0 millis")
                    .extracting(e -> ((ToolExecutionException) e).getErrorCategory())
                    .isEqualTo(TIMEOUT);
        }
        verify(mockRobot).mouseWheel(3);
    }

    @Test
    void scrollUntilStateAchieved_shouldReject_whenExpectedStateIsBlank() {
        when(mockConfig.getMouseScrollNotchesPerAttempt()).thenReturn(3);

        assertThatThrownBy(() -> mouseTools.scrollUntilStateAchieved(ScrollDirection.DOWN, " "))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Expected state description cannot be empty");
        verifyNoInteractions(mockRobot, stateCheckAgent);
    }

    @Test
    void scrollUntilStateAchieved_shouldReject_whenDirectionIsMissing() {
        assertThatThrownBy(() -> mouseTools.scrollUntilStateAchieved(null, "The footer is visible"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Scroll direction must be provided");
        verifyNoInteractions(mockRobot, stateCheckAgent);
    }

    private void stubStateChecks(Boolean first, Boolean... next) {
        lenient().when(mockConfig.getMouseScrollNotchesPerAttempt()).thenReturn(3);
        lenient().when(mockConfig.getMaxActionExecutionDurationMillis()).thenReturn(10_000);
        when(mockConfig.getActionRetryPolicy()).thenReturn(new RetryPolicy(1, 0, 0));
        when(stateCheckAgent.getImageContentCreator(mockConfig))
                .thenReturn(new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000));
        var nextResults = Arrays.stream(next).map(MouseToolsTest::stateCheck).toArray(OperationExecutionResult[]::new);
        when(stateCheckAgent.executeAndGetResult(any())).thenReturn(stateCheck(first), nextResults);
    }

    private static OperationExecutionResult<UiStateCheckResult> stateCheck(boolean reached) {
        return new OperationExecutionResult<>(SUCCESS, "ok", new UiStateCheckResult(reached, "checked"));
    }

    private MockedStatic<UiCommonUtils> mockScreen() {
        var uiCommonUtils = mockStatic(UiCommonUtils.class);
        uiCommonUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        uiCommonUtils.when(UiCommonUtils::getRobot).thenReturn(mockRobot);
        uiCommonUtils.when(UiCommonUtils::getScreenSize).thenReturn(SCREEN_SIZE);
        return uiCommonUtils;
    }
}