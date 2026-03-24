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

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.user_dialogs.AbstractDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Set;
import java.util.UUID;

import static java.awt.BorderLayout.*;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JOptionPane.*;
import static org.tarik.ta.utils.HtmlUtils.escapeHtml;

/**
 * Dialog the lookup of existing procedures to be added as child procedures.
 *
 * <p>Use {@link #displayAndGetResult} as the entry point.</p>
 */
class ExistingProcedureLookupDialog extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(ExistingProcedureLookupDialog.class);

    /**
     * Outcome of the lookup dialog.
     */
    sealed interface LookupResult {
        record Selected(Procedure procedure) implements LookupResult {
        }

        record CreateNew(String searchText) implements LookupResult {
        }

        record Cancelled() implements LookupResult {
        }
    }

    private final KnowledgeService knowledgeService;
    private final boolean showCreateNew;
    private final Set<UUID> excludedIds;
    private final long procedureLookupDelayMs;

    private JTextField searchField;
    private DefaultListModel<Procedure> resultsModel;
    private JList<Procedure> resultsList;
    private JButton useSelectedButton;
    private Timer searchTimer;
    private JPanel spinnerOverlay;
    private LookupResult result = new LookupResult.Cancelled();

    private ExistingProcedureLookupDialog(Window owner, String initialText, KnowledgeService knowledgeService,
                                          boolean showCreateNew, Set<UUID> excludedIds, long procedureLookupDelayMs,
                                          UiTestAgentConfig config) {
        super(owner, "Search Existing Procedures", config);
        this.knowledgeService = knowledgeService;
        this.showCreateNew = showCreateNew;
        this.excludedIds = excludedIds;
        this.procedureLookupDelayMs = procedureLookupDelayMs;

        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(createSearchPanel(initialText), NORTH);
        mainPanel.add(createResultsPanel(), CENTER);
        mainPanel.add(createButtonPanel(), SOUTH);
        add(mainPanel);
        setDefaultSizeAndPosition();
        enforceMinimumWidth();

        // Schedule the initial search before displayPopup() — setVisible(true) on a modal dialog blocks the
        // calling thread, so any code after it only runs after the dialog is closed. Using invokeLater queues
        // the search into the dialog's own event loop, so results appear as soon as it opens.
        if (!initialText.isBlank()) {
            SwingUtilities.invokeLater(() -> {
                showSpinner();
                runSearch(initialText);
            });
        }
        displayPopup();
    }

    private void enforceMinimumWidth() {
        int minWidth = (int) (Toolkit.getDefaultToolkit().getScreenSize().width * 0.4);
        if (getWidth() < minWidth) {
            setSize(minWidth, getHeight());
            setLocationRelativeTo(null);
        }
    }

    private JPanel createSearchPanel(String initialText) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBorder(createEmptyBorder(10, 10, 5, 10));
        panel.add(new JLabel("Description:"), WEST);
        searchField = new JTextField(initialText);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            public void removeUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            public void changedUpdate(DocumentEvent e) {
                scheduleSearch();
            }
        });
        panel.add(searchField, CENTER);
        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(createEmptyBorder(0, 10, 5, 10), createTitledBorder("Matching procedures")));
        resultsModel = new DefaultListModel<>();
        resultsList = new JList<>(resultsModel);
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // HTML label allows the description to wrap on word boundaries when the cell is narrower than the text
        resultsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Procedure p) {
                    String typeStr = p.isAtomic() ? "[Atomic]" : "[Composite]";
                    setText("<html><body>%d. %s %s</body></html>"
                            .formatted(index + 1, escapeHtml(typeStr), escapeHtml(p.description())));
                }
                return this;
            }
        });
        resultsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                useSelectedButton.setEnabled(resultsList.getSelectedIndex() >= 0);
            }
        });
        resultsList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && resultsList.getSelectedIndex() >= 0) {
                    confirmAndSelect();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsList);
        spinnerOverlay = buildSpinnerOverlay();
        spinnerOverlay.setVisible(false);

        // JLayeredPane lets the spinner overlay sit on top of the scroll pane without replacing it
        JLayeredPane layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                for (Component c : getComponents()) {
                    c.setBounds(0, 0, getWidth(), getHeight());
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return scrollPane.getPreferredSize();
            }
        };
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(spinnerOverlay, JLayeredPane.PALETTE_LAYER);

        panel.add(layeredPane, CENTER);
        return panel;
    }

    private JPanel buildSpinnerOverlay() {
        JPanel overlay = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(220, 220, 220, 180));
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        overlay.setOpaque(false);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(200, 20));
        overlay.add(progressBar);
        return overlay;
    }

    private void showSpinner() {
        spinnerOverlay.setVisible(true);
        spinnerOverlay.repaint();
    }

    private void hideSpinner() {
        spinnerOverlay.setVisible(false);
    }

    private JPanel createButtonPanel() {
        useSelectedButton = new JButton("Use Selected");
        useSelectedButton.setEnabled(false);
        setHoverAsClick(useSelectedButton);
        useSelectedButton.addActionListener(_ -> confirmAndSelect());

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

    private void scheduleSearch() {
        if (searchTimer != null && searchTimer.isRunning()) {
            searchTimer.stop();
        }
        // Show spinner immediately so the user sees feedback as soon as they type
        showSpinner();
        searchTimer = new Timer((int) procedureLookupDelayMs, _ -> runSearch(searchField.getText().trim()));
        searchTimer.setRepeats(false);
        searchTimer.start();
    }

    private void runSearch(String text) {
        if (text.isBlank()) {
            hideSpinner();
            resultsModel.clear();
            return;
        }
        // Run search on a virtual thread; update list on EDT
        Thread.ofVirtual().start(() -> {
            try {
                LOG.debug("Running procedure lookup search for: '{}'", text);
                var allMatches = knowledgeService.findTopMatches(text);
                var matches = allMatches.stream().filter(p -> !excludedIds.contains(p.id())).toList();
                if (matches.size() < allMatches.size()) {
                    LOG.debug("Filtered out {} already-linked procedure(s) from {} result(s); {} remain for display",
                            allMatches.size() - matches.size(), allMatches.size(), matches.size());
                }
                SwingUtilities.invokeLater(() -> {
                    hideSpinner();
                    resultsModel.clear();
                    matches.forEach(resultsModel::addElement);
                    useSelectedButton.setEnabled(false);
                    resultsList.clearSelection();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(this::hideSpinner);
                LOG.warn("Procedure lookup search failed for text '{}': {}", text, e.getMessage(), e);
            }
        });
    }

    private void confirmAndSelect() {
        var selected = resultsList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int choice = showConfirmDialog(this,
                "Link to existing procedure: '%s'?".formatted(selected.description()),
                "Confirm Selection", OK_CANCEL_OPTION);
        if (choice == OK_OPTION) {
            result = new LookupResult.Selected(selected);
            dispose();
        }
        // On cancel, stay in the lookup dialog
    }

    @Override
    protected void onDialogClosing() {
        if (searchTimer != null && searchTimer.isRunning()) {
            searchTimer.stop();
        }
    }

    /**
     * Displays the lookup dialog and returns the user's choice.
     *
     * @param owner                  parent window
     * @param initialText            pre-populated search text (empty string for the "Add" flow)
     * @param knowledgeService       service for semantic search
     * @param showCreateNew         whether to show the "Create New" button
     * @param excludedIds           IDs to exclude from results
     * @param procedureLookupDelayMs delay before triggering search
     * @return the lookup result
     */
    static LookupResult displayAndGetResult(Window owner, String initialText, KnowledgeService knowledgeService,
                                            boolean showCreateNew, Set<UUID> excludedIds, long procedureLookupDelayMs,
                                            UiTestAgentConfig config) {
        return new ExistingProcedureLookupDialog(owner, initialText, knowledgeService, showCreateNew, excludedIds, procedureLookupDelayMs, config).result;
    }
}