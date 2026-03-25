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
package org.tarik.ta.user_dialogs.knowledge;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.dto.ProcedureExecutionConfirmationResult;
import org.tarik.ta.user_dialogs.AbstractCountdownPopup;

import javax.swing.*;
import java.awt.*;

import static org.tarik.ta.dto.ProcedureExecutionConfirmationResult.*;

/**
 * A modal confirmation popup for Procedure Execution.
 * Displays the executed step and its parent, with a countdown to automatically
 * proceed.
 * Allows the user to intervene and halt execution.
 */
public class ProcedureExecutionConfirmationPopup
        extends AbstractCountdownPopup<ProcedureExecutionConfirmationResult> {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureExecutionConfirmationPopup.class);
    private static final String TITLE = "Confirm Procedure Execution";

    private JButton proceedButton;

    private ProcedureExecutionConfirmationPopup(String atomicDescription, @Nullable String parentDescription,
                                                @Nullable ExecutionItemContext itemContext, int timeoutSeconds, boolean isPreExecution,
                                                org.tarik.ta.UiTestAgentConfig config) {
        super(TITLE, proceed(), timeoutSeconds, config);

        initializeComponents(atomicDescription, parentDescription, itemContext, isPreExecution);
        setFocusableWindowState(false);
        startCountdown();
        displayPopup();
    }

    private void initializeComponents(String atomicDescription, String parentDescription,
                                      @Nullable ExecutionItemContext itemContext, boolean isPreExecution) {
        JPanel mainPanel = getDefaultMainPanel();
        applyCommonPanelStyling(mainPanel);
        mainPanel.setLayout(new BorderLayout(dialogDefaultHorizontalGap, dialogDefaultVerticalGap));

        String contextHtml = buildContextHtml(itemContext);
        var parentDescriptionString = parentDescription==null? "N/A" : parentDescription;
        String parentHtml = "<b>Parent Procedure:</b> " + parentDescriptionString + "<br/><br/>";
        String stepPrefix = isPreExecution ? "Step to Execute:" : "Executed Step:";
        String message = "<html><body style='width: 300px; text-align: center;'>" +
                contextHtml +
                "<b>" + stepPrefix + "</b> " + atomicDescription + "<br/><br/>" +
                parentHtml +
                "</body></html>";

        JLabel messageLabel = new JLabel(message);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
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

        JButton haltButton = new JButton("Halt");
        haltButton.setBackground(new Color(255, 100, 100));
        haltButton.setForeground(Color.WHITE);
        haltButton.setFocusPainted(false);
        haltButton.addActionListener(_ -> {
            LOG.info("User clicked Halt");
            result.set(halted());
            stopCountdown();
            dispose();
        });

        JPanel buttonsPanel = getButtonsPanel(proceedButton, haltButton);
        buttonsPanel.setOpaque(false);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();

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
        LOG.info("Dialog closed, defaulting to: {}", result.get().decision());
    }

    @Override
    protected boolean shouldPauseOnHover() {
        return true;
    }

    private static String buildContextHtml(@Nullable ExecutionItemContext itemContext) {
        if (itemContext == null) {
            return "";
        }
        if (itemContext.isPrecondition()) {
            return "<b>Current Precondition:</b> " + itemContext.description() + "<br/><br/>";
        }
        String testDataHtml = itemContext.hasTestData()
                ? "<b>Test Data:</b> " + itemContext.formattedTestData() + "<br/>"
                : "";
        return "<b>Current Test Step:</b> " + itemContext.description() + "<br/>" + testDataHtml + "<br/>";
    }

    /**
     * Displays the confirmation popup and blocks until the user makes a choice or
     * the countdown expires.
     */
    public static ProcedureExecutionConfirmationResult displayAndGetUserDecision(
            String atomicDescription, @Nullable String parentDescription, @Nullable ExecutionItemContext itemContext,
            int seconds, boolean isPreExecution, org.tarik.ta.UiTestAgentConfig config) {
        var popup = new ProcedureExecutionConfirmationPopup(atomicDescription, parentDescription, itemContext, seconds, isPreExecution, config);
        return popup.getResult();
    }
}