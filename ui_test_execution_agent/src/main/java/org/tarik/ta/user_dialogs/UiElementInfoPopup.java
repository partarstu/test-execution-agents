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

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class UiElementInfoPopup extends AbstractDialog {
    private static final int FONT_SIZE = 4;
    private final JTextArea nameField;
    private final JTextArea descriptionArea;
    private final JTextArea locationDetails;
    private final JTextArea pageSummaryArea;
    private final JCheckBox dataDependentCheckBox;
    private final JCheckBox zoomInNeededCheckBox;
    private boolean windowClosedByUser = false;

    private UiElementInfoPopup(Window owner, UiElementInfo originalElementInfo) {
        super(owner, "UI Element Info");

        JPanel panel = getDefaultMainPanel();
        var userMessageArea = getUserMessageArea(
                "Please revise, and if needed, modify the following info regarding the element");
        panel.add(new JScrollPane(userMessageArea), BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        nameField = addLabelWithValueField("Name", originalElementInfo.name(), contentPanel);
        descriptionArea = addLabelWithValueField("Description", originalElementInfo.description(), contentPanel);
        locationDetails = addLabelWithValueField("Location Details", originalElementInfo.locationDetails(),
                contentPanel);
        pageSummaryArea = addLabelWithValueField(
                "Name or short description of the page on which the element is located",
                originalElementInfo.pageSummary(), contentPanel);

        boolean isDataDependent = originalElementInfo.isDataDependent();
        dataDependentCheckBox = new JCheckBox("Data-Driven Element", isDataDependent);
        setHoverAsClick(dataDependentCheckBox);
        zoomInNeededCheckBox = new JCheckBox("Use Zoom for Precision", originalElementInfo.zoomInRequired());
        setHoverAsClick(zoomInNeededCheckBox);

        contentPanel.add(dataDependentCheckBox);
        contentPanel.add(zoomInNeededCheckBox);

        panel.add(contentPanel, BorderLayout.CENTER);

        JButton doneButton = new JButton("Done");
        setHoverAsClick(doneButton);
        doneButton.addActionListener(_ -> dispose());
        JPanel buttonsPanel = getButtonsPanel(doneButton);
        panel.add(buttonsPanel, BorderLayout.SOUTH);

        add(panel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    @NotNull
    private JTextArea addLabelWithValueField(String label, String value, JPanel panel) {
        JTextArea nameField = new JTextArea(value.trim());
        nameField.setLineWrap(true);
        nameField.setWrapStyleWord(true);

        JLabel nameLabel = new JLabel(("<html><font size='%d'><b>%s:</b></font></html>").formatted(FONT_SIZE, label));
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(nameLabel, BorderLayout.WEST);

        JScrollPane scrollPane = new JScrollPane(nameField);
        scrollPane.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(scrollPane, BorderLayout.CENTER);
        return nameField;
    }

    @Override
    protected void onDialogClosing() {
        windowClosedByUser = true;
    }

    private UiElementInfo getUpdatedUiElementInfo() {
        if (!windowClosedByUser) {
            return new UiElementInfo(nameField.getText().trim(), descriptionArea.getText().trim(),
                    locationDetails.getText().trim(), pageSummaryArea.getText().trim(),
                    zoomInNeededCheckBox.isSelected(), dataDependentCheckBox.isSelected());
        } else {
            return null;
        }
    }

    public static Optional<UiElementInfo> displayAndGetUpdatedElementInfo(Window owner,
            @NotNull UiElementInfo elementDraftFromModel) {
        var popup = new UiElementInfoPopup(owner, elementDraftFromModel);
        return ofNullable(popup.getUpdatedUiElementInfo());
    }

    public record UiElementInfo(String name, String description, String locationDetails, String pageSummary,
            boolean zoomInRequired, boolean isDataDependent) {
    }
}