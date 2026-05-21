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

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.user_dialogs.AbstractDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Set;
import java.util.UUID;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.BorderLayout.WEST;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static org.tarik.ta.utils.HtmlUtils.escapeHtml;

/**
 * Abstract base for procedure lookup dialogs.
 *
 * <p>Provides the shared search field, scored-procedure results list (with prerequisite badges),
 * and spinner overlay. Subclasses supply their button panel and selection-confirmation logic.</p>
 *
 * <p><b>Construction note:</b> The base constructor only stores shared fields. Subclasses
 * must call {@link #initUi(String)} after initialising their own fields so that the
 * abstract {@link #createButtonPanel()} is invoked with all subclass state available.</p>
 */
abstract class ProcedureLookupDialog extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureLookupDialog.class);

    protected final KnowledgeService knowledgeService;
    protected final Set<UUID> effectNodeIds;
    protected final Set<UUID> recentParentIds;

    protected JTextField searchField;
    protected DefaultListModel<KnowledgeService.ScoredProcedure> resultsModel;
    protected JList<KnowledgeService.ScoredProcedure> resultsList;
    protected JPanel spinnerOverlay;
    private Timer searchTimer;

    protected ProcedureLookupDialog(Window owner, String title, KnowledgeService knowledgeService,
                                    Set<UUID> effectNodeIds, Set<UUID> recentParentIds, UiTestAgentConfig config) {
        super(owner, title, config);
        this.knowledgeService = knowledgeService;
        this.effectNodeIds = effectNodeIds;
        this.recentParentIds = recentParentIds;
    }

    /**
     * Builds the UI and shows the dialog. Must be called by the subclass constructor
     * after all subclass fields are initialised.
     */
    protected final void initUi(String initialText) {
        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(createSearchPanel(initialText), NORTH);
        mainPanel.add(createResultsPanel(), CENTER);
        mainPanel.add(createButtonPanel(), SOUTH);
        add(mainPanel);
        setDefaultSizeAndPosition();
        enforceMinimumWidth();

        if (!initialText.isBlank()) {
            // invokeLater queues the search into the dialog's own event loop so results appear
            // as soon as the dialog opens (setVisible blocks the calling thread on modal dialogs).
            SwingUtilities.invokeLater(() -> {
                showSpinner();
                runSearch(initialText);
            });
        }
        displayPopup();
    }

    // ── Hook / abstract methods ──────────────────────────────────────────────────

    /** IDs to exclude from search results. Default: no exclusions. */
    protected Set<UUID> getExcludedIds() { return Set.of(); }

    /** Builds the button row shown at the bottom of the dialog. Called during {@link #initUi}. */
    protected abstract JPanel createButtonPanel();

    /**
     * Called when the user confirms a selection (double-click or button press).
     * Subclasses define the confirmation behaviour (e.g. confirm-dialog, direct store).
     */
    protected abstract void onProcedureSelected(KnowledgeService.ScoredProcedure scored);

    /**
     * Called whenever the list selection changes. Subclasses may update button enabled-states here.
     * Default: no-op.
     */
    protected void onListSelectionChanged() {}

    // ── Protected helpers ────────────────────────────────────────────────────────

    @Nullable
    protected KnowledgeService.ScoredProcedure getSelectedItem() {
        return resultsList.getSelectedValue();
    }

    protected void showSpinner() {
        spinnerOverlay.setVisible(true);
        spinnerOverlay.repaint();
    }

    protected void hideSpinner() {
        spinnerOverlay.setVisible(false);
    }

    protected void scheduleSearch() {
        if (searchTimer != null && searchTimer.isRunning()) {
            searchTimer.stop();
        }
        showSpinner();
        searchTimer = new Timer((int) procedureLookupDelayMs, _ -> runSearch(searchField.getText().trim()));
        searchTimer.setRepeats(false);
        searchTimer.start();
    }

    // ── UI construction ──────────────────────────────────────────────────────────

    private JPanel createSearchPanel(String initialText) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBorder(createEmptyBorder(10, 10, 5, 10));
        panel.add(new JLabel("Description:"), WEST);
        searchField = new JTextField(initialText);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { scheduleSearch(); }
            public void removeUpdate(DocumentEvent e) { scheduleSearch(); }
            public void changedUpdate(DocumentEvent e) { scheduleSearch(); }
        });
        panel.add(searchField, CENTER);
        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                createEmptyBorder(0, 10, 5, 10), createTitledBorder("Matching procedures")));

        resultsModel = new DefaultListModel<>();
        resultsList = new JList<>(resultsModel);
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsList.setCellRenderer(new ScoredProcedureCellRenderer());
        resultsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onListSelectionChanged();
            }
        });
        resultsList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && resultsList.getSelectedIndex() >= 0) {
                    onProcedureSelected(resultsList.getSelectedValue());
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

    private void enforceMinimumWidth() {
        int minWidth = (int) (Toolkit.getDefaultToolkit().getScreenSize().width * 0.4);
        if (getWidth() < minWidth) {
            setSize(minWidth, getHeight());
            setLocationRelativeTo(null);
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────────

    private void runSearch(String text) {
        if (text.isBlank()) {
            hideSpinner();
            resultsModel.clear();
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                LOG.debug("Running procedure lookup search for: '{}'", text);
                var all = knowledgeService.findTopRankedWithScores(text, effectNodeIds, recentParentIds);
                var excluded = getExcludedIds();
                var filtered = all.stream()
                        .filter(s -> !excluded.contains(s.procedure().id()))
                        .toList();
                if (filtered.size() < all.size()) {
                    LOG.debug("Filtered out {} already-linked procedure(s) from {} result(s); {} remain",
                            all.size() - filtered.size(), all.size(), filtered.size());
                }
                SwingUtilities.invokeLater(() -> {
                    hideSpinner();
                    resultsModel.clear();
                    filtered.forEach(resultsModel::addElement);
                    resultsList.clearSelection();
                    onListSelectionChanged(); // reset button states after results refresh
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(this::hideSpinner);
                LOG.warn("Procedure lookup search failed for '{}': {}", text, e.getMessage(), e);
            }
        });
    }

    @Override
    protected void onDialogClosing() {
        if (searchTimer != null && searchTimer.isRunning()) {
            searchTimer.stop();
        }
    }

    // ── Cell renderer ────────────────────────────────────────────────────────────

    private static final class ScoredProcedureCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof KnowledgeService.ScoredProcedure s) {
                String typeStr = s.procedure().isAtomic() ? "[Atomic]" : "[Composite]";
                String badge = buildPrereqBadge(s);
                setText("<html><body><table width='100%%'><tr><td>%d. %s %s</td><td align='right'>%s</td></tr></table></body></html>"
                        .formatted(index + 1, escapeHtml(typeStr), escapeHtml(s.procedure().description()), badge));
            }
            return this;
        }

        private static String buildPrereqBadge(KnowledgeService.ScoredProcedure s) {
            if (s.totalPrereqs() == 0) return "<font color='gray'>&#8212;</font>";
            String color = s.satisfied() == s.totalPrereqs() ? "green"
                    : s.satisfied() == 0 ? "red"
                    : "#CC8800";
            return "<font color='%s'>%d/%d</font>".formatted(color, s.satisfied(), s.totalPrereqs());
        }
    }
}
