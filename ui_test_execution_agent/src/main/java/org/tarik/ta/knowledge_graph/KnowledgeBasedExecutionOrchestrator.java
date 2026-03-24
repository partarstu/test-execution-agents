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
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.dto.*;
import org.tarik.ta.exceptions.MissingProcedureException;
import org.tarik.ta.knowledge_graph.execution.ExecutionQueue;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.timing.TimingRecorder;
import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.user_dialogs.*;
import org.tarik.ta.user_dialogs.knowledge.ExecutionItemContext;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.time.Instant.now;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.knowledge_graph.StepExecutionOrchestrator.*;
import static org.tarik.ta.user_dialogs.PopupType.WARNING;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

@Singleton
public class KnowledgeBasedExecutionOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeBasedExecutionOrchestrator.class);

    private final KnowledgeService knowledgeService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final StepExecutionOrchestrator stepExecutionOrchestrator;
    private final ProcedureKnowledgeCollectionService procedureKnowledgeCollectionService;
    private final SatisfiesEdgeService satisfiesEdgeService;
    private final LocationHistoryRecorder locationHistoryRecorder;
    private final ElementLocationHistoryLookup elementLocationHistoryLookup;
    private final ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService;
    private final FailureContextService failureContextService;
    private final UiTestAgentConfig uiTestAgentConfig;

    public KnowledgeBasedExecutionOrchestrator(KnowledgeService knowledgeService,
                                               KnowledgeIngestionService knowledgeIngestionService,
                                               StepExecutionOrchestrator stepExecutionOrchestrator,
                                               ProcedureKnowledgeCollectionService procedureKnowledgeCollectionService,
                                               SatisfiesEdgeService satisfiesEdgeService,
                                               LocationHistoryRecorder locationHistoryRecorder,
                                               ElementLocationHistoryLookup elementLocationHistoryLookup,
                                               ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService,
                                               FailureContextService failureContextService,
                                               UiTestAgentConfig uiTestAgentConfig) {
        this.knowledgeService = knowledgeService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.stepExecutionOrchestrator = stepExecutionOrchestrator;
        this.procedureKnowledgeCollectionService = procedureKnowledgeCollectionService;
        this.satisfiesEdgeService = satisfiesEdgeService;
        this.locationHistoryRecorder = locationHistoryRecorder;
        this.elementLocationHistoryLookup = elementLocationHistoryLookup;
        this.procedureUsageByTestCaseTrackingService = procedureUsageByTestCaseTrackingService;
        this.failureContextService = failureContextService;
        this.uiTestAgentConfig = uiTestAgentConfig;
    }

    public void executeBasedOnKnowledge(UiTestExecutionContext context,
                                        TestCase testCase,
                                        int startingStepIndex,
                                        ExecutionStateTracker stateTracker) {
        var queue = ExecutionQueue.fromTestCase(testCase, startingStepIndex);
        LOG.info("Created preconditions and test steps executions queue with {} item(s)", queue.remainingCount());

        var usedProcedureIds = new ArrayList<UUID>();

        detectAndWarnOrderingConflicts(testCase);

        try {
            while (queue.hasNext()) {
                ExecutionItem item = queue.next();
                LOG.info("Processing execution item: {} (remaining in queue: {})", item.getClass().getSimpleName(),
                        queue.remainingCount());
                String itemDescription = item.getDescription();
                List<String> itemTestData = item.getTestData();
                String itemExpectedResults = item.getExpectedResults();

                Optional<KnowledgeService.MatchResult> matchResult =
                        knowledgeService.findBestMatch(itemDescription, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
                if (matchResult.isEmpty()) {
                    LOG.info("No matching procedure found for '{}'", itemDescription);
                    if (uiTestAgentConfig.isFullyUnattended()) {
                        LOG.error("No matching procedure found in UNATTENDED mode for: {}", itemDescription);
                        throw new MissingProcedureException(
                                ("No matching procedure found for '%s' and knowledge collection is not available in " +
                                        "UNATTENDED mode").formatted(itemDescription));
                    }
                    if (!handleNoProcedureMatchFoundCase(item, itemDescription, itemTestData, itemExpectedResults, queue, context, stateTracker.getExecutedAtomicProcedures(), stateTracker)) {
                        recordFailure(context, item, itemDescription,
                                "No matching procedure found and knowledge collection was cancelled or failed");
                        return;
                    }
                    continue;
                }

                var match = matchResult.get();
                var feasible = selectFeasibleProcedure(match.allMatches(), stateTracker);
                if (feasible.isEmpty()) {
                    var missingPrerequisites = match.allMatches().stream()
                            .flatMap(p -> stateTracker.findMissingPrerequisites(p.prerequisites()).stream())
                            .distinct().toList();
                    var reason = "Procedures found for '%s' but none have satisfied prerequisites. Missing: %s"
                            .formatted(itemDescription, missingPrerequisites);
                    LOG.warn(reason);
                    if (uiTestAgentConfig.isFullyUnattended()) {
                        recordFailure(context, item, itemDescription, reason);
                        return;
                    }
                    var resolved = handleLowConfidenceProcedureMatchCase(item, itemDescription, itemTestData, itemExpectedResults, match,
                            stateTracker, context);
                    if (resolved.isEmpty()) {
                        recordFailure(context, item, itemDescription, "User cancelled after no feasible procedure branch found");
                        return;
                    }
                    feasible = resolved;
                } else if (match.confidence() == KnowledgeService.MatchConfidence.LOW && !uiTestAgentConfig.isFullyUnattended()) {
                    LOG.info("Low confidence match for '{}' - prompting user for selection/editing", itemDescription);
                    var resolved = handleLowConfidenceProcedureMatchCase(item, itemDescription, itemTestData, itemExpectedResults, match,
                            stateTracker, context);
                    if (resolved.isEmpty()) {
                        recordFailure(context, item, itemDescription, "User cancelled low-confidence selection");
                        return;
                    }
                    feasible = resolved;
                }
                Procedure procedure = feasible.get();
                stateTracker.addRecentParent(procedure.id());

                LOG.info("Found matching procedure '{}' ({}) for '{}'", procedure.description(), procedure.id(),
                        itemDescription);
                List<Procedure> atomicSteps;
                if (procedure.isAtomic()) {
                    atomicSteps = List.of(procedure);
                    LOG.info("Procedure is already atomic, no decomposition is needed");
                } else {
                    atomicSteps = knowledgeService.resolveToAtomicSteps(procedure.id());
                    LOG.info("Decomposed into {} atomic step(s)", atomicSteps.size());
                }

                boolean hasTestStepData = itemTestData != null && !itemTestData.isEmpty() &&
                        itemTestData.stream().anyMatch(CommonUtils::isNotBlank);
                if (hasTestStepData && !uiTestAgentConfig.isFullyUnattended() && item instanceof TestStepItem) {
                    List<String> affectedProcedures = atomicSteps.stream()
                            .filter(s -> s.testData() != null && !s.testData().isEmpty() &&
                                    s.testData().stream().anyMatch(CommonUtils::isNotBlank))
                            .map(Procedure::description)
                            .toList();

                    if (!affectedProcedures.isEmpty()) {
                        String procList = String.join("\n- ", affectedProcedures);
                        String message = "This Test Step contains test data. The saved data for the following atomic procedures will " +
                                "be discarded:\n" + procList;
                        InformationalPopup.display("Data Override Warning", message, null, WARNING);
                    }
                }

                List<UiTestStepResult> testStepResults = new ArrayList<>();
                List<UiPreconditionResult> preconditionResults = new ArrayList<>();
                boolean allAtomicsSuccess = true;
                boolean reDecomposeNeeded = false;
                int totalAtomics = atomicSteps.size();
                for (int i = 0; i < totalAtomics; i++) {
                    Procedure atomicStep = atomicSteps.get(i);
                    if (!atomicStep.prerequisites().isEmpty() && !stateTracker.arePrerequisitesMet(atomicStep.prerequisites())) {
                        var missing = stateTracker.findMissingPrerequisites(atomicStep.prerequisites());
                        var reason = "Atomic step '%s' skipped — prerequisites not satisfied: %s"
                                .formatted(atomicStep.description(), missing);
                        LOG.warn(reason);
                        if (uiTestAgentConfig.isFullyUnattended()) {
                            recordFailure(context, item, itemDescription, reason);
                            allAtomicsSuccess = false;
                            break;
                        } else {
                            InformationalPopup.display("Prerequisites Not Satisfied", reason, null, WARNING);
                            allAtomicsSuccess = false;
                            break;
                        }
                    }

                    String targetElementId = knowledgeService.findTargetedUiElementId(atomicStep.id())
                            .map(UUID::toString)
                            .orElse(null);

                    if (targetElementId == null) {
                        LOG.debug("No target UI element linked to procedure '{}' — proceeding without element hint",
                                atomicStep.description());
                    }

                    boolean isSingle = totalAtomics == 1;
                    boolean isLast = (i == totalAtomics - 1);
                    String effectiveExpectedResults = computeEffectiveExpectedResults(item, atomicStep, isSingle, isLast);

                    TimingRecorder timingRecorder = knowledgeService::updateTimingProfile;
                    var failureHints = failureContextService.findFailureHints(atomicStep.id());
                    var execContext = new AtomicStepExecutionContext(
                            atomicStep.timingProfile(),
                            timingRecorder,
                            failureHints,
                            targetElementId,
                            effectiveExpectedResults
                    );

                    var loopOutcome = stepExecutionOrchestrator.executeAtomicStepWithRetryLoop(item, atomicStep,
                            procedure.isAtomic() ? null : procedure, context,
                            testStepResults, preconditionResults,
                            stateTracker.getExecutedAtomicProcedures(), execContext);

                    if (loopOutcome == StepExecutionOrchestrator.RetryLoopOutcome.TERMINATE_EXECUTION) {
                        LOG.error("Terminating execution after failure of atomic procedure '{}'", atomicStep.description());
                        captureFailureContext(atomicStep, testStepResults, preconditionResults);
                        allAtomicsSuccess = false;
                        break;
                    }
                    if (loopOutcome == StepExecutionOrchestrator.RetryLoopOutcome.RE_DECOMPOSE_AND_RETRY) {
                        LOG.info("Atomic step '{}' was edited to composite — re-injecting item for re-decomposition", atomicStep.description());
                        queue.injectAtFront(List.of(item));
                        reDecomposeNeeded = true;
                        break;
                    }

                    stateTracker.addEffects(knowledgeService.findEffectsForProcedure(atomicStep.id()));
                    satisfiesEdgeService.persistSatisfiesEdgesAsync(atomicStep.id());
                    stateTracker.addExecutedAtomicProcedure(atomicStep);
                    LOG.debug("Added {} effect(s) to state tracker", atomicStep.effects().size());
                }

                if (reDecomposeNeeded) {
                    continue;
                }
                if (item instanceof TestStepItem(TestStep testStep)) {
                    UiTestStepResult mergedResult = mergeAtomicResults(testStep, testStepResults);
                    context.addStepResult(mergedResult);
                    if (mergedResult.getExecutionStatus() != SUCCESS) {
                        return;
                    }
                } else {
                    UiPreconditionResult mergedResult = mergePreconditionResults(itemDescription, preconditionResults, allAtomicsSuccess);
                    context.addPreconditionResult(mergedResult);
                    if (!mergedResult.isSuccess()) {
                        return;
                    }
                }
                usedProcedureIds.add(procedure.id());
                procedureUsageByTestCaseTrackingService.mergeUsesProcedure(testCase.name(), procedure.id());
            }
        } finally {
            try {
                procedureUsageByTestCaseTrackingService.cleanupStaleUsesProcedure(testCase.name(), usedProcedureIds);
            } catch (Exception e) {
                LOG.error("Failed to clean up stale USES_PROCEDURE edges for test case '{}'", testCase.name(), e);
            }
        }
    }

    private void detectAndWarnOrderingConflicts(TestCase testCase) {
        if (uiTestAgentConfig.isFullyUnattended()) {
            LOG.debug("Ordering conflict detection skipped: running in UNATTENDED mode");
            return;
        }
        if (!satisfiesEdgeService.hasSatisfiesEdges()) {
            LOG.debug("Ordering conflict detection skipped: no SATISFIES edges in graph yet");
            return;
        }
        var steps = testCase.testSteps();
        var descriptions = steps.stream().map(TestStep::stepDescription).toList();
        // Batch-embed all descriptions in one pass instead of embedding each individually
        var embeddings = knowledgeService.embedBatch(descriptions);

        List<UUID> orderedIds = new ArrayList<>();
        Map<UUID, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            var match = knowledgeService.findTopSemanticMatch(embeddings.get(i));
            if (match.isPresent()) {
                UUID procId = match.get().id();
                if (!indexMap.containsKey(procId)) {
                    orderedIds.add(procId);
                    indexMap.put(procId, i);
                }
            }
        }
        if (orderedIds.isEmpty()) {
            LOG.debug("Ordering conflict detection: no semantic matches found for any step, skipping");
            return;
        }
        var conflicts = satisfiesEdgeService.findOrderingConflicts(orderedIds, indexMap);
        if (conflicts.isEmpty()) {
            LOG.info("Ordering conflict detection: no conflicts found for test case '{}'", testCase.name());
        } else {
            String details = String.join("\n", conflicts.stream().map(c -> {
                int producerIndex = indexMap.get(c.producerId());
                int consumerIndex = indexMap.get(c.consumerId());
                return "- Step '%s' requires an effect produced by step '%s', but is scheduled to run before it."
                        .formatted(steps.get(consumerIndex).stepDescription(), steps.get(producerIndex).stepDescription());
            }).toList());
            InformationalPopup.display("Ordering Conflicts Detected",
                    "The following test steps appear to be out of order:\n\n" + details, null, WARNING);
        }
    }

    /**
     * Computes the expected results string to be used for verification of an atomic step.
     * <p>
     * For a test step with a single atomic procedure, the test step's expected results replace the
     * procedure's own expected results. For multiple atomics, non-last steps use only the procedure's
     * own expected results; the last step combines the procedure's results with the test step's.
     * For preconditions with a single atomic, the procedure's relevant data (expectedResults or testData)
     * is returned so the verification agent can use it as context; for multiple atomics nothing changes.
     */
    private static String computeEffectiveExpectedResults(ExecutionItem item, Procedure atomicStep, boolean isSingle, boolean isLast) {
        return switch (item) {
            case TestStepItem(TestStep testStep) -> {
                String testStepExpected = testStep.expectedResults() != null ? testStep.expectedResults() : "";
                String atomicExpected = atomicStep.expectedResults() != null ? atomicStep.expectedResults() : "";
                if (isSingle) {
                    yield testStepExpected;
                } else if (isLast) {
                    yield atomicExpected.isBlank() ? testStepExpected
                            : testStepExpected.isBlank() ? atomicExpected
                            : atomicExpected + "\n" + testStepExpected;
                } else {
                    yield atomicExpected;
                }
            }
            case PreconditionItem ignored -> {
                if (isSingle) {
                    String atomicExpected = atomicStep.expectedResults() != null ? atomicStep.expectedResults() : "";
                    String testDataString = atomicStep.testData() != null ? String.join(", ", atomicStep.testData()) : "";
                    yield atomicExpected.isBlank() ? testDataString : atomicExpected;
                } else {
                    yield "";
                }
            }
        };
    }

    private static void recordFailure(UiTestExecutionContext context, ExecutionItem item, String itemDescription, String errorMessage) {
        switch (item) {
            case PreconditionItem ignored ->
                    context.addPreconditionResult(new UiPreconditionResult(itemDescription, false, errorMessage, captureScreen(), now(), now()));
            case TestStepItem(TestStep testStep) ->
                    context.addStepResult(new UiTestStepResult(testStep, FAILURE, errorMessage, null, captureScreen(), now(), now()));
        }
    }

    /**
     * Returns the highest-scoring candidate whose prerequisites are all satisfied by the current state.
     * Procedures with no prerequisites are always considered feasible.
     */
    private static Optional<Procedure> selectFeasibleProcedure(List<Procedure> candidates, ExecutionStateTracker stateTracker) {
        return candidates.stream()
                .filter(p -> stateTracker.arePrerequisitesMet(p.prerequisites()))
                .findFirst();
    }

    private Optional<Procedure> handleLowConfidenceProcedureMatchCase(ExecutionItem item, String itemDescription,
                                                                      List<String> itemTestData, String itemExpectedResults,
                                                                      KnowledgeService.MatchResult match,
                                                                      ExecutionStateTracker stateTracker,
                                                                      UiTestExecutionContext executionContext) {
        while (true) {
            var selectionResult = org.tarik.ta.user_dialogs.knowledge.ProcedureLowConfidenceSelectionPopup
                    .displayAndGetSelection(null, itemDescription, match.allMatches());
            if (selectionResult.isEmpty()) {
                LOG.warn("User cancelled selection for '{}', stopping execution", itemDescription);
                throw new IllegalStateException("User cancelled knowledge workflow");
            }
            var res = selectionResult.get();
            boolean isPreconditionItem = item instanceof PreconditionItem;
            switch (res.action()) {
                case RETRY -> {
                    knowledgeService.onKnowledgeIngested();
                    var refreshed = refreshBestMatch(itemDescription, stateTracker);
                    if (refreshed.isPresent()) match = refreshed.get();
                }
                case EDIT -> {
                    var existing = knowledgeService.findById(res.existingId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Selected procedure with ID '%s' not found".formatted(res.existingId())));
                    var testCasesUsingIt = procedureUsageByTestCaseTrackingService.findTestCasesUsingProcedure(existing.id());
                    if (!testCasesUsingIt.isEmpty()) {
                        String tcList = String.join("\n- ", testCasesUsingIt);
                        String msg = "This procedure is used by %d test case(s):\n- %s\n\nEditing it may affect all of them."
                                .formatted(testCasesUsingIt.size(), tcList);
                        InformationalPopup.display("Shared Procedure Warning", msg, null, WARNING);
                    }
                    var itemContext = new ExecutionItemContext(itemDescription, itemTestData, isPreconditionItem);
                    var editResult = procedureKnowledgeCollectionService.triggerEditProcedureFlow(existing, itemTestData,
                            itemExpectedResults, !isPreconditionItem, itemContext, executionContext,
                            stateTracker.getExecutedAtomicProcedures());
                    if (editResult.isSaved()) {
                        knowledgeIngestionService.update(editResult.savedProcedureId().get(), editResult.updatedNode().get());
                        knowledgeService.onKnowledgeIngested();
                        LOG.info("Procedure edited, re-fetching matches for '{}'", itemDescription);
                        var refreshed = refreshBestMatch(itemDescription, stateTracker);
                        if (refreshed.isEmpty()) LOG.warn("No matches found after edit for '{}'", itemDescription);
                        else match = refreshed.get();
                    } else {
                        LOG.info("User cancelled the procedure edit flow for '{}'", itemDescription);
                    }
                }
                case CREATE -> {
                    var newProcedureResult = procedureKnowledgeCollectionService.triggerNewProcedureFlow(itemDescription,
                            itemTestData, itemExpectedResults, isPreconditionItem,
                            executionContext, stateTracker.getExecutedAtomicProcedures());
                    newProcedureResult.ifPresent(r -> {
                        knowledgeIngestionService.ingest(r);
                        knowledgeService.onKnowledgeIngested();
                    });
                    var newMatchOpt = knowledgeService.findBestMatch(itemDescription, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
                    if (newMatchOpt.isPresent()) {
                        return Optional.of(newMatchOpt.get().procedure());
                    }
                }
                case null, default -> {
                }
            }
            // After RETRY or EDIT, return the first feasible high-confidence match if one exists now
            if (match.confidence() != KnowledgeService.MatchConfidence.LOW) {
                var feasible = selectFeasibleProcedure(match.allMatches(), stateTracker);
                if (feasible.isPresent()) {
                    return feasible;
                }
            }
        }
    }

    /**
     * Finds the best match for the given description and shows a dialog if none is found.
     * Returns the updated match result, or empty if no match exists.
     */
    private Optional<KnowledgeService.MatchResult> refreshBestMatch(String itemDescription, ExecutionStateTracker stateTracker) {
        var result = knowledgeService.findBestMatch(itemDescription, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
        if (result.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No matches found. Please create a new procedure.");
        }
        return result;
    }

    private boolean handleNoProcedureMatchFoundCase(ExecutionItem item, String itemDescription,
                                                    List<String> itemTestData, String itemExpectedResults,
                                                    ExecutionQueue queue,
                                                    UiTestExecutionContext executionContext,
                                                    List<Procedure> executedAtomics,
                                                    ExecutionStateTracker stateTracker) {
        boolean isPreconditionItem = item instanceof PreconditionItem;
        var knowledgeCollectionResult = procedureKnowledgeCollectionService.triggerNewProcedureFlow(itemDescription,
                itemTestData, itemExpectedResults, isPreconditionItem, executionContext, executedAtomics);
        if (knowledgeCollectionResult.isEmpty()) {
            LOG.warn("User cancelled collecting knowledge for a new procedure for '{}', stopping execution", itemDescription);
            return false;
        }
        LOG.info("User completed collecting knowledge for a new procedure for '{}', ingesting into knowledge DB", itemDescription);
        knowledgeIngestionService.ingest(knowledgeCollectionResult.get());
        knowledgeService.onKnowledgeIngested();
        var newMatch = knowledgeService.findBestMatch(itemDescription, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
        if (newMatch.isPresent()) {
            LOG.info("Successfully matched newly ingested procedure for '{}'", itemDescription);
            queue.injectAtFront(List.of(item));
            return true;
        } else {
            LOG.warn("Newly created procedure has a description which doesn't match '{}' - stopping execution", itemDescription);
            return false;
        }
    }

    private void captureFailureContext(Procedure atomicStep, List<UiTestStepResult> testStepResults,
                                       List<UiPreconditionResult> preconditionResults) {
        var preSelectedCategory = ErrorCategory.UNKNOWN;
        String defaultSymptom = "Unknown failure in procedure " + atomicStep.description();
        String preFilledSymptom = extractLastErrorMessage(testStepResults, preconditionResults, defaultSymptom);

        FailureContext failureContext = uiTestAgentConfig.isFullyUnattended()
                ? new FailureContext(UUID.randomUUID(), preFilledSymptom, preSelectedCategory, "", 1, now(), FailureContext.Mode.UNATTENDED)
                : FailureContextCaptureDialog.displayAndGetSelection(preSelectedCategory, preFilledSymptom);

        if (failureContext != null) {
            failureContextService.captureFailureContext(atomicStep.id(), failureContext);
        }
    }

    private static String extractLastErrorMessage(List<UiTestStepResult> testStepResults,
                                                  List<UiPreconditionResult> preconditionResults,
                                                  String defaultMessage) {
        if (!testStepResults.isEmpty()) {
            var lastStep = testStepResults.getLast();
            if (lastStep.getExecutionStatus() != SUCCESS) {
                String error = lastStep.getErrorMessage();
                return (error != null && !error.isBlank()) ? error : defaultMessage;
            }
        } else if (!preconditionResults.isEmpty()) {
            var lastPre = preconditionResults.getLast();
            if (!lastPre.isSuccess()) {
                String error = lastPre.getErrorMessage();
                return (error != null && !error.isBlank()) ? error : defaultMessage;
            }
        }
        return defaultMessage;
    }
}
