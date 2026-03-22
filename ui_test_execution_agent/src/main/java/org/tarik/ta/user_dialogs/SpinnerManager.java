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
package org.tarik.ta.user_dialogs;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

import static org.tarik.ta.user_dialogs.AbstractDialog.DIALOG_DEFAULT_HORIZONTAL_GAP;
import static org.tarik.ta.user_dialogs.AbstractDialog.DIALOG_DEFAULT_VERTICAL_GAP;

public class SpinnerManager {
    static final int SPINNER_VERTICAL_PADDING = 20;
    static final int SPINNER_HORIZONTAL_PADDING = 30;
    public static final Dimension SPINNER_MIN_SIZE = new Dimension(450, 100);

    private static JDialog dialog;
    private static JLabel messageLabel;
    private static boolean isVisible = false;
    private static String currentMessage = "";

    private SpinnerManager() {}

    public static JPanel createSpinnerPanel(String message) {
        var panel = new JPanel(new BorderLayout(DIALOG_DEFAULT_HORIZONTAL_GAP, DIALOG_DEFAULT_VERTICAL_GAP));
        panel.setBorder(BorderFactory.createEmptyBorder(SPINNER_VERTICAL_PADDING, SPINNER_HORIZONTAL_PADDING,
                SPINNER_VERTICAL_PADDING, SPINNER_HORIZONTAL_PADDING));
        panel.add(new JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER);
        var progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        panel.add(progressBar, BorderLayout.SOUTH);
        return panel;
    }

    private static void initIfNeeded() {
        if (dialog == null) {
            dialog = new JDialog((Frame) null, "Please Wait", false);
            dialog.setAlwaysOnTop(true);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            var panel = new JPanel(new BorderLayout(DIALOG_DEFAULT_HORIZONTAL_GAP, DIALOG_DEFAULT_VERTICAL_GAP));
            panel.setBorder(BorderFactory.createEmptyBorder(SPINNER_VERTICAL_PADDING, SPINNER_HORIZONTAL_PADDING,
                    SPINNER_VERTICAL_PADDING, SPINNER_HORIZONTAL_PADDING));
            messageLabel = new JLabel("", SwingConstants.CENTER);
            panel.add(messageLabel, BorderLayout.CENTER);
            var progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            panel.add(progressBar, BorderLayout.SOUTH);
            dialog.add(panel);
            dialog.setMinimumSize(SPINNER_MIN_SIZE);
        }
    }

    public static void show(String message) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                doShow(message);
            } else {
                SwingUtilities.invokeAndWait(() -> doShow(message));
            }
        } catch (InterruptedException | InvocationTargetException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void doShow(String message) {
        initIfNeeded();
        currentMessage = message;
        messageLabel.setText(message);
        dialog.pack();
        var screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation((screenSize.width - dialog.getWidth()) / 2, (screenSize.height - dialog.getHeight()) / 2);
        dialog.setVisible(true);
        isVisible = true;
    }

    public static void updateMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (isVisible && messageLabel != null) {
                currentMessage = message;
                messageLabel.setText(message);
                dialog.pack();
            }
        });
    }

    public static void hide() {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                doHide();
            } else {
                SwingUtilities.invokeAndWait(SpinnerManager::doHide);
            }
        } catch (InterruptedException | InvocationTargetException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void doHide() {
        if (dialog != null) {
            dialog.setVisible(false);
        }
        isVisible = false;
        currentMessage = "";
    }

    public static SpinnerState hideIfVisible() {
        final SpinnerState[] state = new SpinnerState[1];
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                state[0] = new SpinnerState(isVisible, currentMessage);
                doHide();
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    state[0] = new SpinnerState(isVisible, currentMessage);
                    doHide();
                });
            }
        } catch (InterruptedException | InvocationTargetException e) {
            Thread.currentThread().interrupt();
            return new SpinnerState(false, "");
        }
        return state[0];
    }
}
