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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static java.lang.String.format;

/**
 * Modal popup that displays verification failure details with a screenshot.
 * The popup shows the failure information and has only an OK button.
 * Used in both ATTENDED and SEMI_ATTENDED modes to inform the operator
 * about verification failures before interrupting execution.
 */
public class VerificationFailurePopup extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationFailurePopup.class);

    private VerificationFailurePopup(Window owner, String verificationDescription, String failureReason, BufferedImage screenshot) {
        super(owner, "Verification Failure");

        JPanel mainPanel = getDefaultMainPanel();

        // Create formatted message
        String message = format(
                "<html><body style='width: 400px'>" +
                        "<h3 style='color: red'>Verification Failed</h3>" +
                        "<p><b>Verification:</b> %s</p>" +
                        "<p><b>Reason:</b> %s</p>" +
                        "</body></html>",
                escapeHtml(verificationDescription), escapeHtml(failureReason));

        JLabel messageLabel = new JLabel(message);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Center panel with message and optional screenshot
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.add(messageLabel, BorderLayout.NORTH);

        if (screenshot != null) {
            // Scale screenshot to fit dialog
            int maxWidth = 600;
            int maxHeight = 400;
            Image scaledImage = scaleImage(screenshot, maxWidth, maxHeight);
            JLabel screenshotLabel = new JLabel(new ImageIcon(scaledImage));
            screenshotLabel.setBorder(BorderFactory.createTitledBorder("Screenshot at failure"));
            JScrollPane scrollPane = new JScrollPane(screenshotLabel);
            scrollPane.setPreferredSize(new Dimension(maxWidth + 20, maxHeight + 40));
            centerPanel.add(scrollPane, BorderLayout.CENTER);
        }

        // OK button
        JButton okButton = new JButton("OK");
        okButton.setFont(new Font("Dialog", Font.BOLD, 12));
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
        LOG.info("Verification failure popup closed");
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

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Displays the verification failure popup with screenshot and blocks until user acknowledges.
     *
     * @param verificationDescription Description of the verification that failed
     * @param failureReason           The reason for the failure
     * @param screenshot              Screenshot at the moment of failure (can be null)
     */
    public static void display(String verificationDescription, String failureReason, BufferedImage screenshot) {
        LOG.info("Displaying verification failure popup for: {}", verificationDescription);
        new VerificationFailurePopup(null, verificationDescription, failureReason, screenshot);
    }
}
