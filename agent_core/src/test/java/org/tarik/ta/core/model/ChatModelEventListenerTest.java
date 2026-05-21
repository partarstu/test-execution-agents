/*
 * agent-core - ${project.description}
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.manager.BudgetManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatModelEventListenerTest {

    private BudgetManager budgetManager;
    private ChatModelEventListener listener;

    @BeforeEach
    void setUp() {
        budgetManager = new BudgetManager(new AgentConfig());
        budgetManager.reset();
        listener = new ChatModelEventListener(budgetManager);
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

        listener.onResponse(responseContext);

        assertThat(budgetManager.getAccumulatedInputTokens("test-model")).isEqualTo(10);
        assertThat(budgetManager.getAccumulatedOutputTokens("test-model")).isEqualTo(20);
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
}
