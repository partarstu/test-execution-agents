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
package org.tarik.ta.knowledge_graph;

import org.tarik.ta.config.scopes.UiAgentRequestScope;
import jakarta.annotation.PreDestroy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.dto.*;
import org.tarik.ta.dto.panel.ProcedureNode;
import org.tarik.ta.dto.panel.ProcedureOutcome;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.execution.ExecutionQueue;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ClosingComposite;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionStateSnapshot;
import org.tarik.ta.knowledge_graph.execution.NextAtomicPrefetchCoordinator;
import org.tarik.ta.knowledge_graph.execution.PrefetchedAtomicContext;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.Kind;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository.UnsatisfiedPrerequisite;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Outcome;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Source;
import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.knowledge_graph.service.ExecutionStateTracker.Checkpoint;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.panel.OperatorPrompts;
import org.tarik.ta.panel.RunObserver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Suppliers.memoize;
import static java.time.Instant.now;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.core.utils.CommonUtils.shutDownGracefully;
import static org.tarik.ta.core.model.ContextEntry.StepType.PRECONDITION;
import static org.tarik.ta.core.model.ContextEntry.StepType.TEST_STEP;
import static org.tarik.ta.knowledge_graph.ExecutionResultHelper.*;
import static org.tarik.ta.dto.PopupType.ERROR;
import static org.tarik.ta.dto.PopupType.INFO;
import static org.tarik.ta.dto.PopupType.WARNING;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

@UiAgentRequestScope
public class KnowledgeBasedExecutionOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeBasedExecutionOrchestrator.class);
    private static final Duration EFFECTS_PREFETCH_SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(5);

    private final KnowledgeService knowledgeService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final StepExecutionOrchestrator stepExecutionOrchestrator;
    private final ProcedureKnowledgeCollectionService procedureKnowledgeService;
    private final SatisfiesEdgeService satisfiesEdgeService;
    private final ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService;
    private final FailureContextService failureContextService;
    private final UiTestAgentConfig uiTestAgentConfig;
    private final UiElementCache uiElementCache;
    private final UiElementRepository uiElementRepository;
    private final ElementLocationHistoryLookup elementLocationHistoryLookup;
    private final AsyncExecutionPersistenceService asyncExecutionPersistenceService;
    private final OperatorPrompts operatorPrompts;
    private final RunObserver runObserver;
    private final ExecutionRunService executionRunService;
    private final ExecutorService effectsPrefetchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public KnowledgeBasedExecutionOrchestrator(KnowledgeService knowledgeService,
                                               KnowledgeIngestionService knowledgeIngestionService,
                                               StepExecutionOrchestrator stepExecutionOrchestrator,
                                               ProcedureKnowledgeCollectionService procedureKnowledgeService,
                                               SatisfiesEdgeService satisfiesEdgeService,
                                               ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService,
                                               FailureContextService failureContextService,
                                               UiTestAgentConfig uiTestAgentConfig,
                                               UiElementCache uiElementCache,
                                               UiElementRepository uiElementRepository,
                                               ElementLocationHistoryLookup elementLocationHistoryLookup,
                                               AsyncExecutionPersistenceService asyncExecutionPersistenceService,
                                               OperatorPrompts operatorPrompts,
                                               RunObserver runObserver,
                                               ExecutionRunService executionRunService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.stepExecutionOrchestrator = stepExecutionOrchestrator;
        this.procedureKnowledgeService = procedureKnowledgeService;
        this.satisfiesEdgeService = satisfiesEdgeService;
        this.procedureUsageByTestCaseTrackingService = procedureUsageByTestCaseTrackingService;
        this.failureContextService = failureContextService;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.uiElementCache = uiElementCache;
        this.uiElementRepository = uiElementRepository;
        this.elementLocationHistoryLookup = elementLocationHistoryLookup;
        this.asyncExecutionPersistenceService = asyncExecutionPersistenceService;
        this.operatorPrompts = operatorPrompts;
        this.runObserver = runObserver;
        this.executionRunService = executionRunService;
    }

    public void executeBasedOnKnowledge(TestExecutionContext context,
                                        TestCase testCase,
                                        int startingStepIndex,
                                        ExecutionStateTracker stateTracker) {
        try (var prefetchCoordinator = new NextAtomicPrefetchCoordinator(
                uiTestAgentConfig.getExecutionMode(),
                knowledgeService, satisfiesEdgeService, uiElementCache, uiElementRepository, elementLocationHistoryLookup, failureContextService)) {
            asyncExecutionPersistenceService.bindTestCase(testCase.name());
            var queue = ExecutionQueue.fromTestCase(testCase, startingStepIndex);
            LOG.info("Created preconditions and test steps executions queue with {} item(s)", queue.remainingCount());
            seedEffectsOfSkippedItems(testCase, startingStepIndex, stateTracker);
            var usedProcedureIds = new ArrayList<UUID>();
            detectAndWarnOrderingConflicts(testCase);
            try {
                while (queue.hasNext()) {
                    ExecutionItem item = queue.next();
                    LOG.info("Processing execution item: {} (remaining in queue: {})", item.getClass().getSimpleName(),
                            queue.remainingCount());
                    notifyItemStarted(context, item);
                    startStep(context, item);
                    long itemStartNanos = System.nanoTime();
                    int resultsBeforeItem = resultCount(context, item);
                    var match = prefetchCoordinator.takeMatchResultIfValid(item).or(() -> findBestMatch(item, stateTracker));
                    var lookup = match.map(m -> toProcedureLookup(m, stateTracker))
                            .orElseGet(() -> new ProcedureLookup.NeedsUserResolution(null, List.of()));
                    var resolved = switch (lookup) {
                        case ProcedureLookup.DirectMatch(var procedure, var hasLowStability) -> {
                            LOG.info("Direct high-confidence match found for '{}'", item.getDescription());
                            var flow = processFoundProcedure(procedure, hasLowStability, item, testCase, context, stateTracker,
                                    usedProcedureIds, queue, prefetchCoordinator);
                            yield new ItemResolution(flow, Source.KNOWLEDGE, procedure.id());
                        }
                        case ProcedureLookup.NeedsUserResolution res -> {
                            boolean isUnattended = uiTestAgentConfig.isFullyUnattended();
                            var reason = buildSelectionReason(item.getDescription(), res, item instanceof ExecutionItem.PreconditionItem,
                                    !isUnattended);
                            if (isUnattended) {
                                var flow = recordUnattendedSelectionFailure(item, res.match(), reason, testCase, context, usedProcedureIds);
                                yield new ItemResolution(flow, Source.UNRESOLVED, null);
                            }
                            LOG.info("No direct match for '{}' — prompting user. Reason: {}", item.getDescription(), reason);
                            var userDecision = resolveWithUserInput(item, res.match(), reason, stateTracker, testCase);
                            yield processUserFeedback(userDecision, item, testCase, context, stateTracker, usedProcedureIds, queue, prefetchCoordinator);
                        }
                    };
                    recordResolution(item, resolved, match.orElse(null), context, resultsBeforeItem, itemStartNanos);
                    if (resolved.flow() instanceof ExecutionFlow.Stop) {
                        return;
                    }
                }
                try {
                    procedureUsageByTestCaseTrackingService.cleanupStaleUsesProcedure(testCase.name(), usedProcedureIds);
                } catch (Exception e) {
                    LOG.error("Failed to clean up stale USES_PROCEDURE edges for test case '{}'", testCase.name(), e);
                }
            } catch (DatabaseConnectionException e) {
                LOG.error("DB connection error during execution of test case '{}'", testCase.name(), e);
                if (!uiTestAgentConfig.isFullyUnattended()) {
                    operatorPrompts.showMessage("Database Connection Error",
                            "Lost connection to the knowledge graph DB: " + e.getMessage(), null, ERROR);
                }
                throw e;
            } finally {
                // Every exit path, including an early stop, must leave no usage write in flight when the driver closes.
                asyncExecutionPersistenceService.awaitPendingUsageWrites();
            }
        }
    }

    /**
     * Adds the effects of the preconditions and test steps before the starting step, which the operator has already executed.
     */
    private void seedEffectsOfSkippedItems(@NotNull TestCase testCase, int startingStepIndex, @NotNull ExecutionStateTracker stateTracker) {
        if (startingStepIndex == 0) {
            return;
        }
        var skippedDescriptions = Stream.concat(
                testCase.preconditions().stream().map(Precondition::description),
                testCase.testSteps().subList(0, startingStepIndex).stream().map(TestStep::stepDescription)).toList();
        LOG.info("Seeding the effects of {} skipped item(s) before starting from step #{}", skippedDescriptions.size(), startingStepIndex);
        skippedDescriptions.forEach(description -> seedEffectsOfSkippedItem(description, stateTracker));
    }

    private void seedEffectsOfSkippedItem(@NotNull String description, @NotNull ExecutionStateTracker stateTracker) {
        // A low-confidence match may be a different procedure, and its effects would unblock steps that are not ready.
        var procedure = knowledgeService.findBestMatch(description, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds())
                .filter(match -> match.confidence() == KnowledgeService.MatchConfidence.HIGH)
                .map(KnowledgeService.MatchResult::procedure);
        if (procedure.isEmpty()) {
            LOG.warn("No known procedure matches the skipped item '{}', its effects are not seeded", description);
            return;
        }
        // Only the procedure's own effects outlive it: a composite's scope drops its children's effects when it closes.
        var effects = knowledgeService.findEffectsForProcedure(procedure.get().id());
        stateTracker.removeEffects(satisfiesEdgeService.findNegatedEffectNodeIds(procedure.get(), stateTracker.getEffectNodeIds()));
        stateTracker.addEffects(effects);
        LOG.debug("Seeded {} effect(s) of procedure '{}' for the skipped item '{}'", effects.size(), procedure.get().description(),
                description);
    }

    /**
     * Streams the item that is about to be executed so observers (e.g. a live dashboard) can show the current activity
     * before the corresponding result is produced.
     */
    private static void notifyItemStarted(TestExecutionContext context, ExecutionItem item) {
        var eventEmitter = context.getEventEmitter();
        switch (item) {
            case TestStepItem(TestStep testStep) -> eventEmitter.emitStepStarted(testStep);
            case PreconditionItem _ -> eventEmitter.emitPreconditionStarted(item.getDescription());
        }
    }

    private static void startStep(TestExecutionContext context, ExecutionItem item) {
        var type = switch (item) {
            case TestStepItem _ -> TEST_STEP;
            case PreconditionItem _ -> PRECONDITION;
        };
        context.startStep(type, item.getDescription(), item.getTestData());
    }

    private ExecutionFlow processFoundProcedure(Procedure procedure, boolean hasLowStability, ExecutionItem item,
                                                TestCase testCase, TestExecutionContext context,
                                                ExecutionStateTracker stateTracker,
                                                List<UUID> usedProcedureIds, ExecutionQueue queue,
                                                NextAtomicPrefetchCoordinator prefetchCoordinator) {
        stateTracker.addRecentParent(procedure.id());
        LOG.info("Found matching procedure '{}' ({}) for '{}'", procedure.description(), procedure.id(), item.getDescription());
        if (hasLowStability && !uiTestAgentConfig.isFullyUnattended()) {
            operatorPrompts.showMessage("Unstable Procedure Warning",
                    "Procedure '%s' has a low element-location stability score. It may be unreliable. Consider reviewing and updating it."
                            .formatted(procedure.description()), null, WARNING);
        }
        // A whole-step rerun stays in this loop instead of re-entering the queue: the failed attempt is rolled back and the
        // (possibly replaced) root procedure is refetched and re-executed from its first atomic step.
        var rootProcedure = procedure;
        while (true) {
            var stateBeforeAttempt = stateTracker.checkpoint();
            List<Procedure> atomicSteps;
            try {
                atomicSteps = resolveToAtomicSteps(rootProcedure);
            } catch (IllegalStateException e) {
                var errorMessage = e.getMessage();
                LOG.error("Decomposition failure for procedure '{}' ({}): {}", rootProcedure.description(), rootProcedure.id(), errorMessage);
                if (uiTestAgentConfig.isFullyUnattended()) {
                    throw e;
                }
                var outcome = stepExecutionOrchestrator.handleDecompositionFailureInSupervisedMode(
                        errorMessage, rootProcedure, item, testCase);
                switch (outcome) {
                    case UserDecisionOutcome.TerminateExecution _ -> {
                        recordFailure(context, item, item.getDescription(), errorMessage);
                        return new ExecutionFlow.Stop();
                    }
                    case UserDecisionOutcome.RerunTestStep(var replacementProcedureId) -> {
                        var rerunRoot = prepareTestStepRerun(replacementProcedureId, rootProcedure.id(), item, context,
                                stateTracker, stateBeforeAttempt, prefetchCoordinator);
                        if (rerunRoot == null) {
                            return new ExecutionFlow.Stop();
                        }
                        rootProcedure = rerunRoot;
                    }
                    // Never returned for decomposition failures — skipping the item is the safe mapping
                    case UserDecisionOutcome.ContinueNextStep _, UserDecisionOutcome.RerunAtomicProcedure _ -> {
                        return new ExecutionFlow.Continue();
                    }
                }
                continue;
            }
            runObserver.itemOutlined(item.getTestData(), outlineOf(rootProcedure));
            showTestDataOverrideWarningIfNeeded(item, item.getTestData(), atomicSteps);
            var testStepResults = new ArrayList<UiTestStepResult>();
            var preconditionResults = new ArrayList<UiPreconditionResult>();
            // Recorded before execution so that a failing procedure stays linked to the test cases it breaks
            markProcedureAsUsed(testCase, rootProcedure.id(), usedProcedureIds);
            var loopOutcome = executeHierarchically(rootProcedure, rootProcedure, item, testCase, context, stateTracker, testStepResults, preconditionResults,
                    new AtomicInteger(0), atomicSteps, atomicSteps.size() == 1, List.of(), List.of(), 0, prefetchCoordinator, queue);
            if (loopOutcome instanceof AtomicLoopOutcome.RerunTestStep rerun) {
                // The rerun marks its root again, and a root the operator replaced must not survive the cleanup of a passing run
                usedProcedureIds.remove(rootProcedure.id());
                var rerunRoot = prepareTestStepRerun(rerun.replacementProcedureId(), rootProcedure.id(), item, context,
                        stateTracker, stateBeforeAttempt, prefetchCoordinator);
                if (rerunRoot == null) {
                    return new ExecutionFlow.Stop();
                }
                rootProcedure = rerunRoot;
                continue; // fresh result lists on the next iteration discard this attempt's results
            }
            if (loopOutcome instanceof AtomicLoopOutcome.Terminate) {
                if (item instanceof TestStepItem(TestStep testStep)) {
                    context.addStepResult(mergeAtomicResults(testStep, testStepResults));
                } else {
                    context.addPreconditionResult(mergePreconditionResults(item.getDescription(), preconditionResults));
                }
                return new ExecutionFlow.Stop();
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
            return new ExecutionFlow.Continue();
        }
    }

    /**
     * Prepares a whole-test-step rerun by rolling back the failed attempt and refetching the procedure to rerun (the dialog's
     * replacement if any, else the current root), returning {@code null} with the failure recorded when it no longer exists.
     */
    @Nullable
    private Procedure prepareTestStepRerun(@Nullable UUID replacementProcedureId, UUID currentRootId, ExecutionItem item,
                                           TestExecutionContext context, ExecutionStateTracker stateTracker,
                                           Checkpoint stateBeforeAttempt, NextAtomicPrefetchCoordinator prefetchCoordinator) {
        prefetchCoordinator.invalidate("test step rerun");
        stateTracker.restore(stateBeforeAttempt);
        UUID idToRerun = replacementProcedureId != null ? replacementProcedureId : currentRootId;
        var refetched = knowledgeService.findById(idToRerun);
        if (refetched.isEmpty()) {
            var errorMessage = "Procedure '%s' to be rerun no longer exists in the knowledge DB".formatted(idToRerun);
            LOG.error(errorMessage);
            if (!uiTestAgentConfig.isFullyUnattended()) {
                operatorPrompts.showMessage("Procedure Not Found", errorMessage, null, ERROR);
            }
            recordFailure(context, item, item.getDescription(), errorMessage);
            return null;
        }
        LOG.info("Rerunning the whole test step with procedure '{}' ({})", refetched.get().description(), idToRerun);
        return refetched.get();
    }

    private ItemResolution processUserFeedback(UserFeedback resolution, ExecutionItem item, TestCase testCase,
                                               TestExecutionContext context, ExecutionStateTracker stateTracker,
                                               List<UUID> usedProcedureIds, ExecutionQueue queue,
                                               NextAtomicPrefetchCoordinator prefetchCoordinator) {
        return switch (resolution) {
            case UserFeedback.ManualTermination(var reason) -> {
                LOG.warn("Execution was interrupted by user for item '{}': {}", item.getDescription(), reason);
                recordFailure(context, item, item.getDescription(), reason);
                yield new ItemResolution(new ExecutionFlow.Stop(), Source.UNRESOLVED, null);
            }
            case UserFeedback.Found(var procedure, var source) -> {
                var flow = processFoundProcedure(procedure, false, item, testCase, context, stateTracker, usedProcedureIds, queue,
                        prefetchCoordinator);
                yield new ItemResolution(flow, source, procedure.id());
            }
        };
    }

    private Optional<KnowledgeService.MatchResult> findBestMatch(ExecutionItem item, ExecutionStateTracker stateTracker) {
        return knowledgeService.findBestMatch(item.getDescription(), stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
    }

    /** Records how the item was resolved and how it ended, and shows it on the panel. */
    private void recordResolution(ExecutionItem item, ItemResolution resolved, @Nullable KnowledgeService.MatchResult match,
                                  TestExecutionContext context, int resultsBeforeItem, long itemStartNanos) {
        var outcome = itemOutcome(context, item, resultsBeforeItem);
        long durationMs = Duration.ofNanos(System.nanoTime() - itemStartNanos).toMillis();
        var resolution = executionRunService.recordResolution(item, resolved.source(), match, resolved.chosenProcedureId(), outcome,
                durationMs);
        runObserver.stepResolved(resolution);
    }

    private static int resultCount(TestExecutionContext context, ExecutionItem item) {
        return switch (item) {
            case TestStepItem _ -> context.getTestStepExecutionHistory().size();
            case PreconditionItem _ -> context.getPreconditionExecutionHistory().size();
        };
    }

    // An item that added no result to its history, e.g. after a skipped decomposition failure, was not executed
    private static Outcome itemOutcome(TestExecutionContext context, ExecutionItem item, int resultsBeforeItem) {
        if (resultCount(context, item) == resultsBeforeItem) {
            return Outcome.NOT_EXECUTED;
        }
        boolean succeeded = switch (item) {
            case TestStepItem _ -> context.getTestStepExecutionHistory().getLast().getExecutionStatus() == SUCCESS;
            case PreconditionItem _ -> context.getPreconditionExecutionHistory().getLast().isSuccess();
        };
        return succeeded ? Outcome.SUCCEEDED : Outcome.FAILED;
    }

    private ProcedureLookup toProcedureLookup(@NotNull KnowledgeService.MatchResult match, @NotNull ExecutionStateTracker stateTracker) {
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

    /** The procedure and its descendants in execution order, read from the traversal the decomposition just cached. */
    private List<ProcedureNode> outlineOf(Procedure root) {
        var nodes = new ArrayList<ProcedureNode>();
        addToOutline(root, 0, nodes);
        return nodes;
    }

    private void addToOutline(Procedure procedure, int depth, List<ProcedureNode> nodes) {
        nodes.add(ProcedureNode.pending(procedure.id(), procedure.description(), procedure.expectedResults(), procedure.isAtomic(),
                procedure.optional(), depth));
        if (!procedure.isAtomic()) {
            knowledgeService.getChildrenForExecution(procedure.id()).forEach(child -> addToOutline(child, depth + 1, nodes));
        }
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
            operatorPrompts.showMessage("Data Override Warning", message, null, WARNING);
        }
    }

    private AtomicLoopOutcome executeAtomicLeaf(ExecutionItem item, Procedure atomicStep, Procedure rootProcedure,
                                                List<Procedure> ancestorChain,
                                                List<ClosingComposite> closingComposites,
                                                TestCase testCase, TestExecutionContext context,
                                                ExecutionStateTracker stateTracker,
                                                List<UiTestStepResult> testStepResults,
                                                List<UiPreconditionResult> preconditionResults,
                                                boolean isSingle, boolean isLast,
                                                NextAtomicPrefetchCoordinator prefetchCoordinator,
                                                List<Procedure> atomicSteps, int currentIndex, ExecutionQueue queue) {
        // Consume the prefetch scheduled during the previous atomic's execution (single-use).
        // Retries always fall back to the synchronous contextFactory.
        Optional<PrefetchedAtomicContext> prefetchedOpt = prefetchCoordinator.takeIfValid(atomicStep, item);

        List<UnsatisfiedPrerequisite> missing = prefetchedOpt
                .map(PrefetchedAtomicContext::unsatisfiedPrerequisites)
                .orElseGet(() -> satisfiesEdgeService.findUnsatisfiedPrerequisites(
                        atomicStep.id(), stateTracker.getEffectNodeIds()));
        if (!missing.isEmpty()) {
            var reason = "prerequisites not satisfied: %s".formatted(missing);
            if (atomicStep.optional()) {
                LOG.debug("Optional atomic step '{}' skipped — {}", atomicStep.description(), reason);
                if (!uiTestAgentConfig.isFullyUnattended()) {
                    operatorPrompts.showMessage("Optional Step Skipped",
                            "Optional step '%s' was skipped — %s".formatted(atomicStep.description(), reason),
                            null, INFO);
                }
                return new AtomicLoopOutcome.Skipped();
            }
            var fullReason = "Atomic step '%s' skipped — %s".formatted(atomicStep.description(), reason);
            LOG.warn(fullReason);
            if (!uiTestAgentConfig.isFullyUnattended()) {
                operatorPrompts.showMessage("Prerequisites Not Satisfied", fullReason, null, WARNING);
                var outcome = stepExecutionOrchestrator.handleUnsatisfiedPrerequisitesInSupervisedMode(fullReason, atomicStep,
                        item, testCase);
                switch (outcome) {
                    // The refetched procedure goes through the prerequisite check again, e.g. after its prerequisites were edited
                    case UserDecisionOutcome.RerunAtomicProcedure(var refetched) -> {
                        return executeAtomicLeaf(item, refetched, rootProcedure, ancestorChain, closingComposites, testCase, context,
                                stateTracker, testStepResults, preconditionResults, isSingle, isLast, prefetchCoordinator, atomicSteps,
                                currentIndex, queue);
                    }
                    case UserDecisionOutcome.RerunTestStep(var replacementProcedureId) -> {
                        prefetchCoordinator.invalidate("test step rerun");
                        return new AtomicLoopOutcome.RerunTestStep(replacementProcedureId);
                    }
                    // Continuing is never offered by the next-action dialog, so both end in the failure recorded below
                    case UserDecisionOutcome.ContinueNextStep _, UserDecisionOutcome.TerminateExecution _ -> {
                    }
                }
            }
            asyncExecutionPersistenceService.recordAnomaly(Kind.STEP_FAILED, atomicStep.id(), fullReason);
            switch (item) {
                case TestStepItem(TestStep testStep) -> testStepResults.add(unsuccessfulStepResult(testStep, FAILURE, fullReason));
                case PreconditionItem _ -> preconditionResults.add(failedPreconditionResult(item.getDescription(), fullReason));
            }
            return new AtomicLoopOutcome.Terminate();
        }

        List<PhraseEmbedding> declaredEffects = prefetchedOpt
                .map(PrefetchedAtomicContext::declaredEffects)
                .orElseGet(() -> knowledgeService.findEffectsForProcedure(atomicStep.id()));

        Procedure nextAtomicInDecomposition = (currentIndex + 1 < atomicSteps.size()) ? atomicSteps.get(currentIndex + 1) : null;
        ExecutionItem nextQueueItem = queue.peek();

        // Resolved against the state before execution, which the atomic's negations apply to, and reused after its success
        var negatedEffectIds = satisfiesEdgeService.findNegatedEffectNodeIds(atomicStep, stateTracker.getEffectNodeIds());
        Supplier<ExecutionStateSnapshot> predictedSnapshot = stateTracker.projectedSnapshot(declaredEffects, negatedEffectIds,
                closingComposites);

        asyncExecutionPersistenceService.persistSatisfiesEdges(atomicStep.id());

        AtomicStepExecutionContext execContext = prefetchedOpt
                .map(ctx -> buildExecContextFromPrefetch(ctx, item, atomicStep, isSingle, isLast, ancestorChain))
                .orElseGet(() -> buildExecContext(atomicStep, item, isSingle, isLast, ancestorChain));
        Function<Procedure, AtomicStepExecutionContext> contextFactory =
                p -> buildExecContext(p, item, isSingle, isLast, ancestorChain);

        prefetchCoordinator.scheduleSuccessPathPrefetch(atomicStep, item, nextAtomicInDecomposition, nextQueueItem, predictedSnapshot);

        var loopOutcome = stepExecutionOrchestrator.executeAtomicStepWithRetryLoop(item, atomicStep,
                testCase, context,
                testStepResults, preconditionResults,
                execContext, contextFactory);

        return switch (loopOutcome) {
            case UserDecisionOutcome.ContinueNextStep _ -> {
                stateTracker.removeEffects(negatedEffectIds);
                stateTracker.addEffects(declaredEffects);
                stateTracker.addExecutedAtomicProcedure(atomicStep);
                yield new AtomicLoopOutcome.Completed();
            }
            // An atomic rerun never escapes the retry loop — it re-executes there; stopping is the safe fallback
            case UserDecisionOutcome.RerunAtomicProcedure _, UserDecisionOutcome.TerminateExecution _ -> {
                LOG.error("Terminating execution after failure of atomic procedure '{}'", atomicStep.description());
                asyncExecutionPersistenceService.recordAnomaly(Kind.STEP_FAILED, atomicStep.id(),
                        lastFailureMessage(atomicStep, testStepResults, preconditionResults));
                captureFailureContext(atomicStep, rootProcedure, testStepResults, preconditionResults);
                prefetchCoordinator.invalidate("atomic procedure failed");
                yield new AtomicLoopOutcome.Terminate();
            }
            case UserDecisionOutcome.RerunTestStep(var replacementProcedureId) -> {
                LOG.info("Atomic step '{}' requested a rerun of the whole test step", atomicStep.description());
                prefetchCoordinator.invalidate("test step rerun");
                yield new AtomicLoopOutcome.RerunTestStep(replacementProcedureId);
            }
        };
    }

    private AtomicStepExecutionContext buildExecContext(Procedure atomicStep, ExecutionItem item, boolean isSingle, boolean isLast,
                                                        List<Procedure> ancestorChain) {
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
        String effectiveExpectedResults = item instanceof TestStepItem(TestStep testStep)
                ? computeEffectiveExpectedResults(testStep, atomicStep, isSingle, isLast) : null;
        if (!uiTestAgentConfig.isAtomicVerificationEnabled() && !isLast) {
            effectiveExpectedResults = null;
        }

        return new AtomicStepExecutionContext(
                atomicStep.timingProfile(),
                asyncExecutionPersistenceService::updateTimingProfile,
                failureContextService.findFailureHints(atomicStep.id()),
                targetElementId,
                effectiveExpectedResults,
                targetElement,
                locationHistory,
                ancestorChain
        );
    }

    private AtomicStepExecutionContext buildExecContextFromPrefetch(org.tarik.ta.knowledge_graph.execution.PrefetchedAtomicContext ctx,
                                                                    ExecutionItem item, Procedure atomicStep,
                                                                    boolean isSingle, boolean isLast,
                                                                    List<Procedure> ancestorChain) {
        String effectiveExpectedResults = item instanceof TestStepItem(TestStep testStep)
                ? computeEffectiveExpectedResults(testStep, atomicStep, isSingle, isLast) : null;
        if (!uiTestAgentConfig.isAtomicVerificationEnabled() && !isLast) {
            effectiveExpectedResults = null;
        }
        return new AtomicStepExecutionContext(
                atomicStep.timingProfile(),
                asyncExecutionPersistenceService::updateTimingProfile,
                ctx.failureHints(),
                ctx.targetElementId(),
                effectiveExpectedResults,
                ctx.targetElement(),
                ctx.locationHistory(),
                ancestorChain
        );
    }

    private AtomicLoopOutcome executeHierarchically(Procedure procedure, Procedure rootProcedure, ExecutionItem item, TestCase testCase,
                                                    TestExecutionContext context, ExecutionStateTracker stateTracker,
                                                    List<UiTestStepResult> testStepResults,
                                                    List<UiPreconditionResult> preconditionResults,
                                                    AtomicInteger atomicCounter,
                                                    List<Procedure> atomicSteps, boolean isSingle, List<Procedure> ancestorChain,
                                                    List<ClosingComposite> closingComposites, int depth,
                                                    NextAtomicPrefetchCoordinator prefetchCoordinator, ExecutionQueue queue) {
        String indent = "  ".repeat(depth);
        runObserver.procedureStarted(procedure.id());
        if (procedure.isAtomic()) {
            LOG.info("{}→ Executing atomic: '{}' ({}/{})", indent, procedure.description(), atomicCounter.get() + 1, atomicSteps.size());
            boolean isLast = atomicCounter.getAndIncrement() == atomicSteps.size() - 1;
            var outcome = executeAtomicLeaf(item, procedure, rootProcedure, ancestorChain, closingComposites, testCase, context,
                    stateTracker, testStepResults, preconditionResults, isSingle, isLast, prefetchCoordinator, atomicSteps,
                    atomicCounter.get() - 1, queue);
            return finished(procedure, outcome);
        } else {
            LOG.info("{}→ Entering composite: '{}'", indent, procedure.description());
            stateTracker.enterCompositeScope(procedure);
            var effectsPrefetch = prefetchCompositeEffects(procedure);
            Supplier<List<PhraseEmbedding>> compositeEffects = memoize(() -> awaitCompositeEffects(effectsPrefetch, procedure));
            var closingComposite = new ClosingComposite(compositeEffects,
                    visibleEffectIds -> satisfiesEdgeService.findNegatedEffectNodeIds(procedure, visibleEffectIds));
            var children = knowledgeService.getChildrenForExecution(procedure.id());
            var childAncestorChain = Stream.concat(ancestorChain.stream(), Stream.of(procedure)).toList();
            // This composite closes right after its last child, so it joins the closes that follow that child, innermost first
            var lastChildClosingComposites = Stream.concat(Stream.of(closingComposite), closingComposites.stream()).toList();
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                var childClosingComposites = childIndex == children.size() - 1
                        ? lastChildClosingComposites
                        : List.<ClosingComposite>of();
                var outcome = executeHierarchically(children.get(childIndex), rootProcedure, item, testCase, context, stateTracker,
                        testStepResults, preconditionResults, atomicCounter, atomicSteps, isSingle, childAncestorChain,
                        childClosingComposites, depth + 1, prefetchCoordinator, queue);
                // A whole-step rerun abandons the composite scopes just like a termination — the rerun
                // re-decomposes and starts again from the first atomic step.
                if (outcome instanceof AtomicLoopOutcome.Terminate || outcome instanceof AtomicLoopOutcome.RerunTestStep) {
                    stateTracker.abandonCompositeScope();
                    LOG.info("{}← Abandoned composite scope: '{}'", indent, procedure.description());
                    return finished(procedure, outcome);
                }
                // COMPLETED and SKIPPED both continue to the next sibling
            }
            var graphEffects = compositeEffects.get();
            // Resolved against every visible effect: the ones of the closing frame are dropped with it anyway
            var negatedEffectIds = closingComposite.negatedEffectIds().apply(stateTracker.getEffectNodeIds());
            stateTracker.closeCompositeScope(graphEffects, negatedEffectIds);
            LOG.info("{}← Closed composite: '{}' ({} effects promoted)", indent, procedure.description(), graphEffects.size());
            return finished(procedure, new AtomicLoopOutcome.Completed());
        }
    }

    private AtomicLoopOutcome finished(Procedure procedure, AtomicLoopOutcome outcome) {
        var procedureOutcome = switch (outcome) {
            case AtomicLoopOutcome.Completed _ -> ProcedureOutcome.COMPLETED;
            case AtomicLoopOutcome.Skipped _ -> ProcedureOutcome.SKIPPED;
            case AtomicLoopOutcome.Terminate _ -> ProcedureOutcome.FAILED;
            case AtomicLoopOutcome.RerunTestStep _ -> ProcedureOutcome.ABANDONED;
        };
        runObserver.procedureFinished(procedure.id(), procedureOutcome);
        return outcome;
    }

    /**
     * Starts loading a composite's effects while its children execute, so the remote graph round trip is done by the time the
     * scope closes.
     */
    private CompletableFuture<List<PhraseEmbedding>> prefetchCompositeEffects(@NotNull Procedure procedure) {
        return CompletableFuture.supplyAsync(() -> knowledgeService.findEffectsForProcedure(procedure.id()), effectsPrefetchExecutor);
    }

    @PreDestroy
    public void close() {
        shutDownGracefully(effectsPrefetchExecutor, EFFECTS_PREFETCH_SHUTDOWN_GRACE_PERIOD);
    }

    /**
     * Returns the prefetched composite effects, falling back to a synchronous read if the prefetch failed.
     */
    private List<PhraseEmbedding> awaitCompositeEffects(@NotNull CompletableFuture<List<PhraseEmbedding>> effectsPrefetch,
                                                        @NotNull Procedure procedure) {
        try {
            return effectsPrefetch.join();
        } catch (CompletionException e) {
            LOG.warn("Effects prefetch failed for composite '{}', reading synchronously", procedure.description(), e.getCause());
            return knowledgeService.findEffectsForProcedure(procedure.id());
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
            operatorPrompts.showMessage("Ordering Conflicts Detected",
                    "The following test steps appear to be out of order:\n\n" + details, null, WARNING);
        }
    }

    /**
     * Computes the expected results an atomic step of the test step is verified against: the test step's for its only atomic step,
     * the step's own for a non-last one and both for the last one.
     */
    private static @NonNull String computeEffectiveExpectedResults(TestStep testStep, Procedure atomicStep, boolean isSingle,
                                                                   boolean isLast) {
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

    /**
     * Ends the run on an item without an executable procedure, recording it as an error and the matched procedure, if any, as failing
     * and used by the test case so that the health checks and the blast radius see it.
     */
    private ExecutionFlow recordUnattendedSelectionFailure(ExecutionItem item, @Nullable KnowledgeService.MatchResult match, String reason,
                                                           TestCase testCase, TestExecutionContext context, List<UUID> usedProcedureIds) {
        LOG.error("No executable procedure for '{}' in unattended mode: {}", item.getDescription(), reason);
        if (match != null) {
            var procedureId = match.procedure().id();
            asyncExecutionPersistenceService.recordAnomaly(Kind.STEP_FAILED, procedureId, reason);
            markProcedureAsUsed(testCase, procedureId, usedProcedureIds);
        }
        recordUnsuccessfulResult(context, item, item.getDescription(), reason, TestStepResultStatus.ERROR);
        return new ExecutionFlow.Stop();
    }

    private void markProcedureAsUsed(TestCase testCase, UUID procedureId, List<UUID> usedProcedureIds) {
        usedProcedureIds.add(procedureId);
        asyncExecutionPersistenceService.mergeUsesProcedure(testCase.name(), procedureId);
    }

    private static void recordFailure(TestExecutionContext context, ExecutionItem item, String itemDescription, String errorMessage) {
        recordUnsuccessfulResult(context, item, itemDescription, errorMessage, FAILURE);
    }

    private static void recordUnsuccessfulResult(TestExecutionContext context, ExecutionItem item, String itemDescription,
                                                 String errorMessage, TestStepResultStatus stepStatus) {
        switch (item) {
            case PreconditionItem _ -> context.addPreconditionResult(failedPreconditionResult(itemDescription, errorMessage));
            case TestStepItem(TestStep testStep) -> context.addStepResult(unsuccessfulStepResult(testStep, stepStatus, errorMessage));
        }
    }

    private static UiPreconditionResult failedPreconditionResult(String description, String errorMessage) {
        return new UiPreconditionResult(description, false, errorMessage, captureScreen(), now(), now());
    }

    private static UiTestStepResult unsuccessfulStepResult(TestStep testStep, TestStepResultStatus status, String errorMessage) {
        return new UiTestStepResult(testStep, status, errorMessage, null, captureScreen(), now(), now());
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
                                              TestCase testCase) {
        String itemDescription = item.getDescription();
        List<String> itemTestData = item.getTestData();
        String itemExpectedResults = item.getExpectedResults();
        boolean isPreconditionItem = item instanceof PreconditionItem;
        while (true) {
            LOG.info("Showing procedure selection popup for '{}'. Reason: {}", itemDescription, selectionReason);
            var selectionResult = operatorPrompts.chooseProcedure(selectionReason, itemDescription, knowledgeService,
                    stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds(), isPreconditionItem);
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
                case BROWSE -> {
                    var refreshed = handleProcedureEdit(res.existingId(), itemDescription, itemTestData,
                            itemExpectedResults, isPreconditionItem, testCase, stateTracker);
                    if (refreshed.isPresent()) {
                        match = refreshed.get();
                    }
                }
                case CREATE -> {
                    LOG.info("User selected CREATE for '{}', opening knowledge collection dialog", itemDescription);
                    var newProcedureResult = procedureKnowledgeService.triggerNewProcedureFlow(itemDescription,
                            itemTestData, itemExpectedResults, isPreconditionItem, testCase);
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
                        return new UserFeedback.Found(newMatchOpt.get().procedure(), Source.OPERATOR_CREATED);
                    }
                    LOG.warn("No match found after creation for '{}', returning to selection popup", itemDescription);
                }
                case null, default -> {
                }
            }
            // After RETRY or BROWSE, return Found if there's now a feasible high-confidence match
            if (match != null && match.confidence() != KnowledgeService.MatchConfidence.LOW) {
                var feasible = selectFeasibleProcedure(match.allMatches());
                if (feasible.isPresent()) {
                    return new UserFeedback.Found(feasible.get(), Source.OPERATOR_SELECTED);
                }
            }
        }
    }

    private Optional<KnowledgeService.MatchResult> handleProcedureEdit(UUID procedureId, String itemDescription,
                                                                        List<String> itemTestData, String itemExpectedResults,
                                                                        boolean isPreconditionItem, TestCase testCase,
                                                                        ExecutionStateTracker stateTracker) {
        var existing = knowledgeService.findById(procedureId)
                .orElseThrow(() -> new IllegalStateException("Selected procedure with ID '%s' not found".formatted(procedureId)));
        var testCasesUsingIt = procedureUsageByTestCaseTrackingService.findTestCasesUsingProcedure(existing.id());
        if (!testCasesUsingIt.isEmpty()) {
            String tcList = String.join("\n- ", testCasesUsingIt);
            String msg = "This procedure is used by %d test case(s):\n- %s\n\nEditing it may affect all of them."
                    .formatted(testCasesUsingIt.size(), tcList);
            operatorPrompts.showMessage("Shared Procedure Warning", msg, null, WARNING);
        }
        var itemContext = new ExecutionItemContext(itemDescription, itemTestData, isPreconditionItem);
        var editResult = procedureKnowledgeService.triggerEditProcedureFlow(existing, itemTestData,
                itemExpectedResults, itemContext, testCase);
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

    private static String buildSelectionReason(String description, ProcedureLookup.NeedsUserResolution res, boolean isPrecondition,
                                               boolean asksUserForNextAction) {
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
            var template = asksUserForNextAction ? base + " What do you want to do next ?" : base;
            if (!res.match().wasDemoted()) {
                return template.formatted(itemKind, description);
            }
            return template.formatted(description);
        }
        return ("Matching procedure found for '%s' but its prerequisites are not satisfied. Missing: %s")
                .formatted(description, res.missingPrerequisites());
    }

    /**
     * Finds the best match for the given description and tells the operator when none is found.
     * Returns the updated match result, or empty if no match exists.
     */
    private Optional<KnowledgeService.MatchResult> refreshBestMatch(String itemDescription, ExecutionStateTracker stateTracker) {
        var result = knowledgeService.findBestMatch(itemDescription, stateTracker.getEffectNodeIds(), stateTracker.getRecentParentIds());
        if (result.isEmpty()) {
            operatorPrompts.showMessage("No Matches Found", "No matches found. Please create a new procedure.", null, INFO);
        }
        return result;
    }

    private void captureFailureContext(Procedure atomicStep, Procedure rootProcedure, List<UiTestStepResult> testStepResults,
                                       List<UiPreconditionResult> preconditionResults) {
        var preSelectedCategory = ErrorCategory.UNKNOWN;
        String preFilledSymptom = lastFailureMessage(atomicStep, testStepResults, preconditionResults);

        Optional<FailureContext> failureContext = uiTestAgentConfig.isFullyUnattended()
                ? Optional.of(new FailureContext(UUID.randomUUID(), preFilledSymptom, preSelectedCategory, "", 1, now(),
                FailureContext.Mode.UNATTENDED))
                : operatorPrompts.captureFailureContext(preSelectedCategory, preFilledSymptom);

        failureContext.ifPresent(context -> {
            Procedure targetProcedure = uiTestAgentConfig.isAtomicVerificationEnabled() ? atomicStep : rootProcedure;
            asyncExecutionPersistenceService.captureFailureContext(targetProcedure.id(), context);
        });
    }

    private static String lastFailureMessage(Procedure atomicStep, List<UiTestStepResult> testStepResults,
                                             List<UiPreconditionResult> preconditionResults) {
        return extractLastErrorMessage(testStepResults, preconditionResults,
                "Unknown failure in procedure %s".formatted(atomicStep.description()));
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
        record Found(Procedure procedure, Source source) implements UserFeedback {
        }

        record ManualTermination(String reason) implements UserFeedback {
        }
    }

    private sealed interface AtomicLoopOutcome {
        record Completed() implements AtomicLoopOutcome {
        }

        record Skipped() implements AtomicLoopOutcome {
        }

        record Terminate() implements AtomicLoopOutcome {
        }

        /**
         * The whole test step must be rerun from its first atomic step. A non-null {@code replacementProcedureId} names a
         * procedure created in the same dialog session to rerun instead of the current root.
         */
        record RerunTestStep(@Nullable UUID replacementProcedureId) implements AtomicLoopOutcome {
        }
    }

    /** How an item was resolved to a procedure, if it was, and whether the run goes on after it. */
    private record ItemResolution(ExecutionFlow flow, Source source, @Nullable UUID chosenProcedureId) {
    }

    private sealed interface ExecutionFlow {
        record Continue() implements ExecutionFlow {
        }

        record Stop() implements ExecutionFlow {
        }
    }
}
