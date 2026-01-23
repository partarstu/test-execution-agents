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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult.createNewElement;
import static org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult.otherAction;
import static org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult.proceed;

/**
 * A modal confirmation popup for semi-attended mode.
 * Displays the selected element and intended action, with a countdown to automatically proceed.
 * Allows the user to intervene and choose to create a new element or perform another action.
 */
public class SemiAttendedModeElementLocationConfirmationPopup extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(SemiAttendedModeElementLocationConfirmationPopup.class);
    private static final String TITLE = "Confirm Element Selection";

    private final AtomicReference<SemiAttendedModeElementLocationConfirmationResult> result = new AtomicReference<>(proceed());
    private Timer countdownTimer;
    private int remainingSeconds;
    private JButton proceedButton;

    private SemiAttendedModeElementLocationConfirmationPopup(String elementDescription, String elementName, String intendedAction, int seconds) {
        super(null, TITLE);
        this.remainingSeconds = seconds;
        // Default is APPLICATION_MODAL from AbstractDialog, which blocks execution
        
        initializeComponents(elementDescription, elementName, intendedAction);
        startCountdown();
        displayPopup();
    }

    private void initializeComponents(String elementDescription, String elementName, String intendedAction) {
        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));

        // Info message
        String message = "<html><body style='width: 300px; text-align: center;'>" +
                "<b>Element Description:</b> " + elementDescription + "<br/><br/>" +
                "<b>Selected Element:</b> " + elementName + "<br/><br/>" +
                "<b>Intended Action:</b> " + intendedAction + "<br/><br/>" +
                "Proceeding automatically in...</body></html>";
        
        JTextPane messageArea = getUserMessageArea(message);
        mainPanel.add(messageArea, BorderLayout.CENTER);

        // Buttons
        proceedButton = new JButton(getProceedButtonText());
        proceedButton.addActionListener(_ -> {
            LOG.info("User clicked Proceed");
            result.set(proceed());
            stopCountdown();
            dispose();
        });
        
        JButton createNewButton = new JButton("Create new element");
        createNewButton.addActionListener(_ -> {
            LOG.info("User clicked Create new element");
            result.set(createNewElement());
            stopCountdown();
            dispose();
        });

        JButton otherActionButton = new JButton("Other action");
        otherActionButton.addActionListener(_ -> {
            LOG.info("User clicked Other action");
            result.set(otherAction());
            stopCountdown();
            dispose();
        });

        JPanel buttonsPanel = getButtonsPanel(proceedButton, createNewButton, otherActionButton);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();
        
        // Set proceed button as default
        getRootPane().setDefaultButton(proceedButton);
    }

    private String getProceedButtonText() {
        return "Proceed (" + remainingSeconds + ")";
    }

    private void startCountdown() {
        countdownTimer = new Timer(1000, _ -> {
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                LOG.info("Countdown completed, proceeding automatically");
                result.set(proceed());
                stopCountdown();
                dispose();
            } else {
                proceedButton.setText(getProceedButtonText());
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
    }

    @Override
    protected void onDialogClosing() {
        stopCountdown();
        // Default to proceed if closed, or whatever the current value is (initially proceed)
        LOG.info("Dialog closed, defaulting to: {}", result.get().decision());
    }

    /**
     * Displays the confirmation popup and blocks until the user makes a choice or the countdown expires.
     *
     * @param elementDescription The description of the element.
     * @param elementName        The name of the selected element.
     * @param intendedAction     The intended action description.
     * @param seconds            The countdown duration in seconds.
     * @return The result of the user's decision or the automatic countdown expiration.
     */
    public static SemiAttendedModeElementLocationConfirmationResult displayAndGetUserDecision(String elementDescription, String elementName, String intendedAction, int seconds) {
        var popup = new SemiAttendedModeElementLocationConfirmationPopup(elementDescription, elementName, intendedAction, seconds);
        return popup.result.get();
    }
}
