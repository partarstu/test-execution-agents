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

import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lookup dialog that shows all semantically matching procedures with prerequisite-satisfaction scores,
 * allowing the user to select one for editing.
 *
 * <p>A single click on a row immediately selects the procedure and closes the dialog — no confirm
 * step needed since the only purpose is to pick one for editing.</p>
 *
 * <p>Use {@link #displayAndGetSelection} as the entry point.</p>
 */
class MatchingProcedureBrowseDialog extends ProcedureLookupDialog {

    private Procedure selectedProcedure = null;

    private MatchingProcedureBrowseDialog(Window owner, String initialText, KnowledgeService knowledgeService,
                                          Set<UUID> effectNodeIds, Set<UUID> recentParentIds,
                                          UiTestAgentConfig config) {
        super(owner, "Browse Matching Procedures", knowledgeService, effectNodeIds, recentParentIds, config);
        initUi(initialText);
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && resultsList.getSelectedIndex() >= 0) {
                    onProcedureSelected(resultsList.getSelectedValue());
                }
            }
        });
    }

    @Override
    protected JPanel createButtonPanel() {
        JButton cancelButton = new JButton("Cancel");
        setHoverAsClick(cancelButton);
        cancelButton.addActionListener(_ -> dispose());
        return getButtonsPanel(cancelButton);
    }

    @Override
    protected void onProcedureSelected(KnowledgeService.ScoredProcedure scored) {
        selectedProcedure = scored.procedure();
        dispose();
    }

    /**
     * Displays the browse dialog and returns the selected procedure, or empty if the user cancelled.
     *
     * @param owner            parent window
     * @param initialText      pre-populated search text
     * @param knowledgeService service for semantic search
     * @param effectNodeIds    current execution effect node IDs for prerequisite scoring
     * @param recentParentIds  recent parent procedure IDs for re-ranking
     * @param config           UI configuration
     */
    static Optional<Procedure> displayAndGetSelection(Window owner, String initialText,
                                                      KnowledgeService knowledgeService,
                                                      Set<UUID> effectNodeIds, Set<UUID> recentParentIds,
                                                      UiTestAgentConfig config) {
        return Optional.ofNullable(
                new MatchingProcedureBrowseDialog(owner, initialText, knowledgeService, effectNodeIds, recentParentIds, config)
                        .selectedProcedure);
    }
}
