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
package org.tarik.ta;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.agents.UiPreconditionActionAgent;
import org.tarik.ta.agents.UiPreconditionVerificationAgent;
import org.tarik.ta.agents.UiTestStepActionAgent;
import org.tarik.ta.agents.UiTestStepVerificationAgent;
import org.tarik.ta.core.dto.DataEntry;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.utils.UiCommonUtils;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.model.VisualStateHolder;
import org.tarik.ta.knowledge_graph.model.node.ExecutionRun;
import org.tarik.ta.panel.OperatorPromptInterruptedException;
import org.tarik.ta.panel.OperatorPrompts;
import org.tarik.ta.panel.RunObserver;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class UiTestAgentTest {

    private UiTestAgent uiTestAgent;

    @Mock
    private KnowledgeBasedExecutionOrchestrator knowledgeBasedExecutionOrchestrator;
    @Mock
    private KnowledgeService mockKnowledgeService;
    @Mock
    private UiTestAgentConfig configMock;
    @Mock
    private TestCaseExtractor testCaseExtractor;
    @Mock
    private BudgetManager budgetManager;
    @Mock
    private TestExecutionContext mockContext;
    @Mock
    private VisualStateHolder mockVisualState;
    @Mock
    private ScreenRecorder mockScreenRecorder;
    @Mock
    private LogCapture mockLogCapture;
    @Mock
    private ExecutionStateTracker mockStateTracker;
    @Mock
    private RunObserver mockRunObserver;
    @Mock
    private OperatorPrompts mockOperatorPrompts;
    @Mock
    private ExecutionRunService mockExecutionRunService;

    @Mock
    private UiPreconditionActionAgent preconditionActionAgentMock;
    @Mock
    private UiPreconditionVerificationAgent preconditionVerificationAgentMock;
    @Mock
    private UiTestStepActionAgent uiTestStepActionAgentMock;
    @Mock
    private UiTestStepVerificationAgent uiTestStepVerificationAgentMock;

    private final BufferedImage screenshot = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

    private MockedStatic<UiCommonUtils> commonUtilsMockedStatic;
    private MockedStatic<CommonUtils> coreUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        commonUtilsMockedStatic = mockStatic(UiCommonUtils.class);
        coreUtilsMockedStatic = mockStatic(CommonUtils.class);

        lenient().when(configMock.isFullyUnattended()).thenReturn(true);
        lenient().when(configMock.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        lenient().when(configMock.getActionRetryPolicy()).thenReturn(new RetryPolicy(3, 100, 5000));
        lenient().when(configMock.getVerificationRetryPolicy()).thenReturn(new RetryPolicy(3, 100, 5000));

        commonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(screenshot);
        coreUtilsMockedStatic.when(() -> CommonUtils.sleepMillis(anyLong())).thenAnswer(invocation -> null);

        uiTestAgent = new UiTestAgent(knowledgeBasedExecutionOrchestrator, mockKnowledgeService,
                configMock, testCaseExtractor, budgetManager, mockContext,
                mockScreenRecorder, mockLogCapture, mockStateTracker, mockVisualState, mockRunObserver, mockOperatorPrompts,
                mockExecutionRunService);
    }

    @AfterEach
    void tearDown() {
        commonUtilsMockedStatic.close();
        coreUtilsMockedStatic.close();
    }

    @Test
    @DisplayName("Single test step with action and successful verification")
    void singleStepActionAndVerificationSuccess() {
        // Given
        TestStep step = new TestStep("Perform Action", null, "Verify Result");
        TestCase testCase = new TestCase("Single Step Success", null, List.of(), List.of(step));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));

        // When
        TestExecutionResult result = uiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        verify(budgetManager).activateToolCallBudget();
        verify(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(eq(mockContext), eq(testCase), eq(0), eq(mockStateTracker));
    }

    @Test
    @DisplayName("The test case data table is seeded into the execution context before the first item runs")
    void dataTableIsSeededIntoTheContext() {
        // Given
        TestStep step = new TestStep("Perform Action", null, "Verify Result");
        var dataTable = List.of(new DataEntry("user", "admin"));
        TestCase testCase = new TestCase("Data table", null, dataTable, List.of(step));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));

        // When
        uiTestAgent.executeTestCase("test case message");

        // Then
        var inOrder = inOrder(mockContext, knowledgeBasedExecutionOrchestrator);
        inOrder.verify(mockContext).seedFromDataTable(dataTable);
        inOrder.verify(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("The run observer learns about the test case after the data table is seeded and before the first item runs")
    void runObserverIsToldAboutTheTestCaseAfterSeeding() {
        TestStep step = new TestStep("Perform Action", null, "Verify Result");
        TestCase testCase = new TestCase("Observed", null, List.of(new DataEntry("password", "secret")), List.of(step));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));

        uiTestAgent.executeTestCase("test case message");

        var inOrder = inOrder(mockContext, mockRunObserver, knowledgeBasedExecutionOrchestrator);
        inOrder.verify(mockContext).seedFromDataTable(testCase.testData());
        inOrder.verify(mockRunObserver).taskStarted(testCase);
        inOrder.verify(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("No task is announced to the run observer when no test case can be extracted")
    void runObserverIsNotToldAboutATaskWithoutTestCase() {
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.empty());

        uiTestAgent.executeTestCase("not a test case");

        verify(mockRunObserver, never()).taskStarted(any());
    }

    @Test
    @DisplayName("Supervised mode does not activate unattended budgets")
    void supervisedModeDoesNotActivateUnattendedBudgets() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.empty());

        uiTestAgent.executeTestCase("test case message");

        verify(budgetManager).reset();
        verify(budgetManager, never()).activateTimeBudget();
        verify(budgetManager, never()).activateToolCallBudget();
    }

    @Test
    @DisplayName("Precondition execution fails")
    void preconditionExecutionFails() {
        // Given
        String precondition = "Precondition 1";
        TestCase testCase = new TestCase("Precondition Fail", List.of(new Precondition(precondition, List.of())), List.of(),
                List.of(new TestStep("Dummy Step", null, null)));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));

        doAnswer(invocation -> {
            TestExecutionContext ctx = invocation.getArgument(0);
            ctx.addPreconditionResult(new org.tarik.ta.dto.UiPreconditionResult("Precondition 1", false, "Precondition failed", screenshot, java.time.Instant.now(), java.time.Instant.now()));
            return null;
        }).when(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), any(), anyInt(), any());
        
        when(mockContext.getPreconditionExecutionHistory()).thenReturn(List.of(
                new org.tarik.ta.dto.UiPreconditionResult("Precondition 1", false, "Precondition failed", screenshot, java.time.Instant.now(), java.time.Instant.now())
        ));

        // When
        TestExecutionResult result = uiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
    }

    @Test
    @DisplayName("Execution result contains SystemInfo and logs")
    void executionResultContainsSystemInfoAndLogs() {
        // Given
        TestStep step = new TestStep("Action", null, null);
        TestCase testCase = new TestCase("System Info Test", null, List.of(), List.of(step));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));
        when(mockLogCapture.getLogs()).thenReturn(List.of("Log 1"));

        // When
        UiTestExecutionResult result = (UiTestExecutionResult) uiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getSystemInfo()).isNotNull();
        assertThat(result.getLogs()).contains("Log 1");
    }

    @Test
    @DisplayName("Knowledge-based execution failure returns error")
    void knowledgeBasedExecutionFailureReturnsError() {
        // Given
        TestStep step = new TestStep("UNKNOWN_ACTION", null, null);
        TestCase testCase = new TestCase("Knowledge Failure Test", null, List.of(), List.of(step));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));

        doThrow(new DatabaseConnectionException("Neo4j is not reachable", null))
                .when(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), any(), anyInt(), any());

        // When
        TestExecutionResult result = uiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.getGeneralErrorMessage()).contains("Knowledge-based execution error");
        assertThat(result.getGeneralErrorMessage()).contains("Neo4j is not reachable");
    }

    @Test
    @DisplayName("An unexpected failure still reports the captured logs and the recording")
    void unexpectedFailureStillReportsCapturedLogsAndRecording() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("Unexpected Failure Test", null, List.of(), List.of(new TestStep("Action", null, null)));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));
        when(mockLogCapture.getLogs()).thenReturn(List.of("Log before the failure"));
        when(mockScreenRecorder.getCurrentRecordingPath()).thenReturn("videos/run.mp4");

        when(mockOperatorPrompts.selectStartingStep(testCase.testSteps())).thenThrow(new IllegalStateException("dialog blew up"));

        var result = (UiTestExecutionResult) uiTestAgent.executeTestCase("test case message");

        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.getLogs()).contains("Log before the failure");
        assertThat(result.getVideoPath()).isEqualTo("videos/run.mp4");
    }

    @Test
    void executeTestCase_shouldStartFromTheStepTheOperatorSelects_whenSupervised() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        var steps = List.of(new TestStep("Open", null, null), new TestStep("Save", null, null));
        TestCase testCase = new TestCase("Supervised Start", null, List.of(), steps);
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));
        when(mockOperatorPrompts.selectStartingStep(steps)).thenReturn(1);

        uiTestAgent.executeTestCase("test case message");

        verify(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), eq(testCase), eq(1), any());
    }

    @Test
    void executeTestCase_shouldStartTheRunHistory_andFinishItAsPassed_whenTheRunPasses() {
        TestCase testCase = new TestCase("Run History", null, List.of(), List.of(new TestStep("Action", null, null)));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));

        uiTestAgent.executeTestCase("test case message");

        var order = inOrder(mockRunObserver, mockExecutionRunService, knowledgeBasedExecutionOrchestrator);
        order.verify(mockRunObserver).taskStarted(testCase);
        order.verify(mockExecutionRunService).start("Run History");
        order.verify(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), any(), anyInt(), any());
        order.verify(mockExecutionRunService).finish(ExecutionRun.Status.PASSED);
    }

    @Test
    void executeTestCase_shouldFinishTheRunHistoryAsFailed_whenAStepFails() {
        TestCase testCase = new TestCase("Run History", null, List.of(), List.of(new TestStep("Action", null, null)));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));
        when(mockContext.getTestStepExecutionHistory()).thenReturn(List.of(new UiTestStepResult(new TestStep("Action", null, null),
                TestStepResultStatus.FAILURE, "Not saved", null, null, Instant.now(), Instant.now())));

        uiTestAgent.executeTestCase("test case message");

        verify(mockExecutionRunService).finish(ExecutionRun.Status.FAILED);
    }

    @Test
    void executeTestCase_shouldFinishTheRunHistoryWithAnError_whenTheKnowledgeFlowFails() {
        TestCase testCase = new TestCase("Run History", null, List.of(), List.of(new TestStep("Action", null, null)));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));
        doThrow(new DatabaseConnectionException("Neo4j is not reachable", null))
                .when(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), any(), anyInt(), any());

        uiTestAgent.executeTestCase("test case message");

        verify(mockExecutionRunService).finish(ExecutionRun.Status.ERROR);
    }

    @Test
    void executeTestCase_shouldFinishTheRunHistoryWithAnError_whenTheStepSelectionFails() {
        when(configMock.isFullyUnattended()).thenReturn(false);
        TestCase testCase = new TestCase("Run History", null, List.of(), List.of(new TestStep("Action", null, null)));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));
        when(mockOperatorPrompts.selectStartingStep(testCase.testSteps())).thenThrow(new IllegalStateException("dialog blew up"));

        uiTestAgent.executeTestCase("test case message");

        verify(mockExecutionRunService).finish(ExecutionRun.Status.ERROR);
    }

    @Test
    void executeTestCase_shouldFinishTheRunHistoryAsAborted_whenTheRunIsInterrupted() {
        TestCase testCase = new TestCase("Run History", null, List.of(), List.of(new TestStep("Action", null, null)));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));
        doAnswer(_ -> {
            Thread.currentThread().interrupt();
            throw new OperatorPromptInterruptedException("cancelled");
        }).when(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), any(), anyInt(), any());

        try {
            uiTestAgent.executeTestCase("test case message");
        } finally {
            Thread.interrupted();
        }

        verify(mockExecutionRunService).finish(ExecutionRun.Status.ABORTED);
    }

    @Test
    void executeTestCase_shouldNotStartTheRunHistory_whenTheScreenCaptureFailsToStart() {
        TestCase testCase = new TestCase("Run History", null, List.of(), List.of(new TestStep("Action", null, null)));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));
        doThrow(new IllegalStateException("no display")).when(mockScreenRecorder).beginScreenCapture();

        var result = uiTestAgent.executeTestCase("test case message");

        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        verifyNoInteractions(mockExecutionRunService);
    }

    @Test
    void executeTestCase_shouldNotStartTheRunHistory_withoutATestCase() {
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.empty());

        uiTestAgent.executeTestCase("test case message");

        verifyNoInteractions(mockExecutionRunService);
    }
}
