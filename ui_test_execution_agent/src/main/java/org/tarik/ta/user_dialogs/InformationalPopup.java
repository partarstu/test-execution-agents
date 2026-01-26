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

import org.tarik.ta.tools.CommonUserInteractionTools.PopupType;
import org.tarik.ta.utils.ImageUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class InformationalPopup extends AbstractConfirmationDialog {

    public InformationalPopup(Window owner, String title) {
        super(owner, title);
    }

    public static void display(String title, String message, BufferedImage screenshot, PopupType popupType) {
        new InformationalPopup(null, title).initialize(message, screenshot);
    }

    private void initialize(String message, BufferedImage screenshot) {
        var userMessageArea = getUserMessageArea(message);
        var continueButton = new JButton("OK");
        continueButton.addActionListener(e -> dispose());
        setHoverAsClick(continueButton);
        JPanel buttonsPanel = getButtonsPanel(continueButton);

        JPanel mainPanel = getDefaultMainPanel();

        if (screenshot != null) {
            mainPanel.add(new JScrollPane(userMessageArea), BorderLayout.NORTH);

            int maxWidth = 600;
            int maxHeight = 400;
            BufferedImage displayImage = screenshot;
            if (screenshot.getWidth() > maxWidth || screenshot.getHeight() > maxHeight) {
                double scaleX = (double) maxWidth / screenshot.getWidth();
                double scaleY = (double) maxHeight / screenshot.getHeight();
                double ratio = Math.min(scaleX, scaleY);
                displayImage = ImageUtils.scaleImage(screenshot, ratio);
            }

            JLabel imageLabel = new JLabel(new ImageIcon(displayImage));
            imageLabel.setHorizontalAlignment(JLabel.CENTER);
            mainPanel.add(new JScrollPane(imageLabel), BorderLayout.CENTER);
        } else {
            mainPanel.add(new JScrollPane(userMessageArea), BorderLayout.CENTER);
        }

        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }
}
