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
package org.tarik.ta.core.utils;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.AgentExecutionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCaseExtractorTest {

    @Mock
    private TestCaseExtractionAgent mockExtractionAgent;
    @Mock
    private AiServices<TestCaseExtractionAgent> mockExtractionAgentBuilder;
    @Mock
    private ChatModel mockChatModel;

    @Test
    void extractTestCase_shouldReturnTestCase_whenAgentSucceeds() {
        String message = "run test";
        TestStep step = new TestStep("step 1", List.of(), "result 1");
        TestCase expectedTestCase = new TestCase("Test Case 1", Collections.emptyList(), List.of(step));
        AgentExecutionResult<TestCase> executionResult = new AgentExecutionResult<>(
                AgentExecutionResult.ExecutionStatus.SUCCESS, "Success", true, expectedTestCase,
                Instant.now());

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
            when(mockExtractionAgentBuilder.systemMessageProvider(any())).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.tools(any(Object[].class))).thenReturn(mockExtractionAgentBuilder);
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
                AgentExecutionResult.ExecutionStatus.ERROR, "Failed", false, null, Instant.now());

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
            when(mockExtractionAgentBuilder.systemMessageProvider(any())).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.tools(any(Object[].class))).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.build()).thenReturn(mockExtractionAgent);

            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(executionResult);

            Optional<TestCase> result = TestCaseExtractor.extractTestCase(message);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void extractTestCase_shouldReturnEmpty_whenMessageIsBlank() {
        Optional<TestCase> result = TestCaseExtractor.extractTestCase("");
        assertThat(result).isEmpty();

        result = TestCaseExtractor.extractTestCase(null);
        assertThat(result).isEmpty();

        result = TestCaseExtractor.extractTestCase("   ");
        assertThat(result).isEmpty();
    }

    @Test
    void extractTestCase_shouldReturnEmpty_whenTestCaseHasNoName() {
        String message = "run test";
        TestStep step = new TestStep("step 1", List.of(), "result 1");
        TestCase invalidTestCase = new TestCase("", Collections.emptyList(), List.of(step));
        AgentExecutionResult<TestCase> executionResult = new AgentExecutionResult<>(
                AgentExecutionResult.ExecutionStatus.SUCCESS, "Success", true, invalidTestCase,
                Instant.now());

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
            when(mockExtractionAgentBuilder.systemMessageProvider(any())).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.tools(any(Object[].class))).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.build()).thenReturn(mockExtractionAgent);

            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(executionResult);

            Optional<TestCase> result = TestCaseExtractor.extractTestCase(message);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void extractTestCase_shouldReturnEmpty_whenTestCaseHasNoSteps() {
        String message = "run test";
        TestCase invalidTestCase = new TestCase("Test Case", Collections.emptyList(), Collections.emptyList());
        AgentExecutionResult<TestCase> executionResult = new AgentExecutionResult<>(
                AgentExecutionResult.ExecutionStatus.SUCCESS, "Success", true, invalidTestCase,
                Instant.now());

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
            when(mockExtractionAgentBuilder.systemMessageProvider(any())).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.tools(any(Object[].class))).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.build()).thenReturn(mockExtractionAgent);

            when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(executionResult);

            Optional<TestCase> result = TestCaseExtractor.extractTestCase(message);

            assertThat(result).isEmpty();
        }
    }
}
