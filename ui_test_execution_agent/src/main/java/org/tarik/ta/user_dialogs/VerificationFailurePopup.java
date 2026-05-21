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

import org.tarik.ta.UiTestAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static org.tarik.ta.utils.HtmlUtils.escapeHtml;

/**
 * Modal popup that displays verification failure details with a screenshot.
 * The popup shows the failure information and provides OK and Terminate buttons.
 * Used in SUPERVISED mode to inform the operator
 * about verification failures before retrying or terminating execution.
 */
public class VerificationFailurePopup extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationFailurePopup.class);

    private VerificationFailurePopup(Window owner, String verificationDescription, String failureReason,
                                     BufferedImage screenshot, UiTestAgentConfig config) {
        super(owner, "Verification Failure", config);
        setFocusableWindowState(false);

        JPanel mainPanel = getDefaultMainPanel();

        String message = format(
                "<html><body style='width: 400px'>" +
                        "<h3 style='color: red'>Verification Failed</h3>" +
                        "<p><b>Verification:</b> %s</p>" +
                        "<p><b>Reason:</b> %s</p>" +
                        "<p style='color: #555'><i>Click OK to acknowledge.</i></p>" +
                        "</body></html>",
                escapeHtml(verificationDescription), escapeHtml(failureReason));

        JLabel messageLabel = new JLabel(message);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(dialogDefaultVerticalGap, dialogDefaultHorizontalGap,
                dialogDefaultVerticalGap, dialogDefaultHorizontalGap));

        // Center panel with message and optional screenshot
        JPanel centerPanel = new JPanel(new BorderLayout(dialogDefaultHorizontalGap, dialogDefaultVerticalGap));
        centerPanel.add(messageLabel, BorderLayout.NORTH);

        if (screenshot != null) {
            // Scale screenshot to fit dialog
            Image scaledImage = scaleImage(screenshot, SCREENSHOT_DISPLAY_MAX_WIDTH, SCREENSHOT_DISPLAY_MAX_HEIGHT);
            JLabel screenshotLabel = new JLabel(new ImageIcon(scaledImage));
            screenshotLabel.setBorder(BorderFactory.createTitledBorder("Screenshot at failure"));
            JScrollPane scrollPane = new JScrollPane(screenshotLabel);
            scrollPane.setPreferredSize(new Dimension(SCREENSHOT_DISPLAY_MAX_WIDTH + 20, SCREENSHOT_DISPLAY_MAX_HEIGHT + 40));
            centerPanel.add(scrollPane, BorderLayout.CENTER);
        }

        // OK button
        JButton okButton = new JButton("OK");
        okButton.setFont(new Font(uiTestAgentConfig.getDialogDefaultFontType(), Font.BOLD, 12));
        okButton.addActionListener(_ -> dispose());
        setHoverAsClick(okButton);

        JPanel buttonPanel = getButtonsPanel(okButton);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    @Override
    protected void onDialogClosing() {
        LOG.info("Verification failure popup closed via window controls");
    }

    private static Image scaleImage(BufferedImage original, int maxWidth, int maxHeight) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= maxWidth && height <= maxHeight) {
            return original;
        }

        double scaleX = (double) maxWidth / width;
        double scaleY = (double) maxHeight / height;
        double scale = Math.min(scaleX, scaleY);

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        return original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
    }

    /**
     * Displays the verification failure popup with screenshot and blocks until user responds.
     *
     * @param verificationDescription Description of the verification that failed
     * @param failureReason           The reason for the failure
     * @param screenshot              Screenshot at the moment of failure (can be null)
     * @param config                  Agent configuration
     */
    public static void display(String verificationDescription, String failureReason,
                                       BufferedImage screenshot, UiTestAgentConfig config) {
        LOG.info("Displaying verification failure popup for: {}", verificationDescription);
        new VerificationFailurePopup(null, verificationDescription, failureReason, screenshot, config);
    }
}

