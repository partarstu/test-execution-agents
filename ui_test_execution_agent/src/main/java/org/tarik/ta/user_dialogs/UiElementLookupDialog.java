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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.awt.BorderLayout.*;
import static java.awt.Image.SCALE_AREA_AVERAGING;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;

/**
 * Modal dialog for looking up and selecting a UI element by semantic description.
 * Shows a pre-filled search field, a scrollable list of matching elements (thumbnail + name + description),
 * and action buttons: "Select" (disabled until an element is chosen), "Create new UI element", and optionally "Delete".
 *
 * <p>Use {@link #displayAndGetChoice} as the entry point.</p>
 */
public class UiElementLookupDialog extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementLookupDialog.class);
    private static final String DIALOG_TITLE = "Select UI Element";
    private static final String ELEMENT_LABEL_FORMAT = "<html><body style='width: %dpx; font-size: %dpx;'>"
            + "<p><b>%s</b></p><p></p><p><i>%s</i></p></body></html>";
    private static final int ELEMENT_DESCRIPTION_FONT_SIZE = 8;
    private static final int IMAGE_TARGET_WIDTH = 100;
    private static final int ELEMENT_DESCRIPTION_LENGTH = 550;

    /**
     * Outcome of the lookup dialog.
     */
    public sealed interface LookupResult {
        record Selected(UiElement element) implements LookupResult {}
        record CreateNew(String description) implements LookupResult {}
        record Cancelled() implements LookupResult {}
    }

    private final Function<String, List<UiElement>> searchFunction;
    @Nullable
    private final Consumer<UiElement> deleteHandler;

    private JTextField searchField;
    private DefaultListModel<UiElement> resultsModel;
    private JList<UiElement> resultsList;
    private JButton selectButton;
    @Nullable
    private JButton deleteButton;
    private Timer searchTimer;
    private JPanel spinnerOverlay;
    private LookupResult result = new LookupResult.Cancelled();

    private UiElementLookupDialog(Window owner, String initialDescription,
                                   Function<String, List<UiElement>> searchFunction,
                                   @Nullable Consumer<UiElement> deleteHandler,
                                   UiTestAgentConfig config) {
        super(owner, DIALOG_TITLE, config);
        this.searchFunction = searchFunction;
        this.deleteHandler = deleteHandler;

        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(createSearchPanel(initialDescription), NORTH);
        mainPanel.add(createResultsPanel(), CENTER);
        mainPanel.add(createButtonPanel(), SOUTH);
        add(mainPanel);
        setDefaultSizeAndPosition();
        enforceMinimumWidth();

        if (!initialDescription.isBlank()) {
            SwingUtilities.invokeLater(() -> {
                showSpinner();
                runSearch(initialDescription);
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

    private JPanel createSearchPanel(String initialDescription) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBorder(createEmptyBorder(10, 10, 5, 10));
        panel.add(new JLabel("Description:"), WEST);
        searchField = new JTextField(initialDescription);
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
        panel.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                createEmptyBorder(0, 10, 5, 10), createTitledBorder("Matching UI elements")));

        resultsModel = new DefaultListModel<>();
        resultsList = new JList<>(resultsModel);
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof UiElement element) {
                    var label = getElementLabel(element);
                    label.setOpaque(true);
                    label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
                    label.setBorder(new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
                    return label;
                }
                return this;
            }
        });
        resultsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                boolean hasSelection = resultsList.getSelectedIndex() >= 0;
                selectButton.setEnabled(hasSelection);
                if (deleteButton != null) {
                    deleteButton.setEnabled(hasSelection);
                }
            }
        });
        resultsList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && resultsList.getSelectedIndex() >= 0) {
                    confirmSelect();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsList);
        spinnerOverlay = buildSpinnerOverlay();
        spinnerOverlay.setVisible(false);

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

    private JPanel createButtonPanel() {
        selectButton = new JButton("Select");
        selectButton.setEnabled(false);
        setHoverAsClick(selectButton);
        selectButton.addActionListener(_ -> confirmSelect());

        JButton createNewButton = new JButton("Create new UI element");
        setHoverAsClick(createNewButton);
        createNewButton.addActionListener(_ -> {
            result = new LookupResult.CreateNew(searchField.getText().trim());
            dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        setHoverAsClick(cancelButton);
        cancelButton.addActionListener(_ -> dispose());

        if (deleteHandler != null) {
            deleteButton = new JButton("Delete");
            deleteButton.setEnabled(false);
            setHoverAsClick(deleteButton);
            deleteButton.addActionListener(_ -> confirmAndDelete());
            return getButtonsPanel(selectButton, createNewButton, deleteButton, cancelButton);
        }
        return getButtonsPanel(selectButton, createNewButton, cancelButton);
    }

    private void confirmSelect() {
        var selected = resultsList.getSelectedValue();
        if (selected == null) {
            return;
        }
        result = new LookupResult.Selected(selected);
        dispose();
    }

    private void confirmAndDelete() {
        var selected = resultsList.getSelectedValue();
        if (selected == null || deleteHandler == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete element '%s'? This action cannot be undone.".formatted(selected.name()),
                "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        selectButton.setEnabled(false);
        deleteButton.setEnabled(false);
        Thread.ofVirtual().start(() -> {
            try {
                deleteHandler.accept(selected);
                SwingUtilities.invokeLater(() -> {
                    resultsModel.removeElement(selected);
                    LOG.info("Deleted UI element: name='{}', id={}", selected.name(), selected.id());
                });
            } catch (Exception e) {
                LOG.error("Failed to delete UI element '{}'", selected.name(), e);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Failed to delete element: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    deleteButton.setEnabled(true);
                    selectButton.setEnabled(resultsList.getSelectedIndex() >= 0);
                });
            }
        });
    }

    private void scheduleSearch() {
        if (searchTimer != null && searchTimer.isRunning()) {
            searchTimer.stop();
        }
        showSpinner();
        searchTimer = new Timer(procedureLookupDelayMs, _ -> runSearch(searchField.getText().trim()));
        searchTimer.setRepeats(false);
        searchTimer.start();
    }

    private void runSearch(String text) {
        if (text.isBlank()) {
            hideSpinner();
            resultsModel.clear();
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                var matches = searchFunction.apply(text);
                SwingUtilities.invokeLater(() -> {
                    hideSpinner();
                    resultsModel.clear();
                    matches.forEach(resultsModel::addElement);
                    selectButton.setEnabled(false);
                    if (deleteButton != null) {
                        deleteButton.setEnabled(false);
                    }
                    resultsList.clearSelection();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(this::hideSpinner);
            }
        });
    }

    private void showSpinner() {
        spinnerOverlay.setVisible(true);
        spinnerOverlay.repaint();
    }

    private void hideSpinner() {
        spinnerOverlay.setVisible(false);
    }

    @NotNull
    private JLabel getElementLabel(UiElement element) {
        var elementFullName = isNotBlank(element.parentElementSummary())
                ? "%s belonging to %s".formatted(element.name(), element.parentElementSummary())
                : element.name();
        String labelText = String.format(ELEMENT_LABEL_FORMAT, ELEMENT_DESCRIPTION_LENGTH,
                ELEMENT_DESCRIPTION_FONT_SIZE, elementFullName, element.description());
        JLabel label = new JLabel(labelText);
        label.setIcon(getImageIcon(element));
        label.setHorizontalTextPosition(SwingConstants.RIGHT);
        label.setVerticalTextPosition(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setIconTextGap(dialogDefaultHorizontalGap);
        return label;
    }

    @NotNull
    private static ImageIcon getImageIcon(UiElement element) {
        if (element.screenshot() == null) {
            BufferedImage placeholder = new BufferedImage(IMAGE_TARGET_WIDTH, IMAGE_TARGET_WIDTH / 2, BufferedImage.TYPE_INT_ARGB);
            return new ImageIcon(placeholder);
        }
        var elementScreenshot = element.screenshot().toBufferedImage();
        var originalWidth = elementScreenshot.getWidth();
        var scalingRatio = ((double) IMAGE_TARGET_WIDTH) / originalWidth;
        var imageTargetHeight = (int) (elementScreenshot.getHeight() * scalingRatio);
        return new ImageIcon(
                elementScreenshot.getScaledInstance(IMAGE_TARGET_WIDTH, imageTargetHeight, SCALE_AREA_AVERAGING));
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
     * @param owner              parent window
     * @param initialDescription pre-populated search text
     * @param searchFunction     function to search elements by description
     * @param deleteHandler      handler to delete a selected element, or {@code null} to hide the Delete button
     * @param config             UI config
     */
    public static LookupResult displayAndGetChoice(Window owner, String initialDescription,
                                                    Function<String, List<UiElement>> searchFunction,
                                                    @Nullable Consumer<UiElement> deleteHandler,
                                                    UiTestAgentConfig config) {
        return new UiElementLookupDialog(owner, initialDescription, searchFunction, deleteHandler, config).result;
    }
}
