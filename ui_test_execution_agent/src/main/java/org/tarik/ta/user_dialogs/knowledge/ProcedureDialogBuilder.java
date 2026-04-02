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
package org.tarik.ta.user_dialogs.knowledge;

import org.tarik.ta.user_dialogs.AbstractDialog;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static java.awt.BorderLayout.*;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.border.TitledBorder.TOP;

public class ProcedureDialogBuilder {

    public static JPanel createHeaderPanel(ProcedureDialog dialog, String initialDescription, String headerMessage,
                                           ExecutionItemContext itemContext) {
        JPanel panel = new JPanel(new BorderLayout(dialog.getDialogDefaultHorizontalGap(), dialog.getDialogDefaultVerticalGap()));
        panel.setBorder(createEmptyBorder(dialog.getDialogDefaultVerticalGap(), dialog.getDialogDefaultHorizontalGap(),
                dialog.getDialogDefaultVerticalGap(), dialog.getDialogDefaultHorizontalGap()));

        JPanel topSection = new JPanel(new BorderLayout(dialog.getDialogDefaultHorizontalGap(), dialog.getDialogDefaultVerticalGap()));
        if (itemContext != null) {
            topSection.add(createExecutionContextPanel(dialog, itemContext), NORTH);
        }
        topSection.add(new JScrollPane(dialog.getUserMessageArea(headerMessage)), CENTER);
        panel.add(topSection, NORTH);

        JPanel descPanel = new JPanel(new BorderLayout(5, 0));
        descPanel.add(new JLabel("Description:"), WEST);
        dialog.descriptionArea = dialog.createWrappedTextArea(initialDescription != null ? initialDescription : "", 3, 40);
        descPanel.add(new JScrollPane(dialog.descriptionArea), CENTER);
        panel.add(descPanel, CENTER);
        return panel;
    }

    private static JPanel createExecutionContextPanel(ProcedureDialog dialog, ExecutionItemContext itemContext) {
        String borderTitle = itemContext.isPrecondition() ? "Current Precondition" : "Current Test Step";
        JPanel panel = new JPanel(new BorderLayout(5, 4));
        panel.setBorder(createTitledBorder(borderTitle));

        String contextText;
        if (itemContext.isPrecondition()) {
            contextText = itemContext.description();
        } else {
            contextText = itemContext.hasTestData()
                    ? "Action: %s\nTest Data: %s".formatted(itemContext.description(), itemContext.formattedTestData())
                    : "Action: %s".formatted(itemContext.description());
        }

        JTextArea contextArea = dialog.createWrappedTextArea(contextText, itemContext.isPrecondition() ? 2 : 3, 0);
        contextArea.setEditable(false);
        contextArea.setBackground(panel.getBackground());
        panel.add(new JScrollPane(contextArea), CENTER);
        return panel;
    }

    public static JPanel createAtomicityPanel(ProcedureDialog dialog, boolean initialIsAtomic) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dialog.atomicCheckBox = new JCheckBox("Atomic Step (single UI action)", initialIsAtomic);
        dialog.atomicCheckBox.addActionListener(_ -> dialog.handleAtomicityToggle());
        dialog.setHoverAsClick(dialog.atomicCheckBox);
        panel.add(dialog.atomicCheckBox);

        dialog.locateElementButton =
                dialog.createButton("Locate UI Element...", _ -> dialog.handleElementSelection(dialog.handlers.locate()));
        panel.add(dialog.locateElementButton);

        dialog.selectUiElementButton = dialog.createButton("Select UI element", _ -> dialog.handleSelectUiElement());
        panel.add(dialog.selectUiElementButton);

        return panel;
    }

    public static JPanel createTargetElementPanel(ProcedureDialog dialog) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(
                createTitledBorder(BorderFactory.createEtchedBorder(), "Target UI Element", TitledBorder.LEFT, TOP));
        dialog.elementScreenshotLabel = new JLabel();
        dialog.elementScreenshotLabel.setPreferredSize(
                new Dimension(ProcedureDialog.ELEMENT_SCREENSHOT_PREFERRED_WIDTH,
                        ProcedureDialog.ELEMENT_SCREENSHOT_PREFERRED_HEIGHT));
        dialog.elementScreenshotLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dialog.elementScreenshotLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dialog.elementScreenshotLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dialog.showZoomedScreenshot();
            }
        });
        panel.add(dialog.elementScreenshotLabel, CENTER);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));

        dialog.elementNameLabel = new JLabel();
        dialog.elementNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dialog.elementNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        southPanel.add(dialog.elementNameLabel);

        JPanel elementButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        dialog.editDetailsButton = dialog.createButton("Edit UI Element Details...", _ -> dialog.runHandler(dialog.handlers.editDetails()));
        dialog.editDetailsButton.setEnabled(false);
        elementButtonsPanel.add(dialog.editDetailsButton);
        dialog.replaceScreenshotButton =
                dialog.createButton("Replace Screenshot...", _ -> dialog.runHandler(dialog.handlers.replaceScreenshot()));
        dialog.replaceScreenshotButton.setEnabled(false);
        elementButtonsPanel.add(dialog.replaceScreenshotButton);
        dialog.removeElementButton = dialog.createButton("Remove Element", _ -> dialog.handleRemoveElement());
        dialog.removeElementButton.setEnabled(false);
        elementButtonsPanel.add(dialog.removeElementButton);
        southPanel.add(elementButtonsPanel);

        panel.add(southPanel, SOUTH);
        panel.setVisible(false);
        return panel;
    }

    public static JPanel createChildStepsPanel(ProcedureDialog dialog, List<ChildProcedureInDialog> preloadedChildren) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(createTitledBorder(BorderFactory.createEtchedBorder(), "Child Steps (for Composite)",
                TitledBorder.LEFT, TOP));
        dialog.childStepsModel = new DefaultListModel<>();
        dialog.childStepsContainer = new JPanel();
        dialog.childStepsContainer.setLayout(new BoxLayout(dialog.childStepsContainer, BoxLayout.Y_AXIS));
        dialog.childStepsContainer.setBackground(UIManager.getColor("List.background"));
        JScrollPane scrollPane = new JScrollPane(dialog.childStepsContainer);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        dialog.childStepsCardLayout = new CardLayout();
        dialog.childStepsCards = new JPanel(dialog.childStepsCardLayout);
        dialog.childStepsCards.add(scrollPane, ProcedureDialog.CARD_LIST);
        dialog.childStepsCards.add(buildSpinnerCard(), ProcedureDialog.CARD_SPINNER);
        panel.add(dialog.childStepsCards, CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(dialog.addChildStepButton = dialog.createButton("Add", _ -> dialog.handleAddChildStep()));
        buttonPanel.add(dialog.removeChildStepButton = dialog.createButton("Remove", _ -> {
            if (dialog.childStepsSelectedIndex >= 0 && dialog.childStepsSelectedIndex < dialog.childStepsModel.size()) {
                dialog.childStepsModel.remove(dialog.childStepsSelectedIndex);
            }
        }));
        buttonPanel.add(dialog.moveChildStepUpButton = dialog.createButton("Move Up", _ -> dialog.moveChildStep(-1)));
        buttonPanel.add(dialog.moveChildStepDownButton = dialog.createButton("Move Down", _ -> dialog.moveChildStep(1)));
        panel.add(buttonPanel, SOUTH);

        if (preloadedChildren != null) {
            preloadedChildren.forEach(dialog.childStepsModel::addElement);
        }

        dialog.childStepsModel.addListDataListener(AbstractDialog.dirtyListListener(() -> {
            if (dialog.childStepsSelectedIndex >= dialog.childStepsModel.size()) {
                dialog.childStepsSelectedIndex = Math.max(0, dialog.childStepsModel.size() - 1);
            }
            dialog.refreshChildStepsList();
        }));
        dialog.updateAtomicityState();
        dialog.refreshChildStepsList();

        if (!dialog.childStepsModel.isEmpty()) {
            dialog.triggerBackgroundSimilaritySearch();
        }

        return panel;
    }

    private static JPanel buildSpinnerCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIManager.getColor("List.background"));
        JPanel inner = new JPanel(new BorderLayout(0, 8));
        inner.setOpaque(false);
        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        inner.add(spinner, NORTH);
        JLabel label = new JLabel("Looking up existing procedures…", SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.ITALIC));
        inner.add(label, CENTER);
        panel.add(inner);
        return panel;
    }

    public static JPanel createAdvancedPanel(ProcedureDialog dialog, List<String> initialPrerequisites,
                                             List<String> initialEffects,
                                             List<String> initialTestData, String initialExpectedResults,
                                             boolean showTestDataAndExpectedResults) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(createTitledBorder(
                BorderFactory.createEtchedBorder(), "Advanced",
                TitledBorder.LEFT, TOP));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        dialog.prerequisitesList = new JList<>(dialog.prerequisitesModel);
        contentPanel.add(createListPanel(dialog, "Prerequisites", dialog.prerequisitesList,
                _ -> dialog.addToList("Enter prerequisite:", dialog.prerequisitesModel),
                _ -> dialog.editInList(dialog.prerequisitesList, dialog.prerequisitesModel),
                _ -> dialog.removeFromList(dialog.prerequisitesList, dialog.prerequisitesModel), null, null));

        dialog.effectsList = new JList<>(dialog.effectsModel);
        contentPanel.add(createListPanel(dialog, "Effects", dialog.effectsList,
                _ -> dialog.addToList("Enter effect:", dialog.effectsModel),
                _ -> dialog.editInList(dialog.effectsList, dialog.effectsModel),
                _ -> dialog.removeFromList(dialog.effectsList, dialog.effectsModel),
                "Add as Expected Result", _ -> dialog.addSelectedEffectToExpectedResults()));

        dialog.testDataList = new JList<>(dialog.testDataModel);
        dialog.expectedResultsArea = dialog.createWrappedTextArea("", 3, 30);

        if (showTestDataAndExpectedResults) {
            contentPanel.add(createListPanel(dialog, "Test Data", dialog.testDataList,
                    _ -> dialog.addToList("Enter test data value:", dialog.testDataModel),
                    _ -> dialog.editInList(dialog.testDataList, dialog.testDataModel),
                    _ -> dialog.removeFromList(dialog.testDataList, dialog.testDataModel), null, null));
            JPanel expectedResultsPanel = new JPanel(new BorderLayout());
            expectedResultsPanel.setBorder(createTitledBorder("Expected Results"));
            expectedResultsPanel.add(new JScrollPane(dialog.expectedResultsArea), CENTER);
            contentPanel.add(expectedResultsPanel);
        } else {
            JPanel relevantDataPanel = new JPanel(new BorderLayout());
            relevantDataPanel.setBorder(createTitledBorder("Relevant Data"));
            relevantDataPanel.add(new JScrollPane(dialog.expectedResultsArea), CENTER);
            contentPanel.add(relevantDataPanel);
        }

        panel.add(contentPanel, CENTER);

        initialPrerequisites.forEach(dialog.prerequisitesModel::addElement);
        initialEffects.forEach(dialog.effectsModel::addElement);
        if (showTestDataAndExpectedResults) {
            initialTestData.forEach(dialog.testDataModel::addElement);
        }
        if (initialExpectedResults != null && !initialExpectedResults.isBlank()) {
            dialog.expectedResultsArea.setText(initialExpectedResults);
        }

        return panel;
    }

    private static <T> JPanel createListPanel(ProcedureDialog dialog, String title, JList<T> list,
                                              ActionListener addAction,
                                              ActionListener editAction, ActionListener removeAction,
                                              String extraButtonLabel, ActionListener extraAction) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(createTitledBorder(title));
        panel.add(new JScrollPane(list), CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(dialog.createButton("Add " + title, "Add", addAction));
        buttonPanel.add(dialog.createButton("Edit " + title, "Edit", editAction));
        buttonPanel.add(dialog.createButton("Remove " + title, "Remove", removeAction));
        if (extraButtonLabel != null && extraAction != null) {
            buttonPanel.add(dialog.createButton(extraButtonLabel, extraButtonLabel, extraAction));
        }
        panel.add(buttonPanel, SOUTH);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editAction.actionPerformed(null);
                }
            }
        });
        return panel;
    }
}
