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

import org.tarik.ta.user_dialogs.AbstractDialog;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog asking the user whether to reuse an existing UI element found in the DB
 * or create a new one during the knowledge collecting knowledge flow.
 */
public class ElementReuseConfirmationPopup extends AbstractDialog {
    private UserChoice userChoice = UserChoice.CANCELLED;

    private ElementReuseConfirmationPopup(Window owner, String elementName, String elementDescription) throws HeadlessException {
        super(owner, "UI Element Found");
        initializeDialog(elementName, elementDescription);
    }

    @Override
    protected void onDialogClosing() {
        userChoice = UserChoice.CANCELLED;
    }

    private void initializeDialog(String elementName, String elementDescription) {
        var message = ("An existing UI element was found in the database:\n\nName: \"%s\"\nDescription: \"%s\"\n\n" +
                "Would you like to reuse this element or create a new one?").formatted(elementName, elementDescription);
        var messageArea = getUserMessageArea(message);

        var reuseButton = new JButton("Reuse Existing");
        reuseButton.addActionListener(_ -> {
            userChoice = UserChoice.REUSE_EXISTING;
            dispose();
        });
        setHoverAsClick(reuseButton);

        var createNewButton = new JButton("Create New");
        createNewButton.addActionListener(_ -> {
            userChoice = UserChoice.CREATE_NEW;
            dispose();
        });
        setHoverAsClick(createNewButton);

        JPanel buttonsPanel = getButtonsPanel(reuseButton, createNewButton);

        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(new JScrollPane(messageArea), BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    public static UserChoice displayAndGetChoice(Window owner, String elementName, String elementDescription) {
        var popup = new ElementReuseConfirmationPopup(owner, elementName, elementDescription);
        return popup.userChoice;
    }

    public enum UserChoice {
        REUSE_EXISTING,
        CREATE_NEW,
        CANCELLED
    }
}
