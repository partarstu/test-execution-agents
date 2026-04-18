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
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.utils.UiCommonUtils;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.StepExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.ProcedureKnowledgeCollectionService;
import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.tools.VerificationTools;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;

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
    private UiTestExecutionContext mockContext;
    @Mock
    private ScreenRecorder mockScreenRecorder;
    @Mock
    private LogCapture mockLogCapture;
    @Mock
    private ExecutionStateTracker mockStateTracker;

    @Mock
    private UiPreconditionActionAgent preconditionActionAgentMock;
    @Mock
    private UiPreconditionVerificationAgent preconditionVerificationAgentMock;
    @Mock
    private UiTestStepActionAgent uiTestStepActionAgentMock;
    @Mock
    private UiTestStepVerificationAgent uiTestStepVerificationAgentMock;

    @Mock
    private BufferedImage mockScreenshot;

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

        commonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(mockScreenshot);
        coreUtilsMockedStatic.when(() -> CommonUtils.sleepMillis(anyLong())).thenAnswer(invocation -> null);

        uiTestAgent = new UiTestAgent(knowledgeBasedExecutionOrchestrator, mockKnowledgeService,
                configMock, testCaseExtractor, budgetManager, mockContext,
                mockScreenRecorder, mockLogCapture, mockStateTracker);
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
        TestCase testCase = new TestCase("Single Step Success", null, List.of(step));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));

        // When
        TestExecutionResult result = uiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        verify(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(eq(mockContext), eq(testCase), eq(0), eq(mockStateTracker));
    }

    @Test
    @DisplayName("Precondition execution fails")
    void preconditionExecutionFails() {
        // Given
        String precondition = "Precondition 1";
        TestCase testCase = new TestCase("Precondition Fail", List.of(precondition),
                List.of(new TestStep("Dummy Step", null, null)));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));

        doAnswer(invocation -> {
            UiTestExecutionContext ctx = invocation.getArgument(0);
            ctx.addPreconditionResult(new org.tarik.ta.dto.UiPreconditionResult("Precondition 1", false, "Precondition failed", mockScreenshot, java.time.Instant.now(), java.time.Instant.now()));
            return null;
        }).when(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), any(), anyInt(), any());
        
        when(mockContext.getPreconditionExecutionHistory()).thenReturn(List.of(
                new org.tarik.ta.dto.UiPreconditionResult("Precondition 1", false, "Precondition failed", mockScreenshot, java.time.Instant.now(), java.time.Instant.now())
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
        TestCase testCase = new TestCase("System Info Test", null, List.of(step));
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
    @DisplayName("Unattended mode returns error when procedure is missing")
    void missingProcedureInUnattendedModeReturnsError() {
        // Given
        TestStep step = new TestStep("UNKNOWN_ACTION", null, null);
        TestCase testCase = new TestCase("Missing Knowledge Test", null, List.of(step));
        when(testCaseExtractor.extractTestCase(anyString())).thenReturn(Optional.of(testCase));

        doThrow(new org.tarik.ta.exceptions.MissingProcedureException("No matching procedure found"))
                .when(knowledgeBasedExecutionOrchestrator).executeBasedOnKnowledge(any(), any(), anyInt(), any());

        // When
        TestExecutionResult result = uiTestAgent.executeTestCase("test case message");

        // Then
        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.getGeneralErrorMessage()).contains("Knowledge-based execution error");
        assertThat(result.getGeneralErrorMessage()).contains("No matching procedure found");
    }
}
