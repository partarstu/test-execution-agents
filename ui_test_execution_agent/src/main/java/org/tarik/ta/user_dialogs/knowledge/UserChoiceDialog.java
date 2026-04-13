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

        JButton createNewButton = new JButton("Create New...");
        createNewButton.addActionListener(_ -> handleCreateNew(itemDescription));
        setHoverAsClick(createNewButton);

        JButton cancelButton = new JButton("Terminate");
        cancelButton.addActionListener(_ -> dispose());
        setHoverAsClick(cancelButton);

        if (!allScoredMatches.isEmpty()) {
            JButton browseButton = new JButton("Browse All...");
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
