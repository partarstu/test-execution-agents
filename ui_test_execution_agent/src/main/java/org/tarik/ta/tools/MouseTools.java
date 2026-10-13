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

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.abs;
import static java.lang.System.currentTimeMillis;
import static org.tarik.ta.core.error.ErrorCategory.TIMEOUT;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.utils.NormalizedCoordinates.*;
import static org.tarik.ta.utils.UiCommonUtils.*;
import static org.tarik.ta.core.utils.CommonUtils.*;
import static org.tarik.ta.core.utils.MarkdownSections.SECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.heading;
import static org.tarik.ta.core.utils.MarkdownSections.joinSections;
import static org.tarik.ta.core.utils.MarkdownSections.section;
import static org.tarik.ta.core.utils.MarkdownSections.userMessageContents;
import static org.tarik.ta.utils.UserMessageSections.SCREENSHOT_LABEL;

@UiAgentRequestScope
public class MouseTools extends UiAbstractTools {
    private static final String NORMALIZED_X_DESCRIPTION = "The x-coordinate of the screen point as an integer in [0, 1000], " +
            "normalized to the screen width (0 is the left edge, 1000 the right edge), exactly as returned by the UI element " +
            "location tool";
    private static final String NORMALIZED_Y_DESCRIPTION = "The y-coordinate of the screen point as an integer in [0, 1000], " +
            "normalized to the screen height (0 is the top edge, 1000 the bottom edge), exactly as returned by the UI element " +
            "location tool";
    private static final int MOUSE_ACTION_DELAY_MILLIS = 100;
    private final UiTestAgentConfig uiTestAgentConfig;

    @Inject
    public MouseTools(UiStateCheckAgent uiStateCheckAgent, UiTestAgentConfig uiTestAgentConfig) {
        super(uiStateCheckAgent);
        this.uiTestAgentConfig = uiTestAgentConfig;
    }

    @Tool(value = "Performs a right click with a mouse at the specified coordinates. Use this tool when you need to right-click at a " +
            "specific location on the screen.")
    public void rightMouseClick(
            @P(NORMALIZED_X_DESCRIPTION) int x,
            @P(NORMALIZED_Y_DESCRIPTION) int y) {
        validateCoordinates(x, y);
        try {
            var logical = toLogicalScreenPoint(x, y);
            getRobot().mouseMove(logical.x, logical.y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mousePress(InputEvent.BUTTON3_DOWN_MASK);
            getRobot().mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "performing right click with mouse at (%s, %s)".formatted(x, y));
        }
    }

    @Tool(value = "Performs a left click with a mouse at the specified coordinates. Use this tool when you need to left-click at a " +
            "specific location on the screen.")
    public void leftMouseClick(
            @P(NORMALIZED_X_DESCRIPTION) int x,
            @P(NORMALIZED_Y_DESCRIPTION) int y) {
        validateCoordinates(x, y);
        try {
            leftMouseClick(toLogicalScreenPoint(x, y));
        } catch (Exception e) {
            throw rethrowAsToolException(e, "performing left click with mouse at (%s, %s)".formatted(x, y));
        }
    }

    @Tool(value = "Performs a double click with a left mouse button at the specified coordinates. Use this tool when you need to " +
            "double-click at a specific location on the screen.")
    public void leftMouseDoubleClick(
            @P(NORMALIZED_X_DESCRIPTION) int x,
            @P(NORMALIZED_Y_DESCRIPTION) int y) {
        validateCoordinates(x, y);
        try {
            var logical = toLogicalScreenPoint(x, y);
            getRobot().mouseMove(logical.x, logical.y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
            getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
            getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "performing double click with mouse at (%s, %s)".formatted(x, y));
        }
    }

    @Tool(value = "Moves the mouse to the specified coordinates. Use this tool when you need to move the mouse to a specific " +
            "location on the screen.")
    public void moveMouseTo(
            @P(NORMALIZED_X_DESCRIPTION) int x,
            @P(NORMALIZED_Y_DESCRIPTION) int y) {
        validateCoordinates(x, y);
        try {
            var logical = toLogicalScreenPoint(x, y);
            getRobot().mouseMove(logical.x, logical.y);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "moving mouse to (%s, %s)".formatted(x, y));
        }
    }

    @Tool(value = "Clicks and drags the mouse pointer from the current location by the specified distance." +
            "Use this when you need to click and drag the mouse pointer on the screen for the specific distance in any direction. " +
            "Provide the dragging distance on the X and Y scales as integers in [-1000, 1000], normalized to the screen width " +
            "and height (1000 is the whole screen).")
    public void clickAndDrag(
            @P("Distance to drag the mouse to the right (negative for left), normalized to the screen width") int xOffset,
            @P("Distance to drag the mouse to the bottom (negative for up), normalized to the screen height") int yOffset) {
        if (abs(xOffset) > NORMALIZED_RANGE || abs(yOffset) > NORMALIZED_RANGE) {
            throw new ToolExecutionException(("Invalid offsets: xOffset=%s, yOffset=%s. Offsets must be integers in [-1000, 1000], " +
                    "normalized to the screen.").formatted(xOffset, yOffset), TRANSIENT_TOOL_ERROR);
        }
        try {
            var mouseLocation = getMouseLocation();
            var offset = toLogicalScreenOffset(xOffset, yOffset);
            getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mouseMove(mouseLocation.x + offset.x, mouseLocation.y + offset.y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "clicking and dragging mouse by offset (%s, %s)".formatted(xOffset, yOffset));
        }
    }

    @Tool(value = "Repeatedly clicks at specified coordinates until a desired state is reached or a timeout occurs. Use this tool if a " +
            "single mouse click is not enough in order to reach the desired state. Returns a confirmation once the expected state " +
            "has been reached; the action is then complete and must not be repeated.")
    public String clickElementUntilStateAchieved(
            @P(NORMALIZED_X_DESCRIPTION) int x,
            @P(NORMALIZED_Y_DESCRIPTION) int y,
            @P("Description of the expected state after the click") String expectedStateDescription) {
        validateCoordinates(x, y);
        return repeatUntilStateAchieved(expectedStateDescription, "Clicking left mouse button",
                "clicking at location (%s, %s)".formatted(x, y),
                () -> leftMouseClick(toLogicalScreenPoint(x, y)));
    }

    @Tool(value = "Repeatedly scrolls the mouse wheel in the specified direction at the current mouse position until a desired " +
            "state is reached (e.g. a specific element becomes visible) or a timeout occurs. The state is checked before every " +
            "scroll, so no scrolling happens if it is already reached. This tool needs no UI element and no coordinates: the " +
            "scrolled area is the one under the current mouse position, so if a specific area must be scrolled, the mouse must " +
            "have been moved over that area before. Returns a confirmation once the expected state has been reached; the action " +
            "is then complete and must not be repeated.")
    public String scrollUntilStateAchieved(
            @P("The direction to scroll the content in") ScrollDirection direction,
            @P("Description of the expected state which scrolling must reach") String expectedStateDescription) {
        if (direction == null) {
            throw new ToolExecutionException("Scroll direction must be provided.", TRANSIENT_TOOL_ERROR);
        }
        int notches = uiTestAgentConfig.getMouseScrollNotchesPerAttempt();
        // Robot counts negative wheel notches as scrolling up and positive ones as scrolling down
        int wheelAmount = direction == ScrollDirection.UP ? -notches : notches;
        var directionName = direction.name().toLowerCase(Locale.ROOT);
        return repeatUntilStateAchieved(expectedStateDescription, "Scrolling mouse wheel %s".formatted(directionName),
                "scrolling %s".formatted(directionName),
                () -> {
                    getRobot().mouseWheel(wheelAmount);
                    sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
                });
    }

    public enum ScrollDirection {
        UP, DOWN
    }

    private String repeatUntilStateAchieved(String expectedStateDescription, String actionName, String actionDetails,
                                            Runnable action) {
        if (expectedStateDescription == null || expectedStateDescription.isBlank()) {
            throw new ToolExecutionException("Expected state description cannot be empty.", TRANSIENT_TOOL_ERROR);
        }

        try {
            var actionDescription = "%s until %s".formatted(actionName, expectedStateDescription);
            var userMessage = userMessageContents(joinSections(
                    section(SECTION_LEVEL, "Expected state of the screen", expectedStateDescription),
                    section(SECTION_LEVEL, "Performed action", actionDescription),
                    heading(SECTION_LEVEL, SCREENSHOT_LABEL)));
            var waitDuration = uiTestAgentConfig.getMaxActionExecutionDurationMillis();
            long retryDelayMillis = uiTestAgentConfig.getActionRetryPolicy().delayMillis();
            long deadline = currentTimeMillis() + waitDuration;
            AtomicReference<BufferedImage> latestScreenshot = new AtomicReference<>();
            do {
                var screenshot = latestScreenshot.updateAndGet(_ -> captureScreen());
                var image = uiStateCheckAgent.getImageContentCreator(uiTestAgentConfig).createImageContent(screenshot);
                var result = uiStateCheckAgent.executeAndGetResult(() ->
                                uiStateCheckAgent.check(userMessage, image))
                        .getResultPayload();
                if (result != null && result.success()) {
                    return "The expected state '%s' has been reached, the action is complete.".formatted(expectedStateDescription);
                }
                action.run();
                sleepMillis(retryDelayMillis);
            } while (currentTimeMillis() < deadline);
            throw new ToolExecutionException("Failed to reach expected state '%s' after %s within the timeout of %s millis"
                    .formatted(expectedStateDescription, actionDetails, waitDuration), TIMEOUT);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "%s until state '%s' achieved".formatted(actionDetails, expectedStateDescription));
        }
    }

    protected void leftMouseClick(Point elementLocation) {
        getRobot().mouseMove(elementLocation.x, elementLocation.y);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
        getRobot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
        getRobot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
    }

    private void validateCoordinates(int x, int y) {
        if (x < 0 || y < 0 || x > NORMALIZED_RANGE || y > NORMALIZED_RANGE) {
            throw new ToolExecutionException(("Invalid coordinates: x=%s, y=%s. Coordinates must be integers in [0, 1000], " +
                    "normalized to the screen, exactly as returned by the UI element location tool.").formatted(x, y),
                    TRANSIENT_TOOL_ERROR);
        }
    }
}