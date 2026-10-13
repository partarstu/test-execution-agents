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
package org.tarik.ta.knowledge_graph.service;

import dev.langchain4j.data.message.Content;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.UiElementDescriptionExtractionAgent;
import org.tarik.ta.agents.UiElementResolutionAgent;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.dto.UiElementLocationResult;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.panel.OperatorPrompts;
import org.tarik.ta.tools.UiElementRefinementHelper;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.tarik.ta.agents.UiElementResolutionAgent.CREATE_WORKFLOW;
import static org.tarik.ta.agents.UiElementResolutionAgent.RESOLVE_WORKFLOW;
import static org.tarik.ta.core.utils.MarkdownSections.SECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.joinSections;
import static org.tarik.ta.core.utils.MarkdownSections.section;
import static org.tarik.ta.core.utils.MarkdownSections.userMessageContents;
import static org.tarik.ta.knowledge_graph.service.UiElementResolutionService.DescriptionLabel.ACTION_DESCRIPTION;
import static org.tarik.ta.knowledge_graph.service.UiElementResolutionService.DescriptionLabel.ELEMENT_DESCRIPTION;
import static org.tarik.ta.utils.UiCommonUtils.captureElementScreenshot;

/** Resolves the UI element a procedure targets through the resolution agent, bounded by the element-resolution budget. */
@UiAgentRequestScope
public class UiElementResolutionService {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementResolutionService.class);

    private final UiElementResolutionAgent uiElementResolutionAgent;
    private final UiElementDescriptionExtractionAgent uiElementDescriptionExtractionAgent;
    private final UiTestAgentConfig uiTestAgentConfig;
    private final UiElementRepository uiElementRepository;
    private final UiElementRefinementHelper uiElementRefinementHelper;
    private final UiElementAliasRegistry uiElementAliasRegistry;
    private final OperatorPrompts operatorPrompts;

    public UiElementResolutionService(UiElementResolutionAgent uiElementResolutionAgent,
                                      UiElementDescriptionExtractionAgent uiElementDescriptionExtractionAgent,
                                      UiTestAgentConfig uiTestAgentConfig,
                                      UiElementRepository uiElementRepository,
                                      UiElementRefinementHelper uiElementRefinementHelper,
                                      @NotNull UiElementAliasRegistry uiElementAliasRegistry,
                                      OperatorPrompts operatorPrompts) {
        this.uiElementResolutionAgent = uiElementResolutionAgent;
        this.uiElementDescriptionExtractionAgent = uiElementDescriptionExtractionAgent;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.uiElementRepository = uiElementRepository;
        this.uiElementRefinementHelper = uiElementRefinementHelper;
        this.uiElementAliasRegistry = uiElementAliasRegistry;
        this.operatorPrompts = operatorPrompts;
    }

    /** Locates the element the described action targets; blocks, so it must run on a virtual thread. */
    public ResolutionOutcome locate(String actionDescription, String relatedData) {
        LOG.info("Starting workflow: Automatic Resolution of UI Element...");
        var userMessage = resolutionUserMessage(ACTION_DESCRIPTION, actionDescription, relatedData);
        try {
            return runWithBudget(() -> uiElementResolutionAgent.executeAndGetResult(
                            () -> uiElementResolutionAgent.resolveForAction(userMessage, RESOLVE_WORKFLOW)).getResultPayload(),
                    actionDescription, "An unexpected error occurred while locating the element: ");
        } finally {
            LOG.info("Completed workflow: Automatic Resolution of UI Element...");
        }
    }

    /** Creates the described element and locates it on the screen; blocks, so it must run on a virtual thread. */
    public ResolutionOutcome createAndLocate(String uiElementDescription, String relatedData) {
        LOG.info("Starting workflow: Create and Locate UI Element...");
        var userMessage = resolutionUserMessage(ELEMENT_DESCRIPTION, uiElementDescription, relatedData);
        try {
            return runWithBudget(() -> uiElementResolutionAgent.executeAndGetResult(
                            () -> uiElementResolutionAgent.resolveForAction(userMessage, CREATE_WORKFLOW)).getResultPayload(),
                    uiElementDescription, "An unexpected error occurred while creating the element: ");
        } finally {
            LOG.info("Completed workflow: Create and Locate UI Element...");
        }
    }

    /**
     * Extracts the description of the element a procedure acts on, which is the procedure description when the agent finds none,
     * or empty when the extraction fails.
     */
    public Optional<String> extractElementDescription(String procedureDescription) {
        LOG.info("Extracting UI element description for procedure: '{}'", procedureDescription);
        try {
            var result = uiElementDescriptionExtractionAgent.executeAndGetResult(
                    () -> uiElementDescriptionExtractionAgent.extract(
                            userMessageContents(section(SECTION_LEVEL, "Procedure description", procedureDescription))));
            var payload = result.getResultPayload();
            return Optional.of(payload != null && !payload.elementDescription().isBlank()
                    ? payload.elementDescription() : procedureDescription);
        } catch (Exception e) {
            LOG.warn("Element description extraction failed for '{}'", procedureDescription, e);
            return Optional.empty();
        }
    }

    public List<UiElement> searchElements(String query) {
        return uiElementRefinementHelper.retrieveUiElementsWithMinimumSimilarity(uiElementRepository, query)
                .stream().map(UiElementRepository.UiElementMatch::element).toList();
    }

    public void deleteElement(UiElement element) {
        uiElementRepository.remove(element);
    }

    private static List<Content> resolutionUserMessage(DescriptionLabel descriptionLabel, String description, String relatedData) {
        return userMessageContents(joinSections(section(SECTION_LEVEL, descriptionLabel.label, description),
                section(SECTION_LEVEL, "Data related to the element", relatedData)));
    }

    private ResolutionOutcome runWithBudget(Supplier<UiElementLocationResult> resolution, String contextDescription,
                                            String unexpectedFailurePrefix) {
        int budgetMillis = uiTestAgentConfig.getElementResolutionMaxDurationMillis();
        long deadlineNanos = System.nanoTime() + MILLISECONDS.toNanos(budgetMillis);
        var future = new CompletableFuture<UiElementLocationResult>();
        var worker = Thread.ofVirtual().name("ui-element-resolution").start(() -> {
            try {
                future.complete(resolution.get());
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return processAgentResult(future.get(budgetMillis, MILLISECONDS), contextDescription);
        } catch (TimeoutException _) {
            abandon(worker, future);
            LOG.error("Element resolution exceeded its budget of {} ms for '{}'", budgetMillis, contextDescription);
            return new ResolutionOutcome.Failed("Element location timed out after %d ms.".formatted(budgetMillis));
        } catch (InterruptedException e) {
            abandon(worker, future);
            // The caller is interrupted when the operator's prompt closes; the worker must stop driving the agent before it returns
            awaitWorker(worker, Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime())), contextDescription);
            Thread.currentThread().interrupt();
            LOG.error("Element resolution was interrupted for '{}'", contextDescription, e);
            return new ResolutionOutcome.Failed("Element location was interrupted.");
        } catch (ExecutionException e) {
            var cause = e.getCause();
            LOG.error("Element resolution failed for '{}'", contextDescription, cause);
            return new ResolutionOutcome.Failed("Element location failed: %s".formatted(cause.getMessage()));
        } catch (RuntimeException e) {
            var message = "Element resolution result could not be processed for '%s'.".formatted(contextDescription);
            LOG.error(message, e);
            return new ResolutionOutcome.Failed(unexpectedFailurePrefix + e.getMessage());
        } finally {
            // The resolution agent may have shown a status through its spinner tools and never cleared it.
            operatorPrompts.clearStatus();
        }
    }

    // Best effort: the agent stops only at its next interruptible point, so the worker may outlive the caller.
    private static void abandon(Thread worker, CompletableFuture<?> future) {
        worker.interrupt();
        future.cancel(true);
    }

    private static void awaitWorker(Thread worker, Duration wait, String contextDescription) {
        try {
            if (!worker.join(wait)) {
                LOG.warn("Element resolution for '{}' still runs after its interrupt", contextDescription);
            }
        } catch (InterruptedException _) {
            LOG.warn("Stopped waiting for the interrupted element resolution for '{}'", contextDescription);
        }
    }

    private ResolutionOutcome processAgentResult(UiElementLocationResult payload, String contextDescription) {
        if (payload == null || !payload.success()) {
            String reason = payload != null && payload.message() != null ? payload.message() :
                    "The element could not be located on the screen or in the database.";
            LOG.warn("Element resolution agent returned failure for '{}': {}", contextDescription, reason);
            return new ResolutionOutcome.Failed(reason);
        }

        String elementIdStr = payload.elementId();
        if (elementIdStr == null || elementIdStr.isBlank()) {
            LOG.error("Element resolution agent reported success but returned empty elementId for '{}'", contextDescription);
            return new ResolutionOutcome.Failed("Agent reported success but failed to provide an element ID.");
        }

        UUID uuid = uiElementAliasRegistry.resolve(elementIdStr).orElse(null);
        if (uuid == null) {
            String msg = "Agent resolved unknown element ID '%s'.".formatted(elementIdStr);
            LOG.error("{} Context: '{}'", msg, contextDescription);
            return new ResolutionOutcome.Failed(msg);
        }
        var existingElement = uiElementRepository.findById(uuid).orElse(null);
        if (existingElement == null) {
            String msg = "Agent resolved element ID '%s' but no matching element was found in the database.".formatted(uuid);
            LOG.error("{} Context: '{}'", msg, contextDescription);
            return new ResolutionOutcome.Failed(msg);
        }

        if (existingElement.screenshot() != null) {
            LOG.debug("Element '{}' ({}) already has a screenshot, reusing it", existingElement.name(), uuid);
            return new ResolutionOutcome.Located(existingElement);
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
            return new ResolutionOutcome.Located(updatedElement);
        }
        return new ResolutionOutcome.Located(existingElement);
    }

    /** The located element, or the reason it could not be located, which the operator is shown. */
    public sealed interface ResolutionOutcome {
        record Located(UiElement element) implements ResolutionOutcome {}

        record Failed(String reason) implements ResolutionOutcome {}
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
