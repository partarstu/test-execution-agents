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
package org.tarik.ta.core.a2a;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentExecutionResourceTest {

    private AgentExecutionResource resource;
    private AgentExecutor agentExecutor;
    private AgentCard agentCard;

    @BeforeEach
    void setUp() {
        agentExecutor = mock(AgentExecutor.class);
        agentCard = new AgentCard(
                "test-agent",
                "desc",
                null,
                "1.0",
                null,
                new AgentCapabilities(false, false, false, null),
                List.of("text"),
                List.of("text"),
                List.of(),
                null,
                null,
                null,
                List.of(new AgentInterface("JSONRPC", "http://localhost")),
                null
        );
        jakarta.inject.Provider<AgentExecutor> agentExecutorProvider = () -> agentExecutor;
        jakarta.inject.Provider<AgentCard> agentCardProvider = () -> agentCard;
        resource = new AgentExecutionResource(agentExecutorProvider, agentCardProvider);
        resource.init();
    }

    @Test
    void handle_shouldHandleGetTask() {
        assertThat(handleNonStreaming("{\"jsonrpc\":\"2.0\",\"method\":\"agent/getTask\",\"params\":{\"taskId\":\"123\"},\"id\":1}"))
                .contains("jsonrpc");
    }

    @Test
    void handle_shouldHandleSendMessage() {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"agent/sendMessage\",\"params\":{\"taskId\":\"123\",\"message\":{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}},\"id\":2}";
        assertThat(handleNonStreaming(body)).contains("jsonrpc");
    }

    @Test
    void handle_shouldHandleCancelTask() {
        assertThat(handleNonStreaming("{\"jsonrpc\":\"2.0\",\"method\":\"agent/cancelTask\",\"params\":{\"taskId\":\"123\"},\"id\":3}"))
                .contains("jsonrpc");
    }

    @Test
    void handle_shouldHandleUnknownMethod() {
        String result = handleNonStreaming("{\"jsonrpc\":\"2.0\",\"method\":\"unknown\",\"id\":4}");
        assertThat(result).contains("error");
        assertThat(result).contains("not supported");
    }

    @Test
    void handle_shouldHandleInvalidJson() {
        assertThat(handleNonStreaming("invalid-json")).contains("error");
    }

    /**
     * Drives {@link AgentExecutionResource#handle(Context)} through the non-streaming branch and returns the JSON
     * response written to the Javalin context.
     */
    private String handleNonStreaming(String body) {
        Context ctx = mock(Context.class);
        when(ctx.body()).thenReturn(body);

        resource.handle(ctx);

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(ctx).result(responseCaptor.capture());
        return responseCaptor.getValue();
    }

    @Test
    void getAgentCard_shouldReturnJson() {
        Context ctx = mock(Context.class);
        resource.getAgentCard(ctx);
        
        verify(ctx).json(any());
    }
}
