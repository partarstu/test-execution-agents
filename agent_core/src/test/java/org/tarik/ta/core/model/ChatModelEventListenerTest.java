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
import org.tarik.ta.core.manager.BudgetManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatModelEventListenerTest {

    private ChatModelEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatModelEventListener();
        BudgetManager.reset();
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

        assertThat(BudgetManager.getAccumulatedInputTokens("test-model")).isEqualTo(10);
        assertThat(BudgetManager.getAccumulatedOutputTokens("test-model")).isEqualTo(20);
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
        // Mainly checking no exception and logs are triggered (visually or via LogCapture if needed)
    }

    @Test
    void onResponse_shouldHandleEmptyResponse() {
        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        AiMessage aiMessage = AiMessage.from(""); // Empty text

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
        
        // Testing logMessage indirectly via onRequest with 1 message
        when(requestContext.chatRequest()).thenReturn(chatRequest);

        // System
        when(chatRequest.messages()).thenReturn(List.of(SystemMessage.from("sys")));
        listener.onRequest(requestContext);

        // User
        when(chatRequest.messages()).thenReturn(List.of(UserMessage.from("user")));
        listener.onRequest(requestContext);

        // Tool Result
        when(chatRequest.messages()).thenReturn(List.of(ToolExecutionResultMessage.from("1", "tool", "res")));
        listener.onRequest(requestContext);
        
        // User Message with non-text content (simulated via mock if possible, or just UserMessage.from)
        // UserMessage can have multiple contents
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
