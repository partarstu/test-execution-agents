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
import java.awt.*;
import javax.swing.*;

public abstract class AbstractConfirmationDialog extends AbstractDialog {
    public AbstractConfirmationDialog(Window owner, String title, UiTestAgentConfig config) throws HeadlessException {
        super(owner, title, config);
    }

    @Override
    protected void onDialogClosing() {
        // Dialog does nothing after its closing - it's the same as clicking the OK button
    }

    protected JButton createOkButton() {
        var button = new JButton("OK");
        button.addActionListener(_ -> dispose());
        setHoverAsClick(button);
        return button;
    }

    protected void initializeDialog(String userMessage) {
        var userMessageArea = getUserMessageArea(userMessage);
        JPanel buttonsPanel = getButtonsPanel(createOkButton());

        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(new JScrollPane(userMessageArea), BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }
}