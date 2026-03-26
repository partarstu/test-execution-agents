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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.user_dialogs.AbstractDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dialog for selecting, editing, or creating procedures when the system cannot automatically
 * resolve which procedure to use — due to low confidence, unmet prerequisites, or ambiguous
 * parent chains.
 *
 * <p>This dialog returns the user's decision to the caller, who is responsible for handling
 * the edit/create/cancel actions. This ensures loose coupling with UiTestAgent.</p>
 */
public class ProcedureSelectionPopup extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureSelectionPopup.class);

    private SelectionAction action = SelectionAction.CANCEL;
    private Procedure selectedProcedure;

    private ProcedureSelectionPopup(Window owner,
                                    String headerText,
                                    String itemDescription,
                                    List<Procedure> matches,
                                    org.tarik.ta.UiTestAgentConfig config) {
        super(owner, "Procedure Selection", config);

        JPanel mainPanel = getDefaultMainPanel();

        JLabel headerLabel = new JLabel("<html><b>" + headerText + "</b></html>");
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        mainPanel.add(headerLabel, BorderLayout.NORTH);

        DefaultListModel<Procedure> listModel = new DefaultListModel<>();
        matches.forEach(listModel::addElement);
        JList<Procedure> procedureList = new JList<>(listModel);
        procedureList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Procedure sp) {
                    int width = list.getWidth();
                    int textWidth = width > 0 ? width - 30 : 450;
                    setText(String.format("<html><body style='width: %dpx; margin: 0; padding: 0;'>%d. %s%s</body></html>",
                            textWidth, index + 1, sp.description().replace("<", "&lt;").replace(">", "&gt;"),
                            sp.isAtomic() ? " [Atomic]" : " [Composite]"));
                }
                return this;
            }
        });
        procedureList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        procedureList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = procedureList.locationToIndex(e.getPoint());
                if (index >= 0 && procedureList.getCellBounds(index, index).contains(e.getPoint())) {
                    procedureList.setEnabled(false);
                    procedureList.removeMouseListener(this);
                    Procedure selected = listModel.get(index);
                    handleEdit(selected);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(procedureList);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(500, 200));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JButton retryButton = new JButton("Retry");
        retryButton.addActionListener(_ -> handleRetry(itemDescription));
        setHoverAsClick(retryButton);

        JButton createNewButton = new JButton("Create New...");
        createNewButton.addActionListener(_ -> handleCreateNew(itemDescription));
        setHoverAsClick(createNewButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(_ -> dispose());
        setHoverAsClick(cancelButton);

        JPanel buttonPanel = getButtonsPanel(retryButton, createNewButton, cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    private void handleRetry(String itemDescription) {
        LOG.info("User selected to retry finding best match for: '{}'", itemDescription);
        this.action = SelectionAction.RETRY;
        dispose();
    }

    private void handleEdit(Procedure sp) {
        LOG.info("User selected to edit existing procedure: '{}' (id={})", sp.description(), sp.id());
        this.action = SelectionAction.EDIT;
        this.selectedProcedure = sp;
        dispose();
    }

    private void handleCreateNew(String itemDescription) {
        LOG.info("User selected to create new procedure for: '{}'", itemDescription);
        this.action = SelectionAction.CREATE;
        dispose();
    }

    @Override
    protected void onDialogClosing() {
        action = SelectionAction.CANCEL;
    }

    /**
     * Displays the popup and returns the user's selection decision.
     * The caller is responsible for handling the edit/create/cancel actions.
     *
     * @param owner           the parent window
     * @param headerText      context-specific message explaining why the popup is shown
     * @param itemDescription the description of the item being matched (used for logging)
     * @param matches         the list of candidate procedures
     * @param config          Agent configuration
     * @return the user's selection decision, or empty if cancelled
     */
    public static Optional<UserSelectionResult> displayAndGetSelection(
            Window owner,
            String headerText,
            String itemDescription,
            List<Procedure> matches,
            org.tarik.ta.UiTestAgentConfig config) {

        var popup = new ProcedureSelectionPopup(owner, headerText, itemDescription, matches, config);

        if (popup.action == SelectionAction.CANCEL) {
            LOG.info("User cancelled procedure selection for: '{}'", itemDescription);
            return Optional.empty();
        }

        if (popup.action == SelectionAction.EDIT) {
            return Optional.of(new UserSelectionResult(popup.action, popup.selectedProcedure.id(), popup.selectedProcedure));
        } else {
            return Optional.of(new UserSelectionResult(popup.action, null, null));
        }
    }

    /**
     * Represents the user's selection action from the popup.
     */
    public enum SelectionAction {
        EDIT,
        CREATE,
        RETRY,
        CANCEL
    }

    /**
     * Result record containing the user's selection.
     *
     * @param action            the selected action
     * @param existingId        the ID of the selected procedure to edit, or null otherwise
     * @param selectedProcedure the selected procedure to edit, or null otherwise
     */
    public record UserSelectionResult(SelectionAction action, UUID existingId, Procedure selectedProcedure) {
    }
}
