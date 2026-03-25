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

import org.tarik.ta.utils.ImageUtils;

import org.tarik.ta.UiTestAgentConfig;
import java.awt.*;
import javax.swing.*;
import java.awt.image.BufferedImage;

public class InformationalPopup extends AbstractConfirmationDialog {

    public InformationalPopup(Window owner, String title, UiTestAgentConfig config) {
        super(owner, title, config);
    }

    public static void display(String title, String message, BufferedImage screenshot, PopupType popupType, UiTestAgentConfig config) {
        new InformationalPopup(null, title, config).initialize(message, screenshot, popupType);
    }

    private void initialize(String message, BufferedImage screenshot, PopupType popupType) {
        var messagePanel = createMessageWithIconPanel(getUserMessageArea(message), popupType.getIcon());
        var continueButton = createOkButton();

        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(4, 0, 0, 0, popupType.getBorderColor()),
                mainPanel.getBorder()
        ));

        if (screenshot != null) {
            mainPanel.add(new JScrollPane(messagePanel), BorderLayout.NORTH);

            BufferedImage displayImage = screenshot;
            if (screenshot.getWidth() > SCREENSHOT_DISPLAY_MAX_WIDTH || screenshot.getHeight() > SCREENSHOT_DISPLAY_MAX_HEIGHT) {
                double scaleX = (double) SCREENSHOT_DISPLAY_MAX_WIDTH / screenshot.getWidth();
                double scaleY = (double) SCREENSHOT_DISPLAY_MAX_HEIGHT / screenshot.getHeight();
                double ratio = Math.min(scaleX, scaleY);
                displayImage = ImageUtils.scaleImage(screenshot, ratio);
            }

            JLabel imageLabel = new JLabel(new ImageIcon(displayImage));
            imageLabel.setHorizontalAlignment(JLabel.CENTER);
            mainPanel.add(new JScrollPane(imageLabel), BorderLayout.CENTER);
        } else {
            mainPanel.add(new JScrollPane(messagePanel), BorderLayout.CENTER);
        }

        mainPanel.add(getButtonsPanel(continueButton), BorderLayout.SOUTH);
        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    private JPanel createMessageWithIconPanel(JTextPane messageArea, Icon icon) {
        JPanel panel = new JPanel(new BorderLayout(dialogDefaultHorizontalGap, 0));
        panel.setOpaque(false);
        panel.add(new JLabel(icon), BorderLayout.WEST);
        panel.add(messageArea, BorderLayout.CENTER);
        return panel;
    }
}