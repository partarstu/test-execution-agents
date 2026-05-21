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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.user_dialogs.AbstractDialog;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dialog for selecting, editing, or creating procedures when the system cannot automatically
 * resolve which procedure to use — due to low confidence, unmet prerequisites, or ambiguous
 * parent chains.
 *
 * <p>This dialog returns the user's decision to the caller, who is responsible for handling
 * the edit/create/cancel/browse actions. This ensures loose coupling with UiTestAgent.</p>
 */
public class UserChoiceDialog extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(UserChoiceDialog.class);

    private SelectionAction action = SelectionAction.CANCEL;
    private Procedure selectedProcedure;

    private UserChoiceDialog(Window owner,
                             String headerText,
                             String itemDescription,
                             List<KnowledgeService.ScoredProcedure> allScoredMatches,
                             KnowledgeService knowledgeService,
                             Set<UUID> effectNodeIds,
                             Set<UUID> recentParentIds,
                             UiTestAgentConfig config) {
        super(owner, "Next Action", config);

        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(new JScrollPane(getUserMessageArea(headerText)), BorderLayout.CENTER);

        JButton retryButton = new JButton("Retry");
        retryButton.addActionListener(_ -> handleRetry(itemDescription));
        setHoverAsClick(retryButton);

        JButton createNewButton = new JButton("Create New Procedure");
        createNewButton.addActionListener(_ -> handleCreateNew(itemDescription));
        setHoverAsClick(createNewButton);

        JButton cancelButton = new JButton("Terminate");
        cancelButton.addActionListener(_ -> dispose());
        setHoverAsClick(cancelButton);

        if (!allScoredMatches.isEmpty()) {
            JButton browseButton = new JButton("Browse All Procedures");
            setHoverAsClick(browseButton);
            browseButton.addActionListener(_ -> {
                var selected = MatchingProcedureBrowseDialog.displayAndGetSelection(
                        this, itemDescription, knowledgeService, effectNodeIds, recentParentIds, config);
                if (selected.isPresent()) {
                    this.action = SelectionAction.BROWSE;
                    this.selectedProcedure = selected.get();
                    dispose();
                }
                // cancelled browse → stay in this dialog
            });
            mainPanel.add(getButtonsPanel(retryButton, createNewButton, browseButton, cancelButton), BorderLayout.SOUTH);
        } else {
            mainPanel.add(getButtonsPanel(retryButton, createNewButton, cancelButton), BorderLayout.SOUTH);
        }

        add(mainPanel);
        setDefaultSizeAndPosition();
        displayPopup();
    }

    private void handleRetry(String itemDescription) {
        LOG.info("User selected to retry finding best match for: '{}'", itemDescription);
        this.action = SelectionAction.RETRY;
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
     *
     * @param owner            the parent window
     * @param headerText       context-specific message explaining why the popup is shown
     * @param itemDescription  the description of the item being matched
     * @param allScoredMatches all scored matches — when non-empty, a "Browse All..." button is shown
     * @param knowledgeService service used by the browse dialog for semantic search
     * @param effectNodeIds    current execution effect node IDs for prerequisite scoring in browse
     * @param recentParentIds  recent parent procedure IDs for re-ranking in browse
     * @param config           agent configuration
     * @return the user's selection decision, or empty if cancelled
     */
    public static Optional<UserSelectionResult> displayAndGetSelection(
            Window owner,
            String headerText,
            String itemDescription,
            List<KnowledgeService.ScoredProcedure> allScoredMatches,
            KnowledgeService knowledgeService,
            Set<UUID> effectNodeIds,
            Set<UUID> recentParentIds,
            UiTestAgentConfig config) {

        var dialog = new UserChoiceDialog(owner, headerText, itemDescription,
                allScoredMatches, knowledgeService, effectNodeIds, recentParentIds, config);

        if (dialog.action == SelectionAction.CANCEL) {
            LOG.info("User cancelled procedure selection for: '{}'", itemDescription);
            return Optional.empty();
        }

        if (dialog.action == SelectionAction.BROWSE) {
            return Optional.of(new UserSelectionResult(dialog.action, dialog.selectedProcedure.id(), dialog.selectedProcedure));
        } else {
            return Optional.of(new UserSelectionResult(dialog.action, null, null));
        }
    }

    /**
     * Represents the user's selection action from the popup.
     */
    public enum SelectionAction {
        CREATE,
        RETRY,
        BROWSE,
        CANCEL
    }

    /**
     * Result record containing the user's selection.
     *
     * @param action            the selected action
     * @param existingId        the ID of the selected procedure (BROWSE), or null otherwise
     * @param selectedProcedure the selected procedure (BROWSE), or null otherwise
     */
    public record UserSelectionResult(SelectionAction action, UUID existingId, Procedure selectedProcedure) {}
}
