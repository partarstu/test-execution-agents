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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.*;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.utils.PromptUtils;
import org.tarik.ta.core.utils.TestCaseExtractor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiTestAgentTest {

    @Mock
    private TestCaseExtractionAgent mockExtractionAgent;
    @Mock
    private ApiTestStepActionAgent mockStepActionAgent;
    @Mock
    private ApiPreconditionActionAgent mockPreconditionActionAgent;

    @Mock
    private AiServices<TestCaseExtractionAgent> mockExtractionAgentBuilder;
    @Mock
    private AiServices<ApiTestStepActionAgent> mockStepActionAgentBuilder;
    @Mock
    private AiServices<ApiPreconditionActionAgent> mockPreconditionActionAgentBuilder;

    @Mock
    private ChatModel mockChatModel;
    @Mock
    private ApiContext mockApiContext;

    @Test
    void extractTestCase_shouldReturnTestCase_whenAgentSucceeds() {
        String message = "run test";
        TestStep step = new TestStep("step 1", List.of(), "result 1");
        TestCase expectedTestCase = new TestCase("Test Case 1", Collections.emptyList(), List.of(step));
        OperationExecutionResult<TestCase> executionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", expectedTestCase);

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
             MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class)) {

            // Mock Config
            agentConfig.when(AgentConfig::getTestCaseExtractionAgentModelName).thenReturn("model-name");
            agentConfig.when(AgentConfig::getTestCaseExtractionAgentModelProvider)
                    .thenReturn(AgentConfig.ModelProvider.GOOGLE);
            agentConfig.when(AgentConfig::getTestCaseExtractionAgentPromptVersion).thenReturn("v1");

            // Mock ModelFactory
            when(ModelFactory.getModel(any(), any())).thenReturn(new GenAiModel(mockChatModel));

            // Mock PromptUtils
            when(PromptUtils.loadSystemPrompt(any(), any(), any())).thenReturn("system prompt");

            // Mock AiServices
            aiServices.when(() -> AiServices.builder(TestCaseExtractionAgent.class))
                    .thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.chatModel(any())).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.systemMessageProvider(any()))
                    .thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.toolProvider(any()))
                    .thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.build()).thenReturn(mockExtractionAgent);

            // Mock Agent Execution
            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(executionResult);

            // Execute
            Optional<TestCase> result = TestCaseExtractor.extractTestCase(message);

            // Verify
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedTestCase);
        }
    }

    @Test
    void extractTestCase_shouldReturnEmpty_whenAgentFails() {
        String message = "run test";
        OperationExecutionResult<TestCase> executionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.ERROR, "Failed", null);

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
             MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class)) {

            agentConfig.when(AgentConfig::getTestCaseExtractionAgentModelName).thenReturn("model-name");
            agentConfig.when(AgentConfig::getTestCaseExtractionAgentModelProvider)
                    .thenReturn(AgentConfig.ModelProvider.GOOGLE);
            agentConfig.when(AgentConfig::getTestCaseExtractionAgentPromptVersion).thenReturn("v1");

            when(ModelFactory.getModel(any(), any())).thenReturn(new GenAiModel(mockChatModel));
            when(PromptUtils.loadSystemPrompt(any(), any(), any())).thenReturn("system prompt");

            aiServices.when(() -> AiServices.builder(TestCaseExtractionAgent.class))
                    .thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.chatModel(any())).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.systemMessageProvider(any()))
                    .thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.toolProvider(any()))
                    .thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.build()).thenReturn(mockExtractionAgent);

            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(executionResult);

            Optional<TestCase> result = TestCaseExtractor.extractTestCase(message);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void executeTestCase_shouldPass_whenAllStepsSucceed() {
        String message = "run test";
        TestStep step = new TestStep("step 1", List.of(), "result 1");
        TestCase expectedTestCase = new TestCase("Test Case 1", Collections.emptyList(), List.of(step));

        OperationExecutionResult<TestCase> extractionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", expectedTestCase);
        OperationExecutionResult<VerificationExecutionResult> actionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Action Success",
                new VerificationExecutionResult(true, "Passed"));

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
             MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class);
             MockedStatic<ApiContext> apiContextStatic = mockStatic(ApiContext.class)) {

            mockConfigAndBuilders(aiServices, modelFactory, promptUtils, agentConfig, apiContextStatic);

            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(extractionResult);
            when(mockStepActionAgent.executeWithRetry(any(), any())).thenReturn(actionResult);

            // Execute
            TestExecutionResult result = ApiTestAgent.executeTestCase(message);

            // Verify
            assertThat(result.getTestExecutionStatus())
                    .isEqualTo(TestExecutionResult.TestExecutionStatus.PASSED);
            assertThat(result.getTestCaseName()).isEqualTo("Test Case 1");
            assertThat(result.getStepResults()).hasSize(1);
            assertThat(result.getStepResults().get(0).getExecutionStatus())
                    .isEqualTo(TestStepResult.TestStepResultStatus.SUCCESS);
        }
    }

    @Test
    void executeTestCase_shouldReturnError_whenExtractionFails() {
        String message = "run test";
        OperationExecutionResult<TestCase> extractionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.ERROR, "Extraction Failed", null);

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
             MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class);
             MockedStatic<ApiContext> apiContextStatic = mockStatic(ApiContext.class)) {

            mockConfigAndBuilders(aiServices, modelFactory, promptUtils, agentConfig, apiContextStatic);
            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(extractionResult);

            TestExecutionResult result = ApiTestAgent.executeTestCase(message);

            assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.ERROR);
            assertThat(result.getGeneralErrorMessage()).contains("Could not extract test case");
        }
    }

    @Test
    void executeTestCase_shouldReturnError_whenPreconditionExecutionFails() {
        String message = "run test";
        TestCase testCase = new TestCase("Test Case", List.of("precondition 1"), List.of(new TestStep("step", null, "result")));
        OperationExecutionResult<TestCase> extractionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", testCase);
        OperationExecutionResult<VerificationExecutionResult> preconditionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.ERROR, "Precondition Execution Error", null);

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
             MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class);
             MockedStatic<ApiContext> apiContextStatic = mockStatic(ApiContext.class)) {

            mockConfigAndBuilders(aiServices, modelFactory, promptUtils, agentConfig, apiContextStatic);
            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(extractionResult);
            when(mockPreconditionActionAgent.executeWithRetry(any(), any())).thenReturn(preconditionResult);

            TestExecutionResult result = ApiTestAgent.executeTestCase(message);

            assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.FAILED);
            assertThat(result.getGeneralErrorMessage()).contains("Failure while executing precondition");
        }
    }

    @Test
    void executeTestCase_shouldReturnFailed_whenPreconditionVerificationFails() {
        String message = "run test";
        TestCase testCase = new TestCase("Test Case", List.of("precondition 1"), List.of(new TestStep("step", null, "result")));
        OperationExecutionResult<TestCase> extractionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", testCase);
        OperationExecutionResult<VerificationExecutionResult> preconditionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Precondition Success",
                new VerificationExecutionResult(false, "Verification failed message"));

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
             MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class);
             MockedStatic<ApiContext> apiContextStatic = mockStatic(ApiContext.class)) {

            mockConfigAndBuilders(aiServices, modelFactory, promptUtils, agentConfig, apiContextStatic);
            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(extractionResult);
            when(mockPreconditionActionAgent.executeWithRetry(any(), any())).thenReturn(preconditionResult);

            TestExecutionResult result = ApiTestAgent.executeTestCase(message);

            assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.FAILED);
            assertThat(result.getGeneralErrorMessage()).contains("Precondition verification failed");
        }
    }

    @Test
    void executeTestCase_shouldReturnError_whenStepExecutionFails() {
        String message = "run test";
        TestCase testCase = new TestCase("Test Case", List.of(), List.of(new TestStep("step", null, "result")));
        OperationExecutionResult<TestCase> extractionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", testCase);
        OperationExecutionResult<VerificationExecutionResult> stepResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.ERROR, "Step Execution Error", null);

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
             MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class);
             MockedStatic<ApiContext> apiContextStatic = mockStatic(ApiContext.class)) {

            mockConfigAndBuilders(aiServices, modelFactory, promptUtils, agentConfig, apiContextStatic);
            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(extractionResult);
            when(mockStepActionAgent.executeWithRetry(any(), any())).thenReturn(stepResult);

            TestExecutionResult result = ApiTestAgent.executeTestCase(message);

            assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.ERROR);
            assertThat(result.getGeneralErrorMessage()).contains("Error while executing test step");
        }
    }

    @Test
    void executeTestCase_shouldReturnFailed_whenStepVerificationFails() {
        String message = "run test";
        TestCase testCase = new TestCase("Test Case", List.of(), List.of(new TestStep("step", null, "result")));
        OperationExecutionResult<TestCase> extractionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", testCase);
        OperationExecutionResult<VerificationExecutionResult> stepResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Step Success",
                new VerificationExecutionResult(false, "Verification failed message"));

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
             MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class);
             MockedStatic<ApiContext> apiContextStatic = mockStatic(ApiContext.class)) {

            mockConfigAndBuilders(aiServices, modelFactory, promptUtils, agentConfig, apiContextStatic);
            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(extractionResult);
            when(mockStepActionAgent.executeWithRetry(any(), any())).thenReturn(stepResult);

            TestExecutionResult result = ApiTestAgent.executeTestCase(message);

            assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.FAILED);
            assertThat(result.getGeneralErrorMessage()).contains("Verification failed");
        }
    }

    @Test
    void executeTestCase_shouldReturnError_whenUnexpectedExceptionOccursInStep() {
        String message = "run test";
        TestCase testCase = new TestCase("Test Case", List.of(), List.of(new TestStep("step", null, "result")));
        OperationExecutionResult<TestCase> extractionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", testCase);

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
             MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class);
             MockedStatic<ApiContext> apiContextStatic = mockStatic(ApiContext.class)) {

            mockConfigAndBuilders(aiServices, modelFactory, promptUtils, agentConfig, apiContextStatic);
            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(extractionResult);
            when(mockStepActionAgent.executeWithRetry(any(), any())).thenThrow(new RuntimeException("Unexpected Error"));

            TestExecutionResult result = ApiTestAgent.executeTestCase(message);

            assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.ERROR);
            assertThat(result.getGeneralErrorMessage()).isEqualTo("Unexpected Error");
        }
    }

    private void mockConfigAndBuilders(MockedStatic<AiServices> aiServices,
                                       MockedStatic<ModelFactory> modelFactory,
                                       MockedStatic<PromptUtils> promptUtils,
                                       MockedStatic<AgentConfig> agentConfig,
                                       MockedStatic<ApiContext> apiContextStatic) {
        // Config Mocks
        agentConfig.when(AgentConfig::getTestCaseExtractionAgentModelName).thenReturn("model");
        agentConfig.when(AgentConfig::getTestCaseExtractionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfig.when(AgentConfig::getTestCaseExtractionAgentPromptVersion).thenReturn("v1");
        agentConfig.when(AgentConfig::getTestStepActionAgentModelName).thenReturn("model");
        agentConfig.when(AgentConfig::getTestStepActionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfig.when(AgentConfig::getTestStepActionAgentPromptVersion).thenReturn("v1");
        agentConfig.when(AgentConfig::getPreconditionActionAgentModelName).thenReturn("model");
        agentConfig.when(AgentConfig::getPreconditionActionAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        agentConfig.when(AgentConfig::getPreconditionAgentPromptVersion).thenReturn("v1");
        agentConfig.when(AgentConfig::getAgentToolCallsBudget).thenReturn(5);

        // Mock Factories
        modelFactory.when(() -> ModelFactory.getModel(any(), any())).thenReturn(new GenAiModel(mockChatModel));
        promptUtils.when(() -> PromptUtils.loadSystemPrompt(any(), any(), any())).thenReturn("system prompt");
        apiContextStatic.when(ApiContext::createFromConfig).thenReturn(mockApiContext);

        // Mock AiServices Builders
        // Extraction
        aiServices.when(() -> AiServices.builder(TestCaseExtractionAgent.class)).thenReturn(mockExtractionAgentBuilder);
        lenient().when(mockExtractionAgentBuilder.chatModel(any())).thenReturn(mockExtractionAgentBuilder);
        lenient().when(mockExtractionAgentBuilder.systemMessageProvider(any())).thenReturn(mockExtractionAgentBuilder);
        lenient().when(mockExtractionAgentBuilder.toolProvider(any())).thenReturn(mockExtractionAgentBuilder);
        lenient().when(mockExtractionAgentBuilder.build()).thenReturn(mockExtractionAgent);

        // Step Action
        aiServices.when(() -> AiServices.builder(ApiTestStepActionAgent.class)).thenReturn(mockStepActionAgentBuilder);
        lenient().when(mockStepActionAgentBuilder.chatModel(any())).thenReturn(mockStepActionAgentBuilder);
        lenient().when(mockStepActionAgentBuilder.systemMessageProvider(any())).thenReturn(mockStepActionAgentBuilder);
        lenient().when(mockStepActionAgentBuilder.toolProvider(any())).thenReturn(mockStepActionAgentBuilder);
        lenient().when(mockStepActionAgentBuilder.toolExecutionErrorHandler(any())).thenReturn(mockStepActionAgentBuilder);
        lenient().when(mockStepActionAgentBuilder.maxSequentialToolsInvocations(anyInt())).thenReturn(mockStepActionAgentBuilder);
        lenient().when(mockStepActionAgentBuilder.build()).thenReturn(mockStepActionAgent);

        // Precondition Action
        aiServices.when(() -> AiServices.builder(ApiPreconditionActionAgent.class)).thenReturn(mockPreconditionActionAgentBuilder);
        lenient().when(mockPreconditionActionAgentBuilder.chatModel(any())).thenReturn(mockPreconditionActionAgentBuilder);
        lenient().when(mockPreconditionActionAgentBuilder.systemMessageProvider(any())).thenReturn(mockPreconditionActionAgentBuilder);
        lenient().when(mockPreconditionActionAgentBuilder.toolProvider(any())).thenReturn(mockPreconditionActionAgentBuilder);
        lenient().when(mockPreconditionActionAgentBuilder.toolExecutionErrorHandler(any())).thenReturn(mockPreconditionActionAgentBuilder);
        lenient().when(mockPreconditionActionAgentBuilder.maxSequentialToolsInvocations(anyInt())).thenReturn(mockPreconditionActionAgentBuilder);
        lenient().when(mockPreconditionActionAgentBuilder.build()).thenReturn(mockPreconditionActionAgent);
    }
}