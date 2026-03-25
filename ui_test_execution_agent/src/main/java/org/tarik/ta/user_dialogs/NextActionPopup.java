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

import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.utils.CommonUtils;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

public class NextActionPopup extends AbstractDialog {

    public enum UserDecision {
        EDIT_CURRENT_PROCEDURE,
        CREATE_NEW_PROCEDURE,
        RETRY,
        TERMINATE
    }

    private static final String TITLE = "Further action required";
    private static final String DEFAULT_INPUT_MESSAGE = "What would you like to do next ?"; // New constant
    private final AtomicReference<UserDecision> userDecision = new AtomicReference<>(
            UserDecision.EDIT_CURRENT_PROCEDURE);

    private NextActionPopup(Window owner, String message, UiTestAgentConfig config) {
        super(owner, TITLE, config);
        var userMessageArea = getUserMessageArea(message);

        JButton editCurrentProcedureButton = new JButton("Edit current procedure");
        setHoverAsClick(editCurrentProcedureButton);
        editCurrentProcedureButton.addActionListener(_ -> {
            userDecision.set(UserDecision.EDIT_CURRENT_PROCEDURE);
            dispose();
        });

        JButton createNewProcedureButton = new JButton("Create New Procedure");
        setHoverAsClick(createNewProcedureButton);
        createNewProcedureButton.addActionListener(_ -> {
            userDecision.set(UserDecision.CREATE_NEW_PROCEDURE);
            dispose();
        });

        JButton retryButton = new JButton("Retry");
        setHoverAsClick(retryButton);
        retryButton.addActionListener(_ -> {
            userDecision.set(UserDecision.RETRY);
            dispose();
        });

        JButton terminateButton = new JButton("Terminate");
        setHoverAsClick(terminateButton);
        terminateButton.addActionListener(_ -> {
            userDecision.set(UserDecision.TERMINATE);
            dispose();
        });

        JPanel buttonsPanel = getButtonsPanel(editCurrentProcedureButton, createNewProcedureButton, retryButton, terminateButton);
        JPanel mainPanel = getDefaultMainPanel();

        mainPanel.add(new JScrollPane(userMessageArea), BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    @Override
    protected void onDialogClosing() {
        userDecision.set(UserDecision.EDIT_CURRENT_PROCEDURE);
    }

    public static UserDecision displayAndGetUserDecision(Window owner, String message, UiTestAgentConfig config) {
        String actualMessage = CommonUtils.isNotBlank(message) ? message : DEFAULT_INPUT_MESSAGE;
        var popup = new NextActionPopup(owner, actualMessage, config);
        return popup.userDecision.get();
    }
}
