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
package org.tarik.ta.user_dialogs.knowledge;

import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.user_dialogs.AbstractDialog;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Dialog for selecting which test step to start execution from.
 * Displays all test steps in a table and allows the operator to select one.
 * Default selection is step #0 (first step).
 * If dialog is closed unexpectedly, returns -1.
 */
public class TestStepSelectionPopup extends AbstractDialog {
    private int selectedStepIndex = -1;
    private final JTable stepTable;

    private TestStepSelectionPopup(Window owner, List<TestStep> testSteps, org.tarik.ta.UiTestAgentConfig config) {
        super(owner, "Select Starting Test Step", config);

        JPanel mainPanel = getDefaultMainPanel();

        // Instruction label
        JLabel instructionLabel = new JLabel(
                "<html><b>Select the test step to start from.</b><br>" +
                        "The execution will start from the selected step. If you decide starting from the beginning, all preconditions will be executed first.</html>");
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // Create table model
        String[] columnNames = { "#", "Step Description", "Test Data", "Expected Result" };
        Object[][] data = new Object[testSteps.size()][4];
        for (int i = 0; i < testSteps.size(); i++) {
            TestStep step = testSteps.get(i);
            data[i][0] = i + 1;
            data[i][1] = step.stepDescription();
            data[i][2] = formatTestData(step.testData());
            data[i][3] = step.expectedResults() != null ? step.expectedResults() : "";
        }

        DefaultTableModel model = new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        stepTable = new JTable(model);
        stepTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stepTable.setRowSelectionInterval(0, 0); // Default to first step
        stepTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        stepTable.getColumnModel().getColumn(0).setMaxWidth(50);
        stepTable.getColumnModel().getColumn(1).setPreferredWidth(300);
        stepTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        stepTable.getColumnModel().getColumn(3).setPreferredWidth(200);
        stepTable.setRowHeight(25);

        JScrollPane scrollPane = new JScrollPane(stepTable);
        scrollPane.setPreferredSize(new Dimension(750, Math.min(testSteps.size() * 25 + 30, SCREENSHOT_DISPLAY_MAX_HEIGHT)));

        // Buttons
        JButton startButton = new JButton("Start from Selected Step");
        startButton.setFont(new Font(dialogDefaultFontType, Font.BOLD, 12));
        startButton.addActionListener(_ -> {
            selectedStepIndex = stepTable.getSelectedRow();
            if (selectedStepIndex < 0) {
                selectedStepIndex = 0;
            }
            dispose();
        });
        setHoverAsClick(startButton);

        JButton startFromBeginningButton = new JButton("Start from Beginning");
        startFromBeginningButton.addActionListener(_ -> {
            selectedStepIndex = 0;
            dispose();
        });
        setHoverAsClick(startFromBeginningButton);

        JPanel buttonPanel = getButtonsPanel(startFromBeginningButton, startButton);

        mainPanel.add(instructionLabel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    @Override
    protected void onDialogClosing() {
        selectedStepIndex = -1;
    }

    /**
     * Displays the test step selection dialog and returns the selected step index.
     *
     * @param testSteps List of test steps to display
     * @param config    Agent configuration
     * @return The 0-based index of the selected step (defaults to -1 if closed
     *         unexpectedly)
     */
    public static int displayAndGetSelection(List<TestStep> testSteps, org.tarik.ta.UiTestAgentConfig config) {
        if (testSteps == null || testSteps.isEmpty()) {
            return 0;
        }
        var popup = new TestStepSelectionPopup(null, testSteps, config);
        return popup.selectedStepIndex;
    }

    /**
     * Formats the test data list into a displayable string.
     *
     * @param testData List of test data strings
     * @return Formatted string representation of test data
     */
    private static String formatTestData(List<String> testData) {
        if (testData == null || testData.isEmpty()) {
            return "";
        }
        return String.join(", ", testData);
    }
}
