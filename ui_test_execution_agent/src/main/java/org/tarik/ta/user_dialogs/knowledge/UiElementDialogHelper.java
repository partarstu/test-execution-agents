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

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.agents.UiElementResolutionAgent;
import org.tarik.ta.dto.ElementRefinementOperation;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.tools.UiElementRefinementHelper;
import org.tarik.ta.user_dialogs.SpinnerManager;
import org.tarik.ta.user_dialogs.UiElementRefinementPopup;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.tarik.ta.utils.UiCommonUtils.captureElementScreenshot;

@Singleton
public class UiElementDialogHelper {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementDialogHelper.class);

    private final UiElementResolutionAgent uiElementResolutionAgent;
    private final UiTestAgentConfig uiTestAgentConfig;
    private final UiElementRepository uiElementRepository;
    private final UiElementRefinementHelper uiElementRefinementHelper;

    public UiElementDialogHelper(UiElementResolutionAgent uiElementResolutionAgent, UiTestAgentConfig uiTestAgentConfig,
                                  UiElementRepository uiElementRepository, UiElementRefinementHelper uiElementRefinementHelper) {
        this.uiElementResolutionAgent = uiElementResolutionAgent;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.uiElementRepository = uiElementRepository;
        this.uiElementRefinementHelper = uiElementRefinementHelper;
    }

    /**
     * Builds an {@link AutoLocateHandler} that runs {@link UiElementResolutionAgent#resolve} on a virtual thread with the configured
     * timeout and dispatches the {@link ElementSelectionResult} back to the dialog on the EDT. On success, also
     * populates {@code elementIdRef} so that edit-details and replace-screenshot handlers can read the located element's ID.
     */
    public AutoLocateHandler buildAutoLocateHandler(Supplier<String> itemDescriptionSupplier,
                                                     Supplier<String> elementDataSupplier,
                                                     AtomicReference<UUID> elementIdRef) {
        return resultCallback -> Thread.ofVirtual().start(() -> {
            LOG.info("Starting workflow: Automatic Resolution of UI Element...");
            String itemDesc = itemDescriptionSupplier.get();
            String elementData = elementDataSupplier.get();
            var future = CompletableFuture.supplyAsync(() -> uiElementResolutionAgent.executeAndGetResult(() -> uiElementResolutionAgent.resolve(itemDesc, elementData)))
                    .orTimeout(uiTestAgentConfig.getMaxActionExecutionDurationMillis(), MILLISECONDS);
            try {
                var payload = future.join().getResultPayload();
                if (payload == null || !payload.success()) {
                    String reason = payload != null && payload.message() != null ? payload.message() :
                            "The element could not be located on the screen or in the database.";
                    LOG.warn("KnowledgeCollectionElementResolutionAgent returned failure result for '{}': {}", itemDesc, reason);
                    dispatchFailure(resultCallback, reason);
                    return;
                }

                String elementIdStr = payload.elementId();
                if (elementIdStr == null || elementIdStr.isBlank()) {
                    LOG.error("KnowledgeCollectionElementResolutionAgent reported success but returned empty elementId for '{}'",                            itemDesc);
                    dispatchFailure(resultCallback, "Agent reported success but failed to provide an element ID.");
                    return;
                }

                UUID uuid = UUID.fromString(elementIdStr);
                elementIdRef.set(uuid);
                var elementName = payload.elementName() != null ? payload.elementName() : "(unknown)";
                var existingElement = uiElementRepository.findById(uuid).orElse(null);
                BufferedImage screenshot;
                if (existingElement != null && existingElement.screenshot() != null) {
                    screenshot = existingElement.screenshot().toBufferedImage();
                    LOG.debug("Element '{}' ({}) already has a screenshot, reusing it", elementName, uuid);
                } else {
                    screenshot = payload.elementScreenRegion() != null ? captureElementScreenshot(payload.elementScreenRegion()) : null;
                    if (screenshot != null && existingElement != null) {
                        var updatedElement = new UiElement(existingElement.id(), existingElement.name(),
                                existingElement.description(), existingElement.locationDetails(), existingElement.parentElementSummary(),
                                UiElement.Screenshot.fromBufferedImage(screenshot, "png"),
                                existingElement.isDataDependent());
                        uiElementRepository.update(updatedElement);
                        LOG.debug("Updated screenshot for element '{}' ({}) in DB during auto-locate", elementName, uuid);
                    }
                }

                SwingUtilities.invokeLater(() -> resultCallback.accept(new ElementSelectionResult.Selected(uuid, elementName, screenshot)));
            } catch (CancellationException | CompletionException e) {
                var cause = e.getCause();
                if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    LOG.error("KnowledgeCollectionElementResolutionAgent was interrupted for '%s'".formatted(itemDesc), e);
                    dispatchFailure(resultCallback, "Element location was interrupted.");
                } else {
                    LOG.error("KnowledgeCollectionElementResolutionAgent timed out or failed for '%s'".formatted(itemDesc), e);
                    dispatchFailure(resultCallback, "Element location failed: " + (cause != null ? cause.getMessage() : e.getMessage()));
                }
            } catch (Exception e) {
                LOG.error("KnowledgeCollectionElementResolutionAgent failed for '%s'.".formatted(itemDesc), e);
                dispatchFailure(resultCallback, "An unexpected error occurred while locating the element: " + e.getMessage());
            } finally {
                LOG.info("Completed workflow: Automatic Resolution of UI Element...");
            }
        });
    }

    /**
     * Builds an {@link ElementHandlers} instance, wiring all element dialog handlers for the given item description.
     */
    public ElementHandlers buildElementHandlers(Supplier<String> itemDescriptionSupplier,
                                                Supplier<String> elementDataSupplier,
                                                AtomicReference<UUID> elementIdRef) {
        return new ElementHandlers(
                buildAutoLocateHandler(itemDescriptionSupplier, elementDataSupplier, elementIdRef),
                () -> {
                    LOG.info("Starting workflow: Edit Element Details");
                    try {
                        return uiElementRefinementHelper.updateElementInfo(uiElementRepository, elementIdRef.get());
                    } finally {
                        LOG.info("Completed workflow: Edit Element Details");
                    }
                },
                () -> {
                    LOG.info("Starting workflow: Replace Screenshot");
                    try {
                        return uiElementRefinementHelper.updateElementScreenshot(uiElementRepository, elementIdRef.get());
                    } finally {
                        LOG.info("Completed workflow: Replace Screenshot");
                    }
                },
                buildElementRefinementHandler(itemDescriptionSupplier));
    }

    /**
     * Shows a modal spinner while loading AI suggestions on a virtual thread.
     * Blocks until suggestions are loaded (or the agent fails), then returns.
     */
    public static void showSpinnerUntilDone(Runnable task, String itemDescription) {
        var spinner = createSpinnerDialog();
        Thread.ofVirtual().name("knowledge-collection-suggestion-loader").start(() -> {
            try {
                task.run();
            } finally {
                SwingUtilities.invokeLater(spinner::dispose);
            }
        });
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                spinner.setVisible(true);
            } else {
                SwingUtilities.invokeAndWait(() -> spinner.setVisible(true));
            }
        } catch (Exception e) {
            LOG.warn("Spinner display interrupted for '{}': {}", itemDescription, e.getMessage());
        }
    }

    /**
     * Builds a {@link Runnable} handler that opens {@link UiElementRefinementPopup}
     * for elements semantically
     * similar to {@code itemDescription}, allowing the user to update or delete
     * them.
     */
    public Runnable buildElementRefinementHandler(Supplier<String> itemDescriptionSupplier) {
        return () -> {
            LOG.info("Starting workflow: Refine Elements...");
            try {
                String itemDesc = itemDescriptionSupplier.get();
                List<UiElement> elements = uiElementRefinementHelper.retrieveUiElements(uiElementRepository, itemDesc).stream()
                        .map(UiElementRepository.UiElementMatch::element)
                        .toList();
                if (elements.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No UI elements found matching: %s".formatted(itemDesc),
                            "No Elements Found", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                UiElementRefinementPopup.displayAndGetChoice(null, "Refine existing UI elements for: %s".formatted(itemDesc), elements)
                        .ifPresent(op -> processRefinementOperation(op, uiElementRepository));
            } finally {
                LOG.info("Completed workflow: Refine Elements...");
            }
        };
    }

    /**
     * Processes a {@link ElementRefinementOperation} chosen by the user in
     * {@link UiElementRefinementPopup}.
     */
    public void processRefinementOperation(ElementRefinementOperation op, UiElementRepository repository) {
        switch (op.operation()) {
            case DELETE_ELEMENT -> uiElementRefinementHelper.deleteElement(repository, op.elementId());
            case UPDATE_ELEMENT -> uiElementRefinementHelper.updateElementInfo(repository, op.elementId());
            case UPDATE_SCREENSHOT -> uiElementRefinementHelper.updateElementScreenshot(repository, op.elementId());
            case DONE -> {
                /* no-op */
            }
        }
    }

    private static JDialog createSpinnerDialog() {
        var dialog = new JDialog((Frame) null, "Please Wait", true);
        dialog.setAlwaysOnTop(true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.add(SpinnerManager.createSpinnerPanel("Generating AI suggestions, please wait..."));
        dialog.pack();
        dialog.setMinimumSize(SpinnerManager.SPINNER_MIN_SIZE);
        var screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation((screenSize.width - dialog.getWidth()) / 2, (screenSize.height - dialog.getHeight()) / 2);
        return dialog;
    }

    private static void dispatchFailure(Consumer<ElementSelectionResult> resultCallback, String message) {
        SwingUtilities.invokeLater(() -> resultCallback.accept(new ElementSelectionResult.Failure(message)));
    }

    /**
     * Handler that triggers the auto-locate flow via the collecting knowledge agent.
     * Accepts a result callback and fires the agent asynchronously; the
     * {@link ElementSelectionResult} is delivered back via the callback on the EDT.
     */
    @FunctionalInterface
    interface AutoLocateHandler {
        void locate(Consumer<ElementSelectionResult> resultCallback);
    }

    /**
     * Sealed result type for the element-selection step in the HITL collecting knowledge dialog.
     */
    sealed interface ElementSelectionResult permits ElementSelectionResult.Selected, ElementSelectionResult.Failure {
        record Selected(UUID elementId, String elementName, @Nullable BufferedImage screenshot) implements ElementSelectionResult {
        }

        record Failure(String message) implements ElementSelectionResult {
        }
    }

    /**
     * Groups the element-action handlers for the collecting knowledge dialog.
     */
    record ElementHandlers(
            AutoLocateHandler locate,
            Supplier<Optional<UiElement>> editDetails,
            Supplier<Optional<UiElement>> replaceScreenshot,
            Runnable refine) {
    }
}