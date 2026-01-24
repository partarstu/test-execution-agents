/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.user_dialogs;

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

    protected AbstractCountdownPopup(String title, T defaultResult, int seconds) {
        super(null, title);
        this.result = new AtomicReference<>(defaultResult);
        this.remainingSeconds = seconds;
        setUndecorated(true);
    }

    /**
     * Applies the common visual styling (light yellow background, border) to the panel.
     */
    protected void applyCommonPanelStyling(JPanel panel) {
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)));
        panel.setBackground(new Color(255, 255, 224)); // Light yellow background
    }

    /**
     * Starts the countdown timer.
     */
    protected void startCountdown() {
        countdownTimer = new Timer(1000, _ -> {
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

    @Override
    protected void onDialogClosing() {
        stopCountdown();
        // Subclasses should handle specific closing logic if needed,
        // typically the result is already set to default or updated by user action.
    }

    @Override
    protected void setDefaultPosition() {
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
