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
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.StepExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.ProcedureKnowledgeCollectionService;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.knowledge_graph.service.UiElementCache;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.knowledge_graph.UserDecisionOutcome;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBasedExecutionOrchestratorTest {

    @Mock
    private UiTestExecutionContext mockContext;
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

    private KnowledgeBasedExecutionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(configMock.isFullyUnattended()).thenReturn(true);
        lenient().when(configMock.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        lenient().when(configMock.getAncestryWindowSize()).thenReturn(5);

        orchestrator = new KnowledgeBasedExecutionOrchestrator(
                mockKnowledgeService, mockIngestionService, mockStepExecutionOrchestrator,
                mockProcedureKnowledgeCollectionService, mockSatisfiesEdgeService,
                mockProcedureUsageByTestCaseTrackingService, mockFailureContextService,
                configMock, mockUiElementCache, mockUiElementRepository, mockElementLocationHistoryLookup,
                mockAsyncExecutionPersistenceService);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("executeBasedOnKnowledge should throw exception when no match found in unattended mode")
    void executeBasedOnKnowledge_shouldThrow_whenNoMatchInUnattended() {
        TestCase testCase = new TestCase("test", List.of(), List.of(new TestStep("action", List.of(), "results")));
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.empty());

        // This should throw MissingProcedureException
        org.junit.jupiter.api.Assertions.assertThrows(org.tarik.ta.exceptions.MissingProcedureException.class, () -> {
            orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, mock(ExecutionStateTracker.class));
        });
    }

    @Test
    @DisplayName("SATISFIES edges for current atomic are persisted before verification completes")
    void satisfiesEdgesPersistedBeforeVerification() {
        TestCase testCase = new TestCase("test", List.of(), List.of(new TestStep("action", List.of(), "results")));
        Procedure proc = Procedure.createAtomic("action", List.of(), "", List.of(), List.of(), false);
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);
        
        var match = new KnowledgeService.MatchResult(proc, KnowledgeService.MatchConfidence.HIGH, 
                List.of(new KnowledgeService.ScoredProcedure(proc, 0, 0)), false, false, false);
                
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.findEffectsForProcedure(proc.id())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(proc.id())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(proc.id())).thenReturn(List.of());
        
        when(mockStepExecutionOrchestrator.executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    // Verify that async persist was called BEFORE or DURING execution
                    verify(mockAsyncExecutionPersistenceService).persistSatisfiesEdges(proc.id());
                    return UserDecisionOutcome.CONTINUE_NEXT_STEP;
                });
        
        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);
        verify(mockAsyncExecutionPersistenceService).persistSatisfiesEdges(proc.id());
    }

    @Test
    @DisplayName("Live state tracker enables next-step selection before async SATISFIES write completes")
    void liveStateTrackerEnablesNextStepSelectionBeforeAsyncSatisfiesCompletes() {
        TestCase testCase = new TestCase("test", List.of(), List.of(
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
            List<org.tarik.ta.dto.UiTestStepResult> testStepResults = inv.getArgument(5);
            testStepResults.add(new org.tarik.ta.dto.UiTestStepResult(new TestStep("", List.of(), ""), org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS, null, "", null, java.time.Instant.now(), java.time.Instant.now()));
            return UserDecisionOutcome.CONTINUE_NEXT_STEP;
        }).when(mockStepExecutionOrchestrator).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
                
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
    @DisplayName("Step-level verification still runs when atomic verification is off")
    void stepLevelVerificationRunsWhenAtomicVerificationIsOff() {
        TestCase testCase = new TestCase("test", List.of(), List.of(new TestStep("action", List.of(), "test step expected")));
        Procedure proc1 = Procedure.createAtomic("atomic1", List.of(), "atomic1 exp", List.of(), List.of(), false);
        Procedure proc2 = Procedure.createAtomic("atomic2", List.of(), "atomic2 exp", List.of(), List.of(), false);
        Procedure composite = Procedure.createComposite("action", List.of(), "", List.of(), List.of(), false);
        
        ExecutionStateTracker stateTracker = new ExecutionStateTracker(configMock);
        
        when(configMock.isAtomicVerificationEnabled()).thenReturn(false);
        
        var match = new KnowledgeService.MatchResult(composite, KnowledgeService.MatchConfidence.HIGH, 
                List.of(new KnowledgeService.ScoredProcedure(composite, 0, 0)), false, false, false);
                
        when(mockKnowledgeService.findBestMatch(anyString(), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.resolveToAtomicSteps(composite.id())).thenReturn(List.of(proc1, proc2));
        when(mockKnowledgeService.getChildren(composite.id())).thenReturn(List.of(proc1, proc2));
        when(mockKnowledgeService.findEffectsForProcedure(any())).thenReturn(List.of());
        when(mockKnowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(any())).thenReturn(List.of());
        
        // Capture contextFactory when executeAtomicStepWithRetryLoop is called
        doAnswer(inv -> {
            Procedure p = inv.getArgument(1);
            java.util.function.Function<Procedure, AtomicStepExecutionContext> factory = inv.getArgument(9);
            AtomicStepExecutionContext execCtx = factory.apply(p);
            
            if (p.description().equals("atomic1")) {
                // Not last step, atomic verification disabled -> effective expected results must be null
                assertThat(execCtx.effectiveExpectedResults()).isNull();
            } else if (p.description().equals("atomic2")) {
                // Last step -> step-level verification runs, effective expected results must combine atomic and test step
                assertThat(execCtx.effectiveExpectedResults()).contains("atomic2 exp").contains("test step expected");
            }
            List<org.tarik.ta.dto.UiTestStepResult> testStepResults = inv.getArgument(5);
            testStepResults.add(new org.tarik.ta.dto.UiTestStepResult(new TestStep("", List.of(), ""), org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS, null, "", null, java.time.Instant.now(), java.time.Instant.now()));
            return UserDecisionOutcome.CONTINUE_NEXT_STEP;
        }).when(mockStepExecutionOrchestrator).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        orchestrator.executeBasedOnKnowledge(mockContext, testCase, 0, stateTracker);
        verify(mockStepExecutionOrchestrator, times(2)).executeAtomicStepWithRetryLoop(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
