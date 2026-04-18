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
package org.tarik.ta.knowledge_graph;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.KnowledgeSuggestionAgent;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.ExecutionGraphContextBuilder;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.user_dialogs.knowledge.ExecutionItemContext;
import org.tarik.ta.user_dialogs.knowledge.ProcedureDialog;
import org.tarik.ta.user_dialogs.knowledge.SuggestionLoaderFactory;
import org.tarik.ta.user_dialogs.knowledge.UiElementDialogHelper;
import org.tarik.ta.user_dialogs.knowledge.UserChoiceDialog;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.utils.ImageUtils;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

/**
 * Service for triggering the Human-in-the-Loop knowledge collection flows.
 * Extracted from KnowledgeBasedExecutionOrchestrator to break the circular dependency
 * between StepExecutionOrchestrator and KnowledgeBasedExecutionOrchestrator.
 */
@Singleton
public class ProcedureKnowledgeCollectionService {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureKnowledgeCollectionService.class);

    private final KnowledgeSuggestionAgent knowledgeSuggestionAgent;
    private final KnowledgeService knowledgeService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final UiElementRepository uiElementRepository;
    private final UiElementDialogHelper uiElementDialogHelper;
    private final UiTestAgentConfig uiTestAgentConfig;
    private final ProcedureUsageByTestCaseTrackingService usageTrackingService;

    public ProcedureKnowledgeCollectionService(KnowledgeSuggestionAgent knowledgeSuggestionAgent,
                                        KnowledgeService knowledgeService,
                                        KnowledgeIngestionService knowledgeIngestionService,
                                        UiElementRepository uiElementRepository,
                                        UiElementDialogHelper uiElementDialogHelper,
                                        UiTestAgentConfig uiTestAgentConfig,
                                        ProcedureUsageByTestCaseTrackingService usageTrackingService) {
        this.knowledgeSuggestionAgent = knowledgeSuggestionAgent;
        this.knowledgeService = knowledgeService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.uiElementRepository = uiElementRepository;
        this.uiElementDialogHelper = uiElementDialogHelper;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.usageTrackingService = usageTrackingService;
    }

    /**
     * Triggers the Human-in-the-Loop flow for creating a new procedure.
     * Pre-loads AI suggestions using the projected execution graph, then opens the dialog.
     * Exceptions propagate to the caller; ingestion is the caller's responsibility.
     *
     * @param itemDescription  the description of the unmatched item
     * @param testData         test data for the item
     * @param expectedResults  expected results for the item
     * @param isPrecondition   whether the item is a precondition (affects which UI sections are shown)
     * @param executionContext the current test execution context
     * @param executedAtomics  atomic procedures already executed during this test run, in order
     * @return the collected procedure if user completed, empty if cancelled
     */
    public Optional<IngestionNode> triggerNewProcedureFlow(String itemDescription,
                                                           List<String> testData,
                                                           String expectedResults,
                                                           boolean isPrecondition,
                                                           TestCase testCase,
                                                           UiTestExecutionContext executionContext,
                                                           List<Procedure> executedAtomics) {
        LOG.info("Triggering new procedure knowledge collection flow for: '{}'", itemDescription);
        // Factory builds the projected execution graph context for any new procedure (root or child step)
        SuggestionLoaderFactory childLoaderFactory = (precedingAtomicsSupplier) -> (desc) ->
                loadSuggestionsWithSpinner(desc, testData, expectedResults,
                        ExecutionGraphContextBuilder.buildExecutionGraphContext(testCase, executionContext, executedAtomics,
                                precedingAtomicsSupplier.get()));
        // Pre-load suggestions for the root level (no preceding siblings)
        var aiSuggestions = loadSuggestionsWithSpinner(itemDescription, testData, expectedResults,
                ExecutionGraphContextBuilder.buildExecutionGraphContext(testCase, executionContext, executedAtomics, List.of()));
        var itemContext = new ExecutionItemContext(itemDescription, testData, isPrecondition);
        return ProcedureDialog.displayAndGetResult(null, itemDescription, aiSuggestions,
                !isPrecondition, itemContext, knowledgeService, knowledgeIngestionService, childLoaderFactory,
                uiTestAgentConfig, uiElementRepository, uiElementDialogHelper);
    }

    /**
     * Triggers the Human-in-the-Loop flow for editing an existing procedure.
     * The existing procedure itself does not receive AI suggestions; only new child steps added during
     * editing do. Returns the updated node to the caller for ingestion — does not ingest internally.
     * Exceptions propagate to the caller.
     *
     * @param startingProcedure the procedure to start editing from
     * @param testData test data for the item
     * @param expectedResults expected results for the item
     * @param showTestDataAndExpectedResults whether to show test data and expected results sections
     * @param itemContext the execution item context
     * @param executionContext the current test execution context
     * @param executedAtomics atomic procedures already executed during this test run, in order
     * @return saved result carrying the updated node (caller must ingest), or cancelled
     */
    public ProcedureEditResult triggerEditProcedureFlow(Procedure startingProcedure, List<String> testData,
                                                        String expectedResults,
                                                        boolean showTestDataAndExpectedResults,
                                                        ExecutionItemContext itemContext,
                                                        TestCase testCase,
                                                        UiTestExecutionContext executionContext,
                                                        List<Procedure> executedAtomics) {
        // Factory is the same regardless of which procedure is being edited — built once before the loop
        SuggestionLoaderFactory childLoaderFactory = (precedingAtomicsSupplier) -> (desc) ->
                loadSuggestionsWithSpinner(desc, testData, expectedResults,
                        ExecutionGraphContextBuilder.buildExecutionGraphContext(testCase, executionContext, executedAtomics,
                                precedingAtomicsSupplier.get()));
        Procedure current = startingProcedure;
        while (true) {
            var parents = knowledgeService.findParents(current.id());
            boolean hasParent = !parents.isEmpty();
            var children = knowledgeService.getChildren(current.id());
            UUID targetElementId = null;
            if (current.isAtomic()) {
                targetElementId = knowledgeService.findTargetedUiElementId(current.id()).orElse(null);
            }
            var preloadedChildren = children.isEmpty() ? null : children;
            var outcome = ProcedureDialog.displayForEditing(null, current, targetElementId,
                    showTestDataAndExpectedResults, hasParent, itemContext, knowledgeService, knowledgeIngestionService,
                    childLoaderFactory, preloadedChildren,
                    uiTestAgentConfig, uiElementRepository, uiElementDialogHelper, usageTrackingService);
            if (outcome.deleted()) {
                LOG.info("Procedure '{}' deleted by user", current.description());
                return ProcedureEditResult.cancelled();
            } else if (outcome.result() instanceof IngestionNode.NewProcedure np) {
                LOG.info("Procedure '{}' edited by user", current.description());
                return ProcedureEditResult.saved(current.id(), np);
            } else if (outcome.editParentRequested() && hasParent) {
                if (parents.size() > 1) {
                    var selection = UserChoiceDialog.displayAndGetSelection(null,
                            "Multiple parent procedures found. Select the parent to edit:",
                            current.description(), List.of(), knowledgeService, Set.of(), Set.of(), uiTestAgentConfig);
                    if (selection.isPresent() && selection.get().action() == UserChoiceDialog.SelectionAction.BROWSE) {
                        current = knowledgeService.findById(selection.get().existingId())
                                .orElseThrow(() -> new IllegalStateException("Selected parent with ID '%s' not found"
                                        .formatted(selection.get().existingId())));
                    } else {
                        return ProcedureEditResult.cancelled();
                    }
                } else {
                    current = parents.getFirst();
                }
            } else {
                return ProcedureEditResult.cancelled();
            }
        }
    }

    private KnowledgeSuggestionResult loadSuggestionsWithSpinner(String itemDescription, List<String> testData,
                                                                    String expectedResults, String agentContext) {
        var suggestionsRef = new AtomicReference<>(KnowledgeSuggestionResult.empty());
        // Capture screen before showing the spinner so no dialog/spinner overlays appear in the screenshot
        var screenshot = ImageUtils.singleImageContent(captureScreen());
        UiElementDialogHelper.showSpinnerUntilDone(() -> {
            try {
                var result = knowledgeSuggestionAgent.executeAndGetResult(
                        () -> knowledgeSuggestionAgent.suggest(itemDescription, agentContext, testData.toString(), expectedResults, screenshot));
                var payload = result.getResultPayload();
                if (payload != null) {
                    suggestionsRef.set(payload);
                } else {
                    LOG.warn("Knowledge Suggestion Agent returned no result for '{}'", itemDescription);
                }
            } catch (Exception e) {
                LOG.warn("Suggestion loading failed for '{}': {}", itemDescription, e.getMessage(), e);
            }
        }, itemDescription);
        return suggestionsRef.get();
    }

    public record ProcedureEditResult(boolean isSaved, Optional<UUID> savedProcedureId,
                                      Optional<IngestionNode.NewProcedure> updatedNode) {
        public static ProcedureEditResult cancelled() {
            return new ProcedureEditResult(false, Optional.empty(), Optional.empty());
        }

        public static ProcedureEditResult saved(UUID id, IngestionNode.NewProcedure node) {
            return new ProcedureEditResult(true, Optional.of(id), Optional.of(node));
        }
    }

}
