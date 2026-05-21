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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class FailureContextCaptureDialog extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(FailureContextCaptureDialog.class);

    private final AtomicReference<FailureContext> result = new AtomicReference<>();
    private final JComboBox<ErrorCategory> categoryComboBox;
    private final JTextField symptomField;
    private final JTextArea resolutionArea;

    private FailureContextCaptureDialog(ErrorCategory preSelectedCategory, String preFilledSymptom, UiTestAgentConfig config) {
        super(null, "Capture Failure Context", config);

        categoryComboBox = new JComboBox<>(ErrorCategory.values());
        if (preSelectedCategory != null) {
            categoryComboBox.setSelectedItem(preSelectedCategory);
        }

        symptomField = new JTextField(preFilledSymptom == null ? "" : preFilledSymptom, 30);

        resolutionArea = new JTextArea(4, 30);
        resolutionArea.setLineWrap(true);
        resolutionArea.setWrapStyleWord(true);

        JButton submitButton = new JButton("Submit");
        JButton cancelButton = new JButton("Cancel");

        submitButton.addActionListener(_ -> {
            result.set(createContext());
            dispose();
        });

        cancelButton.addActionListener(_ -> {
            result.set(null);
            dispose();
        });

        JPanel panel = new JPanel(new BorderLayout(dialogDefaultHorizontalGap, dialogDefaultVerticalGap));
        panel.setBorder(BorderFactory.createEmptyBorder(dialogDefaultVerticalGap, dialogDefaultHorizontalGap,
                dialogDefaultVerticalGap, dialogDefaultHorizontalGap));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(dialogDefaultVerticalGap / 2, dialogDefaultHorizontalGap / 2,
                dialogDefaultVerticalGap / 2, dialogDefaultHorizontalGap / 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Category:"), gbc);
        gbc.gridx = 1;
        formPanel.add(categoryComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Symptom:"), gbc);
        gbc.gridx = 1;
        formPanel.add(symptomField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Resolution:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        formPanel.add(new JScrollPane(resolutionArea), gbc);

        JLabel descLabel = new JLabel("<html><b>A failure occurred.</b> Help the agent learn by capturing the context and resolution so it can avoid this error in the future.</html>");
        descLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, dialogDefaultVerticalGap, 0));

        panel.add(descLabel, BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);

        JPanel buttonPanel = getButtonsPanel(submitButton, cancelButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        add(panel);
    }

    private FailureContext createContext() {
        ErrorCategory category = (ErrorCategory) categoryComboBox.getSelectedItem();
        String symptom = symptomField.getText().trim();
        if (symptom.isEmpty()) {
            symptom = "Unknown failure";
        }
        String resolution = resolutionArea.getText().trim();
        return new FailureContext(UUID.randomUUID(), symptom, category, resolution, 1, Instant.now(), FailureContext.Mode.SUPERVISED);
    }

    @Override
    protected void onDialogClosing() {
        if (result.get() == null) {
            LOG.info("FailureContextCaptureDialog closed by user (cancelled).");
        }
    }

    @Override
    public void setDefaultPosition() {
        pack();
        setLocationRelativeTo(null);
    }

    public FailureContext getResult() {
        return result.get();
    }

    public static FailureContext displayAndGetSelection(ErrorCategory preSelectedCategory, String preFilledSymptom, UiTestAgentConfig config) {
        var dialog = new FailureContextCaptureDialog(preSelectedCategory, preFilledSymptom, config);
        dialog.displayPopup();
        return dialog.getResult();
    }
}
