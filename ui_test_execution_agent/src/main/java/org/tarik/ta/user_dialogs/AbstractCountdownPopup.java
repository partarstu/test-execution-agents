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
package org.tarik.ta.user_dialogs;

import org.tarik.ta.UiTestAgentConfig;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base class for countdown popups.
 * Provides common logic for timer management, result handling, and visual styling.
 */
public abstract class AbstractCountdownPopup<T> extends AbstractDialog {
    protected final AtomicReference<T> result;
    protected int remainingSeconds;
    private Timer countdownTimer;

    protected AbstractCountdownPopup(String title, T defaultResult, int seconds, UiTestAgentConfig config) {
        super(null, title, config);
        this.result = new AtomicReference<>(defaultResult);
        this.remainingSeconds = seconds;
        setUndecorated(true);
        setFocusableWindowState(false);
    }

    /**
     * Applies the common visual styling (light yellow background, border) to the panel.
     */
    protected void applyCommonPanelStyling(JPanel panel) {
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(dialogDefaultVerticalGap, dialogDefaultHorizontalGap,
                        dialogDefaultVerticalGap, dialogDefaultHorizontalGap)));
        panel.setBackground(new Color(255, 255, 224)); // Light yellow background
    }

    /**
     * Starts the countdown timer.
     */
    protected void startCountdown() {
        countdownTimer = new Timer(1000, _ -> {
            if (shouldPauseOnHover() && isMouseOver()) {
                return;
            }
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                stopCountdown();
                onCountdownFinished();
                dispose();
            } else {
                updateCountdownDisplay();
            }
        });
        countdownTimer.start();
    }

    /**
     * Stops the countdown timer.
     */
    protected void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
    }

    /**
     * @return true if the countdown should pause when the mouse is over the popup. Default is false.
     */
    protected boolean shouldPauseOnHover() {
        return false;
    }

    private boolean isMouseOver() {
        if (!isVisible()) return false;
        try {
            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            if (pointerInfo == null) return false;
            Point mouseLocation = pointerInfo.getLocation();
            return getBounds().contains(mouseLocation);
        } catch (HeadlessException e) {
            return false;
        }
    }

    @Override
    protected void onDialogClosing() {
        stopCountdown();
        // Subclasses should handle specific closing logic if needed,
        // typically the result is already set to default or updated by user action.
    }

    @Override
    public void setDefaultPosition() {
        pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int x = screenSize.width - getWidth() - 20 - screenInsets.right;
        int y = screenSize.height - getHeight() - 40 - screenInsets.bottom;
        setLocation(x, y);
    }

    /**
     * Called when the countdown timer ticks (every second).
     * Subclasses should update their UI here.
     */
    protected abstract void updateCountdownDisplay();

    /**
     * Called when the countdown reaches zero.
     * Subclasses should define what happens (e.g., set result to default proceed).
     */
    protected abstract void onCountdownFinished();
    
    /**
     * Returns the result of the dialog interaction.
     */
    public T getResult() {
        return result.get();
    }
}