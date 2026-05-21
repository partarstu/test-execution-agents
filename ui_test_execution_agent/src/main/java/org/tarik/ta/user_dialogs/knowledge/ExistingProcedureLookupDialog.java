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
package org.tarik.ta.user_dialogs.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.UUID;

import static javax.swing.JOptionPane.OK_CANCEL_OPTION;
import static javax.swing.JOptionPane.OK_OPTION;
import static javax.swing.JOptionPane.showConfirmDialog;

/**
 * Dialog for looking up existing procedures to be added as child steps.
 *
 * <p>Use {@link #displayAndGetResult} as the entry point.</p>
 */
class ExistingProcedureLookupDialog extends ProcedureLookupDialog {
    private static final Logger LOG = LoggerFactory.getLogger(ExistingProcedureLookupDialog.class);

    /**
     * Outcome of the lookup dialog.
     */
    sealed interface LookupResult {
        record Selected(Procedure procedure) implements LookupResult {}
        record CreateNew(String searchText) implements LookupResult {}
        record Cancelled() implements LookupResult {}
    }

    private final boolean showCreateNew;
    private final Set<UUID> excludedIds;

    private JButton useSelectedButton;
    private LookupResult result = new LookupResult.Cancelled();

    private ExistingProcedureLookupDialog(Window owner, String initialText, KnowledgeService knowledgeService,
                                          boolean showCreateNew, Set<UUID> excludedIds,
                                          Set<UUID> effectNodeIds, Set<UUID> recentParentIds,
                                          UiTestAgentConfig config) {
        super(owner, "Search Existing Procedures", knowledgeService, effectNodeIds, recentParentIds, config);
        this.showCreateNew = showCreateNew;
        this.excludedIds = excludedIds;
        initUi(initialText);
    }

    @Override
    protected Set<UUID> getExcludedIds() { return excludedIds; }

    @Override
    protected JPanel createButtonPanel() {
        useSelectedButton = new JButton("Use Selected");
        useSelectedButton.setEnabled(false);
        setHoverAsClick(useSelectedButton);
        useSelectedButton.addActionListener(_ -> {
            var selected = getSelectedItem();
            if (selected != null) {
                onProcedureSelected(selected);
            }
        });

        JButton cancelButton = new JButton("Cancel");
        setHoverAsClick(cancelButton);
        cancelButton.addActionListener(_ -> dispose());

        if (showCreateNew) {
            JButton createNewButton = new JButton("Create New");
            setHoverAsClick(createNewButton);
            createNewButton.addActionListener(_ -> {
                String searchText = searchField.getText().trim();
                LOG.info("'Create New' clicked in procedure lookup with search text: '{}'", searchText);
                result = new LookupResult.CreateNew(searchText);
                dispose();
            });
            return getButtonsPanel(useSelectedButton, createNewButton, cancelButton);
        }
        return getButtonsPanel(useSelectedButton, cancelButton);
    }

    @Override
    protected void onProcedureSelected(KnowledgeService.ScoredProcedure scored) {
        int choice = showConfirmDialog(this,
                "Link to existing procedure: '%s'?".formatted(scored.procedure().description()),
                "Confirm Selection", OK_CANCEL_OPTION);
        if (choice == OK_OPTION) {
            result = new LookupResult.Selected(scored.procedure());
            dispose();
        }
        // On cancel, stay in the lookup dialog
    }

    @Override
    protected void onListSelectionChanged() {
        if (useSelectedButton != null) {
            useSelectedButton.setEnabled(resultsList.getSelectedIndex() >= 0);
        }
    }

    /**
     * Displays the lookup dialog and returns the user's choice.
     *
     * @param owner            parent window
     * @param initialText      pre-populated search text
     * @param knowledgeService service for semantic search
     * @param showCreateNew    whether to show the "Create New" button
     * @param excludedIds      IDs to exclude from results
     * @param effectNodeIds    current execution effect node IDs for prerequisite scoring
     * @param recentParentIds  recent parent procedure IDs for re-ranking
     * @param config           UI configuration
     */
    static LookupResult displayAndGetResult(Window owner, String initialText, KnowledgeService knowledgeService,
                                            boolean showCreateNew, Set<UUID> excludedIds,
                                            Set<UUID> effectNodeIds, Set<UUID> recentParentIds,
                                            UiTestAgentConfig config) {
        return new ExistingProcedureLookupDialog(owner, initialText, knowledgeService, showCreateNew, excludedIds,
                effectNodeIds, recentParentIds, config).result;
    }
}
