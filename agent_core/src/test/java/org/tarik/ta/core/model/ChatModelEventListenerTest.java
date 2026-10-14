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
package org.tarik.ta.core.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.telemetry.GenAiMetrics;
import org.tarik.ta.core.utils.TestCaseExtractor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.LoggedMessages.loggedMessagesOf;
import static org.tarik.ta.core.telemetry.GenAiAttributes.OPERATION_NAME;
import static org.tarik.ta.core.telemetry.GenAiAttributes.PROVIDER_NAME;
import static org.tarik.ta.core.telemetry.GenAiAttributes.REQUEST_MODEL;
import static org.tarik.ta.core.telemetry.GenAiAttributes.RESPONSE_MODEL;
import static org.tarik.ta.core.telemetry.GenAiAttributes.SERVER_ADDRESS;

class ChatModelEventListenerTest {

    private BudgetManager budgetManager;
    private GenAiMetrics metrics;
    private ChatModelEventListener listener;

    @BeforeEach
    void setUp() {
        budgetManager = new BudgetManager(new AgentConfig());
        budgetManager.reset();
        metrics = mock(GenAiMetrics.class);
        listener = new ChatModelEventListener(budgetManager, metrics, "test_step_action", "openai", "gpt-test", "api.example.org");
    }

    @Test
    void onResponse_shouldLogAndConsumeTokens() {
        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        AiMessage aiMessage = AiMessage.from("response text");
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .modelName("test-model")
                .tokenUsage(new TokenUsage(10, 20))
                .build();

        when(responseContext.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(chatResponse.metadata()).thenReturn(metadata);

        budgetManager.callInTask("task-1", () -> {
            listener.onResponse(responseContext);
            return null;
        });

        assertThat(budgetManager.releaseTask("task-1")).singleElement()
                .satisfies(usage -> assertThat(usage.inputTokens()).isEqualTo(10))
                .satisfies(usage -> assertThat(usage.outputTokens()).isEqualTo(20));
    }

    @Test
    void onResponse_shouldRecordTheUsageOfItsOwnOperation() {
        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .modelName("gpt-test-0815")
                .tokenUsage(new TokenUsage(10, 20))
                .build();

        when(responseContext.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.aiMessage()).thenReturn(AiMessage.from("response text"));
        when(chatResponse.metadata()).thenReturn(metadata);

        listener.onResponse(responseContext);

        var attributes = Attributes.builder()
                .put(OPERATION_NAME, "test_step_action")
                .put(PROVIDER_NAME, "openai")
                .put(REQUEST_MODEL, "gpt-test")
                .put(RESPONSE_MODEL, "gpt-test-0815")
                .put(SERVER_ADDRESS, "api.example.org")
                .build();
        verify(metrics).recordTokenUsage(eq(attributes), eq(10), eq(20), eq(0));
        verify(metrics).recordOperationDuration(eq(attributes), anyLong());
    }

    @Test
    void onResponse_shouldHandleThinkingAndToolRequests() {
        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        ChatResponse chatResponse = mock(ChatResponse.class);

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("test-tool")
                .arguments("{}")
                .build();

        AiMessage aiMessage = AiMessage.builder()
                .text("text")
                .thinking("thinking")
                .toolExecutionRequests(Collections.singletonList(toolRequest))
                .build();
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().build();

        when(responseContext.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(chatResponse.metadata()).thenReturn(metadata);

        listener.onResponse(responseContext);
    }

    @Test
    void onResponse_shouldHandleEmptyResponse() {
        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        AiMessage aiMessage = AiMessage.from("");

        when(responseContext.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(chatResponse.metadata()).thenReturn(ChatResponseMetadata.builder().build());

        listener.onResponse(responseContext);
    }

    @Test
    void onRequest_shouldLogMessages() {
        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        ChatRequest chatRequest = mock(ChatRequest.class);

        List<ChatMessage> messages = List.of(
                SystemMessage.from("sys"),
                UserMessage.from("user")
        );

        when(requestContext.chatRequest()).thenReturn(chatRequest);
        when(chatRequest.messages()).thenReturn(messages);

        listener.onRequest(requestContext);
    }

    @Test
    void onRequest_shouldLogLatestMessageWhenMoreThanTwo() {
        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        ChatRequest chatRequest = mock(ChatRequest.class);

        List<ChatMessage> messages = List.of(
                SystemMessage.from("sys"),
                UserMessage.from("user"),
                AiMessage.from("ai"),
                ToolExecutionResultMessage.from("1", "tool", "result")
        );

        when(requestContext.chatRequest()).thenReturn(chatRequest);
        when(chatRequest.messages()).thenReturn(messages);

        listener.onRequest(requestContext);
    }

    @Test
    void logMessage_shouldHandleDifferentMessageTypes() {
        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        ChatRequest chatRequest = mock(ChatRequest.class);

        when(requestContext.chatRequest()).thenReturn(chatRequest);

        when(chatRequest.messages()).thenReturn(List.of(SystemMessage.from("sys")));
        listener.onRequest(requestContext);

        when(chatRequest.messages()).thenReturn(List.of(UserMessage.from("user")));
        listener.onRequest(requestContext);

        when(chatRequest.messages()).thenReturn(List.of(ToolExecutionResultMessage.from("1", "tool", "res")));
        listener.onRequest(requestContext);

        UserMessage complexUserMessage = UserMessage.from(TextContent.from("text"), ImageContent.from("base64", "image/png"));
        when(chatRequest.messages()).thenReturn(List.of(complexUserMessage));
        listener.onRequest(requestContext);
    }

    @Test
    void onError_shouldLog() {
        ChatModelErrorContext errorContext = mock(ChatModelErrorContext.class);
        when(errorContext.error()).thenReturn(new RuntimeException("test error"));
        when(errorContext.attributes()).thenReturn(Map.of("my-attribute", "val"));

        listener.onError(errorContext);
    }

    @Test
    void onRequestAndOnResponse_shouldNotLogTheContent_whenTheOperationExtractsTheTestCase() {
        var extractionListener = new ChatModelEventListener(budgetManager, metrics, TestCaseExtractor.OPERATION_NAME, "openai",
                "gpt-test", null);

        var loggedMessages = loggedMessagesOf(ChatModelEventListener.class, () -> {
            extractionListener.onRequest(requestWith(UserMessage.from("password = s3cr3t-value")));
            extractionListener.onResponse(responseWith(AiMessage.from(List.of(ToolExecutionRequest.builder()
                    .id("1").name("endExecutionAndGetFinalResult").arguments("{\"password\":\"s3cr3t-value\"}").build()))));
        });

        assertThat(loggedMessages).isNotEmpty().noneMatch(message -> message.contains("s3cr3t-value"));
    }

    @Test
    void onRequest_shouldLogTheContent_whenTheOperationIsNotTheTestCaseExtraction() {
        var loggedMessages = loggedMessagesOf(ChatModelEventListener.class,
                () -> listener.onRequest(requestWith(UserMessage.from("Click the 'Save' button"))));

        assertThat(loggedMessages).anyMatch(message -> message.contains("Click the 'Save' button"));
    }

    private static ChatModelRequestContext requestWith(ChatMessage message) {
        var requestContext = mock(ChatModelRequestContext.class);
        var chatRequest = mock(ChatRequest.class);
        when(requestContext.chatRequest()).thenReturn(chatRequest);
        when(chatRequest.messages()).thenReturn(List.of(message));
        return requestContext;
    }

    private static ChatModelResponseContext responseWith(AiMessage aiMessage) {
        var responseContext = mock(ChatModelResponseContext.class);
        var chatResponse = mock(ChatResponse.class);
        when(responseContext.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        return responseContext;
    }
}
