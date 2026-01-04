package org.tarik.ta;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiPreconditionVerificationAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.agents.ApiTestStepVerificationAgent;
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
        private ApiTestStepVerificationAgent mockStepVerificationAgent;

        @Mock
        private AiServices<TestCaseExtractionAgent> mockExtractionAgentBuilder;
        @Mock
        private AiServices<ApiTestStepActionAgent> mockStepActionAgentBuilder;
        @Mock
        private AiServices<ApiTestStepVerificationAgent> mockStepVerificationAgentBuilder;

        @Mock
        private ChatModel mockChatModel;
        @Mock
        private ApiContext mockApiContext;

        @Test
        void extractTestCase_shouldReturnTestCase_whenAgentSucceeds() {
                String message = "run test";
                TestStep step = new TestStep("step 1", List.of(), "result 1");
                TestCase expectedTestCase = new TestCase("Test Case 1", Collections.emptyList(), List.of(step));
                AgentExecutionResult<TestCase> executionResult = new AgentExecutionResult<>(
                                AgentExecutionResult.ExecutionStatus.SUCCESS, "Success", true, expectedTestCase,
                                java.time.Instant.now());

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
                AgentExecutionResult<TestCase> executionResult = new AgentExecutionResult<>(
                                AgentExecutionResult.ExecutionStatus.ERROR, "Failed", false, null,
                                java.time.Instant.now());

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

                AgentExecutionResult<TestCase> extractionResult = new AgentExecutionResult<>(
                                AgentExecutionResult.ExecutionStatus.SUCCESS, "Success", true, expectedTestCase,
                                java.time.Instant.now());
                AgentExecutionResult<EmptyExecutionResult> actionResult = new AgentExecutionResult<>(
                                AgentExecutionResult.ExecutionStatus.SUCCESS, "Action Success", true,
                                new EmptyExecutionResult(),
                                java.time.Instant.now());
                AgentExecutionResult<VerificationExecutionResult> verificationResult = new AgentExecutionResult<>(
                                AgentExecutionResult.ExecutionStatus.SUCCESS, "Verification Success", true,
                                new VerificationExecutionResult(true, "Passed"), java.time.Instant.now());

                try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
                                MockedStatic<ModelFactory> modelFactory = mockStatic(ModelFactory.class);
                                MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class);
                                MockedStatic<AgentConfig> agentConfig = mockStatic(AgentConfig.class);
                                MockedStatic<ApiContext> apiContextStatic = mockStatic(ApiContext.class)) {

                        // Config Mocks (Simplified - relying on loose matching for brevity)
                        agentConfig.when(() -> AgentConfig.getTestCaseExtractionAgentModelName()).thenReturn("model");
                        agentConfig.when(() -> AgentConfig.getTestCaseExtractionAgentModelProvider())
                                        .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                        agentConfig.when(() -> AgentConfig.getTestCaseExtractionAgentPromptVersion()).thenReturn("v1");
                        // Add other config mocks as needed or rely on nice mocks if possible? No,
                        // strict stubbing usually.
                        // But since I mock ModelFactory and PromptUtils, I might not need all config
                        // values if I mock their consumers correctly.
                        // Actually, ApiTestAgent calls config getters to pass to ModelFactory.
                        // I need to mock ALL used config getters.
                        agentConfig.when(AgentConfig::getTestStepActionAgentModelName).thenReturn("model");
                        agentConfig.when(AgentConfig::getTestStepActionAgentModelProvider)
                                        .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                        agentConfig.when(AgentConfig::getTestStepActionAgentPromptVersion).thenReturn("v1");
                        agentConfig.when(AgentConfig::getTestStepVerificationAgentModelName).thenReturn("model");
                        agentConfig.when(AgentConfig::getTestStepVerificationAgentModelProvider)
                                        .thenReturn(AgentConfig.ModelProvider.GOOGLE);
                        agentConfig.when(AgentConfig::getTestStepVerificationAgentPromptVersion).thenReturn("v1");

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

                        // Action
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
                        when(mockStepActionAgent.executeWithRetry(any())).thenReturn(actionResult);

                        // Verification
                        aiServices.when(() -> AiServices.builder(ApiTestStepVerificationAgent.class))
                                        .thenReturn(mockStepVerificationAgentBuilder);
                        when(mockStepVerificationAgentBuilder.chatModel(any()))
                                        .thenReturn(mockStepVerificationAgentBuilder);
                        when(mockStepVerificationAgentBuilder.systemMessageProvider(any()))
                                        .thenReturn(mockStepVerificationAgentBuilder);
                        when(mockStepVerificationAgentBuilder.tools(any(Object[].class)))
                                        .thenReturn(mockStepVerificationAgentBuilder);
                        when(mockStepVerificationAgentBuilder.toolExecutionErrorHandler(any()))
                                        .thenReturn(mockStepVerificationAgentBuilder);
                        when(mockStepVerificationAgentBuilder.maxSequentialToolsInvocations(anyInt()))
                                        .thenReturn(mockStepVerificationAgentBuilder);
                        when(mockStepVerificationAgentBuilder.build()).thenReturn(mockStepVerificationAgent);
                        when(mockStepVerificationAgent.executeWithRetry(any(), any())).thenReturn(verificationResult);

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