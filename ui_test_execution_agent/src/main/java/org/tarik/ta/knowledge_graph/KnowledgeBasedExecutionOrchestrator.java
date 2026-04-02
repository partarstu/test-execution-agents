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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.dto.*;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.exceptions.MissingProcedureException;
import org.tarik.ta.knowledge_graph.execution.ExecutionQueue;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.timing.TimingRecorder;
import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.user_dialogs.*;
import org.tarik.ta.user_dialogs.knowledge.ExecutionItemContext;
import org.tarik.ta.user_dialogs.knowledge.ProcedureSelectionPopup;

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
    private final ProcedureKnowledgeCollectionService procedureKnowledgeService;
    private final SatisfiesEdgeService satisfiesEdgeService;
    private final ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService;
    private final FailureContextService failureContextService;
    private final UiTestAgentConfig uiTestAgentConfig;
    private final UiElementCache uiElementCache;

    public KnowledgeBasedExecutionOrchestrator(KnowledgeService knowledgeService,
                                               KnowledgeIngestionService knowledgeIngestionService,
                                               StepExecutionOrchestrator stepExecutionOrchestrator,
                                               ProcedureKnowledgeCollectionService procedureKnowledgeService,
                                               SatisfiesEdgeService satisfiesEdgeService,
                                               ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService,
                                               FailureContextService failureContextService,
                                               UiTestAgentConfig uiTestAgentConfig,
                                               UiElementCache uiElementCache) {
        this.knowledgeService = knowledgeService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.stepExecutionOrchestrator = stepExecutionOrchestrator;
        this.procedureKnowledgeService = procedureKnowledgeService;
        this.satisfiesEdgeService = satisfiesEdgeService;
        this.procedureUsageByTestCaseTrackingService = procedureUsageByTestCaseTrackingService;
        this.failureContextService = failureContextService;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.uiElementCache = uiElementCache;
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
                var nextStep = switch (findProcedureInDb(item, stateTracker)) {
                    case ProcedureLookup.DirectMatch(var procedure) -> {
                        LOG.info("Direct high-confidence match found for '{}'", item.getDescription());
                        yield processFoundProcedure(procedure, item, testCase, context, stateTracker, usedProcedureIds, queue);
                    }
                    case ProcedureLookup.LowConfidenceMatch(var match, var _) -> {
                        if (uiTestAgentConfig.isFullyUnattended()) {
                            throw new MissingProcedureException("Only low-confidence procedure search results found for '%s': %s"
                                    .formatted(item.getDescription(), match));
                        } else {
                            LOG.info("Low confidence match for '{}' — prompting user for selection/editing", item.getDescription());
                            var userDecision = promptUserToHandleLowConfidenceMatch(item, match, stateTracker, testCase, context);
                            yield processUserFeedback(userDecision, item, testCase, context, stateTracker, usedProcedureIds, queue);
                        }
                    }
                    case ProcedureLookup.NoProcedureWithFulfilledPrerequisites(var match, var missingPrereqs) -> {
                        var reason = "Procedures found for '%s' but none have satisfied prerequisites. Missing: %s"
                                .formatted(item.getDescription(), missingPrereqs);
                        if (uiTestAgentConfig.isFullyUnattended()) {
                            throw new MissingProcedureException(reason);
                        } else {
                            LOG.warn(reason);
                            var userDecision = promptUserToHandleMissingPrerequisites(item, match, reason, stateTracker, testCase, context);
                            yield processUserFeedback(userDecision, item, testCase, context, stateTracker, usedProcedureIds, queue);
                        }
                    }
                    case ProcedureLookup.NoMatchFound() -> {
                        if (uiTestAgentConfig.isFullyUnattended()) {
                            throw new MissingProcedureException(("No matching procedure found for '%s' and knowledge collection is not " +
                                    "available in UNATTENDED mode").formatted(item.getDescription()));
                        } else {
                            LOG.info("No matching procedure found for '{}' — prompting user to create one", item.getDescription());
                            var userDecision = promptUserToCreateNewProcedure(item, stateTracker, testCase, context);
                            yield processUserFeedback(userDecision, item, testCase, context, stateTracker, usedProcedureIds, queue);
                        }
                    }
                };
                if (nextStep instanceof ExecutionFlow.Stop) {
                    return;
                }
            }
        } catch (DatabaseConnectionException e) {
            LOG.error("DB connection error during execution of test case '{}'", testCase.name(), e);
            if (!uiTestAgentConfig.isFullyUnattended()) {
                InformationalPopup.display("Database Connection Error",
                        "Lost connection to the knowledge graph DB: " + e.getMessage(), null, PopupType.ERROR, uiTestAgentConfig);
            }
            throw e;
        } finally {
            try {
                procedureUsageByTestCaseTrackingService.cleanupStaleUsesProcedure(testCase.name(), usedProcedureIds);
            } catch (Exception e) {
                LOG.error("Failed to clean up stale USES_PROCEDURE edges for test case '{}'", testCase.name(), e);
            }
        }
    }

    private ExecutionFlow processFoundProcedure(Procedure procedure, ExecutionItem item, TestCase testCase,
                                                UiTestExecutionContext context, ExecutionStateTracker stateTracker,
                                                List<UUID> usedProcedureIds, ExecutionQueue queue) {
        stateTracker.addRecentParent(procedure.id());
        LOG.info("Found matching procedure '{}' ({}) for '{}'", procedure.description(), procedure.id(), item.getDescription());
        var atomicSteps = resolveToAtomicSteps(procedure);
        showTestDataOverrideWarningIfNeeded(item, item.getTestData(), atomicSteps);
        var testStepResults = new ArrayList<UiTestStepResult>();
        var preconditionResults = new ArrayList<UiPreconditionResult>();
        var loopOutcome =
                executeAtomicStepsLoop(item, procedure, atomicSteps, testCase, context, stateTracker, testStepResults, preconditionResults);
        if (loopOutcome == AtomicLoopOutcome.TERMINATE) {
            return new ExecutionFlow.Stop();
        }
        if (loopOutcome == AtomicLoopOutcome.RE_DECOMPOSE) {
            queue.injectAtFront(List.of(item));
            return new ExecutionFlow.Continue();
        }
        if (item instanceof TestStepItem(TestStep testStep)) {
            UiTestStepResult mergedResult = mergeAtomicResults(testStep, testStepResults);
            context.addStepResult(mergedResult);
            if (mergedResult.getExecutionStatus() != SUCCESS) {
                return new ExecutionFlow.Stop();
            }
        } else {
            UiPreconditionResult mergedResult = mergePreconditionResults(item.getDescription(), preconditionResults);
            context.addPreconditionResult(mergedResult);
            if (!mergedResult.isSuccess()) {
                return new ExecutionFlow.Stop();
            }
        }
        usedProcedureIds.add(procedure.id());
        procedureUsageByTestCaseTrackingService.mergeUsesProcedure(testCase.name(), procedure.id());
        return new ExecutionFlow.Continue();
    }

    private ExecutionFlow processUserFeedback(UserFeedback resolution, ExecutionItem item, TestCase testCase,
                                              UiTestExecutionContext context, ExecutionStateTracker stateTracker,
                                              List<UUID> usedProcedureIds, ExecutionQueue queue) {
        return switch (resolution) {
            case UserFeedback.ManualTermination(var reason) -> {
                LOG.warn("Execution was interrupted by user for item '{}': {}", item.getDescription(), reason);
                recordFailure(context, item, item.getDescription(), reason);
                yield new ExecutionFlow.Stop();
            }
            case UserFeedback.NewProcedureCreated() -> {
                LOG.info("New procedure created for '{}', re-queuing for re-processing", item.getDescription());
                queue.injectAtFront(List.of(item));
                yield new ExecutionFlow.Rerun();
            }
            case UserFeedback.Found(var procedure) ->
                    processFoundProcedure(procedure, item, testCase, context, stateTracker, usedProcedureIds, queue);
            case UserFeedback.AutomaticTermination(var reason) -> {
                LOG.warn("Execution needs to be terminated for item '{}': {}", item.getDescription(), reason);
                recordFailure(context, item, item.getDescription(), reason);
                yield new ExecutionFlow.Stop();
            }
        };
    }

    /**
     * Performs a pure knowledge lookup for the given item — no dialogs, no queue operations.
     * Returns a typed result describing what was found so the caller can decide how to proceed.
     */
    private ProcedureLookup findProcedureInDb(ExecutionItem item, ExecutionStateTracker stateTracker) {
        var matchResult = knowledgeService.findBestMatch(item.getDescription(), stateTracker.getEffectNodeIds(),
                stateTracker.getRecentParentIds());
        if (matchResult.isEmpty()) {
            return new ProcedureLookup.NoMatchFound();
        }
        var match = matchResult.get();
        var feasible = selectFeasibleProcedure(match.allMatches(), stateTracker);
        if (feasible.isEmpty()) {
            var missing = match.allMatches().stream()
                    .flatMap(p -> stateTracker.findMissingPrerequisites(p.prerequisites()).stream())
                    .distinct()
                    .toList();
            return new ProcedureLookup.NoProcedureWithFulfilledPrerequisites(match, missing);
        }
        if (match.confidence() == KnowledgeService.MatchConfidence.LOW) {
            return new ProcedureLookup.LowConfidenceMatch(match, feasible.get());
        }
        return new ProcedureLookup.DirectMatch(feasible.get());
    }

    /**
     * Handles the case where no procedure was found at all — triggers the new-procedure creation flow.
     */
    private UserFeedback promptUserToCreateNewProcedure(ExecutionItem item, ExecutionStateTracker stateTracker,
                                                        TestCase testCase, UiTestExecutionContext context) {
        String itemDescription = item.getDescription();
        boolean isPreconditionItem = item instanceof PreconditionItem;
        var result = procedureKnowledgeService.triggerNewProcedureFlow(itemDescription, item.getTestData(),
                item.getExpectedResults(), isPreconditionItem, testCase, context, stateTracker.getExecutedAtomicProcedures());
        if (result.isEmpty()) {
            LOG.warn("User cancelled collecting knowledge for a new procedure for '{}', stopping execution", itemDescription);
            return new UserFeedback.ManualTermination("No matching procedure found and knowledge collection was cancelled");
        }
        LOG.info("User completed collecting knowledge for a new procedure for '{}', ingesting into knowledge DB", itemDescription);
        knowledgeIngestionService.ingest(result.get());
        knowledgeService.onKnowledgeIngested();
        var newMatch = knowledgeService.findBestMatch(itemDescription, stateTracker.getEffectNodeIds(),
                stateTracker.getRecentParentIds());
        if (newMatch.isPresent()) {
            LOG.info("Successfully matched newly ingested procedure for '{}'", itemDescription);
            return new UserFeedback.NewProcedureCreated();
        }
        {
            return new UserFeedback.AutomaticTermination(
                    "Newly created procedure has a description which doesn't match '%s'".formatted(itemDescription));
        }
    }

    /**
     * Handles a low-confidence match by prompting the user to select, edit, or create a procedure.
     */
    private UserFeedback promptUserToHandleLowConfidenceMatch(ExecutionItem item, KnowledgeService.MatchResult match,
                                                              ExecutionStateTracker stateTracker, TestCase testCase,
                                                              UiTestExecutionContext context) {
        var reason = "No high-confidence match found for '%s'. Select an existing procedure to edit/retry, or create a new one."
                .formatted(item.getDescription());
        return resolveWithUserInput(item, match, reason, stateTracker, testCase, context);
    }

    /**
     * Handles the case where procedures exist but none have satisfied prerequisites — prompts the user to resolve it.
     */
    private UserFeedback promptUserToHandleMissingPrerequisites(ExecutionItem item, KnowledgeService.MatchResult match,
                                                                String selectionReason, ExecutionStateTracker stateTracker,
                                                                TestCase testCase, UiTestExecutionContext context) {
        return resolveWithUserInput(item, match, selectionReason, stateTracker, testCase, context);
    }

    private List<Procedure> resolveToAtomicSteps(Procedure procedure) {
        if (procedure.isAtomic()) {
            LOG.info("Procedure is already atomic, no decomposition is needed");
            return List.of(procedure);
        }
        var atomicSteps = knowledgeService.resolveToAtomicSteps(procedure.id());
        LOG.info("Decomposed into {} atomic step(s)", atomicSteps.size());
        return atomicSteps;
    }

    private void showTestDataOverrideWarningIfNeeded(ExecutionItem item, List<String> itemTestData, List<Procedure> atomicSteps) {
        boolean hasTestStepData = itemTestData != null && !itemTestData.isEmpty() &&
                itemTestData.stream().anyMatch(CommonUtils::isNotBlank);
        if (!hasTestStepData || uiTestAgentConfig.isFullyUnattended() || !(item instanceof TestStepItem)) {
            return;
        }
        List<String> affectedProcedures = atomicSteps.stream()
                .filter(s -> s.testData() != null && !s.testData().isEmpty() &&
                        s.testData().stream().anyMatch(CommonUtils::isNotBlank))
                .map(Procedure::description)
                .toList();
        if (!affectedProcedures.isEmpty()) {
            String procList = String.join("\n- ", affectedProcedures);
            String message = "This Test Step contains test data. The saved data for the following atomic procedures will " +
                    "be discarded:\n" + procList;
            InformationalPopup.display("Data Override Warning", message, null, WARNING, uiTestAgentConfig);
        }
    }

    private AtomicLoopOutcome executeAtomicStepsLoop(ExecutionItem item, Procedure procedure, List<Procedure> atomicSteps,
                                                     TestCase testCase, UiTestExecutionContext context,
                                                     ExecutionStateTracker stateTracker,
                                                     List<UiTestStepResult> testStepResults,
                                                     List<UiPreconditionResult> preconditionResults) {
        int totalAtomics = atomicSteps.size();
        for (int i = 0; i < totalAtomics; i++) {
            Procedure atomicStep = atomicSteps.get(i);
            if (!atomicStep.prerequisites().isEmpty() && !stateTracker.arePrerequisitesMet(atomicStep.prerequisites())) {
                var missing = stateTracker.findMissingPrerequisites(atomicStep.prerequisites());
                var reason = "Atomic step '%s' skipped — prerequisites not satisfied: %s"
                        .formatted(atomicStep.description(), missing);
                LOG.warn(reason);
                if (uiTestAgentConfig.isFullyUnattended()) {
                    recordFailure(context, item, item.getDescription(), reason);
                } else {
                    InformationalPopup.display("Prerequisites Not Satisfied", reason, null, WARNING, uiTestAgentConfig);
                }
                break;
            }

            String targetElementId = knowledgeService.findTargetedUiElementId(atomicStep.id())
                    .map(UUID::toString)
                    .orElse(null);
            UiElement targetElement = targetElementId != null
                    ? uiElementCache.get(UUID.fromString(targetElementId)).orElse(null)
                    : null;
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
                    effectiveExpectedResults,
                    targetElement
            );

            var loopOutcome = stepExecutionOrchestrator.executeAtomicStepWithRetryLoop(item, atomicStep,
                    testCase, procedure.isAtomic() ? null : procedure, context,
                    testStepResults, preconditionResults,
                    stateTracker.getExecutedAtomicProcedures(), execContext);

            if (loopOutcome == UserDecisionOutcome.TERMINATE_EXECUTION) {
                LOG.error("Terminating execution after failure of atomic procedure '{}'", atomicStep.description());
                captureFailureContext(atomicStep, testStepResults, preconditionResults);
                return AtomicLoopOutcome.TERMINATE;
            }
            if (loopOutcome == UserDecisionOutcome.RE_DECOMPOSE_AND_RETRY) {
                LOG.info("Atomic step '{}' was edited to composite — re-injecting item for re-decomposition",
                        atomicStep.description());
                return AtomicLoopOutcome.RE_DECOMPOSE;
            }

            var graphEffects = knowledgeService.findEffectsForProcedure(atomicStep.id());
            stateTracker.addEffects(graphEffects);
            if (graphEffects.isEmpty() && !atomicStep.effects().isEmpty()) {
                LOG.warn("No HAS_EFFECT phrase nodes found for '{}' (id={}) — falling back to string-only state " +
                                "tracking; prerequisite semantic matching will be degraded for subsequent steps",
                        atomicStep.description(), atomicStep.id());
                stateTracker.addEffectPhrases(atomicStep.effects());
            }
            satisfiesEdgeService.persistSatisfiesEdgesAsync(atomicStep.id());
            stateTracker.addExecutedAtomicProcedure(atomicStep);
        }
        return AtomicLoopOutcome.COMPLETED;
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
                    "The following test steps appear to be out of order:\n\n" + details, null, WARNING, uiTestAgentConfig);
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
            case TestStepItem(TestStep testStep) -> getTestStepEffectiveExpectedResults(atomicStep, isSingle, isLast, testStep);
            case PreconditionItem ignored -> getPreconditionEffectiveExpectedResults(atomicStep, isSingle);
        };
    }

    private static @NonNull String getPreconditionEffectiveExpectedResults(Procedure atomicStep, boolean isSingle) {
        if (isSingle) {
            String atomicExpected = getAtomicStepExpectedResults(atomicStep);
            String testDataString = atomicStep.testData() != null ? String.join(", ", atomicStep.testData()) : "";
            return atomicExpected.isBlank() ? testDataString : atomicExpected;
        } else {
            return "";
        }
    }

    private static @NonNull String getTestStepEffectiveExpectedResults(Procedure atomicStep, boolean isSingle, boolean isLast,
                                                                       TestStep testStep) {
        String testStepExpected = testStep.expectedResults() != null ? testStep.expectedResults() : "";
        String atomicExpected = getAtomicStepExpectedResults(atomicStep);
        if (isSingle) {
            return testStepExpected;
        } else if (isLast) {
            return atomicExpected.isBlank()
                    ? testStepExpected
                    : testStepExpected.isBlank() ? atomicExpected : atomicExpected + "\n" + testStepExpected;
        } else {
            return atomicExpected;
        }
    }

    private static @NonNull String getAtomicStepExpectedResults(Procedure atomicStep) {
        return atomicStep.expectedResults() != null ? atomicStep.expectedResults() : "";
    }

    private static void recordFailure(UiTestExecutionContext context, ExecutionItem item, String itemDescription, String errorMessage) {
        switch (item) {
            case PreconditionItem _ -> context.addPreconditionResult(new UiPreconditionResult(itemDescription, false, errorMessage,
                    captureScreen(), now(), now()));
            case TestStepItem(TestStep testStep) -> context.addStepResult(new UiTestStepResult(testStep, FAILURE, errorMessage, null,
                    captureScreen(), now(), now()));
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

    /**
     * Shows the procedure selection popup in a loop, handling RETRY, EDIT, and CREATE actions until
     * a procedure is resolved or the user terminates.
     */
    private UserFeedback resolveWithUserInput(ExecutionItem item, KnowledgeService.MatchResult match,
                                              String selectionReason, ExecutionStateTracker stateTracker,
                                              TestCase testCase, UiTestExecutionContext executionContext) {
        String itemDescription = item.getDescription();
        List<String> itemTestData = item.getTestData();
        String itemExpectedResults = item.getExpectedResults();
        boolean isPreconditionItem = item instanceof PreconditionItem;
        while (true) {
            LOG.info("Showing procedure selection popup for '{}'. Reason: {}", itemDescription, selectionReason);
            var selectionResult = ProcedureSelectionPopup
                    .displayAndGetSelection(null, selectionReason, itemDescription, match.allMatches(), uiTestAgentConfig);
            if (selectionResult.isEmpty()) {
                LOG.warn("User cancelled selection for '{}', stopping execution", itemDescription);
                return new UserFeedback.ManualTermination("User cancelled procedure selection for '%s'".formatted(itemDescription));
            }
            var res = selectionResult.get();
            switch (res.action()) {
                case RETRY -> {
                    LOG.info("User selected RETRY for '{}', refreshing matches", itemDescription);
                    knowledgeService.onKnowledgeIngested();
                    var refreshed = refreshBestMatch(itemDescription, stateTracker);
                    if (refreshed.isPresent()) {
                        match = refreshed.get();
                    }
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
                        InformationalPopup.display("Shared Procedure Warning", msg, null, WARNING, uiTestAgentConfig);
                    }
                    var itemContext = new ExecutionItemContext(itemDescription, itemTestData, isPreconditionItem);
                    var editResult = procedureKnowledgeService.triggerEditProcedureFlow(existing, itemTestData,
                            itemExpectedResults, !isPreconditionItem, itemContext, testCase, executionContext,
                            stateTracker.getExecutedAtomicProcedures());
                    if (editResult.isSaved()) {
                        knowledgeIngestionService.update(editResult.savedProcedureId().get(), editResult.updatedNode().get());
                        knowledgeService.onKnowledgeIngested();
                        LOG.info("Procedure edited, re-fetching matches for '{}'", itemDescription);
                        var refreshed = refreshBestMatch(itemDescription, stateTracker);
                        if (refreshed.isEmpty()) {
                            LOG.warn("No matches found after edit for '{}'", itemDescription);
                        } else {
                            match = refreshed.get();
                        }
                    } else {
                        LOG.info("User cancelled the procedure edit flow for '{}'", itemDescription);
                    }
                }
                case CREATE -> {
                    LOG.info("User selected CREATE for '{}', opening knowledge collection dialog", itemDescription);
                    var newProcedureResult = procedureKnowledgeService.triggerNewProcedureFlow(itemDescription,
                            itemTestData, itemExpectedResults, isPreconditionItem, testCase,
                            executionContext, stateTracker.getExecutedAtomicProcedures());
                    if (newProcedureResult.isEmpty()) {
                        LOG.info("User cancelled new procedure creation for '{}', returning to selection popup", itemDescription);
                        continue;
                    }
                    LOG.info("New procedure created for '{}', ingesting into knowledge DB", itemDescription);
                    knowledgeIngestionService.ingest(newProcedureResult.get());
                    knowledgeService.onKnowledgeIngested();
                    var newMatchOpt = knowledgeService.findBestMatch(itemDescription, stateTracker.getEffectNodeIds(),
                            stateTracker.getRecentParentIds());
                    if (newMatchOpt.isPresent()) {
                        return new UserFeedback.Found(newMatchOpt.get().procedure());
                    }
                    LOG.warn("No match found after creation for '{}', returning to selection popup", itemDescription);
                }
                case null, default -> {
                }
            }
            // After RETRY or EDIT, return Found if there's now a feasible high-confidence match
            if (match.confidence() != KnowledgeService.MatchConfidence.LOW) {
                var feasible = selectFeasibleProcedure(match.allMatches(), stateTracker);
                if (feasible.isPresent()) {
                    return new UserFeedback.Found(feasible.get());
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

    private void captureFailureContext(Procedure atomicStep, List<UiTestStepResult> testStepResults,
                                       List<UiPreconditionResult> preconditionResults) {
        var preSelectedCategory = ErrorCategory.UNKNOWN;
        String defaultSymptom = "Unknown failure in procedure " + atomicStep.description();
        String preFilledSymptom = extractLastErrorMessage(testStepResults, preconditionResults, defaultSymptom);

        FailureContext failureContext = uiTestAgentConfig.isFullyUnattended()
                ? new FailureContext(UUID.randomUUID(), preFilledSymptom, preSelectedCategory, "", 1, now(), FailureContext.Mode.UNATTENDED)
                : FailureContextCaptureDialog.displayAndGetSelection(preSelectedCategory, preFilledSymptom, uiTestAgentConfig);

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

    private sealed interface ProcedureLookup {
        record DirectMatch(Procedure procedure) implements ProcedureLookup {
        }

        record LowConfidenceMatch(KnowledgeService.MatchResult match, Procedure feasibleProcedure) implements ProcedureLookup {
        }

        record NoProcedureWithFulfilledPrerequisites(KnowledgeService.MatchResult match, List<String> missingPrerequisites)
                implements ProcedureLookup {
        }

        record NoMatchFound() implements ProcedureLookup {
        }
    }

    private sealed interface UserFeedback {
        record Found(Procedure procedure) implements UserFeedback {
        }

        record NewProcedureCreated() implements UserFeedback {
        }

        record ManualTermination(String reason) implements UserFeedback {
        }

        record AutomaticTermination(String reason) implements UserFeedback {
        }
    }

    private enum AtomicLoopOutcome {COMPLETED, TERMINATE, RE_DECOMPOSE}

    private sealed interface ExecutionFlow {
        record Continue() implements ExecutionFlow {
        }

        record Rerun() implements ExecutionFlow {
        }

        record Stop() implements ExecutionFlow {
        }
    }
}
