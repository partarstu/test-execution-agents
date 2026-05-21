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

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.UiTestAgentConfig;

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
    private boolean windowClosedByUser = false;

    private UiElementInfoPopup(Window owner, UiElementInfo originalElementInfo, UiTestAgentConfig config) {
        super(owner, "UI Element Info", config);

        JPanel panel = getDefaultMainPanel();
        var userMessageArea = getUserMessageArea(
                "Please revise, and if needed, modify the following info regarding the element");
        panel.add(new JScrollPane(userMessageArea), BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(dialogDefaultVerticalGap, dialogDefaultHorizontalGap,
                dialogDefaultVerticalGap, dialogDefaultHorizontalGap));
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
        
        contentPanel.add(dataDependentCheckBox);

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
                    dataDependentCheckBox.isSelected());
        } else {
            return null;
        }
    }

    public static Optional<UiElementInfo> displayAndGetUpdatedElementInfo(Window owner,
            @NotNull UiElementInfo elementDraftFromModel, UiTestAgentConfig config) {
        var popup = new UiElementInfoPopup(owner, elementDraftFromModel, config);
        return ofNullable(popup.getUpdatedUiElementInfo());
    }

    public record UiElementInfo(String name, String description, String locationDetails, String pageSummary,
            boolean isDataDependent) {
    }
}