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
import org.tarik.ta.agents.UiPreconditionActionAgent;
import org.tarik.ta.agents.UiPreconditionVerificationAgent;
import org.tarik.ta.agents.UiTestStepActionAgent;
import org.tarik.ta.agents.UiTestStepVerificationAgent;
import org.tarik.ta.agents.UiElementDescriptionAgent;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.agents.PageDescriptionAgent;
import org.tarik.ta.agents.UiElementBoundingBoxAgent;
import org.tarik.ta.agents.BestUiElementMatchSelectionAgent;
import org.tarik.ta.agents.DbUiElementSelectionAgent;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.utils.UiCommonUtils;
import org.tarik.ta.core.utils.PromptUtils;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.AgentConfig;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class UiTestAgentTest {

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
        private UiPreconditionActionAgent preconditionActionAgentMock;
        @Mock
        private UiPreconditionVerificationAgent preconditionVerificationAgentMock;
        @Mock
        private UiTestStepActionAgent uiTestStepActionAgentMock;
        @Mock
        private UiTestStepVerificationAgent uiTestStepVerificationAgentMock;
        @Mock
        private UiStateCheckAgent uiStateCheckAgentMock;
        @Mock
        private UiElementDescriptionAgent uiElementDescriptionAgentMock;
        @Mock
        private PageDescriptionAgent pageDescriptionAgentMock;
        @Mock
        private UiElementBoundingBoxAgent uiElementBoundingBoxAgentMock;
        @Mock
        private BestUiElementMatchSelectionAgent bestUiElementMatchSelectionAgentMock;
        @Mock
        private DbUiElementSelectionAgent dbUiElementSelectionAgentMock;

        @Mock
        private AiServices<TestCaseExtractionAgent> testCaseExtractionAgentBuilder;
        @Mock
        private AiServices<UiPreconditionActionAgent> preconditionActionAgentBuilder;
        @Mock
        private AiServices<UiPreconditionVerificationAgent> preconditionVerificationAgentBuilder;
        @Mock
        private AiServices<UiTestStepActionAgent> testStepActionAgentBuilder;
        @Mock
        private AiServices<UiTestStepVerificationAgent> testStepVerificationAgentBuilder;
        @Mock
        private AiServices<UiStateCheckAgent> toolVerificationAgentBuilder;
        @Mock
        private AiServices<UiElementDescriptionAgent> uiElementDescriptionAgentBuilder;
        @Mock
        private AiServices<PageDescriptionAgent> pageDescriptionAgentBuilder;
        @Mock
        private AiServices<UiElementBoundingBoxAgent> elementBoundingBoxAgentBuilder;
        @Mock
        private AiServices<BestUiElementMatchSelectionAgent> elementSelectionAgentBuilder;
        @Mock
        private AiServices<DbUiElementSelectionAgent> dbElementSelectionAgentBuilder;

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

        private static final int ACTION_VERIFICATION_DELAY_MILLIS = 5;

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

                // Agent Config
                agentConfigMockedStatic.when(AgentConfig::getActionVerificationDelayMillis)
                                .thenReturn(ACTION_VERIFICATION_DELAY_MILLIS);
                agentConfigMockedStatic.when(AgentConfig::getActionRetryPolicy).thenReturn(new RetryPolicy(3, 100, 5000));
                agentConfigMockedStatic.when(AgentConfig::getVerificationRetryPolicy)
                                .thenReturn(new RetryPolicy(3, 100, 5000));
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::isElementLocationPrefetchingEnabled)
                                .thenReturn(false);
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::isFullyUnattended).thenReturn(true);
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getExecutionMode).thenReturn(ExecutionMode.UNATTENDED);
                agentConfigMockedStatic.when(AgentConfig::getTestCaseExtractionAgentModelProvider)
                                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getPreconditionActionAgentModelProvider)
                                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentModelProvider)
                                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(UiTestAgentConfig::getPreconditionVerificationAgentModelProvider)
                                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(UiTestAgentConfig::getTestStepVerificationAgentModelProvider)
                                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                agentConfigMockedStatic.when(AgentConfig::getTestCaseExtractionAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getPreconditionActionAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentModelName).thenReturn("test-model");
                agentConfigMockedStatic.when(UiTestAgentConfig::getPreconditionVerificationAgentModelName)
                                .thenReturn("test-model");
                agentConfigMockedStatic.when(UiTestAgentConfig::getTestStepVerificationAgentModelName)
                                .thenReturn("test-model");
                agentConfigMockedStatic.when(AgentConfig::getPreconditionAgentPromptVersion).thenReturn("v1");
                agentConfigMockedStatic.when(AgentConfig::getTestStepActionAgentPromptVersion).thenReturn("v1");
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getPageDescriptionAgentModelName)
                                .thenReturn("test-model");
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getElementBoundingBoxAgentModelName)
                                .thenReturn("test-model");
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getUiElementVisualMatchAgentModelName)
                                .thenReturn("test-model");
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getPageDescriptionAgentModelProvider)
                                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getElementBoundingBoxAgentModelProvider)
                                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getUiElementVisualMatchAgentModelProvider)
                                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getPageDescriptionAgentPromptVersion)
                                .thenReturn("v1");
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getElementBoundingBoxAgentPromptVersion)
                                .thenReturn("v1");
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getElementSelectionAgentPromptVersion)
                                .thenReturn("v1");
                agentConfigMockedStatic.when(UiTestAgentConfig::getPreconditionVerificationAgentPromptVersion)
                                .thenReturn("v1");
                agentConfigMockedStatic.when(UiTestAgentConfig::getTestStepVerificationAgentPromptVersion).thenReturn("v1");

                mockModel = new GenAiModel(mockChatModel);

                modelFactoryMockedStatic.when(() -> ModelFactory.getModel(any(), any())).thenReturn(mockModel);
                modelFactoryMockedStatic.when(() -> ModelFactory.getModel(any(), any(), anyInt())).thenReturn(mockModel);

                // Common Utils & Core Utils
                coreUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
                coreUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(null)).thenReturn(false);
                commonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(mockScreenshot);
                coreUtilsMockedStatic.when(() -> sleepMillis(anyLong())).thenAnswer(invocation -> null);

                promptUtilsMockedStatic.when(() -> loadSystemPrompt(any(), any(), any())).thenReturn("System Prompt");

                // AiServices Mocking
                aiServicesMockedStatic.when(() -> AiServices.builder(TestCaseExtractionAgent.class))
                                .thenReturn(testCaseExtractionAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(UiPreconditionActionAgent.class))
                                .thenReturn(preconditionActionAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(UiPreconditionVerificationAgent.class))
                                .thenReturn(preconditionVerificationAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(UiTestStepActionAgent.class))
                                .thenReturn(testStepActionAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(UiTestStepVerificationAgent.class))
                                .thenReturn(testStepVerificationAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(UiStateCheckAgent.class))
                                .thenReturn(toolVerificationAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(UiElementDescriptionAgent.class))
                                .thenReturn(uiElementDescriptionAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(PageDescriptionAgent.class))
                                .thenReturn(pageDescriptionAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(UiElementBoundingBoxAgent.class))
                                .thenReturn(elementBoundingBoxAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(BestUiElementMatchSelectionAgent.class))
                                .thenReturn(elementSelectionAgentBuilder);
                aiServicesMockedStatic.when(() -> AiServices.builder(DbUiElementSelectionAgent.class))
                                .thenReturn(dbElementSelectionAgentBuilder);

                // Retriever Factory
                retrieverFactoryMockedStatic.when(RetrieverFactory::getUiElementRetriever)
                                .thenReturn(mockUiElementRetriever);
                
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getDbElementCandidateSelectionAgentModelName)
                                .thenReturn("test-model");
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getDbElementCandidateSelectionAgentModelProvider)
                                .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                uiAgentConfigMockedStatic.when(UiTestAgentConfig::getDbElementCandidateSelectionAgentPromptVersion)
                                .thenReturn("v1");

                // Builder chains
                configureBuilder(testCaseExtractionAgentBuilder, testCaseExtractionAgentMock);
                configureBuilder(preconditionActionAgentBuilder, preconditionActionAgentMock);
                configureBuilder(preconditionVerificationAgentBuilder, preconditionVerificationAgentMock);
                configureBuilder(testStepActionAgentBuilder, uiTestStepActionAgentMock);
                configureBuilder(testStepVerificationAgentBuilder, uiTestStepVerificationAgentMock);
                configureBuilder(toolVerificationAgentBuilder, uiStateCheckAgentMock);
                configureBuilder(uiElementDescriptionAgentBuilder, uiElementDescriptionAgentMock);
                configureBuilder(pageDescriptionAgentBuilder, pageDescriptionAgentMock);
                configureBuilder(elementBoundingBoxAgentBuilder, uiElementBoundingBoxAgentMock);
                configureBuilder(elementSelectionAgentBuilder, bestUiElementMatchSelectionAgentMock);
                configureBuilder(dbElementSelectionAgentBuilder, dbUiElementSelectionAgentMock);
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
        @DisplayName("Single test step with action and successful verification")
        void singleStepActionAndVerificationSuccess() {
                // Given
                TestStep step = new TestStep("Perform Action", null, "Verify Result");
                TestCase testCase = new TestCase("Single Step Success", null, List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action executed", new EmptyExecutionResult(),
                                mockScreenshot))
                                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Verification executed",
                                new VerificationExecutionResult(true, "Verified"), mockScreenshot))
                                .when(uiTestStepVerificationAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

                // Then
                assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
                assertThat(result.getStepResults()).hasSize(1);
                assertThat(result.getStepResults().getFirst().getExecutionStatus())
                                .isEqualTo(TestStepResultStatus.SUCCESS);

                verify(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));
                verify(uiTestStepVerificationAgentMock).executeAndGetResult(any(Supplier.class));
        }

        @Test
        @DisplayName("Single step with action only (no verification)")
        void singleStepActionOnlySuccess() {
                // Given
                TestStep step = new TestStep("Perform Action Only", null, null);
                TestCase testCase = new TestCase("Single Action Only", null, List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action executed", new EmptyExecutionResult(),
                                mockScreenshot))
                                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

                // Then
                assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
                verify(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));
                verifyNoInteractions(uiTestStepVerificationAgentMock);
        }

        @Test
        @DisplayName("Preconditions execution and verification success")
        void preconditionsSuccess() {
                // Given
                String precondition = "Precondition 1";
                TestStep step = new TestStep("Action", null, null);
                TestCase testCase = new TestCase("Precondition Success", List.of(precondition), List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Precondition executed",
                                new EmptyExecutionResult(), mockScreenshot))
                                .when(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Precondition verified",
                                new VerificationExecutionResult(true, "Verified"), mockScreenshot))
                                .when(preconditionVerificationAgentMock).executeWithRetry(any(Supplier.class), any());

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action executed", new EmptyExecutionResult(),
                                mockScreenshot))
                                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

                // Then
                assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
                verify(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));
                verify(preconditionVerificationAgentMock).executeWithRetry(any(Supplier.class), any());
                verify(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));
        }

        @Test
        @DisplayName("Precondition execution fails")
        void preconditionExecutionFails() {
                // Given
                String precondition = "Precondition 1";
                TestCase testCase = new TestCase("Precondition Fail", List.of(precondition),
                                List.of(new TestStep("Dummy Step", null, null)));

                mockTestCaseExtraction(testCase);

                doReturn(new UiOperationExecutionResult<>(ERROR, "Precondition failed", null, mockScreenshot))
                                .when(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

                // Then
assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
                verify(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));
                verifyNoInteractions(preconditionVerificationAgentMock);
                verifyNoInteractions(uiTestStepActionAgentMock);
        }

        @Test
        @DisplayName("Precondition verification fails")
        void preconditionVerificationFails() {
                // Given
                String precondition = "Precondition 1";
                TestCase testCase = new TestCase("Precondition Verify Fail", List.of(precondition),
                                List.of(new TestStep("Dummy Step", null, null)));

                mockTestCaseExtraction(testCase);

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Precondition executed",
                                new EmptyExecutionResult(), mockScreenshot))
                                .when(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Precondition verified",
                                new VerificationExecutionResult(false, "Not Verified"), mockScreenshot))
                                .when(preconditionVerificationAgentMock).executeWithRetry(any(Supplier.class), any());

                // When
                TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

                // Then
                        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
                        assertThat(result.getGeneralErrorMessage()).contains("Precondition verification failed. Not Verified");                verify(preconditionActionAgentMock).executeAndGetResult(any(Supplier.class));
                verify(preconditionVerificationAgentMock).executeWithRetry(any(Supplier.class), any());
                verifyNoInteractions(uiTestStepActionAgentMock);
        }

        @Test
        @DisplayName("Test step action fails")
        void testStepActionFails() {
                // Given
                TestStep step = new TestStep("Action", null, "Verify");
                TestCase testCase = new TestCase("Action Fail", null, List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new UiOperationExecutionResult<>(ERROR, "Action failed", null, mockScreenshot))
                                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

                // Then
                assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
                assertThat(result.getStepResults().getFirst().getExecutionStatus())
                                .isEqualTo(TestStepResultStatus.ERROR);
                verify(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));
                verifyNoInteractions(uiTestStepVerificationAgentMock);
        }

        @Test
        @DisplayName("Test step verification fails")
        void testStepVerificationFails() {
                // Given
                TestStep step = new TestStep("Action", null, "Verify");
                TestCase testCase = new TestCase("Verification Fail", null, List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action executed", new EmptyExecutionResult(),
                                mockScreenshot))
                                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Verification executed",
                                new VerificationExecutionResult(false, "Verification failed"), mockScreenshot))
                                .when(uiTestStepVerificationAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                TestExecutionResult result = UiTestAgent.executeTestCase("test case message");

                // Then
                assertThat(result.getTestExecutionStatus()).isEqualTo(FAILED);
                assertThat(result.getStepResults().getFirst().getExecutionStatus())
                                .isEqualTo(TestStepResultStatus.FAILURE);
                verify(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));
                                verify(uiTestStepVerificationAgentMock, atLeastOnce()).executeAndGetResult(any(Supplier.class));
        }

        @Test
        @DisplayName("Execution result contains SystemInfo and logs")
        void executionResultContainsSystemInfoAndLogs() {
                // Given
                TestStep step = new TestStep("Action", null, null);
                TestCase testCase = new TestCase("System Info Test", null, List.of(step));

                mockTestCaseExtraction(testCase);

                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Action executed", new EmptyExecutionResult(),
                                mockScreenshot))
                                .when(uiTestStepActionAgentMock).executeAndGetResult(any(Supplier.class));

                // When
                org.tarik.ta.dto.UiTestExecutionResult result = (org.tarik.ta.dto.UiTestExecutionResult) UiTestAgent
                                .executeTestCase("test case message");

                // Then
                assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
                assertThat(result.getSystemInfo()).isNotNull();
                assertThat(result.getSystemInfo().device()).isNotNull();
                assertThat(result.getSystemInfo().osVersion()).isNotBlank();
                assertThat(result.getLogs()).isNotNull();
                assertThat(result.getLogs()).isNotEmpty(); // Should contain at least start/end logs
        }

        private void mockTestCaseExtraction(TestCase testCase) {
                doReturn(new UiOperationExecutionResult<>(SUCCESS, "Test case extracted", testCase, mockScreenshot))
                                .when(testCaseExtractionAgentMock).executeAndGetResult(any(Supplier.class));
        }
}
