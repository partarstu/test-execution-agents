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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.dto.KnowledgeSuggestionResult.SuggestedStep;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;
import org.tarik.ta.utils.ImageUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.user_dialogs.*;


import static java.awt.BorderLayout.*;
import static java.awt.Font.PLAIN;
import static java.lang.Math.min;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static javax.swing.JOptionPane.*;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.createAtomic;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.createComposite;

import java.awt.event.ActionListener;

/**
 * Dialog for collecting and editing procedure definitions for the knowledge base.
 * Supports atomic (single UI action) and composite (multi-step) procedures with prerequisites,
 * effects, and bidirectional parent-child navigation.
 */
public class ProcedureDialog extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureDialog.class);
    static final int ELEMENT_SCREENSHOT_PREFERRED_WIDTH = 200;
    static final int ELEMENT_SCREENSHOT_PREFERRED_HEIGHT = 120;

    private final UiElementRepository uiElementRepository;
    private final UiElementDialogHelper uiElementDialogHelper;

    JButton locateElementButton;
    JButton editDetailsButton;
    JButton replaceScreenshotButton;
    JButton removeElementButton;
    JButton selectUiElementButton;
    JLabel elementNameLabel;
    JLabel elementScreenshotLabel;
    JButton addChildStepButton;
    JButton removeChildStepButton;
    JButton moveChildStepUpButton;
    JButton moveChildStepDownButton;

    JTextArea descriptionArea;
    JCheckBox atomicCheckBox;
    DefaultListModel<ChildProcedureInDialog> childStepsModel;
    JPanel childStepsContainer;
    int childStepsSelectedIndex = -1;
    final DefaultListModel<String> prerequisitesModel = new DefaultListModel<>();
    JList<String> prerequisitesList;
    final DefaultListModel<String> effectsModel = new DefaultListModel<>();
    JList<String> effectsList;
    final DefaultListModel<String> testDataModel = new DefaultListModel<>();
    JList<String> testDataList;
    JTextArea expectedResultsArea;

    static final String CARD_LIST = "list";
    static final String CARD_SPINNER = "spinner";

    private boolean initializing = true;
    private boolean hasUnsavedChanges = false;
    private boolean editParentRequested = false;
    private boolean windowClosedByUser = false;
    private boolean procedureDeleted = false;
    private UUID targetUiElementId;
    private BufferedImage currentElementScreenshot;
    final UiElementDialogHelper.ElementHandlers handlers;
    private final boolean showTestDataAndExpectedResults;
    private final Set<Integer> stepsWithSimilarItems = new HashSet<>();
    @NotNull
    final KnowledgeService knowledgeService;
    @NotNull
    private final KnowledgeIngestionService ingestionService;
    @Nullable
    private final ProcedureUsageByTestCaseTrackingService usageTrackingService;
    private final UUID currentProcedureId;
    private final SuggestionLoaderFactory childLoaderFactory;
    private final ExecutionItemContext itemContext;
    private final ChildStepRowBuilder rowBuilder;
    CardLayout childStepsCardLayout;
    JPanel childStepsCards;

    private ProcedureDialog(Window owner, DialogConfig cfg) {
        super(owner, cfg.title(), cfg.uiTestAgentConfig());
        this.showTestDataAndExpectedResults = cfg.showTestDataAndExpectedResults();
        this.targetUiElementId = cfg.targetUiElementId();
        this.knowledgeService = cfg.knowledgeService();
        this.ingestionService = cfg.ingestionService();
        this.usageTrackingService = cfg.usageTrackingService();
        this.currentProcedureId = cfg.currentProcedureId();
        this.childLoaderFactory = cfg.childLoaderFactory();
        this.itemContext = cfg.itemContext();
        this.uiElementRepository = cfg.uiElementRepository();
        this.uiElementDialogHelper = cfg.uiElementDialogHelper();

        var p = cfg.existingProcedure();
        JPanel mainPanel = getDefaultMainPanel();

        JPanel headerPanel =
                ProcedureDialogBuilder.createHeaderPanel(this, p != null ? p.description() : "", cfg.headerMessage(), cfg.itemContext());
        mainPanel.add(headerPanel, NORTH);

        this.handlers = uiElementDialogHelper.buildElementHandlers(
                () -> descriptionArea.getText().trim(),
                () -> getEffectiveTestData().toString(),
                () -> targetUiElementId);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(ProcedureDialogBuilder.createTargetElementPanel(this), NORTH);
        JPanel advancedPanel = ProcedureDialogBuilder.createAdvancedPanel(this,
                p != null ? p.prerequisites() : List.of(),
                p != null ? p.effects() : List.of(),
                p != null ? p.testData() : List.of(),
                p != null ? p.expectedResults() : "",
                cfg.showTestDataAndExpectedResults());
        rightPanel.add(advancedPanel, CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(ProcedureDialogBuilder.createAtomicityPanel(this, p == null || p.isAtomic()), NORTH);
        rowBuilder = new ChildStepRowBuilder(() -> childStepsSelectedIndex, stepsWithSimilarItems, atomicCheckBox::isSelected,
                knowledgeService, idx -> {
            childStepsSelectedIndex = idx;
            refreshChildStepsList();
        }, this::editChildStep, this::openLookupForStep, this::createButton);
        centerPanel.add(ProcedureDialogBuilder.createChildStepsPanel(this, cfg.preloadedChildren()), CENTER);
        mainPanel.add(centerPanel, CENTER);

        JButton saveButton = createButton("Save", _ -> {
            if (validateInput()) {
                dispose();
            }
        });
        JButton cancelButton = createButton("Cancel", _ -> {
            if (allChangesSaved() || confirmDiscardChanges()) {
                windowClosedByUser = true;
                dispose();
            }
        });

        List<JButton> buttonsList = new ArrayList<>(List.of(saveButton));
        // Only new (unsaved) procedures may trigger AI suggestions
        if (childLoaderFactory != null && currentProcedureId == null) {
            buttonsList.add(createButton("Get AI Suggestions", _ -> handleGetAiSuggestions()));
        }
        if (cfg.hasParent()) {
            buttonsList.add(createButton("Edit Parent", _ -> {
                if (allChangesSaved() || confirmDiscardChanges()) {
                    editParentRequested = true;
                    dispose();
                }
            }));
        }
        if (currentProcedureId != null) {
            buttonsList.add(createButton("Delete", _ -> handleDeleteProcedure()));
        }
        buttonsList.add(cancelButton);

        mainPanel.add(getButtonsPanel(buttonsList.toArray(new JButton[0])), SOUTH);
        add(mainPanel);
        setDefaultSizeAndPosition();
        // Ensure the atomicity-row buttons (checkbox + Locate + Refine) are always visible
        setMinimumSize(new Dimension(1050, 650));
        if (getWidth() < 1050 || getHeight() < 650) {
            setSize(Math.max(getWidth(), 1050), Math.max(getHeight(), 650));
            setLocationRelativeTo(null);
        }
        initializing = false;
        attachDirtyListeners();
    }

    void handleAtomicityToggle() {
        if (atomicCheckBox.isSelected() && childStepsModel != null && !childStepsModel.isEmpty()) {
            if (confirmChildDeletion()) {
                childStepsModel.clear();
                updateAtomicityState();
            } else {
                atomicCheckBox.setSelected(false);
            }
        } else {
            updateAtomicityState();
        }
    }

    private boolean confirmChildDeletion() {
        Object[] options = {"Yes", "Cancel"};
        return showOptionDialog(this,
                "Changing to an Atomic Step will remove all existing child procedures.\nDo you want to continue?",
                "Confirm Deletion", YES_NO_OPTION, WARNING_MESSAGE, null, options, options[1]) == 0;
    }

    void showZoomedScreenshot() {
        if (currentElementScreenshot == null) {
            return;
        }
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Image scaled = ImageUtils.scaleToFitBox(currentElementScreenshot, screenSize.width / 2, screenSize.height / 2);
        JDialog zoomDialog = new JDialog(this, "Element Screenshot", true);
        zoomDialog.add(new JLabel(new ImageIcon(scaled)));
        zoomDialog.pack();
        zoomDialog.setLocationRelativeTo(null);
        zoomDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        zoomDialog.setVisible(true);
    }

    void updateTargetElementUi(String elementName, BufferedImage screenshot) {
        elementNameLabel.setText(elementName);
        if (screenshot != null) {
            double ratio = min(
                    (double) ELEMENT_SCREENSHOT_PREFERRED_WIDTH / screenshot.getWidth(),
                    (double) ELEMENT_SCREENSHOT_PREFERRED_HEIGHT / screenshot.getHeight());
            elementScreenshotLabel.setIcon(new ImageIcon(ratio < 1.0 ? ImageUtils.scaleImage(screenshot, ratio) : screenshot));
            elementScreenshotLabel.setText(null);
        } else {
            elementScreenshotLabel.setIcon(null);
            elementScreenshotLabel.setText("No screenshot available");
        }
        elementScreenshotLabel.getParent().setVisible(atomicCheckBox.isSelected() && targetUiElementId != null);
    }

    void runHandler(Supplier<Optional<UiElement>> handler) {
        if (handler == null) {
            return;
        }
        withDialogHidden(() -> handler.get().ifPresent(updatedElement -> {
            BufferedImage newImg = updatedElement.screenshot() != null
                    ? updatedElement.screenshot().toBufferedImage()
                    : currentElementScreenshot;
            updateTargetElementUi(updatedElement.name(), newImg);
            currentElementScreenshot = newImg;
        }));
    }

    void triggerBackgroundSimilaritySearch() {
        childStepsCardLayout.show(childStepsCards, CARD_SPINNER);
        SimilaritySearchTask.execute(knowledgeService, Collections.list(childStepsModel.elements()), getExcludedIds(), results -> {
            stepsWithSimilarItems.clear();
            stepsWithSimilarItems.addAll(results);
            childStepsCardLayout.show(childStepsCards, CARD_LIST);
            refreshChildStepsList();
        });
    }

    /**
     * Handles the "Add" button: opens the lookup dialog first, then opens the Edit child step dialog.
     */
    void handleAddChildStep() {
        withDialogHidden(() -> {
            var lookupResult = ExistingProcedureLookupDialog.displayAndGetResult(
                    this, "", knowledgeService, true, getExcludedIds(), getChildEffectNodeIds(),
                    currentProcedureId != null ? Set.of(currentProcedureId) : Set.of(), uiTestAgentConfig);
            if (lookupResult instanceof ExistingProcedureLookupDialog.LookupResult.Selected(Procedure procedure)) {
                childStepsModel.addElement(new ChildProcedureInDialog.Linked(procedure, null));
            } else if (lookupResult instanceof ExistingProcedureLookupDialog.LookupResult.CreateNew(String searchText)) {
                addAndEditNewChildStep(searchText);
            }
        });
    }

    /**
     * Adds a new child step with the given description pre-filled and immediately opens the Edit child step dialog.
     * If the edit is cancelled the step is removed from the model.
     */
    private void addAndEditNewChildStep(String description) {
        var newStep = newBlankDialogStep(description, showTestDataAndExpectedResults);
        childStepsModel.addElement(newStep);
        int newIndex = childStepsModel.size() - 1;
        refreshChildStepsList();
        editInlineChildStep(newIndex, (ChildProcedureInDialog.New) childStepsModel.get(newIndex));
        if (childStepsModel.get(newIndex) instanceof ChildProcedureInDialog.New n && n.needsSave()) {
            childStepsModel.remove(newIndex);
        }
    }

    void refreshChildStepsList() {
        childStepsContainer.removeAll();
        for (int i = 0; i < childStepsModel.size(); i++) {
            childStepsContainer.add(rowBuilder.buildRow(i, childStepsModel.get(i)));
        }
        childStepsContainer.add(Box.createVerticalGlue());
        childStepsContainer.revalidate();
        childStepsContainer.repaint();
        updateMoveButtonStates();
    }

    /**
     * Opens the lookup dialog for a specific child step and replaces it if the user selects an existing procedure.
     */
    private void openLookupForStep(int index) {
        if (index >= 0 && index < childStepsModel.size()) {
            ChildProcedureInDialog step = childStepsModel.get(index);
            withDialogHidden(() -> {
                var lookupResult = ExistingProcedureLookupDialog.displayAndGetResult(
                        this, step.description(), knowledgeService, false, getExcludedIds(), getChildEffectNodeIds(),
                        currentProcedureId != null ? Set.of(currentProcedureId) : Set.of(), uiTestAgentConfig);
                if (lookupResult instanceof ExistingProcedureLookupDialog.LookupResult.Selected(Procedure procedure)) {
                    childStepsModel.set(index, new ChildProcedureInDialog.Linked(procedure, null));
                    stepsWithSimilarItems.remove(index);
                    registerUnsavedChanges();
                }
            });
        }
    }

    void editInList(JList<String> list, DefaultListModel<String> model) {
        int index = list.getSelectedIndex();
        if (index >= 0) {
            String current = model.getElementAt(index);
            String input = (String) showInputDialog(this, "Edit:", "Edit", PLAIN_MESSAGE, null, null, current);
            if (input != null && !input.isBlank()) {
                model.set(index, input);
            }
        }
    }

    void addSelectedEffectToExpectedResults() {
        String selected = effectsList.getSelectedValue();
        if (selected == null || selected.isBlank()) {
            return;
        }
        String current = expectedResultsArea.getText();
        expectedResultsArea.setText(current.isBlank() ? selected : current + "\n" + selected);
    }

    JButton createButton(String text, ActionListener action) {
        return createButton(text, text, action);
    }

    JButton createButton(String logContext, String text, @NotNull ActionListener action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> {
            LOG.info("Button '{}' clicked", logContext);
            action.actionPerformed(e);
        });
        setHoverAsClick(button);
        return button;
    }

    void addToList(String prompt, DefaultListModel<String> model) {
        String input = showInputDialog(this, prompt);
        if (input != null && !input.isBlank()) {
            model.addElement(input);
        }
    }

    <T> void removeFromList(JList<T> list, DefaultListModel<T> model) {
        int index = list.getSelectedIndex();
        if (index >= 0) {
            model.remove(index);
        }
    }

    private void updateMoveButtonStates() {
        boolean isAtomic = atomicCheckBox.isSelected();
        moveChildStepUpButton.setEnabled(!isAtomic && childStepsSelectedIndex > 0);
        moveChildStepDownButton.setEnabled(
                !isAtomic && childStepsSelectedIndex >= 0 && childStepsSelectedIndex < childStepsModel.size() - 1);
    }

    void moveChildStep(int offset) {
        int index = childStepsSelectedIndex;
        int newIndex = index + offset;
        if (index >= 0 && newIndex >= 0 && newIndex < childStepsModel.size()) {
            ChildProcedureInDialog tmp = childStepsModel.get(index);
            childStepsModel.set(index, childStepsModel.get(newIndex));
            childStepsModel.set(newIndex, tmp);
            childStepsSelectedIndex = newIndex;
            refreshChildStepsList();
        }
    }

    private void registerUnsavedChanges() {
        if (!initializing) {
            hasUnsavedChanges = true;
        }
    }

    private Set<UUID> getExcludedIds() {
        Set<UUID> ids = new HashSet<>();
        if (currentProcedureId != null) {
            ids.add(currentProcedureId);
        }
        for (int i = 0; i < childStepsModel.size(); i++) {
            if (childStepsModel.get(i) instanceof ChildProcedureInDialog.Linked linked) {
                ids.add(linked.procedure().id());
            }
        }
        return ids;
    }

    private Set<UUID> getChildEffectNodeIds() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < childStepsModel.size(); i++) {
            if (childStepsModel.get(i) instanceof ChildProcedureInDialog.Linked linked) {
                knowledgeService.findEffectsForProcedure(linked.procedure().id())
                        .forEach(pe -> ids.add(pe.id()));
            }
        }
        return ids;
    }

    boolean allChangesSaved() {
        return !hasUnsavedChanges;
    }

    private boolean confirmDiscardChanges() {
        return showConfirmDialog(this, "You have unsaved changes. Discard them?", "Unsaved Changes", YES_NO_OPTION, WARNING_MESSAGE) ==
                YES_OPTION;
    }

    private void attachDirtyListeners() {
        var docListener = dirtyDocListener(this::registerUnsavedChanges);
        List.of(descriptionArea, expectedResultsArea).forEach(a -> a.getDocument().addDocumentListener(docListener));
        atomicCheckBox.addItemListener(_ -> registerUnsavedChanges());

        var listListener = dirtyListListener(this::registerUnsavedChanges);
        List.of(childStepsModel, prerequisitesModel, effectsModel, testDataModel).forEach(m -> m.addListDataListener(listListener));
    }

    JTextArea createWrappedTextArea(String text, int rows, int cols) {
        JTextArea area = new JTextArea(text, rows, cols);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(dialogDefaultFontType, PLAIN, dialogDefaultFontSize));
        return area;
    }

    void updateAtomicityState() {
        boolean isAtomic = atomicCheckBox.isSelected();
        boolean hasElement = isAtomic && targetUiElementId != null;

        List.of(locateElementButton, selectUiElementButton).forEach(b -> b.setEnabled(isAtomic));
        List.of(editDetailsButton, replaceScreenshotButton, removeElementButton).forEach(b -> b.setEnabled(hasElement));
        List.of(childStepsContainer, addChildStepButton, removeChildStepButton).forEach(b -> b.setEnabled(!isAtomic));
        updateMoveButtonStates();

        if (elementScreenshotLabel != null && elementScreenshotLabel.getParent() != null) {
            elementScreenshotLabel.getParent().setVisible(hasElement);
        }

        for (Component comp : childStepsContainer.getComponents()) {
            setEnabledRecursively(comp, !isAtomic);
        }
    }

    private static void setEnabledRecursively(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof Container c) {
            for (Component child : c.getComponents()) {
                setEnabledRecursively(child, enabled);
            }
        }
    }

    void handleElementSelection(@NotNull UiElementDialogHelper.AutoLocateHandler handler) {
        locateElementButton.setEnabled(false);
        locateElementButton.setText("Locating...");
        hideTemporarily();
        Thread.ofVirtual().start(() -> {
            var elementOpt = handler.locate();
            SwingUtilities.invokeLater(() -> {
                elementOpt.ifPresent(this::linkElement);
                restoreDialogAfterElementSelection();
            });
        });
    }

    void handleSelectUiElement() {
        selectUiElementButton.setEnabled(false);
        selectUiElementButton.setText("Selecting...");
        hideTemporarily();
        Thread.ofVirtual().start(() -> {
            var elementOpt = handlers.selectElement().locate();
            SwingUtilities.invokeLater(() -> {
                elementOpt.ifPresent(this::linkElement);
                restoreAfterSelectUiElement();
            });
        });
    }

    private void linkElement(@NotNull UiElement element) {
        targetUiElementId = element.id();
        currentElementScreenshot = element.screenshot() != null ? element.screenshot().toBufferedImage() : null;
        LOG.info("Target UI element linked: name='{}', id={}", element.name(), element.id());
        updateTargetElementUi(element.name(), currentElementScreenshot);
        editDetailsButton.setEnabled(true);
        replaceScreenshotButton.setEnabled(true);
        removeElementButton.setEnabled(true);
    }

    void handleRemoveElement() {
        int choice = showConfirmDialog(this, "Remove the target element connection from this procedure?",
                "Remove Element", YES_NO_OPTION, WARNING_MESSAGE);
        if (choice == YES_OPTION) {
            targetUiElementId = null;
            currentElementScreenshot = null;
            updateTargetElementUi("No element located.", null);
            updateAtomicityState();
            registerUnsavedChanges();
        }
    }

    private void handleDeleteProcedure() {
        var parents = knowledgeService.findParents(currentProcedureId);
        var testCases = usageTrackingService != null
                ? usageTrackingService.findTestCasesUsingProcedure(currentProcedureId)
                : List.<String>of();

        boolean confirmed;
        if (!parents.isEmpty() || !testCases.isEmpty()) {
            var message = buildUsageWarningMessage(parents, testCases);
            Object[] options = {"Delete Anyway", "Cancel"};
            confirmed = showOptionDialog(this, message, "Procedure In Use", YES_NO_OPTION,
                    WARNING_MESSAGE, null, options, options[1]) == 0;
        } else {
            confirmed = showConfirmDialog(this, "Delete this procedure? This action cannot be undone.",
                    "Confirm Deletion", YES_NO_OPTION, WARNING_MESSAGE) == YES_OPTION;
        }

        if (confirmed) {
            ingestionService.deleteProcedure(currentProcedureId);
            knowledgeService.onKnowledgeIngested();
            procedureDeleted = true;
            dispose();
        }
    }

    private static String buildUsageWarningMessage(List<Procedure> parents, List<String> testCases) {
        var sb = new StringBuilder("This procedure is currently in use and cannot be safely removed:\n");
        if (!parents.isEmpty()) {
            sb.append("\nParent procedures that reference it:\n");
            parents.forEach(p -> sb.append("  - ").append(p.description()).append("\n"));
        }
        if (!testCases.isEmpty()) {
            sb.append("\nTest cases that use it:\n");
            testCases.forEach(tc -> sb.append("  - ").append(tc).append("\n"));
        }
        sb.append("\nOnly this procedure is deleted. Its child procedures are kept.\nDo you want to delete it anyway?");
        return sb.toString();
    }

    private void restoreDialogAfterElementSelection() {
        if (!isDisplayable()) {
            return;
        }
        locateElementButton.setText("Locate UI Element...");
        locateElementButton.setEnabled(true);
        restoreDialogVisibility();
    }

    private void restoreAfterSelectUiElement() {
        if (!isDisplayable()) {
            return;
        }
        selectUiElementButton.setText("Select UI element");
        selectUiElementButton.setEnabled(atomicCheckBox.isSelected());
        restoreDialogVisibility();
    }

    /**
     * Opens a recursive {@link ProcedureDialog} for the given child step index.
     * Dispatches on the sealed {@link ChildProcedureInDialog} type: linked steps are edited via the
     * {@link KnowledgeIngestionService}; new steps are edited inline.
     */
    private void editChildStep(int index) {
        if (index >= 0 && index < childStepsModel.size()) {
            withDialogHidden(() -> {
                switch (childStepsModel.get(index)) {
                    case ChildProcedureInDialog.Linked linked -> editLinkedChildStep(index, linked);
                    case ChildProcedureInDialog.New newStep -> editInlineChildStep(index, newStep);
                }
            });
        }
    }

    private void editLinkedChildStep(int index, ChildProcedureInDialog.Linked linked) {
        var procedure = knowledgeService.findById(linked.procedure().id()).orElse(null);
        if (procedure == null) {
            showMessageDialog(this, "Linked procedure no longer exists in the knowledge base.",
                    "Not Found", ERROR_MESSAGE);
            return;
        }
        var targetElementId = procedure.isAtomic()
                ? knowledgeService.findTargetedUiElementId(procedure.id()).orElse(null)
                : null;
        var children = knowledgeService.getChildren(procedure.id());
        var outcome = displayForEditing(this, procedure, targetElementId,
                showTestDataAndExpectedResults, false, itemContext, knowledgeService, ingestionService,
                buildChildLoaderFactory(index), children.isEmpty() ? null : children,
                uiTestAgentConfig, uiElementRepository, uiElementDialogHelper, usageTrackingService);
        if (outcome.deleted()) {
            childStepsModel.remove(index);
            registerUnsavedChanges();
        } else if (outcome.result() instanceof IngestionNode.NewProcedure np) {
            ingestionService.update(procedure.id(), np);
            knowledgeService.onKnowledgeIngested();
            var updatedProcedure = knowledgeService.findById(procedure.id()).orElse(procedure);
            childStepsModel.set(index, new ChildProcedureInDialog.Linked(updatedProcedure, outcome.elementScreenshot()));
            registerUnsavedChanges();
        }
    }

    private void editInlineChildStep(int index, ChildProcedureInDialog.New existing) {
        var preloadedChildren = existing.children().isEmpty() ? null : existing.children();
        var cfg = new DialogConfig("Edit Child Step",
                "Modify this child step. Use 'Edit Parent' to return to the parent without saving.",
                existing.procedure(), preloadedChildren,
                existing.targetUiElementId(), showTestDataAndExpectedResults, true, existing.needsSave(),
                itemContext, knowledgeService, ingestionService, null,
                buildChildLoaderFactory(index), existing.elementScreenshot(),
                uiTestAgentConfig, uiElementRepository, uiElementDialogHelper, null);
        var outcome = openDialog(this, cfg);
        if (!outcome.cancelled() && !outcome.editParentRequested() && outcome.result() instanceof IngestionNode.NewProcedure np) {
            UUID newId = ingestionService.ingest(np);
            knowledgeService.onKnowledgeIngested();
            var updated = new ChildProcedureInDialog.Linked(knowledgeService.findById(newId).orElseThrow(), outcome.elementScreenshot());
            childStepsModel.set(index, updated);
            registerUnsavedChanges();
        }
    }

    /**
     * Creates a {@link SuggestionLoaderFactory} for the child step at {@code stepIndex}.
     * The factory combines the current dialog's preceding atomics (siblings before {@code stepIndex},
     * flattened to atomic leaves) with any deeper-level preceding atomics supplied by grandchild dialogs,
     * then delegates to the parent factory — composing the full projected execution graph across all
     * nesting levels.
     */
    private @Nullable SuggestionLoaderFactory buildChildLoaderFactory(int stepIndex) {
        if (childLoaderFactory == null) {
            return null;
        }
        return (subPrecedingSupplier) -> childLoaderFactory.create(() -> {
            var combined = new ArrayList<>(collectPrecedingAtomics(stepIndex));
            combined.addAll(subPrecedingSupplier.get());
            return combined;
        });
    }

    /**
     * Collects all atomic leaf procedures from child steps that come before {@code upToExcluded}.
     * Linked steps are resolved via the knowledge service; unsaved New steps are traversed in-memory.
     * All preceding siblings are included regardless of whether they have been saved yet.
     */
    private List<Procedure> collectPrecedingAtomics(int upToExcluded) {
        return Collections.list(childStepsModel.elements()).stream()
                .limit(upToExcluded)
                .flatMap(step -> flattenToAtomics(step).stream())
                .toList();
    }

    private List<Procedure> flattenToAtomics(ChildProcedureInDialog step) {
        return switch (step) {
            case ChildProcedureInDialog.Linked linked -> knowledgeService.resolveToAtomicSteps(linked.procedure().id());
            case ChildProcedureInDialog.New newStep -> {
                if (newStep.isAtomic()) {
                    yield List.of(newStep.procedure());
                }
                yield newStep.children().stream()
                        .flatMap(child -> flattenToAtomics(child).stream())
                        .toList();
            }
        };
    }

    /**
     * Loads and applies AI suggestions without user confirmation — used for auto-trigger on new child steps.
     * Preceding atomics are baked into the loader by the parent dialog's {@code buildChildLoaderFactory}.
     */
    private void autoLoadSuggestions() {
        if (childLoaderFactory != null) {
            withDialogHidden(this::loadAndApplySuggestions);
        }
    }

    private void handleGetAiSuggestions() {
        if (showConfirmDialog(this,
                "This will replace all current field values with AI-generated suggestions. All existing values will be discarded. Continue?",
                "Get AI Suggestions", YES_NO_OPTION, WARNING_MESSAGE) != YES_OPTION) {
            return;
        }
        withDialogHidden(this::loadAndApplySuggestions);
    }

    private void loadAndApplySuggestions() {
        // No preceding siblings for the procedure itself — they are captured in the factory's outer context
        applySuggestions(childLoaderFactory.create(List::of).load(descriptionArea.getText().trim()));
    }

    /**
     * Replaces all editable field values with the provided AI suggestions.
     * Clears child steps before setting atomicity to avoid the child-removal confirmation dialog.
     */
    private void applySuggestions(KnowledgeSuggestionResult suggestions) {
        initializing = true;
        try {
            childStepsModel.clear();
            atomicCheckBox.setSelected(suggestions.isAtomicSuggestion());
            updateAtomicityState();
            if (suggestions.suggestedChildSteps() != null) {
                for (SuggestedStep step : suggestions.suggestedChildSteps()) {
                    // Atomicity and expected results are TBD — user must edit each child step to configure it
                    childStepsModel.addElement(newBlankDialogStep(step.description(), showTestDataAndExpectedResults));
                }
            }
            prerequisitesModel.clear();
            ofNullable(suggestions.suggestedPreconditions()).ifPresent(l -> l.forEach(prerequisitesModel::addElement));
            effectsModel.clear();
            ofNullable(suggestions.suggestedEffects()).ifPresent(l -> l.forEach(effectsModel::addElement));
            if (showTestDataAndExpectedResults) {
                ofNullable(suggestions.suggestedExpectedResults()).ifPresent(expectedResultsArea::setText);
            }
        } finally {
            initializing = false;
        }
        hasUnsavedChanges = true;
        if (!childStepsModel.isEmpty()) {
            triggerBackgroundSimilaritySearch();
        }
    }

    private boolean validateInput() {
        String description = descriptionArea.getText().trim();
        if (description.isBlank()) {
            showMessageDialog(this, "Please enter a description for the procedure.", "Validation Error", ERROR_MESSAGE);
            return false;
        }

        if (!atomicCheckBox.isSelected() && childStepsModel.isEmpty()) {
            showMessageDialog(this, "Composite procedures must have at least one child step.", "Validation Error", ERROR_MESSAGE);
            return false;
        }

        var children = Collections.list(childStepsModel.elements());
        var unsavedChild = children.stream().filter(s -> s instanceof ChildProcedureInDialog.New n && n.needsSave()).findFirst();
        if (unsavedChild.isPresent()) {
            showMessageDialog(this, "Child step '%s' has not been saved yet. Please click Edit and save it before saving this procedure."
                    .formatted(unsavedChild.get().description()), "Validation Error", ERROR_MESSAGE);
            return false;
        }

        var invalidChild = ChildProcedureInDialog.findInvalidComposite(children);
        if (invalidChild.isPresent()) {
            showMessageDialog(this, "Child step '%s' is composite but has no children. All composite steps must have at least one child."
                    .formatted(invalidChild.get()), "Validation Error", ERROR_MESSAGE);
            return false;
        }

        if (atomicCheckBox.isSelected() && targetUiElementId == null) {
            int result = showConfirmDialog(this, "No target UI element selected. Continue anyway?", "No Target Element", YES_NO_OPTION);
            return result == YES_OPTION;
        }
        return true;
    }

    @Override
    protected void onDialogClosing() {
        windowClosedByUser = true;
    }

    private ProcedureDialogOutcome getDialogOutcome() {
        if (procedureDeleted) {
            return new ProcedureDialogOutcome(null, false, false, true, null);
        }
        if (windowClosedByUser) {
            return new ProcedureDialogOutcome(null, false, true, false, null);
        }
        if (editParentRequested) {
            return new ProcedureDialogOutcome(null, true, false, false, null);
        }

        String description = descriptionArea.getText().trim();
        boolean isAtomic = atomicCheckBox.isSelected();
        boolean isPrecondition = !showTestDataAndExpectedResults;
        var prerequisites = modelToList(prerequisitesModel);
        var effects = modelToList(effectsModel);
        List<String> testData = modelToList(testDataModel);
        String expectedResults = expectedResultsArea.getText().trim();

        Procedure procedure = isAtomic
                ? createAtomic(description, testData, expectedResults, prerequisites, effects, isPrecondition)
                : createComposite(description, testData, expectedResults, prerequisites, effects, isPrecondition);
        List<IngestionNode> childNodes = isAtomic ? List.of()
                : Collections.list(childStepsModel.elements()).stream().map(ChildProcedureInDialog::toIngestionNode).toList();
        var result = new IngestionNode.NewProcedure(procedure, isAtomic ? targetUiElementId : null, childNodes);
        return new ProcedureDialogOutcome(result, false, false, false, currentElementScreenshot);
    }

    private static ChildProcedureInDialog.New newBlankDialogStep(String description, boolean showTestDataAndExpectedResults) {
        return new ChildProcedureInDialog.New(
                createAtomic(description, List.of(), "", List.of(), List.of(), !showTestDataAndExpectedResults),
                null, null, true, List.of());
    }

    private List<String> getEffectiveTestData() {
        if (itemContext != null && itemContext.hasTestData()) {
            return itemContext.testData();
        }
        return modelToList(testDataModel);
    }

    private static List<String> modelToList(DefaultListModel<String> model) {
        return Collections.list(model.elements());
    }

    /**
     * Unified internal entry point: creates the dialog, sets up element UI if needed,
     * schedules auto-suggestion loading if configured, then displays and returns the outcome.
     */
    private static ProcedureDialogOutcome openDialog(Window owner, DialogConfig cfg) {
        var dialog = new ProcedureDialog(owner, cfg);
        if (cfg.targetUiElementId() != null) {
            setupTargetElementUi(dialog, cfg.targetUiElementId(), cfg.preloadedElementScreenshot());
        }
        if (cfg.autoTriggerSuggestions()) {
            SwingUtilities.invokeLater(dialog::autoLoadSuggestions);
        }
        dialog.displayPopup();
        return dialog.getDialogOutcome();
    }

    /**
     * Displays the collecting knowledge dialog for a new procedure and returns the result.
     * Pre-loaded AI suggestions pre-fill the form; {@code childLoaderFactory} powers the "Get AI Suggestions"
     * button and auto-trigger for new child steps.
     */
    public static Optional<IngestionNode> displayAndGetResult(Window owner, String initialDescription,
                                                              KnowledgeSuggestionResult aiSuggestions,
                                                              boolean showTestDataAndExpectedResults,
                                                              @Nullable ExecutionItemContext itemContext,
                                                              @NotNull KnowledgeService knowledgeService,
                                                              @NotNull KnowledgeIngestionService ingestionService,
                                                              @Nullable SuggestionLoaderFactory childLoaderFactory,
                                                              @NotNull UiTestAgentConfig uiTestAgentConfig,
                                                              @NotNull UiElementRepository uiElementRepository,
                                                              @NotNull UiElementDialogHelper uiElementDialogHelper) {
        List<ChildProcedureInDialog> preloadedChildren = (aiSuggestions != null && !aiSuggestions.suggestedChildSteps().isEmpty())
                ? aiSuggestions.suggestedChildSteps().stream()
                .<ChildProcedureInDialog>map(s -> newBlankDialogStep(s.description(), showTestDataAndExpectedResults))
                .toList()
                : null;
        var cfg = new DialogConfig("Collect knowledge New Procedure",
                "Define a new procedure. AI suggestions are pre-filled where available.",
                buildTransientProcedure(initialDescription, aiSuggestions, showTestDataAndExpectedResults),
                preloadedChildren, null, showTestDataAndExpectedResults, false, false,
                itemContext, knowledgeService, ingestionService, null, childLoaderFactory, null,
                uiTestAgentConfig, uiElementRepository, uiElementDialogHelper, null);
        var outcome = openDialog(owner, cfg);
        return (outcome.cancelled() || outcome.editParentRequested()) ? empty() : ofNullable(outcome.result());
    }

    /**
     * Builds a transient {@link Procedure} to pre-fill the dialog with AI suggestion data or an initial description.
     * Returns {@code null} when there is nothing to pre-fill (empty form).
     */
    @Nullable
    private static Procedure buildTransientProcedure(String initialDescription,
                                                     @Nullable KnowledgeSuggestionResult aiSuggestions,
                                                     boolean showTestDataAndExpectedResults) {
        if (initialDescription.isBlank() && aiSuggestions == null) {
            return null;
        }
        var ai = ofNullable(aiSuggestions);
        var prerequisites = ai.map(KnowledgeSuggestionResult::suggestedPreconditions).orElse(List.of());
        var effects = ai.map(KnowledgeSuggestionResult::suggestedEffects).orElse(List.of());
        var expectedResults = ai.map(KnowledgeSuggestionResult::suggestedExpectedResults).orElse("");
        boolean isAtomic = aiSuggestions == null || aiSuggestions.isAtomicSuggestion();
        return isAtomic
                ? createAtomic(initialDescription, List.of(), expectedResults, prerequisites, effects, !showTestDataAndExpectedResults)
                : createComposite(initialDescription, List.of(), expectedResults, prerequisites, effects, !showTestDataAndExpectedResults);
    }

    /**
     * Displays the collecting knowledge dialog for editing an existing procedure.
     * {@code childLoaderFactory} powers AI suggestions for any new child steps added during editing.
     */
    public static ProcedureDialogOutcome displayForEditing(Window owner, Procedure existingProcedure,
                                                           UUID targetUiElementId,
                                                           boolean showTestDataAndExpectedResults, boolean hasParent,
                                                           @Nullable ExecutionItemContext itemContext,
                                                           @NotNull KnowledgeService knowledgeService,
                                                           @NotNull KnowledgeIngestionService ingestionService,
                                                           @Nullable SuggestionLoaderFactory childLoaderFactory,
                                                           @Nullable List<Procedure> preloadedChildren,
                                                           @NotNull UiTestAgentConfig uiTestAgentConfig,
                                                           @NotNull UiElementRepository uiElementRepository,
                                                           @NotNull UiElementDialogHelper uiElementDialogHelper,
                                                           @Nullable ProcedureUsageByTestCaseTrackingService usageTrackingService) {
        List<ChildProcedureInDialog> childSteps = preloadedChildren == null ? null
                : preloadedChildren.stream().<ChildProcedureInDialog>map(c -> new ChildProcedureInDialog.Linked(c, null)).toList();
        var cfg = new DialogConfig("Edit Procedure", "Modify the existing procedure definition.",
                existingProcedure, childSteps, targetUiElementId, showTestDataAndExpectedResults,
                hasParent, false, itemContext, knowledgeService, ingestionService, existingProcedure.id(),
                childLoaderFactory, null, uiTestAgentConfig,
                uiElementRepository, uiElementDialogHelper, usageTrackingService);
        return openDialog(owner, cfg);
    }

    /**
     * Enables the element-management buttons and populates the UI element panel in the dialog.
     * When a {@code preloadedScreenshot} is provided it is used directly;
     * otherwise the screenshot is loaded from the retriever by {@code elementId}.
     */
    private static void setupTargetElementUi(ProcedureDialog dialog, @Nullable UUID elementId,
                                             @Nullable BufferedImage preloadedScreenshot) {
        if (elementId != null) {
            dialog.editDetailsButton.setEnabled(true);
            dialog.replaceScreenshotButton.setEnabled(true);
            var uiElementOpt = dialog.uiElementRepository.findById(elementId);
            String name = uiElementOpt.map(UiElement::name).orElse("Element ID: " + elementId);
            BufferedImage screenshot = preloadedScreenshot != null
                    ? preloadedScreenshot
                    : uiElementOpt.map(UiElement::screenshot).map(UiElement.Screenshot::toBufferedImage).orElse(null);
            if (screenshot != null) {
                dialog.currentElementScreenshot = screenshot;
            }
            dialog.updateTargetElementUi(name, screenshot);
            dialog.removeElementButton.setEnabled(true);
        }
    }
}
