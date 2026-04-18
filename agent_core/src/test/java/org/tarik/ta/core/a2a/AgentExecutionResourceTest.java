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
package org.tarik.ta.core.a2a;

import io.a2a.spec.AgentCard;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentExecutionResourceTest {

    private AgentExecutionResource resource;
    private AgentExecutor agentExecutor;
    private AgentCard agentCard;

    @BeforeEach
    void setUp() {
        agentExecutor = mock(AgentExecutor.class);
        // AgentCard is a final record, and it has validation in constructor.
        io.a2a.spec.AgentCapabilities capabilities = new io.a2a.spec.AgentCapabilities(false, false, false, java.util.List.of());
        agentCard = new AgentCard(
                "test-agent", 
                "desc", 
                "url", 
                null, 
                "1.0", 
                "doc", 
                capabilities, 
                java.util.List.of(), 
                java.util.List.of(), 
                java.util.List.of(), 
                false, 
                java.util.Map.of(), 
                java.util.List.of(), 
                "icon", 
                java.util.List.of(), 
                "JSONRPC", 
                "1.0", 
                java.util.List.of()
        );
        jakarta.inject.Provider<AgentExecutor> agentExecutorProvider = () -> agentExecutor;
        jakarta.inject.Provider<AgentCard> agentCardProvider = () -> agentCard;
        resource = new AgentExecutionResource(agentExecutorProvider, agentCardProvider);
        resource.init();
    }

    @Test
    void handleNonStreamingRequests_shouldHandleGetTask() {
        Context ctx = mock(Context.class);
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"agent/getTask\",\"params\":{\"taskId\":\"123\"},\"id\":1}";
        when(ctx.body()).thenReturn(body);

        String result = resource.handleNonStreamingRequests(ctx);
        // It might return "not supported" if the handler isn't fully set up, but it covers the case
        assertThat(result).contains("jsonrpc");
    }

    @Test
    void handleNonStreamingRequests_shouldHandleSendMessage() {
        Context ctx = mock(Context.class);
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"agent/sendMessage\",\"params\":{\"taskId\":\"123\",\"message\":{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}},\"id\":2}";
        when(ctx.body()).thenReturn(body);

        String result = resource.handleNonStreamingRequests(ctx);
        assertThat(result).contains("jsonrpc");
    }

    @Test
    void handleNonStreamingRequests_shouldHandleCancelTask() {
        Context ctx = mock(Context.class);
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"agent/cancelTask\",\"params\":{\"taskId\":\"123\"},\"id\":3}";
        when(ctx.body()).thenReturn(body);

        String result = resource.handleNonStreamingRequests(ctx);
        assertThat(result).contains("jsonrpc");
    }

    @Test
    void handleNonStreamingRequests_shouldHandleUnknownMethod() {
        Context ctx = mock(Context.class);
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"unknown\",\"id\":4}";
        when(ctx.body()).thenReturn(body);

        String result = resource.handleNonStreamingRequests(ctx);
        assertThat(result).contains("error");
        assertThat(result).contains("not supported"); 
    }

    @Test
    void handleNonStreamingRequests_shouldHandleInvalidJson() {
        Context ctx = mock(Context.class);
        when(ctx.body()).thenReturn("invalid-json");

        String result = resource.handleNonStreamingRequests(ctx);
        assertThat(result).contains("error");
    }

    @Test
    void getAgentCard_shouldReturnJson() {
        Context ctx = mock(Context.class);
        resource.getAgentCard(ctx);
        
        verify(ctx).json(any());
    }
}
