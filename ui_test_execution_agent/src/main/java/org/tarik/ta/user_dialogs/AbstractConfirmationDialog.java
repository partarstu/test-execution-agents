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