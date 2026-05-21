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

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.UiElementDescriptionExtractionAgent;
import org.tarik.ta.agents.UiElementResolutionAgent;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.tools.UiElementRefinementHelper;
import org.tarik.ta.user_dialogs.SpinnerManager;

import static org.tarik.ta.user_dialogs.knowledge.UiElementDialogHelper.DescriptionLabel.ACTION_DESCRIPTION;
import static org.tarik.ta.user_dialogs.knowledge.UiElementDialogHelper.DescriptionLabel.ELEMENT_DESCRIPTION;
import org.tarik.ta.user_dialogs.UiElementLookupDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.tarik.ta.agents.UiElementResolutionAgent.CREATE_WORKFLOW;
import static org.tarik.ta.agents.UiElementResolutionAgent.RESOLVE_WORKFLOW;
import static org.tarik.ta.utils.UiCommonUtils.captureElementScreenshot;

@Singleton
public class UiElementDialogHelper {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementDialogHelper.class);

    private final UiElementResolutionAgent uiElementResolutionAgent;
    private final UiElementDescriptionExtractionAgent uiElementDescriptionExtractionAgent;
    private final UiTestAgentConfig uiTestAgentConfig;
    private final UiElementRepository uiElementRepository;
    private final UiElementRefinementHelper uiElementRefinementHelper;

    public UiElementDialogHelper(UiElementResolutionAgent uiElementResolutionAgent,
                                 UiElementDescriptionExtractionAgent uiElementDescriptionExtractionAgent,
                                 UiTestAgentConfig uiTestAgentConfig,
                                 UiElementRepository uiElementRepository, UiElementRefinementHelper uiElementRefinementHelper) {
        this.uiElementResolutionAgent = uiElementResolutionAgent;
        this.uiElementDescriptionExtractionAgent = uiElementDescriptionExtractionAgent;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.uiElementRepository = uiElementRepository;
        this.uiElementRefinementHelper = uiElementRefinementHelper;
    }

    /**
     * Builds an {@link AutoLocateHandler} that runs {@link UiElementResolutionAgent#resolveForAction} with the configured timeout.
     * The handler is blocking — callers must invoke it from a virtual thread. On success returns the located {@link UiElement};
     * on any failure (agent, timeout, or element not found in DB) logs the error, shows an error dialog on the EDT, and returns empty.
     */
    public AutoLocateHandler buildAutoLocateHandler(Supplier<String> actionDescriptionSupplier, Supplier<String> relatedDataSupplier) {
        return () -> {
            LOG.info("Starting workflow: Automatic Resolution of UI Element...");
            String actionDescription = actionDescriptionSupplier.get();
            String relatedData = relatedDataSupplier.get();
            var future = CompletableFuture.supplyAsync(() -> uiElementResolutionAgent.executeAndGetResult(
                            () -> uiElementResolutionAgent.resolveForAction(ACTION_DESCRIPTION.label, actionDescription, relatedData, RESOLVE_WORKFLOW)))
                    .orTimeout(uiTestAgentConfig.getMaxActionExecutionDurationMillis(), MILLISECONDS);
            try {
                return processAgentResult(future.join().getResultPayload(), actionDescription);
            } catch (CancellationException | CompletionException e) {
                handleAgentException(e, actionDescription);
                return Optional.empty();
            } catch (Exception e) {
                String msg = "KnowledgeCollectionElementResolutionAgent failed for '%s'.".formatted(actionDescription);
                LOG.error(msg, e);
                dispatchFailure("An unexpected error occurred while locating the element: " + e.getMessage());
                return Optional.empty();
            } finally {
                SpinnerManager.hide();
                LOG.info("Completed workflow: Automatic Resolution of UI Element...");
            }
        };
    }

    /**
     * Builds an {@link AutoLocateHandler} that creates a new UI element (skipping DB search) and then locates it on screen.
     * Must be invoked from a virtual thread.
     */
    public AutoLocateHandler buildCreateAndLocateHandler(String uiElementDescription, Supplier<String> elementDataSupplier) {
        return () -> {
            LOG.info("Starting workflow: Create and Locate UI Element...");
            String relatedData = elementDataSupplier.get();
            var future = CompletableFuture.supplyAsync(() -> uiElementResolutionAgent.executeAndGetResult(
                            () -> uiElementResolutionAgent.resolveForAction(ELEMENT_DESCRIPTION.label, uiElementDescription, relatedData, CREATE_WORKFLOW)))
                    .orTimeout(uiTestAgentConfig.getMaxActionExecutionDurationMillis(), MILLISECONDS);
            try {
                return processAgentResult(future.join().getResultPayload(), uiElementDescription);
            } catch (CancellationException | CompletionException e) {
                handleAgentException(e, uiElementDescription);
                return Optional.empty();
            } catch (Exception e) {
                String msg = "Create and Locate UI Element agent failed for '%s'.".formatted(uiElementDescription);
                LOG.error(msg, e);
                dispatchFailure("An unexpected error occurred while creating the element: " + e.getMessage());
                return Optional.empty();
            } finally {
                SpinnerManager.hide();
                LOG.info("Completed workflow: Create and Locate UI Element...");
            }
        };
    }

    /**
     * Builds an {@link AutoLocateHandler} that opens {@link UiElementLookupDialog}, allowing the user to select an
     * existing element or create a new one. Must be invoked from a virtual thread.
     *
     * <p>Before opening the dialog, uses {@link UiElementDescriptionExtractionAgent} to derive a UI-element-focused
     * search query from the procedure description. The result is cached per handler instance and invalidated whenever
     * the procedure description changes.</p>
     */
    public AutoLocateHandler buildSelectElementHandler(Supplier<String> actionDescriptionSupplier,
                                                       Supplier<String> elementDataSupplier) {
        var lastProcedureDesc = new AtomicReference<String>();
        var cachedElementDesc = new AtomicReference<String>();

        return () -> {
            LOG.info("Starting workflow: Select UI Element...");
            try {
                String procedureDescription = actionDescriptionSupplier.get();
                String uiElementDescription = resolveElementDescription(procedureDescription, lastProcedureDesc, cachedElementDesc);
                Function<String, List<UiElement>> searchFn = query ->
                        uiElementRefinementHelper.retrieveUiElementsWithMinimumSimilarity(uiElementRepository, query)
                                .stream().map(UiElementRepository.UiElementMatch::element).toList();
                Consumer<UiElement> deleteHandler = uiElementRepository::remove;
                AtomicReference<UiElementLookupDialog.LookupResult> result = new AtomicReference<>();
                SwingUtilities.invokeAndWait(() -> result.set(UiElementLookupDialog.displayAndGetChoice(
                        null, uiElementDescription, searchFn, deleteHandler, uiTestAgentConfig)));

                return switch (result.get()) {
                    case UiElementLookupDialog.LookupResult.Selected(UiElement element) -> Optional.of(element);
                    case UiElementLookupDialog.LookupResult.CreateNew(String elementDescription) ->
                            buildCreateAndLocateHandler(elementDescription, elementDataSupplier).locate();
                    case UiElementLookupDialog.LookupResult.Cancelled() -> Optional.empty();
                };
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Select UI Element workflow was interrupted", e);
                return Optional.empty();
            } catch (InvocationTargetException e) {
                LOG.error("Select UI Element workflow failed", e);
                return Optional.empty();
            } finally {
                LOG.info("Completed workflow: Select UI Element...");
            }
        };
    }

    /**
     * Returns the cached element description if the procedure description hasn't changed; otherwise calls the
     * extraction agent, caches the result, and returns it. Falls back to the raw procedure description on failure.
     */
    private String resolveElementDescription(String procedureDescription,
                                              AtomicReference<String> lastProcedureDesc,
                                              AtomicReference<String> cachedElementDesc) {
        if (procedureDescription.equals(lastProcedureDesc.get())) {
            LOG.debug("Reusing cached element description for procedure: '{}'", procedureDescription);
            return cachedElementDesc.get();
        }
        LOG.info("Extracting UI element description for procedure: '{}'", procedureDescription);
        try {
            var result = uiElementDescriptionExtractionAgent.executeAndGetResult(
                    () -> uiElementDescriptionExtractionAgent.extract(procedureDescription));
            var payload = result.getResultPayload();
            String extracted = payload != null && !payload.elementDescription().isBlank()
                    ? payload.elementDescription() : procedureDescription;
            lastProcedureDesc.set(procedureDescription);
            cachedElementDesc.set(extracted);
            return extracted;
        } catch (Exception e) {
            LOG.warn("Element description extraction failed for '{}', using procedure description as fallback",
                    procedureDescription, e);
            return procedureDescription;
        }
    }

    /**
     * Builds an {@link ElementHandlers} instance, wiring all element dialog handlers for the given item description.
     */
    public ElementHandlers buildElementHandlers(Supplier<String> itemDescriptionSupplier, Supplier<String> elementDataSupplier,
                                                Supplier<UUID> elementIdSupplier) {
        return new ElementHandlers(
                buildAutoLocateHandler(itemDescriptionSupplier, elementDataSupplier),
                () -> {
                    LOG.info("Starting workflow: Edit Element Details");
                    try {
                        return uiElementRefinementHelper.promptUserToUpdateElementInfo(uiElementRepository, elementIdSupplier.get());
                    } finally {
                        LOG.info("Completed workflow: Edit Element Details");
                    }
                },
                () -> {
                    LOG.info("Starting workflow: Replace Screenshot");
                    try {
                        return uiElementRefinementHelper.promptUserToUpdateElementScreenshot(uiElementRepository, elementIdSupplier.get());
                    } finally {
                        LOG.info("Completed workflow: Replace Screenshot");
                    }
                },
                buildSelectElementHandler(itemDescriptionSupplier, elementDataSupplier));
    }

    /**
     * Shows a modal spinner while loading AI suggestions on a virtual thread.
     * Blocks until suggestions are loaded (or the agent fails), then returns.
     */
    public static void showSpinnerUntilDone(Runnable task, String actionDescription) {
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
            LOG.warn("Spinner display interrupted for '{}': {}", actionDescription, e.getMessage());
        }
    }

    private Optional<UiElement> processAgentResult(
            org.tarik.ta.dto.UiElementLocationResult payload, String contextDescription) {
        if (payload == null || !payload.success()) {
            String reason = payload != null && payload.message() != null ? payload.message() :
                    "The element could not be located on the screen or in the database.";
            LOG.warn("Element resolution agent returned failure for '{}': {}", contextDescription, reason);
            dispatchFailure(reason);
            return Optional.empty();
        }

        String elementIdStr = payload.elementId();
        if (elementIdStr == null || elementIdStr.isBlank()) {
            LOG.error("Element resolution agent reported success but returned empty elementId for '{}'", contextDescription);
            dispatchFailure("Agent reported success but failed to provide an element ID.");
            return Optional.empty();
        }

        UUID uuid = UUID.fromString(elementIdStr);
        var existingElement = uiElementRepository.findById(uuid).orElse(null);
        if (existingElement == null) {
            String msg = "Agent resolved element ID '%s' but no matching element was found in the database.".formatted(uuid);
            LOG.error("{} Context: '{}'", msg, contextDescription);
            dispatchFailure(msg);
            return Optional.empty();
        }

        if (existingElement.screenshot() != null) {
            LOG.debug("Element '{}' ({}) already has a screenshot, reusing it", existingElement.name(), uuid);
            return Optional.of(existingElement);
        }

        BufferedImage screenshot = payload.elementScreenRegion() != null
                ? captureElementScreenshot(payload.elementScreenRegion()) : null;
        if (screenshot != null) {
            var updatedElement = new UiElement(existingElement.id(), existingElement.name(),
                    existingElement.description(), existingElement.locationDetails(), existingElement.parentElementSummary(),
                    UiElement.Screenshot.fromBufferedImage(screenshot, "png"),
                    existingElement.isDataDependent());
            uiElementRepository.update(updatedElement);
            LOG.debug("Updated screenshot for element '{}' ({}) in DB", existingElement.name(), uuid);
            return Optional.of(updatedElement);
        }
        return Optional.of(existingElement);
    }

    private void handleAgentException(RuntimeException e, String contextDescription) {
        var cause = e.getCause();
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            String msg = "Element resolution agent was interrupted for '%s'".formatted(contextDescription);
            LOG.error(msg, e);
            dispatchFailure("Element location was interrupted.");
        } else {
            String msg = "Element resolution agent timed out or failed for '%s'".formatted(contextDescription);
            LOG.error(msg, e);
            dispatchFailure("Element location failed: " + (cause != null ? cause.getMessage() : e.getMessage()));
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

    private static void dispatchFailure(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, "Locate Element Failed", JOptionPane.ERROR_MESSAGE));
    }

    /**
     * Handler that triggers the auto-locate flow via the collecting knowledge agent.
     * Blocks until the agent completes; must be invoked from a virtual thread.
     * Returns the located {@link UiElement}, or empty if location failed (error already reported to the user).
     */
    @FunctionalInterface
    public interface AutoLocateHandler {
        Optional<UiElement> locate();
    }

    /**
     * Groups the element-action handlers for the collecting knowledge dialog.
     */
    public record ElementHandlers(
            AutoLocateHandler locate,
            Supplier<Optional<UiElement>> editDetails,
            Supplier<Optional<UiElement>> replaceScreenshot,
            AutoLocateHandler selectElement) {
    }

    enum DescriptionLabel {
        ACTION_DESCRIPTION("Action description"),
        ELEMENT_DESCRIPTION("Element description");

        final String label;

        DescriptionLabel(String label) {
            this.label = label;
        }
    }
}
