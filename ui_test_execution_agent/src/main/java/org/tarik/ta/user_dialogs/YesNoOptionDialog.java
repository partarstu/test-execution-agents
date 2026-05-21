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

import static javax.swing.JOptionPane.NO_OPTION;
import static javax.swing.JOptionPane.YES_OPTION;

public class YesNoOptionDialog extends AbstractDialog {
    private int userChoice = NO_OPTION;

    private YesNoOptionDialog(Window owner, String title, JPanel contentPanel, UiTestAgentConfig config) throws HeadlessException {
        super(owner, title, config);
        initializeDialog(contentPanel);
    }

    @Override
    protected void onDialogClosing() {
        userChoice = NO_OPTION;
    }

    private void initializeDialog(JPanel contentPanel) {
        var yesButton = new JButton("Yes");
        yesButton.addActionListener(_ -> {
            userChoice = YES_OPTION;
            dispose();
        });
        setHoverAsClick(yesButton);

        var noButton = new JButton("No");
        noButton.addActionListener(_ -> {
            userChoice = NO_OPTION;
            dispose();
        });
        setHoverAsClick(noButton);

        JPanel buttonsPanel = getButtonsPanel(yesButton, noButton);

        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(new JScrollPane(contentPanel), BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        pack();
        displayPopup();
    }

    public int getUserChoice() {
        return userChoice;
    }

    public static int display(Window owner, String title, JPanel contentPanel, UiTestAgentConfig config) {
        var dialog = new YesNoOptionDialog(owner, title, contentPanel, config);
        return dialog.getUserChoice();
    }
}