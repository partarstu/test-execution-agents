/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.*;
import org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.core.utils.PromptUtils;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.dto.VerificationStatus;
import org.tarik.ta.manager.VerificationManager;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.utils.UiCommonUtils;
import org.tarik.ta.core.error.RetryPolicy;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class UiTestAgentTimeoutTest {

    private GenAiModel mockModel;
    @Mock
    private ChatModel mockChatModel;
    @Mock
    private BufferedImage mockScreenshot;
    @Mock
    private UiElementRetriever mockUiElementRetriever;

    @Mock
    private TestCaseExtractionAgent testCaseExtractionAgentMock;
    @Mock
    private UiTestStepActionAgent uiTestStepActionAgentMock;
    @Mock
    private UiTestStepVerificationAgent uiTestStepVerificationAgentMock;

    @Mock
    private AiServices<TestCaseExtractionAgent> testCaseExtractionAgentBuilder;
    @Mock
    private AiServices<UiTestStepActionAgent> testStepActionAgentBuilder;
    @Mock
    private AiServices<UiTestStepVerificationAgent> testStepVerificationAgentBuilder;

    // Static mocks
    private MockedStatic<ModelFactory> modelFactoryMockedStatic;
    private MockedStatic<UiCommonUtils> commonUtilsMockedStatic;
    private MockedStatic<CommonUtils> coreUtilsMockedStatic;
    private MockedStatic<AgentConfig> agentConfigMockedStatic;
    private MockedStatic<UiTestAgentConfig> uiAgentConfigMockedStatic;
    private MockedStatic<AiServices> aiServicesMockedStatic;
    private MockedStatic<RetrieverFactory> retrieverFactoryMockedStatic;
    private MockedStatic<PromptUtils> promptUtilsMockedStatic;
    private MockedConstruction<ScreenRecorder> screenRecorderMockedConstruction;

    @BeforeEach
    void setUp() {
        modelFactoryMockedStatic = mockStatic(ModelFactory.class);
        commonUtilsMockedStatic = mockStatic(UiCommonUtils.class);
        coreUtilsMockedStatic = mockStatic(CommonUtils.class);
        agentConfigMockedStatic = mockStatic(AgentConfig.class);
        uiAgentConfigMockedStatic = mockStatic(UiTestAgentConfig.class);
        aiServicesMockedStatic = mockStatic(AiServices.class);
        retrieverFactoryMockedStatic = mockStatic(RetrieverFactory.class);
        screenRecorderMockedConstruction = mockConstruction(ScreenRecorder.class);
        promptUtilsMockedStatic = mockStatic(PromptUtils.class);

        // Basic config setup
        mockConfig();
        mockAgentBuilders();

        mockModel = new GenAiModel(mockChatModel);
        modelFactoryMockedStatic.when(() -> ModelFactory.getModel(any(), any())).thenReturn(mockModel);

        coreUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
        coreUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(null)).thenReturn(false);
        commonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(mockScreenshot);
        coreUtilsMockedStatic.when(() -> sleepMillis(anyLong())).thenAnswer(invocation -> null);
        promptUtilsMockedStatic.when(() -> loadSystemPrompt(any(), any(), any())).thenReturn("System Prompt");
        retrieverFactoryMockedStatic.when(RetrieverFactory::getUiElementRetriever).thenReturn(mockUiElementRetriever);
    }

    private void mockConfig() {
        agentConfigMockedStatic.when(AgentConfig::getActionVerificationDelayMillis).thenReturn(5);
        agentConfigMockedStatic.when(AgentConfig::getActionRetryPolicy).thenReturn(new RetryPolicy(3, 100, 5000));
        agentConfigMockedStatic.when(AgentConfig::getVerificationRetryPolicy).thenReturn(new RetryPolicy(3, 100, 5000));
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::isElementLocationPrefetchingEnabled).thenReturn(false);
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::isUnattendedMode).thenReturn(false);
        
        // Mock minimal necessary config for agents to be built
        agentConfigMockedStatic.when(AgentConfig::getTestCaseExtractionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfigMockedStatic.when(AgentConfig::getTestCaseExtractionAgentModelName).thenReturn("test-model");
        agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentModelName).thenReturn("test-model");
        agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentPromptVersion).thenReturn("v1");
        
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getTestStepVerificationAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getTestStepVerificationAgentModelName).thenReturn("test-model");
        uiAgentConfigMockedStatic.when(UiTestAgentConfig::getTestStepVerificationAgentPromptVersion).thenReturn("v1");
        
    }

    private void mockAgentBuilders() {
        // Default catch-all for AiServices.builder to avoid NPEs for unconfigured agents
        aiServicesMockedStatic.when(() -> AiServices.builder(any(Class.class))).thenReturn(mock(AiServices.class, RETURNS_DEEP_STUBS));

        aiServicesMockedStatic.when(() -> AiServices.builder(TestCaseExtractionAgent.class)).thenReturn(testCaseExtractionAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiTestStepActionAgent.class)).thenReturn(testStepActionAgentBuilder);
        aiServicesMockedStatic.when(() -> AiServices.builder(UiTestStepVerificationAgent.class)).thenReturn(testStepVerificationAgentBuilder);

        configureBuilder(testCaseExtractionAgentBuilder, testCaseExtractionAgentMock);
        configureBuilder(testStepActionAgentBuilder, uiTestStepActionAgentMock);
        configureBuilder(testStepVerificationAgentBuilder, uiTestStepVerificationAgentMock);
    }

    private <T> void configureBuilder(AiServices<T> builder, T agent) {
        lenient().when(builder.chatModel(any())).thenReturn(builder);
        lenient().when(builder.tools(any(Object[].class))).thenReturn(builder);
        lenient().when(builder.toolExecutionErrorHandler(any())).thenReturn(builder);
        lenient().when(builder.systemMessageProvider(any())).thenReturn(builder);
        lenient().when(builder.maxSequentialToolsInvocations(anyInt())).thenReturn(builder);
        lenient().when(builder.build()).thenReturn(agent);
    }

    @AfterEach
    void tearDown() {
        modelFactoryMockedStatic.close();
        commonUtilsMockedStatic.close();
        coreUtilsMockedStatic.close();
        agentConfigMockedStatic.close();
        aiServicesMockedStatic.close();
        retrieverFactoryMockedStatic.close();
        screenRecorderMockedConstruction.close();
        promptUtilsMockedStatic.close();
        uiAgentConfigMockedStatic.close();
    }

    @Test
    @DisplayName("Test execution should stop when verification times out")
    void testExecutionStopsOnVerificationTimeout() {
        // Given
        TestStep step1 = new TestStep("Step 1", null, "Verify 1");
        TestStep step2 = new TestStep("Step 2", null, "Verify 2");
        TestCase testCase = new TestCase("Timeout Test", null, List.of(step1, step2));

        mockTestCaseExtraction(testCase);

        // Step 1 Action Success
        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action 1 executed", new EmptyExecutionResult(), mockScreenshot))
                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

        // Mock VerificationManager to simulate timeout
        try (MockedConstruction<VerificationManager> mockedVerificationManager = mockConstruction(VerificationManager.class,
                (mock, context) -> {
                    // First timeout
                    VerificationStatus timeoutStatus = new VerificationStatus(true, null);
                    // Second timeout (extended)
                    VerificationStatus extendedTimeoutStatus = new VerificationStatus(true, null);

                    when(mock.waitForVerificationToFinish())
                            .thenReturn(timeoutStatus) // First call
                            .thenReturn(extendedTimeoutStatus); // Second call
                })) {

            // When
            TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

            // Then
            assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
            assertThat(result.getStepResults()).hasSize(1);
            assertThat(result.getStepResults().getFirst().getTestStep()).isEqualTo(step1);
            assertThat(result.getStepResults().getFirst().getErrorMessage()).contains("hasn't completed within extended timeout");
            
            // Verify Step 2 action was NEVER executed
            verify(uiTestStepActionAgentMock, times(1)).executeAndGetResult(any(Supplier.class));
        }
    }
    
    @Test
    @DisplayName("Test execution should stop when verification fails after extended wait")
    void testExecutionStopsOnVerificationFailureAfterExtendedWait() {
        // Given
        TestStep step1 = new TestStep("Step 1", null, "Verify 1");
        TestStep step2 = new TestStep("Step 2", null, "Verify 2");
        TestCase testCase = new TestCase("Timeout Test", null, List.of(step1, step2));

        mockTestCaseExtraction(testCase);

        // Step 1 Action Success
        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action 1 executed", new EmptyExecutionResult(), mockScreenshot))
                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

        // Mock VerificationManager
        try (MockedConstruction<VerificationManager> mockedVerificationManager = mockConstruction(VerificationManager.class,
                (mock, context) -> {
                    // First timeout
                    VerificationStatus timeoutStatus = new VerificationStatus(true, null);
                    // Second call returns completed but FAILED
                    VerificationStatus delayedFailureStatus = new VerificationStatus(false, false);
                    
                    when(mock.waitForVerificationToFinish())
                            .thenReturn(timeoutStatus) // First call
                            .thenReturn(delayedFailureStatus); // Second call
                })) {

            // When
            TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

            // Then
            // Step 1 fails, Step 2 should not run.
            // In this specific mock scenario (no lambda execution), step 1 is not recorded as "failed" in the results list
            // because the addFailedTestStep is supposed to happen in the lambda.
            assertThat(result.getStepResults()).isEmpty();

            verify(uiTestStepActionAgentMock, times(1)).executeAndGetResult(any(Supplier.class));
        }
    }


    private void mockTestCaseExtraction(TestCase testCase) {
        doReturn(new UiOperationExecutionResult<>(SUCCESS, "Test case extracted", testCase, mockScreenshot))
                .when(testCaseExtractionAgentMock).executeAndGetResult(any(Supplier.class));
    }
}