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
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Optional.ofNullable;

/**
 * A non-blocking countdown popup displayed at the bottom-right corner of the screen.
 * The popup shows a countdown timer and a "Halt" button.
 * If the operator clicks Halt before countdown completes, execution is halted.
 * If countdown completes without halt, execution proceeds automatically.
 */
public class CountdownHaltPopup extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(CountdownHaltPopup.class);
    private static final String TITLE = "Agent Running";
    public static final int TEXT_SIZE = 50;

    public enum Result {
        PROCEED,
        HALTED
    }

    private final AtomicReference<Result> result = new AtomicReference<>(Result.PROCEED);
    private Timer countdownTimer;
    private int remainingSeconds;
    private JLabel countdownLabel;
    private JButton haltButton;

    private CountdownHaltPopup(int seconds, String operationDescription) {
        super(null, TITLE);
        this.remainingSeconds = seconds;
        setModalityType(ModalityType.MODELESS);
        setUndecorated(true);

        initializeComponents(operationDescription);
        startCountdown();
        displayPopup();
    }

    private void initializeComponents(String operationDescription) {
        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)));
        mainPanel.setBackground(new Color(255, 255, 224)); // Light yellow background

        // Operation description
        JLabel descLabel = new JLabel("<html><b>Completed:</b> " + truncate(operationDescription) + "</html>");
        descLabel.setFont(new Font("Dialog", Font.PLAIN, 12));

        // Countdown display
        countdownLabel = new JLabel(getCountdownText(), SwingConstants.CENTER);
        countdownLabel.setFont(new Font("Dialog", Font.BOLD, 14));

        // Halt button with countdown display
        haltButton = new JButton("Halt (" + remainingSeconds + ")");
        haltButton.setFont(new Font("Dialog", Font.BOLD, 12));
        haltButton.setBackground(new Color(255, 100, 100));
        haltButton.setForeground(Color.WHITE);
        haltButton.setFocusPainted(false);
        setHoverAsClick(haltButton);
        haltButton.addActionListener(_ -> {
            LOG.info("Operator clicked Halt button");
            result.set(Result.HALTED);
            stopCountdown();
            dispose();
        });

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setOpaque(false);
        centerPanel.add(descLabel, BorderLayout.NORTH);
        centerPanel.add(countdownLabel, BorderLayout.CENTER);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(getButtonsPanel(haltButton), BorderLayout.EAST);

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    private String getCountdownText() {
        return "Proceeding in " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "") + "...";
    }

    @Override
    protected void setDefaultPosition() {
        pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int x = screenSize.width - getWidth() - 20 - screenInsets.right;
        int y = screenSize.height - getHeight() - 40 - screenInsets.bottom;
        setLocation(x, y);
    }

    private void startCountdown() {
        countdownTimer = new Timer(1000, _ -> {
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                LOG.info("Countdown completed, proceeding with execution");
                stopCountdown();
                dispose();
            } else {
                countdownLabel.setText(getCountdownText());
                haltButton.setText("Halt (" + remainingSeconds + ")");
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
    }

    @Override
    protected void onDialogClosing() {
        stopCountdown();
        result.set(Result.PROCEED);
    }

    private static String truncate(String text) {
        return ofNullable(text)
                .map(t -> t.length() <= TEXT_SIZE ? t : t.substring(0, TEXT_SIZE - 3) + "...")
                .orElse("");
    }

    /**
     * Displays the countdown popup and blocks until countdown completes or operator halts.
     *
     * @param seconds              Number of seconds to count down
     * @param operationDescription Description of the operation that was completed
     * @return HALTED if operator clicked halt, PROCEED if countdown completed
     */
    public static Result displayWithCountdown(int seconds, String operationDescription) {
        var popup = new CountdownHaltPopup(seconds, operationDescription);
        return popup.result.get();
    }
}
