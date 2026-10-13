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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.StepExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.ProcedureKnowledgeCollectionService;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.Kind;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Outcome;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Source;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository.UnsatisfiedPrerequisite;
import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.knowledge_graph.service.UiElementCache;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.knowledge_graph.UserDecisionOutcome;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.panel.ProcedureNode;
import org.tarik.ta.dto.panel.ProcedureOutcome;
import org.tarik.ta.dto.UserSelectionResult;
import org.tarik.ta.dto.UserSelectionResult.SelectionAction;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.panel.OperatorPrompts;
import org.tarik.ta.panel.RunObserver;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.dto.UiPreconditionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.exceptions.DatabaseConnectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.model.ContextEntry.StepType.PRECONDITION;
import static org.tarik.ta.core.model.ContextEntry.StepType.TEST_STEP;

@ExtendWith(MockitoExtension.class)
class KnowledgeBasedExecutionOrchestratorTest {

    @Mock
    private TestExecutionContext mockContext;
    @Mock
    private KnowledgeService mockKnowledgeService;
    @Mock
    private KnowledgeIngestionService mockIngestionService;
    @Mock
    private SatisfiesEdgeService mockSatisfiesEdgeService;
    @Mock
    private FailureContextService mockFailureContextService;
    @Mock
    private ProcedureUsageByTestCaseTrackingService mockProcedureUsageByTestCaseTrackingService;
    @Mock
    private StepExecutionOrchestrator mockStepExecutionOrchestrator;
    @Mock
    private ProcedureKnowledgeCollectionService mockProcedureKnowledgeCollectionService;
    @Mock
    private UiTestAgentConfig configMock;
    @Mock
    private UiElementCache mockUiElementCache;
    @Mock
    private UiElementRepository mockUiElementRepository;
    @Mock
    private ElementLocationHistoryLookup mockElementLocationHistoryLookup;
    @Mock
    private AsyncExecutionPersistenceService mockAsyncExecutionPersistenceService;
    @Mock
    private OperatorPrompts mockOperatorPrompts;
    @Mock
    private RunObserver mockRunObserver;
    @Mock
    private ExecutionRunService mockExecutionRunService;

    private KnowledgeBasedExecutionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(configMock.isFullyUnattended()).thenReturn(true);
        lenient().when(configMock.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        lenient().when(configMock.getAncestryWindowSize()).thenReturn(5);
        lenient().when(mockContext.getEventEmitter()).thenReturn(StreamingEventEmitter.NOOP);

        orchestrator = new KnowledgeBasedExecutionOrchestrator(
                mockKnowledgeService, mockIngestionService, mockStepExecutionOrchestrator,
                mockProcedureKnowledgeCollectionService, mockSatisfiesEdgeService,
                mockProcedureUsageByTestCaseTrackingService, mockFailureContextService,
                configMock, mockUiElementCache, mockUiElementRepository, mockElementLocationHistoryLookup,
                mockAsyncExecutionPersistenceService, mockOperatorPrompts, mockRunObserver, mockExecutionRunService);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void executeBasedOnKnowledge_shouldRecordAnErrorStepResultAndStop_whenNoMatchInUnattendedMode() {
        TestCase testCase = new TestCase("test", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results"),
                new TestStep("next action", List.of(), "results")));
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.empty());

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, new ExecutionStateTracker(configMock));
        }

        verify(mockContext).addStepResult(argThat(result -> result.getExecutionStatus() == TestStepResultStatus.ERROR
                && result.getErrorMessage().contains("No matching procedure found for 'action'")));
        verify(mockKnowledgeService).findBestMatch(eq("action"), any(), any());
        verify(mockKnowledgeService, never()).findBestMatch(eq("next action"), any(), any());
        verify(mockAsyncExecutionPersistenceService, never()).recordAnomaly(any(), any(), any());
        verify(mockAsyncExecutionPersistenceService, never()).mergeUsesProcedure(any(), any());
    }

    @Test
    void executeBasedOnKnowledge_shouldRecordTheFailedPreconditionAnomalyAndUsage_whenOnlyALowConfidenceMatchInUnattendedMode() {
        TestCase testCase = new TestCase("tc", List.of(new Precondition("logged in", List.of())), List.of(),
                List.of(new TestStep("action", List.of(), "results")));
        Procedure lowConfidenceProcedure = Procedure.createAtomic("other step", List.of(), "", List.of(), List.of(), false);
        when(mockKnowledgeService.findBestMatch(eq("logged in"), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(lowConfidenceProcedure, KnowledgeService.MatchConfidence.LOW,
                        List.of(new KnowledgeService.ScoredProcedure(lowConfidenceProcedure, 0, 0)), false, false, false)));

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, new ExecutionStateTracker(configMock));
        }

        verify(mockContext).addPreconditionResult(argThat(result -> !result.isSuccess()
                && result.getErrorMessage().equals("No high-confidence match found for the test precondition 'logged in'.")));
        verify(mockAsyncExecutionPersistenceService).recordAnomaly(eq(Kind.STEP_FAILED), eq(lowConfidenceProcedure.id()),
                contains("logged in"));
        verify(mockAsyncExecutionPersistenceService).mergeUsesProcedure("tc", lowConfidenceProcedure.id());
        verify(mockStepExecutionOrchestrator, never())
                .executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SATISFIES edges for current atomic are persisted before verification completes")
    void satisfiesEdgesPersistedBeforeVerification() {
        TestCase testCase = new TestCase("test", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure proc = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);
        
        var match = new KnowledgeService.MatchResult(proc, KnowledgeService.MatchConfidence.HIGH, 
                List.of(new KnowledgeService.ScoredProcedure(proc, 0, 0)), false, false, false);
                
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.findEffectsForProcedure(proc.id())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(proc.id())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(proc.id())).thenReturn(List.of());
        
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    // Verify that async persist was called BEFORE or DURING execution
                    verify(mockAsyncExecutionPersistenceService).persistSatisfiesEdges(proc.id());
                    return new UserDecisionOutcome.ContinueNextStep();
                });
        
        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);
        verify(mockAsyncExecutionPersistenceService).persistSatisfiesEdges(proc.id());
    }

    @Test
    @DisplayName("Live state tracker enables next-step selection before async SATISFIES write completes")
    void liveStateTrackerEnablesNextStepSelectionBeforeAsyncSatisfiesCompletes() {
        TestCase testCase = new TestCase("test", List.of(), List.of(), List.of(
            new TestStep("step1", List.of(), "res1"),
            new TestStep("step2", List.of(), "res2")
        ));
        
        Procedure proc1 = Procedure.createAtomic("step1", List.of(), "", List.of(), List.of(), false);
        Procedure proc2 = Procedure.createAtomic("step2", List.of(), "", List.of(), List.of(), false);
        
        var effectId = UUID.randomUUID();
        var effect = new org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding(effectId, "effect", new float[0], org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType.EFFECT);
        
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);
        
        var match1 = new KnowledgeService.MatchResult(proc1, KnowledgeService.MatchConfidence.HIGH, 
                List.of(new KnowledgeService.ScoredProcedure(proc1, 0, 0)), false, false, false);
        var match2 = new KnowledgeService.MatchResult(proc2, KnowledgeService.MatchConfidence.HIGH, 
                List.of(new KnowledgeService.ScoredProcedure(proc2, 0, 0)), false, false, false);
        
        when(mockKnowledgeService.findBestMatch("step1", Set.of(), Set.of())).thenReturn(Optional.of(match1));
        when(mockKnowledgeService.findBestMatch("step2", Set.of(effectId), Set.of(proc1.id()))).thenReturn(Optional.of(match2));
        
        when(mockKnowledgeService.findEffectsForProcedure(proc1.id())).thenReturn(List.of(effect));
        when(mockKnowledgeService.findEffectsForProcedure(proc2.id())).thenReturn(List.of());
        
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());

        doAnswer(inv -> {
            List<org.tarik.ta.dto.UiTestStepResult> testStepResults = inv.getArgument(4);
            testStepResults.add(new org.tarik.ta.dto.UiTestStepResult(new TestStep("", List.of(), ""), org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS, null, "", null, java.time.Instant.now(), java.time.Instant.now()));
            return new UserDecisionOutcome.ContinueNextStep();
        }).when(mockStepExecutionOrchestrator).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());

        // Delay persistence to simulate async
        doAnswer(inv -> {
            Thread.sleep(50);
            return null;
        }).when(mockAsyncExecutionPersistenceService).persistSatisfiesEdges(any());

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);
        
        // Next step was matched correctly (at least once, as prefetch might also trigger match)
        verify(mockKnowledgeService, atLeastOnce()).findBestMatch("step2", Set.of(effectId), Set.of(proc1.id()));
    }

    @Test
    void executeBasedOnKnowledge_shouldRemoveTheNegatedEffectsFromTheState_whenTheNegatingAtomicSucceeds() {
        TestCase testCase = new TestCase("test", List.of(), List.of(), List.of(
                new TestStep("open dialog", List.of(), "res1"),
                new TestStep("close dialog", List.of(), "res2"),
                new TestStep("submit form", List.of(), "res3")));
        var openDialog = Procedure.createAtomic("open dialog", List.of(), "", List.of(), List.of(), false);
        var closeDialog = Procedure.createAtomic("close dialog", List.of(), "", List.of(), List.of(), false)
                .withNegatedEffects(List.of("the dialog is open"));
        var submitForm = Procedure.createAtomic("submit form", List.of(), "", List.of(), List.of(), false);
        var dialogOpen = PhraseEmbedding.createEffect("the dialog is open", new float[0]);
        var formShown = PhraseEmbedding.createEffect("the form is shown", new float[0]);
        var stateTracker = new ExecutionStateTracker(configMock);
        when(mockKnowledgeService.findBestMatch(eq("open dialog"), any(), any())).thenReturn(Optional.of(highMatch(openDialog)));
        when(mockKnowledgeService.findBestMatch(eq("close dialog"), any(), any())).thenReturn(Optional.of(highMatch(closeDialog)));
        when(mockKnowledgeService.findBestMatch(eq("submit form"), any(), any())).thenReturn(Optional.of(highMatch(submitForm)));
        when(mockKnowledgeService.findEffectsForProcedure(openDialog.id())).thenReturn(List.of(dialogOpen));
        when(mockKnowledgeService.findEffectsForProcedure(closeDialog.id())).thenReturn(List.of(formShown));
        when(mockKnowledgeService.findEffectsForProcedure(submitForm.id())).thenReturn(List.of());
        when(mockSatisfiesEdgeService.findNegatedEffectNodeIds(any(), any()))
                .thenAnswer(inv -> closeDialog.equals(inv.getArgument(0)) ? Set.of(dialogOpen.id()) : Set.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        doAnswer(inv -> {
            List<UiTestStepResult> testStepResults = inv.getArgument(4);
            testStepResults.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "", null,
                    Instant.now(), Instant.now()));
            return new UserDecisionOutcome.ContinueNextStep();
        }).when(mockStepExecutionOrchestrator).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockSatisfiesEdgeService).findNegatedEffectNodeIds(closeDialog, Set.of(dialogOpen.id()));
        verify(mockKnowledgeService, atLeastOnce()).findBestMatch(eq("submit form"), eq(Set.of(formShown.id())), any());
        assertThat(stateTracker.getEffectNodeIds()).containsExactly(formShown.id());
    }

    private static KnowledgeService.MatchResult highMatch(Procedure procedure) {
        return new KnowledgeService.MatchResult(procedure, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(procedure, 0, 0)), false, false, false);
    }

    @Test
    @DisplayName("cleanupStaleUsesProcedure is not called when DatabaseConnectionException is thrown")
    void cleanupStaleUsesProcedure_notCalled_onDatabaseConnectionException() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any()))
                .thenThrow(new DatabaseConnectionException("connection lost", new RuntimeException()));

        assertThatThrownBy(() -> orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, mock(ExecutionStateTracker.class)))
                .isInstanceOf(DatabaseConnectionException.class);

        verify(mockProcedureUsageByTestCaseTrackingService, never()).cleanupStaleUsesProcedure(any(), any());
    }

    @Test
    @DisplayName("cleanupStaleUsesProcedure is called exactly once on successful execution")
    void cleanupStaleUsesProcedure_calledOnce_onSuccessfulExecution() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure proc = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        var match = new KnowledgeService.MatchResult(proc, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(proc, 0, 0)), false, false, false);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.findEffectsForProcedure(proc.id())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(proc.id())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(proc.id())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "", null,
                            java.time.Instant.now(), java.time.Instant.now()));
                    return new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockProcedureUsageByTestCaseTrackingService, times(1)).cleanupStaleUsesProcedure(eq("tc"), any());
    }

    @Test
    @DisplayName("Step-level verification still runs when atomic verification is off")
    void stepLevelVerificationRunsWhenAtomicVerificationIsOff() {
        TestCase testCase = new TestCase("test", List.of(), List.of(), List.of(new TestStep("action", List.of(), "test step expected")));
        Procedure proc1 = Procedure.createAtomic("atomic1", List.of(), "atomic1 exp", List.of(), List.of(), false);
        Procedure proc2 = Procedure.createAtomic("atomic2", List.of(), "atomic2 exp", List.of(), List.of(), false);
        Procedure composite = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);
        
        when(configMock.isAtomicVerificationEnabled()).thenReturn(false);
        
        var match = new KnowledgeService.MatchResult(composite, KnowledgeService.MatchConfidence.HIGH, 
                List.of(new KnowledgeService.ScoredProcedure(composite, 0, 0)), false, false, false);
                
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.resolveToAtomicSteps(composite.id())).thenReturn(List.of(proc1, proc2));
        when(mockKnowledgeService.getChildrenForExecution(composite.id())).thenReturn(List.of(proc1, proc2));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        
        // Capture contextFactory when executeAtomicStepWithRetryLoop is called
        doAnswer(inv -> {
            Procedure p = inv.getArgument(1);
            Function<Procedure, AtomicStepExecutionContext> factory = inv.getArgument(7);
            AtomicStepExecutionContext execCtx = factory.apply(p);
            
            if (p.description().equals("atomic1")) {
                // Not last step, atomic verification disabled -> effective expected results must be null
                assertThat(execCtx.effectiveExpectedResults()).isNull();
            } else if (p.description().equals("atomic2")) {
                // Last step -> step-level verification runs, effective expected results must combine atomic and test step
                assertThat(execCtx.effectiveExpectedResults()).contains("atomic2 exp").contains("test step expected");
            }
            List<org.tarik.ta.dto.UiTestStepResult> testStepResults = inv.getArgument(4);
            testStepResults.add(new org.tarik.ta.dto.UiTestStepResult(new TestStep("", List.of(), ""), org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS, null, "", null, java.time.Instant.now(), java.time.Instant.now()));
            return new UserDecisionOutcome.ContinueNextStep();
        }).when(mockStepExecutionOrchestrator).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);
        verify(mockStepExecutionOrchestrator, times(2)).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("The prefetched prerequisite check of the next atomic sees the effect of the composite closing after the current atomic")
    void executeBasedOnKnowledge_shouldExecuteNextAtomic_whenItsPrerequisiteIsTheEffectOfTheCompositeClosingBeforeIt() {
        var scroll = Procedure.createAtomic("Mouse scroll until specific element is visible", List.of(), "", List.of(), List.of(), false);
        var clickToggle = Procedure.createAtomic("Click on the toggle switch", List.of(), "", List.of(), List.of(), false);
        var scrollUntilVisible = Procedure.createComposite("Scroll until the toggle is visible", List.of(), "", List.of(), List.of(),
                false);
        var root = Procedure.createComposite("Activate wrapping the header text", List.of(), "", List.of(), List.of(), false);
        var toggleVisible = effect("The toggle is visible");
        stubHierarchy(root, List.of(scroll, clickToggle), Map.of(root.id(), List.of(scrollUntilVisible, clickToggle),
                scrollUntilVisible.id(), List.of(scroll)));
        stubEffects(Map.of(scroll.id(), List.of(effect("The mouse wheel is scrolled")), scrollUntilVisible.id(), List.of(toggleVisible)));
        when(mockSatisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any())).thenAnswer(invocation ->
                !clickToggle.id().equals(invocation.getArgument(0)) || invocation.<Set<UUID>>getArgument(1).contains(toggleVisible.id())
                        ? List.of()
                        : List.of(new UnsatisfiedPrerequisite("The toggle is visible", 0.0, null)));

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(root), 0, new ExecutionStateTracker(configMock));

        verify(mockStepExecutionOrchestrator)
                .executeAtomicStepWithRetryLoop(any(), eq(clickToggle), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("The prefetched prerequisite check of the next atomic sees only the outermost effect of the composites closing together")
    void executeBasedOnKnowledge_shouldCheckNextAtomicAgainstOutermostCompositeEffect_whenNestedCompositesCloseTogether() {
        var scroll = Procedure.createAtomic("Mouse scroll", List.of(), "", List.of(), List.of(), false);
        var clickToggle = Procedure.createAtomic("Click on the toggle switch", List.of(), "", List.of(), List.of(), false);
        var inner = Procedure.createComposite("Scroll the pane", List.of(), "", List.of(), List.of(), false);
        var outer = Procedure.createComposite("Scroll until the toggle is visible", List.of(), "", List.of(), List.of(), false);
        var root = Procedure.createComposite("Activate wrapping the header text", List.of(), "", List.of(), List.of(), false);
        var outerEffect = effect("The toggle is visible");
        stubHierarchy(root, List.of(scroll, clickToggle), Map.of(root.id(), List.of(outer, clickToggle), outer.id(), List.of(inner),
                inner.id(), List.of(scroll)));
        stubEffects(Map.of(scroll.id(), List.of(effect("The mouse wheel is scrolled")), inner.id(), List.of(effect("The pane is scrolled")),
                outer.id(), List.of(outerEffect)));

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(root), 0, new ExecutionStateTracker(configMock));

        verify(mockSatisfiesEdgeService).findUnsatisfiedPrerequisites(eq(clickToggle.id()), eq(Set.of(outerEffect.id())));
    }

    @Test
    @DisplayName("A composite's effects are read synchronously only once when their prefetch fails, although prediction and close use them")
    void executeBasedOnKnowledge_shouldReadCompositeEffectsSynchronouslyOnce_whenTheirPrefetchFails() {
        var scroll = Procedure.createAtomic("Mouse scroll", List.of(), "", List.of(), List.of(), false);
        var clickToggle = Procedure.createAtomic("Click on the toggle switch", List.of(), "", List.of(), List.of(), false);
        var composite = Procedure.createComposite("Scroll until the toggle is visible", List.of(), "", List.of(), List.of(), false);
        var root = Procedure.createComposite("Activate wrapping the header text", List.of(), "", List.of(), List.of(), false);
        stubHierarchy(root, List.of(scroll, clickToggle), Map.of(root.id(), List.of(composite, clickToggle),
                composite.id(), List.of(scroll)));
        stubEffects(Map.of());
        when(mockKnowledgeService.findEffectsForProcedure(composite.id()))
                .thenThrow(new IllegalStateException("graph unavailable"))
                .thenReturn(List.of(effect("The toggle is visible")));

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(root), 0, new ExecutionStateTracker(configMock));

        verify(mockKnowledgeService, times(2)).findEffectsForProcedure(composite.id());
        verify(mockStepExecutionOrchestrator)
                .executeAtomicStepWithRetryLoop(any(), eq(clickToggle), any(), any(), any(), any(), any(), any());
    }

    private void stubHierarchy(Procedure root, List<Procedure> atomicSteps, Map<UUID, List<Procedure>> childrenById) {
        stubHighConfidenceMatch(root.description(), root);
        when(mockKnowledgeService.resolveToAtomicSteps(root.id())).thenReturn(atomicSteps);
        when(mockKnowledgeService.getChildrenForExecution(any()))
                .thenAnswer(invocation -> childrenById.get(invocation.<UUID>getArgument(0)));
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    List<UiTestStepResult> results = invocation.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "", null,
                            Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.ContinueNextStep();
                });
    }

    private void stubEffects(Map<UUID, List<PhraseEmbedding>> effectsById) {
        when(mockKnowledgeService.findEffectsForProcedure(any()))
                .thenAnswer(invocation -> effectsById.getOrDefault(invocation.<UUID>getArgument(0), List.of()));
    }

    private static TestCase singleStepTestCase(Procedure root) {
        return new TestCase("tc", List.of(), List.of(), List.of(new TestStep(root.description(), List.of(), "results")));
    }

    @Test
    @DisplayName("Rerun Test Step refetches the root by ID and reruns from the first atomic step")
    void rerunTestStepShouldRefetchRootAndRerunFromFirstAtomic() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        Procedure atomic1 = Procedure.createAtomic("atomic1", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        var match = new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.resolveToAtomicSteps(root.id())).thenReturn(List.of(atomic1));
        when(mockKnowledgeService.getChildrenForExecution(root.id())).thenReturn(List.of(atomic1));
        when(mockKnowledgeService.findById(root.id())).thenReturn(Optional.of(root));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());

        var rerunCounter = new AtomicInteger();
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "",
                            null, Instant.now(), Instant.now()));
                    // The first attempt ends with a whole-step rerun request; the second succeeds
                    return rerunCounter.incrementAndGet() == 1
                            ? new UserDecisionOutcome.RerunTestStep(null)
                            : new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockKnowledgeService).findById(root.id());
        verify(mockStepExecutionOrchestrator, times(2)).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rerun Test Step restores the effects the failed attempt negated before rerunning")
    void rerunTestStepShouldRestoreTheEffectsNegatedByTheFailedAttempt() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(
                new TestStep("open dialog", List.of(), "res1"),
                new TestStep("close and submit", List.of(), "res2")));
        var openDialog = Procedure.createAtomic("open dialog", List.of(), "", List.of(), List.of(), false);
        var root = Procedure.createComposite("close and submit", List.of(), "", List.of(), List.of(), false);
        var closeDialog = Procedure.createAtomic("close dialog", List.of(), "", List.of(), List.of(), false);
        var submitForm = Procedure.createAtomic("submit form", List.of(), "", List.of(), List.of(), false);
        var dialogOpen = PhraseEmbedding.createEffect("the dialog is open", new float[0]);
        var stateTracker = new ExecutionStateTracker(configMock);
        when(mockKnowledgeService.findBestMatch(eq("open dialog"), any(), any())).thenReturn(Optional.of(highMatch(openDialog)));
        when(mockKnowledgeService.findBestMatch(eq("close and submit"), any(), any())).thenReturn(Optional.of(highMatch(root)));
        when(mockKnowledgeService.resolveToAtomicSteps(root.id())).thenReturn(List.of(closeDialog, submitForm));
        when(mockKnowledgeService.getChildrenForExecution(root.id())).thenReturn(List.of(closeDialog, submitForm));
        when(mockKnowledgeService.findById(root.id())).thenReturn(Optional.of(root));
        when(mockKnowledgeService.findEffectsForProcedure(any()))
                .thenAnswer(inv -> openDialog.id().equals(inv.getArgument(0)) ? List.of(dialogOpen) : List.of());
        when(mockSatisfiesEdgeService.findNegatedEffectNodeIds(any(), any())).thenAnswer(inv -> {
            Set<UUID> currentEffectIds = inv.getArgument(1);
            return closeDialog.equals(inv.getArgument(0)) && currentEffectIds.contains(dialogOpen.id()) ? Set.of(dialogOpen.id()) : Set.of();
        });
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        var submitAttempts = new AtomicInteger();
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "",
                            null, Instant.now(), Instant.now()));
                    // The first attempt of the second step fails on its last atomic step and requests a rerun of the whole step
                    return submitForm.equals(inv.getArgument(1)) && submitAttempts.incrementAndGet() == 1
                            ? new UserDecisionOutcome.RerunTestStep(null)
                            : new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockSatisfiesEdgeService, times(2)).findNegatedEffectNodeIds(closeDialog, Set.of(dialogOpen.id()));
        assertThat(stateTracker.getEffectNodeIds()).doesNotContain(dialogOpen.id());
        assertThat(stateTracker.getExecutedAtomicProcedures()).containsExactly(openDialog, closeDialog, submitForm);
    }

    @Test
    @DisplayName("Rerun Test Step uses the created procedure ID when present")
    void rerunTestStepShouldUseCreatedProcedureIdWhenPresent() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        Procedure oldAtomic = Procedure.createAtomic("old atomic", List.of(), "", List.of(), List.of(), false);
        Procedure createdRoot = Procedure.createComposite("created action", List.of(), "", List.of(), List.of(), false);
        Procedure createdAtomic = Procedure.createAtomic("created atomic", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        var match = new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.resolveToAtomicSteps(root.id())).thenReturn(List.of(oldAtomic));
        when(mockKnowledgeService.getChildrenForExecution(root.id())).thenReturn(List.of(oldAtomic));
        when(mockKnowledgeService.resolveToAtomicSteps(createdRoot.id())).thenReturn(List.of(createdAtomic));
        when(mockKnowledgeService.getChildrenForExecution(createdRoot.id())).thenReturn(List.of(createdAtomic));
        when(mockKnowledgeService.findById(createdRoot.id())).thenReturn(Optional.of(createdRoot));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());

        var executedProcedures = new ArrayList<Procedure>();
        var rerunCounter = new AtomicInteger();
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    executedProcedures.add(inv.getArgument(1));
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "",
                            null, Instant.now(), Instant.now()));
                    // The first attempt ends with a whole-step rerun request; the second succeeds
                    return rerunCounter.incrementAndGet() == 1
                            ? new UserDecisionOutcome.RerunTestStep(createdRoot.id())
                            : new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockKnowledgeService).findById(createdRoot.id());
        verify(mockKnowledgeService, never()).findById(root.id());
        assertThat(executedProcedures.getLast()).isEqualTo(createdAtomic);
        var order = inOrder(mockRunObserver);
        order.verify(mockRunObserver).itemOutlined(eq(List.of()), argThat(nodes -> nodes.getFirst().id().equals(root.id())));
        order.verify(mockRunObserver).itemOutlined(eq(List.of()), argThat(nodes -> nodes.stream().map(ProcedureNode::id).toList()
                .equals(List.of(createdRoot.id(), createdAtomic.id()))));
    }

    @Test
    @DisplayName("Rerun of a deleted procedure stops the execution with a recorded failure")
    void rerunTestStepShouldStopWithFailure_whenProcedureDeleted() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(configMock.isFullyUnattended()).thenReturn(false);
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.findById(root.id())).thenReturn(Optional.empty());
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());

        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.RerunTestStep(null));

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

            verify(mockOperatorPrompts).showMessage(eq("Procedure Not Found"), anyString(), any(), eq(PopupType.ERROR));
        }
        verify(mockContext).addStepResult(any());
    }

    @Test
    @DisplayName("A failed attempt's recorded executed atomics are rolled back before the rerun")
    void rerunTestStepShouldRollBackExecutedAtomicsOfFailedAttempt() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        Procedure atomic1 = Procedure.createAtomic("atomic1", List.of(), "", List.of(), List.of(), false);
        Procedure atomic2 = Procedure.createAtomic("atomic2", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        var match = new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.resolveToAtomicSteps(root.id())).thenReturn(List.of(atomic1, atomic2));
        when(mockKnowledgeService.getChildrenForExecution(root.id())).thenReturn(List.of(atomic1, atomic2));
        when(mockKnowledgeService.findById(root.id())).thenReturn(Optional.of(root));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());

        var executedAtomicsPerCall = new ArrayList<List<Procedure>>();
        var callCounter = new AtomicInteger();
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    executedAtomicsPerCall.add(stateTracker.getExecutedAtomicProcedures());
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "",
                            null, Instant.now(), Instant.now()));
                    // atomic2 of the first attempt fails the step; the whole second attempt succeeds
                    return callCounter.incrementAndGet() == 2
                            ? new UserDecisionOutcome.RerunTestStep(null)
                            : new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        // 4 executions: attempt 1 (atomic1, atomic2), attempt 2 after the rollback (atomic1, atomic2)
        assertThat(executedAtomicsPerCall).hasSize(4);
        assertThat(executedAtomicsPerCall.get(0)).isEmpty();
        assertThat(executedAtomicsPerCall.get(1)).containsExactly(atomic1);
        assertThat(executedAtomicsPerCall.get(2)).isEmpty();
        assertThat(executedAtomicsPerCall.get(3)).containsExactly(atomic1);
    }

    @Test
    @DisplayName("Decomposition failure with a Rerun Test Step decision re-decomposes the refetched root and reruns")
    void decompositionFailureShouldRerunRefetchedRoot_whenDialogReturnsRerunTestStep() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        Procedure atomic1 = Procedure.createAtomic("atomic1", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        var match = new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.resolveToAtomicSteps(root.id()))
                .thenThrow(new IllegalStateException("Composite procedure has no child steps to execute"))
                .thenReturn(List.of(atomic1));
        when(mockKnowledgeService.getChildrenForExecution(root.id())).thenReturn(List.of(atomic1));
        when(mockKnowledgeService.findById(root.id())).thenReturn(Optional.of(root));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());

        when(mockStepExecutionOrchestrator.handleDecompositionFailureInSupervisedMode(anyString(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.RerunTestStep(null));
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "",
                            null, Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockStepExecutionOrchestrator).handleDecompositionFailureInSupervisedMode(anyString(), any(), any(), any());
        verify(mockKnowledgeService, times(2)).resolveToAtomicSteps(root.id());
        verify(mockStepExecutionOrchestrator, times(1))
                .executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
        // The rerun attempt's success is recorded — the item is not silently skipped
        verify(mockContext).addStepResult(any());
    }

    @Test
    @DisplayName("Decomposition failure in unattended mode rethrows the exception")
    void decompositionFailureShouldRethrow_whenUnattended() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.resolveToAtomicSteps(root.id()))
                .thenThrow(new IllegalStateException("Composite procedure has no child steps to execute"));

        assertThatThrownBy(() -> orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker))
                .isInstanceOf(IllegalStateException.class);
        verify(mockStepExecutionOrchestrator, never())
                .executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Decomposition failure termination records the failure and stops the execution")
    void decompositionFailureShouldRecordFailureAndStop_whenUserTerminates() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.resolveToAtomicSteps(root.id()))
                .thenThrow(new IllegalStateException("Composite procedure has no child steps to execute"));
        when(mockStepExecutionOrchestrator.handleDecompositionFailureInSupervisedMode(anyString(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.TerminateExecution());

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);
        }
        verify(mockContext).addStepResult(any());
        verify(mockStepExecutionOrchestrator, never())
                .executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rerun of a deleted root after a decomposition failure stops with a recorded failure")
    void decompositionFailureShouldStopWithFailure_whenRerunRootDeleted() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.resolveToAtomicSteps(root.id()))
                .thenThrow(new IllegalStateException("Composite procedure has no child steps to execute"));
        when(mockStepExecutionOrchestrator.handleDecompositionFailureInSupervisedMode(anyString(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.RerunTestStep(null));
        when(mockKnowledgeService.findById(root.id())).thenReturn(Optional.empty());

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);
            verify(mockOperatorPrompts).showMessage(eq("Procedure Not Found"), anyString(), any(), eq(PopupType.ERROR));
        }
        verify(mockContext).addStepResult(any());
        verify(mockStepExecutionOrchestrator, never())
                .executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A continue outcome on a decomposition failure skips the item without a recorded result")
    void decompositionFailureShouldSkipItem_whenUserContinues() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.resolveToAtomicSteps(root.id()))
                .thenThrow(new IllegalStateException("Composite procedure has no child steps to execute"));
        when(mockStepExecutionOrchestrator.handleDecompositionFailureInSupervisedMode(anyString(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.ContinueNextStep());

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockContext, never()).addStepResult(any());
        verify(mockStepExecutionOrchestrator, never())
                .executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A terminated atomic failure captures the failure context and stops with the merged result")
    void atomicExecutionShouldCaptureFailureContextAndStop_whenUserTerminates() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.FAILURE, "step failed",
                            null, null, Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.TerminateExecution();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockAsyncExecutionPersistenceService).captureFailureContext(eq(root.id()), any());
        verify(mockAsyncExecutionPersistenceService).bindTestCase("tc");
        verify(mockAsyncExecutionPersistenceService).recordAnomaly(Kind.STEP_FAILED, root.id(), "step failed");
        verify(mockContext).addStepResult(any());
    }

    @Test
    @DisplayName("Every precondition and test step starts its step in the context once, however many atomic steps it has")
    void executeBasedOnKnowledge_shouldStartEveryItemInTheContextOnce() {
        TestCase testCase = new TestCase("tc", List.of(new Precondition("Log in", List.of("user=admin"))), List.of(),
                List.of(new TestStep("Open the dashboard", List.of("Dashboard name is 'Sales'"), "results")));
        Procedure login = Procedure.createAtomic("Log in", List.of(), "", List.of(), List.of(), false);
        Procedure openDashboard = Procedure.createComposite("Open the dashboard", List.of(), "", List.of(), List.of(), false);
        Procedure clickSearch = Procedure.createAtomic("Click the search field", List.of(), "", List.of(), List.of(), false);
        Procedure selectResult = Procedure.createAtomic("Select the search result", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(eq("Log in"), any(), any())).thenReturn(Optional.of(highConfidenceMatch(login)));
        when(mockKnowledgeService.findBestMatch(eq("Open the dashboard"), any(), any()))
                .thenReturn(Optional.of(highConfidenceMatch(openDashboard)));
        when(mockKnowledgeService.resolveToAtomicSteps(openDashboard.id())).thenReturn(List.of(clickSearch, selectResult));
        when(mockKnowledgeService.getChildrenForExecution(openDashboard.id())).thenReturn(List.of(clickSearch, selectResult));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    if (inv.getArgument(0) instanceof ExecutionItem.PreconditionItem) {
                        List<UiPreconditionResult> results = inv.getArgument(5);
                        results.add(new UiPreconditionResult("Log in", true, null, null, Instant.now(), Instant.now()));
                    } else {
                        List<UiTestStepResult> results = inv.getArgument(4);
                        results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "",
                                null, Instant.now(), Instant.now()));
                    }
                    return new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        var inOrder = inOrder(mockContext);
        inOrder.verify(mockContext).startStep(PRECONDITION, "Log in", List.of("user=admin"));
        inOrder.verify(mockContext).startStep(TEST_STEP, "Open the dashboard", List.of("Dashboard name is 'Sales'"));
        verify(mockContext, times(2)).startStep(any(), any(), any());
    }

    private static KnowledgeService.MatchResult highConfidenceMatch(Procedure procedure) {
        return new KnowledgeService.MatchResult(procedure, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(procedure, 0, 0)), false, false, false);
    }

    @Test
    @DisplayName("A precondition's atomic step gets no expected results, even when its procedure stores relevant data")
    void preconditionAtomicStepShouldHaveNoExpectedResults() {
        TestCase testCase = new TestCase("tc", List.of(new Precondition("precondition", List.of())), List.of(), List.of());
        Procedure root = Procedure.createAtomic("precondition", List.of(), "user=admin", List.of(), List.of(), true);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);
        var executionContexts = new ArrayList<AtomicStepExecutionContext>();

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Function<Procedure, AtomicStepExecutionContext> contextFactory = inv.getArgument(7);
                    executionContexts.add(contextFactory.apply(inv.getArgument(1)));
                    List<UiPreconditionResult> results = inv.getArgument(5);
                    results.add(new UiPreconditionResult("precondition", true, null, null, Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        assertThat(executionContexts).singleElement()
                .satisfies(executionContext -> assertThat(executionContext.effectiveExpectedResults()).isNull());
    }

    @Test
    @DisplayName("A terminated atomic precondition failure stops with the merged precondition result")
    void atomicPreconditionShouldStopWithMergedResult_whenUserTerminates() {
        TestCase testCase = new TestCase("tc", List.of(new Precondition("precondition", List.of())), List.of(), List.of());
        Procedure root = Procedure.createAtomic("precondition", List.of(), "", List.of(), List.of(), true);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiPreconditionResult> results = inv.getArgument(5);
                    results.add(new UiPreconditionResult("precondition", false, "precondition failed", null,
                            Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.TerminateExecution();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockContext).addPreconditionResult(any());
    }

    @Test
    @DisplayName("A failed precondition is merged, recorded and stops the execution")
    void preconditionShouldMergeResultAndStop_whenExecutionFailed() {
        TestCase testCase = new TestCase("tc", List.of(new Precondition("precondition", List.of())), List.of(), List.of());
        Procedure root = Procedure.createAtomic("precondition", List.of(), "", List.of(), List.of(), true);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiPreconditionResult> results = inv.getArgument(5);
                    results.add(new UiPreconditionResult("precondition", false, "precondition failed", null,
                            Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockContext).addPreconditionResult(any());
    }

    @Test
    @DisplayName("An optional atomic with unsatisfied prerequisites is skipped")
    void optionalAtomicShouldBeSkipped_whenPrerequisitesUnsatisfied() {
        // The unattended-only prefetch coordinator would compute the prerequisites itself; supervised mode
        // keeps the check on the synchronous satisfiesEdgeService path under test
        when(configMock.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = optionalAtomic("action");
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockSatisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any()))
                .thenReturn(List.of(new UnsatisfiedPrerequisite("missing effect", 0.1, "effect phrase")));

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockStepExecutionOrchestrator, never())
                .executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
        verify(mockContext).addStepResult(any());
    }

    @Test
    @DisplayName("A mandatory atomic with unsatisfied prerequisites terminates the execution")
    void mandatoryAtomicShouldTerminate_whenPrerequisitesUnsatisfied() {
        // The unattended-only prefetch coordinator would compute the prerequisites itself; supervised mode
        // keeps the check on the synchronous satisfiesEdgeService path under test
        when(configMock.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockSatisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any()))
                .thenReturn(List.of(new UnsatisfiedPrerequisite("missing effect", 0.1, "effect phrase")));

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);
        }

        verify(mockContext).addStepResult(any());
        verify(mockAsyncExecutionPersistenceService).recordAnomaly(eq(Kind.STEP_FAILED), eq(root.id()), contains("missing effect"));
        verify(mockAsyncExecutionPersistenceService).mergeUsesProcedure("tc", root.id());
    }

    @Test
    @DisplayName("Unsatisfied prerequisites in supervised mode ask for the next action and terminate when the user chooses so")
    void unsatisfiedPrerequisitesShouldAskForNextActionAndTerminate_whenUserTerminates() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockSatisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any()))
                .thenReturn(List.of(new UnsatisfiedPrerequisite("missing effect", 0.1, "effect phrase")));
        when(mockStepExecutionOrchestrator.handleUnsatisfiedPrerequisitesInSupervisedMode(anyString(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.TerminateExecution());

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);
            verify(mockOperatorPrompts).showMessage(eq("Prerequisites Not Satisfied"), anyString(), any(), eq(PopupType.WARNING));
        }

        verify(mockStepExecutionOrchestrator).handleUnsatisfiedPrerequisitesInSupervisedMode(anyString(), eq(root), any(), any());
        verify(mockStepExecutionOrchestrator, never())
                .executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
        verify(mockContext).addStepResult(argThat(result -> result.getExecutionStatus() == TestStepResultStatus.FAILURE));
    }

    @Test
    @DisplayName("An atomic rerun after unsatisfied prerequisites re-checks the refetched procedure and executes it once satisfied")
    void unsatisfiedPrerequisitesShouldRecheckRefetchedProcedure_whenUserRerunsAtomic() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockSatisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any()))
                .thenReturn(List.of(new UnsatisfiedPrerequisite("missing effect", 0.1, "effect phrase")))
                .thenReturn(List.of());
        when(mockStepExecutionOrchestrator.handleUnsatisfiedPrerequisitesInSupervisedMode(anyString(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.RerunAtomicProcedure(root));
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "",
                            null, Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockSatisfiesEdgeService, times(2)).findUnsatisfiedPrerequisites(eq(root.id()), any());
        verify(mockStepExecutionOrchestrator)
                .executeAtomicStepWithRetryLoop(any(), eq(root), any(), any(), any(), any(), any(), any());
        verify(mockContext).addStepResult(argThat(result -> result.getExecutionStatus() == TestStepResultStatus.SUCCESS));
    }

    @Test
    @DisplayName("A test step rerun after unsatisfied prerequisites refetches the root and executes it once satisfied")
    void unsatisfiedPrerequisitesShouldRerunTestStep_whenUserRerunsTestStep() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure root = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(root, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(root, 0, 0)), false, false, false)));
        when(mockKnowledgeService.findById(root.id())).thenReturn(Optional.of(root));
        when(mockSatisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any()))
                .thenReturn(List.of(new UnsatisfiedPrerequisite("missing effect", 0.1, "effect phrase")))
                .thenReturn(List.of());
        when(mockStepExecutionOrchestrator.handleUnsatisfiedPrerequisitesInSupervisedMode(anyString(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.RerunTestStep(null));
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "",
                            null, Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockKnowledgeService).findById(root.id());
        verify(mockStepExecutionOrchestrator)
                .executeAtomicStepWithRetryLoop(any(), eq(root), any(), any(), any(), any(), any(), any());
        // Only the successful rerun is recorded, the failed attempt is discarded
        verify(mockContext).addStepResult(argThat(result -> result.getExecutionStatus() == TestStepResultStatus.SUCCESS));
    }

    @Test
    @DisplayName("A root replaced in a test step rerun is left out of the procedures the passing run keeps linked")
    void replacedRootShouldNotBeKeptAsUsed_whenTheRerunWithTheReplacementPasses() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure replacedRoot = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        Procedure replacement = Procedure.createAtomic("action, the right way", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);

        stubHighConfidenceMatch("action", replacedRoot);
        when(mockKnowledgeService.findById(replacement.id())).thenReturn(Optional.of(replacement));
        when(mockSatisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any()))
                .thenReturn(List.of(new UnsatisfiedPrerequisite("missing effect", 0.1, "effect phrase")))
                .thenReturn(List.of());
        when(mockStepExecutionOrchestrator.handleUnsatisfiedPrerequisitesInSupervisedMode(anyString(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.RerunTestStep(replacement.id()));
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<UiTestStepResult> results = inv.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "",
                            null, Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);

        verify(mockAsyncExecutionPersistenceService).mergeUsesProcedure("tc", replacedRoot.id());
        verify(mockAsyncExecutionPersistenceService).mergeUsesProcedure("tc", replacement.id());
        verify(mockProcedureUsageByTestCaseTrackingService).cleanupStaleUsesProcedure("tc", List.of(replacement.id()));
    }

    @Test
    @DisplayName("Starting from a later step seeds the effects of the skipped precondition and test steps")
    void executeBasedOnKnowledge_shouldSeedEffectsOfSkippedItems_whenStartingFromLaterStep() {
        when(configMock.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        TestCase testCase = new TestCase("tc", List.of(new Precondition("precondition", List.of())), List.of(), List.of(
                new TestStep("skipped", List.of(), "res0"), new TestStep("selected", List.of(), "res1")));
        Procedure preconditionProcedure = Procedure.createAtomic("precondition", List.of(), "", List.of(), List.of(), false);
        Procedure skippedProcedure = Procedure.createAtomic("skipped", List.of(), "", List.of(), List.of(), false);
        Procedure selectedProcedure = Procedure.createAtomic("selected", List.of(), "", List.of(), List.of(), false);
        var preconditionEffect = effect("dashboard is opened");
        var skippedEffect = effect("time range picker is visible");
        stubHighConfidenceMatch("precondition", preconditionProcedure);
        stubHighConfidenceMatch("skipped", skippedProcedure);
        stubHighConfidenceMatch("selected", selectedProcedure);
        when(mockKnowledgeService.findEffectsForProcedure(preconditionProcedure.id())).thenReturn(List.of(preconditionEffect));
        when(mockKnowledgeService.findEffectsForProcedure(skippedProcedure.id())).thenReturn(List.of(skippedEffect));
        when(mockKnowledgeService.findEffectsForProcedure(selectedProcedure.id())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.ContinueNextStep());

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 1, new ExecutionStateTracker(configMock));

        verify(mockSatisfiesEdgeService).findUnsatisfiedPrerequisites(selectedProcedure.id(),
                Set.of(preconditionEffect.id(), skippedEffect.id()));
        verify(mockStepExecutionOrchestrator).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A skipped item without a high-confidence match seeds no effects and the selected step still runs")
    void executeBasedOnKnowledge_shouldSeedNoEffects_whenSkippedItemHasNoHighConfidenceMatch() {
        when(configMock.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        TestCase testCase = new TestCase("tc", List.of(new Precondition("unknown precondition", List.of())), List.of(), List.of(
                new TestStep("vague step", List.of(), "res0"), new TestStep("selected", List.of(), "res1")));
        Procedure lowConfidenceProcedure = Procedure.createAtomic("other step", List.of(), "", List.of(), List.of(), false);
        Procedure selectedProcedure = Procedure.createAtomic("selected", List.of(), "", List.of(), List.of(), false);
        when(mockKnowledgeService.findBestMatch(eq("unknown precondition"), any(), any())).thenReturn(Optional.empty());
        when(mockKnowledgeService.findBestMatch(eq("vague step"), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(lowConfidenceProcedure, KnowledgeService.MatchConfidence.LOW,
                        List.of(new KnowledgeService.ScoredProcedure(lowConfidenceProcedure, 0, 0)), false, false, false)));
        stubHighConfidenceMatch("selected", selectedProcedure);
        when(mockKnowledgeService.findEffectsForProcedure(selectedProcedure.id())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UserDecisionOutcome.ContinueNextStep());

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 1, new ExecutionStateTracker(configMock));

        verify(mockKnowledgeService, never()).findEffectsForProcedure(lowConfidenceProcedure.id());
        verify(mockSatisfiesEdgeService).findUnsatisfiedPrerequisites(selectedProcedure.id(), Set.of());
        verify(mockStepExecutionOrchestrator).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void atomicFailureInSupervisedMode_shouldPersistNoFailureContext_whenTheOperatorDismissesIt() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        Procedure root = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        stubTerminatedAtomicFailure(root, List.of(root));
        when(mockOperatorPrompts.captureFailureContext(ErrorCategory.UNKNOWN, "step failed")).thenReturn(Optional.empty());

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(root), 0, new ExecutionStateTracker(configMock));

        verify(mockOperatorPrompts).captureFailureContext(ErrorCategory.UNKNOWN, "step failed");
        verify(mockAsyncExecutionPersistenceService, never()).captureFailureContext(any(), any());
    }

    @Test
    void atomicFailureInSupervisedMode_shouldPersistTheOperatorsFailureContextForTheAtomic_whenAtomicVerificationIsOn() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        when(configMock.isAtomicVerificationEnabled()).thenReturn(true);
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        Procedure atomic = Procedure.createAtomic("click save", List.of(), "", List.of(), List.of(), false);
        stubTerminatedAtomicFailure(root, List.of(atomic));
        var failureContext = new FailureContext(UUID.randomUUID(), "step failed", ErrorCategory.UNKNOWN, "wait for the dialog", 1,
                Instant.now(), FailureContext.Mode.SUPERVISED);
        when(mockOperatorPrompts.captureFailureContext(ErrorCategory.UNKNOWN, "step failed")).thenReturn(Optional.of(failureContext));

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(root), 0, new ExecutionStateTracker(configMock));

        verify(mockAsyncExecutionPersistenceService).captureFailureContext(atomic.id(), failureContext);
    }

    @Test
    void unmatchedItemInSupervisedMode_shouldAskTheOperatorToChooseAProcedureAndStop_whenTheOperatorTerminates() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.empty());
        when(mockOperatorPrompts.chooseProcedure(anyString(), anyString(), any(), any(), any(), anyBoolean())).thenReturn(Optional.empty());

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, new ExecutionStateTracker(configMock));
        }

        verify(mockOperatorPrompts).chooseProcedure(eq("No matching procedure found for 'action'. Please create a new one."), eq("action"),
                eq(mockKnowledgeService), eq(Set.of()), eq(Set.of()), eq(false));
        verify(mockStepExecutionOrchestrator, never()).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(),
                any());
    }

    @Test
    void unmatchedItemInSupervisedMode_shouldTellTheOperatorAndAskAgain_whenARetryStillFindsNoMatch() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.empty());
        when(mockOperatorPrompts.chooseProcedure(anyString(), anyString(), any(), any(), any(), anyBoolean()))
                .thenReturn(Optional.of(new UserSelectionResult(SelectionAction.RETRY, null, null)), Optional.empty());

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, new ExecutionStateTracker(configMock));
        }

        verify(mockOperatorPrompts).showMessage("No Matches Found", "No matches found. Please create a new procedure.", null, PopupType.INFO);
        verify(mockOperatorPrompts, times(2)).chooseProcedure(anyString(), anyString(), any(), any(), any(), anyBoolean());
    }

    private void stubTerminatedAtomicFailure(Procedure root, List<Procedure> atomicSteps) {
        stubHighConfidenceMatch(root.description(), root);
        if (!root.isAtomic()) {
            when(mockKnowledgeService.resolveToAtomicSteps(root.id())).thenReturn(atomicSteps);
            when(mockKnowledgeService.getChildrenForExecution(root.id())).thenReturn(atomicSteps);
        }
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    List<UiTestStepResult> results = invocation.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.FAILURE, "step failed",
                            null, null, Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.TerminateExecution();
                });
    }

    private void stubHighConfidenceMatch(String description, Procedure procedure) {
        when(mockKnowledgeService.findBestMatch(eq(description), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(procedure, KnowledgeService.MatchConfidence.HIGH,
                        List.of(new KnowledgeService.ScoredProcedure(procedure, 0, 0)), false, false, false)));
    }

    private static PhraseEmbedding effect(String phrase) {
        return new PhraseEmbedding(UUID.randomUUID(), phrase, new float[0], PhraseEmbedding.PhraseType.EFFECT);
    }

    private static Procedure optionalAtomic(String description) {
        var template = Procedure.createAtomic(description, List.of(), "", List.of(), List.of(), false);
        return new Procedure(template.id(), template.description(), template.testData(), template.expectedResults(),
                true, false, true, template.prerequisites(), template.effects(), template.negatedEffects(), template.additionalInfo(),
                template.createdAt(), template.updatedAt(), null, null);
    }

    @Test
    @DisplayName("Every procedure node of a run is reported started and finished, the children nested inside their composites")
    void executeBasedOnKnowledge_shouldReportEveryProcedureStartedAndFinished_inNestingOrder() {
        var scroll = Procedure.createAtomic("Mouse scroll", List.of(), "", List.of(), List.of(), false);
        var clickToggle = Procedure.createAtomic("Click on the toggle switch", List.of(), "", List.of(), List.of(), false);
        var inner = Procedure.createComposite("Scroll the pane", List.of(), "", List.of(), List.of(), false);
        var root = Procedure.createComposite("Activate wrapping the header text", List.of(), "", List.of(), List.of(), false);
        stubHierarchy(root, List.of(scroll, clickToggle), Map.of(root.id(), List.of(inner, clickToggle), inner.id(), List.of(scroll)));

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(root), 0, new ExecutionStateTracker(configMock));

        var order = inOrder(mockRunObserver);
        order.verify(mockRunObserver).itemOutlined(List.of(), List.of(
                ProcedureNode.pending(root.id(), root.description(), "", false, false, 0),
                ProcedureNode.pending(inner.id(), inner.description(), "", false, false, 1),
                ProcedureNode.pending(scroll.id(), scroll.description(), "", true, false, 2),
                ProcedureNode.pending(clickToggle.id(), clickToggle.description(), "", true, false, 1)));
        order.verify(mockRunObserver).procedureStarted(root.id());
        order.verify(mockRunObserver).procedureStarted(inner.id());
        order.verify(mockRunObserver).procedureStarted(scroll.id());
        order.verify(mockRunObserver).procedureFinished(scroll.id(), ProcedureOutcome.COMPLETED);
        order.verify(mockRunObserver).procedureFinished(inner.id(), ProcedureOutcome.COMPLETED);
        order.verify(mockRunObserver).procedureStarted(clickToggle.id());
        order.verify(mockRunObserver).procedureFinished(clickToggle.id(), ProcedureOutcome.COMPLETED);
        order.verify(mockRunObserver).procedureFinished(root.id(), ProcedureOutcome.COMPLETED);
    }

    @Test
    @DisplayName("A skipped optional atomic is reported as skipped")
    void executeBasedOnKnowledge_shouldReportASkippedProcedure_whenAnOptionalAtomicIsSkipped() {
        when(configMock.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        Procedure root = optionalAtomic("action");
        stubHighConfidenceMatch("action", root);
        when(mockSatisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any()))
                .thenReturn(List.of(new UnsatisfiedPrerequisite("missing effect", 0.1, "effect phrase")));

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(root), 0, new ExecutionStateTracker(configMock));

        verify(mockRunObserver).procedureFinished(root.id(), ProcedureOutcome.SKIPPED);
    }

    @Test
    @DisplayName("A terminated atomic and its composite are reported as failed")
    void executeBasedOnKnowledge_shouldReportFailedProcedures_whenTheRunIsTerminated() {
        Procedure atomic = Procedure.createAtomic("Click Save", List.of(), "", List.of(), List.of(), false);
        Procedure root = Procedure.createComposite("Save the form", List.of(), "", List.of(), List.of(), false);
        stubTerminatedAtomicFailure(root, List.of(atomic));

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(root), 0, new ExecutionStateTracker(configMock));

        verify(mockRunObserver).procedureFinished(atomic.id(), ProcedureOutcome.FAILED);
        verify(mockRunObserver).procedureFinished(root.id(), ProcedureOutcome.FAILED);
    }

    @Test
    @DisplayName("An attempt given up for a whole-step rerun is reported as abandoned, the rerun as completed")
    void executeBasedOnKnowledge_shouldReportAbandonedProcedures_whenTheTestStepIsRerun() {
        Procedure root = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        Procedure atomic = Procedure.createAtomic("atomic1", List.of(), "", List.of(), List.of(), false);
        stubHighConfidenceMatch(root.description(), root);
        when(mockKnowledgeService.resolveToAtomicSteps(root.id())).thenReturn(List.of(atomic));
        when(mockKnowledgeService.getChildrenForExecution(root.id())).thenReturn(List.of(atomic));
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockKnowledgeService.findById(root.id())).thenReturn(Optional.of(root));
        var attempts = new AtomicInteger();
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    List<UiTestStepResult> results = invocation.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "", null,
                            Instant.now(), Instant.now()));
                    return attempts.incrementAndGet() == 1 ? new UserDecisionOutcome.RerunTestStep(null)
                            : new UserDecisionOutcome.ContinueNextStep();
                });

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(root), 0, new ExecutionStateTracker(configMock));

        var order = inOrder(mockRunObserver);
        order.verify(mockRunObserver).procedureFinished(atomic.id(), ProcedureOutcome.ABANDONED);
        order.verify(mockRunObserver).procedureFinished(root.id(), ProcedureOutcome.ABANDONED);
        order.verify(mockRunObserver).procedureFinished(atomic.id(), ProcedureOutcome.COMPLETED);
        order.verify(mockRunObserver).procedureFinished(root.id(), ProcedureOutcome.COMPLETED);
    }

    @Test
    @DisplayName("Each item matched by the knowledge graph, directly or through the prefetch, is recorded with its match and outcome")
    void executeBasedOnKnowledge_shouldRecordAKnowledgeResolutionWithItsMatchAndOutcome_forEveryItem() {
        Procedure first = Procedure.createAtomic("Open the form", List.of(), "", List.of(), List.of(), false);
        Procedure second = Procedure.createAtomic("Click Save", List.of(), "", List.of(), List.of(), false);
        var firstMatch = highConfidenceMatch(first);
        var secondMatch = highConfidenceMatch(second);
        when(mockKnowledgeService.findBestMatch(eq("Open the form"), any(), any())).thenReturn(Optional.of(firstMatch));
        when(mockKnowledgeService.findBestMatch(eq("Click Save"), any(), any())).thenReturn(Optional.of(secondMatch));
        stubSucceedingAtomics();
        var succeeded = new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "", null,
                Instant.now(), Instant.now());
        when(mockContext.getTestStepExecutionHistory())
                .thenReturn(List.of(), List.of(succeeded), List.of(succeeded), List.of(succeeded), List.of(succeeded, succeeded));
        var recorded = new StepResolution(UUID.randomUUID(), 0, TEST_STEP, "Open the form", Source.KNOWLEDGE, null, List.of(), false,
                false, first.id(), Outcome.SUCCEEDED, 0);
        when(mockExecutionRunService.recordResolution(any(), any(), any(), any(), any(), anyLong())).thenReturn(recorded);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("Open the form", List.of(), ""),
                new TestStep("Click Save", List.of(), "")));

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, new ExecutionStateTracker(configMock));

        verify(mockExecutionRunService).recordResolution(argThat(item -> item.getDescription().equals("Open the form")),
                eq(Source.KNOWLEDGE), eq(firstMatch), eq(first.id()), eq(Outcome.SUCCEEDED), anyLong());
        verify(mockExecutionRunService).recordResolution(argThat(item -> item.getDescription().equals("Click Save")),
                eq(Source.KNOWLEDGE), eq(secondMatch), eq(second.id()), eq(Outcome.SUCCEEDED), anyLong());
        verify(mockRunObserver, times(2)).stepResolved(recorded);
    }

    @Test
    @DisplayName("An item without an executable procedure in unattended mode is recorded as unresolved with the candidates of its match")
    void executeBasedOnKnowledge_shouldRecordAnUnresolvedFailedItem_whenUnattendedSelectionFails() {
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure lowConfidenceProcedure = Procedure.createAtomic("other step", List.of(), "", List.of(), List.of(), false);
        var match = new KnowledgeService.MatchResult(lowConfidenceProcedure, KnowledgeService.MatchConfidence.LOW,
                List.of(new KnowledgeService.ScoredProcedure(lowConfidenceProcedure, 0, 0)), false, false, false);
        when(mockKnowledgeService.findBestMatch(eq("action"), any(), any())).thenReturn(Optional.of(match));
        var failed = new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.ERROR, "none", null, null,
                Instant.now(), Instant.now());
        when(mockContext.getTestStepExecutionHistory()).thenReturn(List.of(), List.of(failed));

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, new ExecutionStateTracker(configMock));
        }

        verify(mockExecutionRunService).recordResolution(any(), eq(Source.UNRESOLVED), eq(match), isNull(), eq(Outcome.FAILED), anyLong());
    }

    @Test
    @DisplayName("An item the operator terminates without choosing a procedure is recorded as unresolved")
    void executeBasedOnKnowledge_shouldRecordAnUnresolvedItem_whenTheOperatorTerminates() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("tc", List.of(), List.of(), List.of(new TestStep("action", List.of(), "results")));
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.empty());
        when(mockOperatorPrompts.chooseProcedure(anyString(), anyString(), any(), any(), any(), anyBoolean())).thenReturn(Optional.empty());

        try (var utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, new ExecutionStateTracker(configMock));
        }

        verify(mockExecutionRunService).recordResolution(any(), eq(Source.UNRESOLVED), isNull(), isNull(), any(), anyLong());
    }

    @Test
    @DisplayName("A procedure found after the operator's retry is recorded as selected by the operator")
    void executeBasedOnKnowledge_shouldRecordAnOperatorSelectedResolution_whenARetryFindsTheProcedure() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        when(configMock.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        Procedure procedure = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        when(mockKnowledgeService.findBestMatch(eq("action"), any(), any()))
                .thenReturn(Optional.empty(), Optional.of(highConfidenceMatch(procedure)));
        when(mockOperatorPrompts.chooseProcedure(anyString(), anyString(), any(), any(), any(), anyBoolean()))
                .thenReturn(Optional.of(new UserSelectionResult(SelectionAction.RETRY, null, null)));
        stubSucceedingAtomics();

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(procedure), 0, new ExecutionStateTracker(configMock));

        verify(mockExecutionRunService).recordResolution(any(), eq(Source.OPERATOR_SELECTED), isNull(), eq(procedure.id()), any(),
                anyLong());
    }

    @Test
    @DisplayName("A procedure the operator creates for the item is recorded as created by the operator")
    void executeBasedOnKnowledge_shouldRecordAnOperatorCreatedResolution_whenTheOperatorCreatesTheProcedure() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        when(configMock.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        Procedure procedure = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        when(mockKnowledgeService.findBestMatch(eq("action"), any(), any()))
                .thenReturn(Optional.empty(), Optional.of(highConfidenceMatch(procedure)));
        when(mockOperatorPrompts.chooseProcedure(anyString(), anyString(), any(), any(), any(), anyBoolean()))
                .thenReturn(Optional.of(new UserSelectionResult(SelectionAction.CREATE, null, null)));
        IngestionNode createdNode = new IngestionNode.NewProcedure(procedure, null, List.of());
        when(mockProcedureKnowledgeCollectionService.triggerNewProcedureFlow(anyString(), any(), any(), anyBoolean(), any()))
                .thenReturn(Optional.of(createdNode));
        stubSucceedingAtomics();

        orchestrator.executeBasedOnKnowledge(mockContext, singleStepTestCase(procedure), 0, new ExecutionStateTracker(configMock));

        verify(mockExecutionRunService).recordResolution(any(), eq(Source.OPERATOR_CREATED), isNull(), eq(procedure.id()), any(),
                anyLong());
    }

    private void stubSucceedingAtomics() {
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    List<UiTestStepResult> results = invocation.getArgument(4);
                    results.add(new UiTestStepResult(new TestStep("", List.of(), ""), TestStepResultStatus.SUCCESS, null, "", null,
                            Instant.now(), Instant.now()));
                    return new UserDecisionOutcome.ContinueNextStep();
                });
    }
}
