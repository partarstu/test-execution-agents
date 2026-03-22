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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private FailureContextCaptureDialog(ErrorCategory preSelectedCategory, String preFilledSymptom) {
        super(null, "Capture Failure Context");

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

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
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
        descLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        panel.add(descLabel, BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);

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

    public static FailureContext displayAndGetSelection(ErrorCategory preSelectedCategory, String preFilledSymptom) {
        var dialog = new FailureContextCaptureDialog(preSelectedCategory, preFilledSymptom);
        dialog.displayPopup();
        return dialog.getResult();
    }
}
