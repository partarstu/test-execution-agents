/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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

import org.tarik.ta.utils.ImageUtils;

import org.tarik.ta.UiTestAgentConfig;
import java.awt.*;
import javax.swing.*;
import java.awt.image.BufferedImage;

public class InformationalPopup extends AbstractConfirmationDialog {

    public InformationalPopup(Window owner, String title, UiTestAgentConfig config) {
        super(owner, title, config);
        setFocusableWindowState(false);
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