package org.tarik.ta;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
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
        private AiServices<TestCaseExtractionAgent> mockExtractionAgentBuilder;
        @Mock
        private AiServices<ApiTestStepActionAgent> mockStepActionAgentBuilder;

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
                        when(mockExtractionAgentBuilder.tools(any(Object[].class)))
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
                        when(mockExtractionAgentBuilder.tools(any(Object[].class)))
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

                        // Config Mocks
                        agentConfig.when(() -> AgentConfig.getTestCaseExtractionAgentModelName()).thenReturn("model");
                        agentConfig.when(() -> AgentConfig.getTestCaseExtractionAgentModelProvider())
                                        .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                        agentConfig.when(() -> AgentConfig.getTestCaseExtractionAgentPromptVersion()).thenReturn("v1");
                        agentConfig.when(AgentConfig::getTestStepActionAgentModelName).thenReturn("model");
                        agentConfig.when(AgentConfig::getTestStepActionAgentModelProvider)
                                        .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                        agentConfig.when(AgentConfig::getTestStepActionAgentPromptVersion).thenReturn("v1");

                        // Mock Factories
                        when(ModelFactory.getModel(any(), any())).thenReturn(new GenAiModel(mockChatModel));
                        when(PromptUtils.loadSystemPrompt(any(), any(), any())).thenReturn("system prompt");
                        apiContextStatic.when(ApiContext::createFromConfig).thenReturn(mockApiContext);

                        // Mock AiServices Builders
                        // Extraction
                        aiServices.when(() -> AiServices.builder(TestCaseExtractionAgent.class))
                                        .thenReturn(mockExtractionAgentBuilder);
                        when(mockExtractionAgentBuilder.chatModel(any())).thenReturn(mockExtractionAgentBuilder);
                        when(mockExtractionAgentBuilder.systemMessageProvider(any()))
                                        .thenReturn(mockExtractionAgentBuilder);
                        when(mockExtractionAgentBuilder.tools(any(Object[].class)))
                                        .thenReturn(mockExtractionAgentBuilder);
                        when(mockExtractionAgentBuilder.build()).thenReturn(mockExtractionAgent);
                        when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(extractionResult);

                        // Action (now includes verification)
                        aiServices.when(() -> AiServices.builder(ApiTestStepActionAgent.class))
                                        .thenReturn(mockStepActionAgentBuilder);
                        when(mockStepActionAgentBuilder.chatModel(any())).thenReturn(mockStepActionAgentBuilder);
                        when(mockStepActionAgentBuilder.systemMessageProvider(any()))
                                        .thenReturn(mockStepActionAgentBuilder);
                        when(mockStepActionAgentBuilder.tools(any(Object[].class)))
                                        .thenReturn(mockStepActionAgentBuilder);
                        when(mockStepActionAgentBuilder.toolExecutionErrorHandler(any()))
                                        .thenReturn(mockStepActionAgentBuilder);
                        when(mockStepActionAgentBuilder.maxSequentialToolsInvocations(anyInt()))
                                        .thenReturn(mockStepActionAgentBuilder);
                        when(mockStepActionAgentBuilder.build()).thenReturn(mockStepActionAgent);
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
}