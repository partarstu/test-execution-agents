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

import static org.tarik.ta.user_dialogs.NoElementFoundPopup.UserDecision.CONTINUE;

public class NoElementFoundPopup extends AbstractDialog {
    public enum UserDecision {
        CONTINUE,
        TERMINATE
    }

    private final AtomicReference<UserDecision> userDecision = new AtomicReference<>(UserDecision.TERMINATE);

    private NoElementFoundPopup(Window owner, String message, UiTestAgentConfig config) {
        super(owner, "UI element not found", config);
        setFocusableWindowState(false);
        var userMessageArea = getUserMessageArea(message);
        var continueButton = new JButton("Continue");
        setHoverAsClick(continueButton);
        continueButton.addActionListener(_ -> {
            userDecision.set(CONTINUE);
            dispose();
        });

        var terminateButton = new JButton("Terminate");
        setHoverAsClick(terminateButton);
        terminateButton.addActionListener(_ -> dispose());

        JPanel buttonsPanel = getButtonsPanel(continueButton, terminateButton);
        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(new JScrollPane(userMessageArea), BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    @Override
    protected void onDialogClosing() {
        userDecision.set(UserDecision.TERMINATE);
    }

    public static UserDecision displayAndGetUserDecision(Window owner, String message, UiTestAgentConfig config) {
        var popup = new NoElementFoundPopup(owner, message, config);
        return popup.userDecision.get();
    }
}