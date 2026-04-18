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
import org.jetbrains.annotations.Nullable;
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
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository.UnsatisfiedPrerequisite;
import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.user_dialogs.*;
import org.tarik.ta.user_dialogs.knowledge.ExecutionItemContext;
import org.tarik.ta.user_dialogs.knowledge.UserChoiceDialog;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.time.Instant.now;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.knowledge_graph.ExecutionResultHelper.*;
import static org.tarik.ta.knowledge_graph.UserDecisionOutcome.*;
import static org.tarik.ta.user_dialogs.PopupType.ERROR;
import static org.tarik.ta.user_dialogs.PopupType.INFO;
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
    private final ElementLocationHistoryLookup elementLocationHistoryLookup;

    public KnowledgeBasedExecutionOrchestrator(KnowledgeService knowledgeService,
                                               KnowledgeIngestionService knowledgeIngestionService,
                                               StepExecutionOrchestrator stepExecutionOrchestrator,
                                               ProcedureKnowledgeCollectionService procedureKnowledgeService,
                                               SatisfiesEdgeService satisfiesEdgeService,
                                               ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService,
                                               FailureContextService failureContextService,
                                               UiTestAgentConfig uiTestAgentConfig,
                                               UiElementCache uiElementCache,
                                               ElementLocationHistoryLookup elementLocationHistoryLookup) {
        this.knowledgeService = knowledgeService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.stepExecutionOrchestrator = stepExecutionOrchestrator;
        this.procedureKnowledgeService = procedureKnowledgeService;
        this.satisfiesEdgeService = satisfiesEdgeService;
        this.procedureUsageByTestCaseTrackingService = procedureUsageByTestCaseTrackingService;
        this.failureContextService = failureContextService;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.uiElementCache = uiElementCache;
        this.elementLocationHistoryLookup = elementLocationHistoryLookup;
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
                    case ProcedureLookup.DirectMatch(var procedure, var hasLowStability) -> {
                        LOG.info("Direct high-confidence match found for '{}'", item.getDescription());
                        yield processFoundProcedure(procedure, hasLowStability, item, testCase, context, stateTracker, usedProcedureIds, queue);
                    }
                    case ProcedureLookup.NeedsUserResolution res -> {
                        if (uiTestAgentConfig.isFullyUnattended()) {
                            throw new MissingProcedureException(buildSelectionReason(item.getDescription(), res, item instanceof ExecutionItem.PreconditionItem));
                        }
                        var reason = buildSelectionReason(item.getDescription(), res, item instanceof ExecutionItem.PreconditionItem);
                        LOG.info("No direct match for '{}' — prompting user. Reason: {}", item.getDescription(), reason);
                        var userDecision = resolveWithUserInput(item, res.match(), reason, stateTracker, testCase, context);
                        yield processUserFeedback(userDecision, item, testCase, context, stateTracker, usedProcedureIds, queue);
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
                        "Lost connection to the knowledge graph DB: " + e.getMessage(), null, ERROR, uiTestAgentConfig);
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

    private ExecutionFlow processFoundProcedure(Procedure procedure, boolean hasLowStability, ExecutionItem item,
                                                TestCase testCase, UiTestExecutionContext context,
                                                ExecutionStateTracker stateTracker,
                                                List<UUID> usedProcedureIds, ExecutionQueue queue) {
        stateTracker.addRecentParent(procedure.id());
        LOG.info("Found matching procedure '{}' ({}) for '{}'", procedure.description(), procedure.id(), item.getDescription());
        if (hasLowStability && !uiTestAgentConfig.isFullyUnattended()) {
            InformationalPopup.display("Unstable Procedure Warning",
                    "Procedure '%s' has a low element-location stability score. It may be unreliable. Consider reviewing and updating it."
                            .formatted(procedure.description()), null, WARNING, uiTestAgentConfig);
        }
        List<Procedure> atomicSteps;
        try {
            atomicSteps = resolveToAtomicSteps(procedure);
        } catch (IllegalStateException e) {
            var errorMessage = e.getMessage();
            LOG.error("Decomposition failure for procedure '{}' ({}): {}", procedure.description(), procedure.id(), errorMessage);
            if (uiTestAgentConfig.isFullyUnattended()) {
                throw e;
            }
            var outcome = stepExecutionOrchestrator.handleDecompositionFailureInSupervisedMode(
                    errorMessage, procedure, item, testCase, context);
            return switch (outcome) {
                case TERMINATE_EXECUTION -> {
                    recordFailure(context, item, item.getDescription(), errorMessage);
                    yield new ExecutionFlow.Stop();
                }
                case RE_DECOMPOSE_AND_RETRY, RE_FETCH_AND_RETRY -> {
                    queue.injectAtFront(List.of(item));
                    yield new ExecutionFlow.Continue();
                }
                case CONTINUE_NEXT_STEP -> new ExecutionFlow.Continue();
            };
        }
        showTestDataOverrideWarningIfNeeded(item, item.getTestData(), atomicSteps);
        var testStepResults = new ArrayList<UiTestStepResult>();
        var preconditionResults = new ArrayList<UiPreconditionResult>();
        var loopOutcome = executeHierarchically(procedure, item, testCase, context, stateTracker, testStepResults, preconditionResults,
                new AtomicInteger(0), atomicSteps.size(), atomicSteps.size() == 1, null, 0);
        if (loopOutcome == AtomicLoopOutcome.TERMINATE) {
            if (item instanceof TestStepItem(TestStep testStep)) {
                context.addStepResult(mergeAtomicResults(testStep, testStepResults));
            } else {
                context.addPreconditionResult(mergePreconditionResults(item.getDescription(), preconditionResults));
            }
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
            case UserFeedback.Found(var procedure) ->
                    processFoundProcedure(procedure, false, item, testCase, context, stateTracker, usedProcedureIds, queue);
        };
    }

    private ProcedureLookup findProcedureInDb(ExecutionItem item, ExecutionStateTracker stateTracker) {
        var matchResult = knowledgeService.findBestMatch(item.getDescription(), stateTracker.getEffectNodeIds(),
                stateTracker.getRecentParentIds());
        if (matchResult.isEmpty()) {
            return new ProcedureLookup.NeedsUserResolution(null, List.of());
        }
        var match = matchResult.get();
        var feasible = selectFeasibleProcedure(match.allMatches());
        if (feasible.isPresent() && match.confidence() == KnowledgeService.MatchConfidence.HIGH) {
            return new ProcedureLookup.DirectMatch(feasible.get(), match.selectedHasLowStability());
        }
        if (match.confidence() == KnowledgeService.MatchConfidence.HIGH) {
            // High semantic score but no feasible procedure — prerequisites are the blocker
            var topCandidate = match.allMatches().getFirst();
            var missing = satisfiesEdgeService.findUnsatisfiedPrerequisites(
                    topCandidate.procedure().id(),
                    stateTracker.getEffectNodeIds());
            return new ProcedureLookup.NeedsUserResolution(match, missing);
        }
        // LOW confidence — semantic score too low; prerequisites are irrelevant
        return new ProcedureLookup.NeedsUserResolution(match, List.of());
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

    private AtomicLoopOutcome executeAtomicLeaf(ExecutionItem item, Procedure atomicStep, @Nullable Procedure directParent,
                                                TestCase testCase, UiTestExecutionContext context,
                                                ExecutionStateTracker stateTracker,
                                                List<UiTestStepResult> testStepResults,
                                                List<UiPreconditionResult> preconditionResults,
                                                boolean isSingle, boolean isLast) {
        var missing = satisfiesEdgeService.findUnsatisfiedPrerequisites(
                atomicStep.id(),
                stateTracker.getEffectNodeIds());
        if (!missing.isEmpty()) {
            var reason = "prerequisites not satisfied: %s".formatted(missing);
            if (atomicStep.optional()) {
                LOG.debug("Optional atomic step '{}' skipped — {}", atomicStep.description(), reason);
                if (!uiTestAgentConfig.isFullyUnattended()) {
                    InformationalPopup.display("Optional Step Skipped",
                            "Optional step '%s' was skipped — %s".formatted(atomicStep.description(), reason),
                            null, INFO, uiTestAgentConfig);
                }
                return AtomicLoopOutcome.SKIPPED;
            }
            var fullReason = "Atomic step '%s' skipped — %s".formatted(atomicStep.description(), reason);
            LOG.warn(fullReason);
            if (!uiTestAgentConfig.isFullyUnattended()) {
                InformationalPopup.display("Prerequisites Not Satisfied", fullReason, null, WARNING, uiTestAgentConfig);
            }
            switch (item) {
                case TestStepItem(TestStep testStep) ->
                        testStepResults.add(new UiTestStepResult(testStep, FAILURE, fullReason, null, captureScreen(), now(), now()));
                case PreconditionItem _ ->
                        preconditionResults.add(new UiPreconditionResult(item.getDescription(), false, fullReason, captureScreen(), now(), now()));
            }
            return AtomicLoopOutcome.TERMINATE;
        }

        Function<Procedure, AtomicStepExecutionContext> contextFactory = p -> buildExecContext(p, item, isSingle, isLast);
        var execContext = contextFactory.apply(atomicStep);

        var loopOutcome = stepExecutionOrchestrator.executeAtomicStepWithRetryLoop(item, atomicStep,
                testCase, directParent, context,
                testStepResults, preconditionResults,
                stateTracker.getExecutedAtomicProcedures(), execContext, contextFactory);

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
        satisfiesEdgeService.persistSatisfiesEdgesAsync(atomicStep.id());
        stateTracker.addExecutedAtomicProcedure(atomicStep);

        return AtomicLoopOutcome.COMPLETED;
    }

    private AtomicStepExecutionContext buildExecContext(Procedure atomicStep, ExecutionItem item, boolean isSingle, boolean isLast) {
        String targetElementId = knowledgeService.findTargetedUiElementId(atomicStep.id())
                .map(UUID::toString)
                .orElse(null);
        UiElement targetElement = null;
        ElementLocationHistory locationHistory = null;
        if (targetElementId != null) {
            UUID elementUuid = UUID.fromString(targetElementId);
            targetElement = uiElementCache.get(elementUuid).orElse(null);
            locationHistory = elementLocationHistoryLookup.lookup(elementUuid).orElse(null);
        } else {
            LOG.debug("No target UI element linked to procedure '{}' — proceeding without element hint",
                    atomicStep.description());
        }
        return new AtomicStepExecutionContext(
                atomicStep.timingProfile(),
                knowledgeService::updateTimingProfile,
                failureContextService.findFailureHints(atomicStep.id()),
                targetElementId,
                computeEffectiveExpectedResults(item, atomicStep, isSingle, isLast),
                targetElement,
                locationHistory
        );
    }

    private AtomicLoopOutcome executeHierarchically(Procedure procedure, ExecutionItem item, TestCase testCase,
                                                    UiTestExecutionContext context, ExecutionStateTracker stateTracker,
                                                    List<UiTestStepResult> testStepResults,
                                                    List<UiPreconditionResult> preconditionResults,
                                                    AtomicInteger atomicCounter,
                                                    int totalAtomics, boolean isSingle, @Nullable Procedure directParent, int depth) {
        String indent = "  ".repeat(depth);
        if (procedure.isAtomic()) {
            LOG.info("{}→ Executing atomic: '{}' ({}/{})", indent, procedure.description(), atomicCounter.get() + 1, totalAtomics);
            boolean isLast = atomicCounter.getAndIncrement() == totalAtomics - 1;
            return executeAtomicLeaf(item, procedure, directParent, testCase, context, stateTracker,
                    testStepResults, preconditionResults, isSingle, isLast);
        } else {
            LOG.info("{}→ Entering composite: '{}'", indent, procedure.description());
            stateTracker.enterCompositeScope(procedure);
            var children = knowledgeService.getChildren(procedure.id());
            for (Procedure child : children) {
                var outcome = executeHierarchically(child, item, testCase, context, stateTracker,
                        testStepResults, preconditionResults, atomicCounter, totalAtomics, isSingle, procedure, depth + 1);
                if (outcome == AtomicLoopOutcome.TERMINATE || outcome == AtomicLoopOutcome.RE_DECOMPOSE) {
                    stateTracker.abandonCompositeScope();
                    LOG.info("{}← Abandoned composite scope: '{}'", indent, procedure.description());
                    return outcome;
                }
                // COMPLETED and SKIPPED both continue to the next sibling
            }
            var graphEffects = knowledgeService.findEffectsForProcedure(procedure.id());
            stateTracker.closeCompositeScope(graphEffects);
            LOG.info("{}← Closed composite: '{}' ({} effects promoted)", indent, procedure.description(), graphEffects.size());
            return AtomicLoopOutcome.COMPLETED;
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
     * Returns the first candidate whose prerequisites are all semantically satisfied (per re-ranking scores).
     * Procedures with no prerequisites are always considered feasible.
     */
    private static Optional<Procedure> selectFeasibleProcedure(List<KnowledgeService.ScoredProcedure> candidates) {
        return candidates.stream()
                .filter(sp -> sp.totalPrereqs() == 0 || sp.satisfied() == sp.totalPrereqs())
                .map(KnowledgeService.ScoredProcedure::procedure)
                .findFirst();
    }

    /**
     * Shows the procedure selection popup in a loop, handling RETRY, BROWSE, and CREATE actions until
     * a procedure is resolved or the user terminates.
     */
    private UserFeedback resolveWithUserInput(ExecutionItem item, @Nullable KnowledgeService.MatchResult match,
                                              String selectionReason, ExecutionStateTracker stateTracker,
                                              TestCase testCase, UiTestExecutionContext executionContext) {
        String itemDescription = item.getDescription();
        List<String> itemTestData = item.getTestData();
        String itemExpectedResults = item.getExpectedResults();
        boolean isPreconditionItem = item instanceof PreconditionItem;
        List<KnowledgeService.ScoredProcedure> allScoredMatches = knowledgeService.findTopRankedWithScores(
                itemDescription, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
        while (true) {
            LOG.info("Showing procedure selection popup for '{}'. Reason: {}", itemDescription, selectionReason);
            var selectionResult = UserChoiceDialog.displayAndGetSelection(
                    null, selectionReason, itemDescription,
                    allScoredMatches, knowledgeService,
                    stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds(),
                    uiTestAgentConfig);
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
                    allScoredMatches = knowledgeService.findTopRankedWithScores(
                            itemDescription, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
                }
                case BROWSE -> {
                    var refreshed = handleProcedureEdit(res.existingId(), itemDescription, itemTestData,
                            itemExpectedResults, isPreconditionItem, testCase, executionContext, stateTracker);
                    if (refreshed.isPresent()) {
                        match = refreshed.get();
                    }
                    allScoredMatches = knowledgeService.findTopRankedWithScores(
                            itemDescription, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
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
                    allScoredMatches = knowledgeService.findTopRankedWithScores(
                            itemDescription, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
                }
                case null, default -> {
                }
            }
            // After RETRY or BROWSE, return Found if there's now a feasible high-confidence match
            if (match != null && match.confidence() != KnowledgeService.MatchConfidence.LOW) {
                var feasible = selectFeasibleProcedure(match.allMatches());
                if (feasible.isPresent()) {
                    return new UserFeedback.Found(feasible.get());
                }
            }
        }
    }

    private Optional<KnowledgeService.MatchResult> handleProcedureEdit(UUID procedureId, String itemDescription,
                                                                        List<String> itemTestData, String itemExpectedResults,
                                                                        boolean isPreconditionItem, TestCase testCase,
                                                                        UiTestExecutionContext executionContext,
                                                                        ExecutionStateTracker stateTracker) {
        var existing = knowledgeService.findById(procedureId)
                .orElseThrow(() -> new IllegalStateException("Selected procedure with ID '%s' not found".formatted(procedureId)));
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
            }
            return refreshed;
        }
        LOG.info("User cancelled the procedure edit flow for '{}'", itemDescription);
        return Optional.empty();
    }

    private static String buildSelectionReason(String description, ProcedureLookup.NeedsUserResolution res, boolean isPrecondition) {
        var itemKind = isPrecondition ? "the test precondition" : "the test step";
        if (res.match() == null) {
            return "No matching procedure found for '%s'. Please create a new one.".formatted(description);
        }
        if (res.match().confidence() == KnowledgeService.MatchConfidence.LOW) {
            var base = !res.match().wasDemoted()
                    ? "No high-confidence match found for %s '%s'."
                    : res.match().demotedDueToPrerequisites()
                        ? "A high-confidence procedure match was found for '%s' but was demoted because its prerequisites are not satisfied."
                        : "A high-confidence procedure match was found for '%s' but was demoted by a better-ranked contextual match.";
            if (!res.match().wasDemoted()) {
                return (base + " What do you want to do next ?").formatted(itemKind, description);
            }
            return (base + " What do you want to do next ?").formatted(description);
        }
        return ("Matching procedure found for '%s' but its prerequisites are not satisfied. Missing: %s")
                .formatted(description, res.missingPrerequisites());
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
        record DirectMatch(Procedure procedure, boolean hasLowStability) implements ProcedureLookup {}

        record NeedsUserResolution(@Nullable KnowledgeService.MatchResult match, List<UnsatisfiedPrerequisite> missingPrerequisites)
                implements ProcedureLookup {}
    }

    private sealed interface UserFeedback {
        record Found(Procedure procedure) implements UserFeedback {
        }

        record ManualTermination(String reason) implements UserFeedback {
        }
    }

    private enum AtomicLoopOutcome {COMPLETED, TERMINATE, RE_DECOMPOSE, SKIPPED}

    private sealed interface ExecutionFlow {
        record Continue() implements ExecutionFlow {
        }

        record Stop() implements ExecutionFlow {
        }
    }
}
