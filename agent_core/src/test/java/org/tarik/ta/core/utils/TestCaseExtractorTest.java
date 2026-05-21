/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.utils;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;

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
    private AgentConfig mockAgentConfig;
    @Mock
    private ModelFactory mockModelFactory;
    @Mock
    private TestCaseExtractionAgent mockExtractionAgent;
    @Mock
    private AiServices<TestCaseExtractionAgent> mockExtractionAgentBuilder;
    @Mock
    private ChatModel mockChatModel;

    private TestCaseExtractor extractor;

    @BeforeEach
    void setUp() {
        when(mockAgentConfig.getTestCaseExtractionAgentModelName()).thenReturn("model-name");
        when(mockAgentConfig.getTestCaseExtractionAgentModelProvider()).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        when(mockAgentConfig.getTestCaseExtractionAgentPromptVersion()).thenReturn("v1");
        when(mockModelFactory.getModel(any(), any())).thenReturn(new GenAiModel(mockChatModel));

        try (MockedStatic<AiServices> aiServices = mockStatic(AiServices.class);
             MockedStatic<PromptUtils> promptUtils = mockStatic(PromptUtils.class)) {

            when(PromptUtils.loadSystemPrompt(any(), any(), any())).thenReturn("system prompt");
            aiServices.when(() -> AiServices.builder(TestCaseExtractionAgent.class))
                    .thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.chatModel(any())).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.systemMessageProvider(any())).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.toolProvider(any())).thenReturn(mockExtractionAgentBuilder);
            when(mockExtractionAgentBuilder.build()).thenReturn(mockExtractionAgent);

            extractor = new TestCaseExtractor(mockModelFactory, mockAgentConfig);
        }
    }

    @Test
    void extractTestCase_shouldReturnTestCase_whenAgentSucceeds() {
        String message = "run test";
        TestStep step = new TestStep("step 1", List.of(), "result 1");
        TestCase expectedTestCase = new TestCase("Test Case 1", Collections.emptyList(), List.of(step));
        OperationExecutionResult<TestCase> executionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", expectedTestCase);

        when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(executionResult);

        Optional<TestCase> result = extractor.extractTestCase(message);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expectedTestCase);
    }

    @Test
    void extractTestCase_shouldReturnEmpty_whenAgentFails() {
        String message = "run test";
        OperationExecutionResult<TestCase> executionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.ERROR, "Failed", null);

        when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(executionResult);

        Optional<TestCase> result = extractor.extractTestCase(message);

        assertThat(result).isEmpty();
    }

    @Test
    void extractTestCase_shouldReturnEmpty_whenMessageIsBlank() {
        assertThat(extractor.extractTestCase("")).isEmpty();
        assertThat(extractor.extractTestCase(null)).isEmpty();
        assertThat(extractor.extractTestCase("   ")).isEmpty();
    }

    @Test
    void extractTestCase_shouldReturnEmpty_whenTestCaseHasNoName() {
        String message = "run test";
        TestStep step = new TestStep("step 1", List.of(), "result 1");
        TestCase invalidTestCase = new TestCase("", Collections.emptyList(), List.of(step));
        OperationExecutionResult<TestCase> executionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", invalidTestCase);

        when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(executionResult);

        Optional<TestCase> result = extractor.extractTestCase(message);

        assertThat(result).isEmpty();
    }

    @Test
    void extractTestCase_shouldReturnEmpty_whenTestCaseHasNoSteps() {
        String message = "run test";
        TestCase invalidTestCase = new TestCase("Test Case", Collections.emptyList(), Collections.emptyList());
        OperationExecutionResult<TestCase> executionResult = new OperationExecutionResult<>(
                OperationExecutionResult.ExecutionStatus.SUCCESS, "Success", invalidTestCase);

        when(mockExtractionAgent.executeAndGetResult(any())).thenReturn(executionResult);

        Optional<TestCase> result = extractor.extractTestCase(message);

        assertThat(result).isEmpty();
    }
}
