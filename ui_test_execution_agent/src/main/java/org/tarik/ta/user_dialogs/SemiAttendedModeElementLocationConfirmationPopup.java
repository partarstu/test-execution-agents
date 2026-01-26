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

import static org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult.createNewElement;
import static org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult.otherAction;
import static org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult.proceed;

/**
 * A modal confirmation popup for semi-attended mode.
 * Displays the selected element and intended action, with a countdown to automatically proceed.
 * Allows the user to intervene and choose to create a new element or perform another action.
 */
public class SemiAttendedModeElementLocationConfirmationPopup
        extends AbstractCountdownPopup<SemiAttendedModeElementLocationConfirmationResult> {
    private static final Logger LOG = LoggerFactory.getLogger(SemiAttendedModeElementLocationConfirmationPopup.class);
    private static final String TITLE = "Confirm Element Selection";

    private JButton proceedButton;

    private SemiAttendedModeElementLocationConfirmationPopup(String elementName, String intendedAction, int timeoutSeconds,
                                                             boolean elementLocationCorrectnessConfirmedByAgent) {
        super(TITLE, proceed(), timeoutSeconds);
        // Default is APPLICATION_MODAL from AbstractDialog, which blocks execution

        initializeComponents(elementName, intendedAction, elementLocationCorrectnessConfirmedByAgent);
        setFocusableWindowState(false);
        startCountdown();
        displayPopup();
    }

    private void initializeComponents(String elementName, String intendedAction, boolean elementLocationCorrectnessConfirmedByAgent) {
        JPanel mainPanel = getDefaultMainPanel();
        applyCommonPanelStyling(mainPanel);
        mainPanel.setLayout(new BorderLayout(DIALOG_DEFAULT_HORIZONTAL_GAP, DIALOG_DEFAULT_VERTICAL_GAP));

        // Info message
        String assessmentText = elementLocationCorrectnessConfirmedByAgent ? "elements match" : "elements don't match";
        String assessmentIcon = elementLocationCorrectnessConfirmedByAgent ? "<font size='6' color='green'>&#10004;</font>" :
                "<font size='6' color='red'>&#10060;</font>";
        String message = "<html><body style='width: 300px; text-align: center;'>" +
                "<b>Selected Element:</b> " + elementName + "<br/><br/>" +
                "<b>Intended Action:</b> " + intendedAction + "<br/><br/>" +
                "<b>Agent assessment:</b> " + assessmentText + " " + assessmentIcon + "</body></html>";

        JLabel messageLabel = new JLabel(message);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        // Use a consistent font or default
        messageLabel.setFont(new Font("Dialog", Font.PLAIN, 14));

        mainPanel.add(messageLabel, BorderLayout.CENTER);

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
        buttonsPanel.setOpaque(false); // Make transparent to show yellow background
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();

        // Set proceed button as default
        getRootPane().setDefaultButton(proceedButton);
    }

    private String getProceedButtonText() {
        return "Proceed (" + remainingSeconds + ")";
    }

    @Override
    protected void updateCountdownDisplay() {
        proceedButton.setText(getProceedButtonText());
    }

    @Override
    protected void onCountdownFinished() {
        LOG.info("Countdown completed, proceeding automatically");
        result.set(proceed());
    }

    @Override
    protected void onDialogClosing() {
        super.onDialogClosing();
        // Default to proceed if closed, or whatever the current value is (initially proceed)
        LOG.info("Dialog closed, defaulting to: {}", result.get().decision());
    }

    @Override
    protected boolean shouldPauseOnHover() {
        return true;
    }

    /**
     * Displays the confirmation popup and blocks until the user makes a choice or the countdown expires.
     *
     * @param elementName                                The name of the selected element.
     * @param intendedAction                             The intended action description.
     * @param seconds                                    The countdown duration in seconds.
     * @param elementLocationCorrectnessConfirmedByAgent Whether the agent thinks the element matches.
     * @return The result of the user's decision or the automatic countdown expiration.
     */
    public static SemiAttendedModeElementLocationConfirmationResult displayAndGetUserDecision(
            String elementName, String intendedAction, int seconds, boolean elementLocationCorrectnessConfirmedByAgent) {
        var popup = new SemiAttendedModeElementLocationConfirmationPopup(elementName, intendedAction, seconds,
                elementLocationCorrectnessConfirmedByAgent);
        return popup.getResult();
    }
}
