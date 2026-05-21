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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
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

import org.tarik.ta.utils.UiCommonUtils;

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
        
        // Act & Assert
        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
            StepExecutionOrchestrator.AtomicStepResult result = orchestrator.executeAtomicStep(
                    item, atomic, mockContext, testStepResults, preconditionResults, stepExecutionContext
            );
            assertThat(result).isInstanceOf(StepExecutionOrchestrator.AtomicStepResult.Success.class);
            verify(mockVerificationTools, never()).verifyTestStep(any(), any(), any(), any(), any());
        }
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
        
        // Act & Assert
        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
            StepExecutionOrchestrator.AtomicStepResult result = orchestrator.executeAtomicStep(
                    item, atomic, mockContext, testStepResults, preconditionResults, stepExecutionContext
            );
            assertThat(result).isInstanceOf(StepExecutionOrchestrator.AtomicStepResult.Success.class);
            verify(mockVerificationTools).verifyTestStep(eq("expect this"), eq(atomic.description()), eq(""), eq(mockContext), eq(mockTestStepVerificationAgent));
        }
    }
}
