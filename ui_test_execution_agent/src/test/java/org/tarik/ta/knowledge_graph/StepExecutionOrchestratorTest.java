package org.tarik.ta.knowledge_graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.tools.VerificationTools;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.knowledge_graph.timing.TimingRecorder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepExecutionOrchestratorTest {

    @Mock private VerificationTools mockVerificationTools;
    @Mock private BudgetManager mockBudgetManager;
    @Mock private UiTestAgentConfig mockConfig;
    @Mock private ProcedureKnowledgeCollectionService mockProcedureKnowledgeCollectionService;
    @Mock private KnowledgeService mockKnowledgeService;
    @Mock private KnowledgeIngestionService mockKnowledgeIngestionService;
    @Mock private UiTestStepActionAgent mockTestStepActionAgent;
    @Mock private UiPreconditionActionAgent mockPreconditionActionAgent;
    @Mock private UiTestStepVerificationAgent mockTestStepVerificationAgent;
    @Mock private UiPreconditionVerificationAgent mockPreconditionVerificationAgent;
    
    @Mock private UiTestExecutionContext mockContext;

    private StepExecutionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(mockConfig.getActionVerificationDelayMillis()).thenReturn(100);
        orchestrator = new StepExecutionOrchestrator(
                mockVerificationTools, mockBudgetManager, mockConfig,
                mockProcedureKnowledgeCollectionService, mockKnowledgeService,
                mockKnowledgeIngestionService, mockTestStepActionAgent,
                mockPreconditionActionAgent, mockTestStepVerificationAgent,
                mockPreconditionVerificationAgent
        );
    }

    @Test
    void executeAtomicStep_skipsPerAtomicVerificationWhenDisabled() {
        // Arrange
        TestStep testStep = new TestStep("action", List.of(), "results");
        ExecutionItem.TestStepItem item = new ExecutionItem.TestStepItem(testStep);
        Procedure atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        
        List<UiTestStepResult> testStepResults = new ArrayList<>();
        List<org.tarik.ta.dto.UiPreconditionResult> preconditionResults = new ArrayList<>();
        
        // Mock action agent to return success
        UiOperationExecutionResult<EmptyExecutionResult> successResult = new UiOperationExecutionResult<>(OperationExecutionResult.ExecutionStatus.SUCCESS, "done", new EmptyExecutionResult(true, null), null);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successResult);
        
        // Use real instance for stepExecutionContext with null effectiveExpectedResults
        AtomicStepExecutionContext stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), "ui1", "", null, null);
        
        // Act
        StepExecutionOrchestrator.AtomicStepResult result = orchestrator.executeAtomicStep(
                item, atomic, mockContext, testStepResults, preconditionResults, stepExecutionContext
        );
        
        // Assert
        assertThat(result).isInstanceOf(StepExecutionOrchestrator.AtomicStepResult.Success.class);
        verify(mockVerificationTools, never()).verifyTestStep(any(), any(), any(), any(), any());
    }

    @Test
    void executeAtomicStep_runsVerificationWhenEnabled() {
        // Arrange
        TestStep testStep = new TestStep("action", List.of(), "results");
        ExecutionItem.TestStepItem item = new ExecutionItem.TestStepItem(testStep);
        Procedure atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        
        List<UiTestStepResult> testStepResults = new ArrayList<>();
        List<org.tarik.ta.dto.UiPreconditionResult> preconditionResults = new ArrayList<>();
        
        // Mock action agent to return success
        UiOperationExecutionResult<EmptyExecutionResult> successResult = new UiOperationExecutionResult<>(OperationExecutionResult.ExecutionStatus.SUCCESS, "done", new EmptyExecutionResult(true, null), null);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successResult);
        
        AtomicStepExecutionContext stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), "ui1", "expect this", null, null);
        VerificationExecutionResult vResult = new VerificationExecutionResult(true, "verified");
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any())).thenReturn(vResult);
        
        // Act
        StepExecutionOrchestrator.AtomicStepResult result = orchestrator.executeAtomicStep(
                item, atomic, mockContext, testStepResults, preconditionResults, stepExecutionContext
        );
        
        // Assert
        assertThat(result).isInstanceOf(StepExecutionOrchestrator.AtomicStepResult.Success.class);
        verify(mockVerificationTools).verifyTestStep(eq("expect this"), eq(atomic.description()), eq(""), eq(mockContext), eq(mockTestStepVerificationAgent));
    }
}
