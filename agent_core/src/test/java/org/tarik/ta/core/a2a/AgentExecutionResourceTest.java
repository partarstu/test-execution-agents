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

import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentExecutionResourceTest {

    private static final String VALID_STREAMING_BODY =
            "{\"jsonrpc\":\"2.0\",\"method\":\"SendStreamingMessage\",\"params\":{\"message\":{\"messageId\":\"msg-1\"," +
                    "\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"hello\"}]}},\"id\":6}";

    private AgentExecutionResource resource;
    private AgentExecutor agentExecutor;
    private AgentCard agentCard;

    @BeforeEach
    void setUp() {
        agentExecutor = mock(AgentExecutor.class);
        agentCard = AgentCard.builder()
                .name("test-agent")
                .description("desc")
                .version("1.0")
                .capabilities(new AgentCapabilities(false, false, false, null))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost")))
                .build();
        jakarta.inject.Provider<AgentExecutor> agentExecutorProvider = () -> agentExecutor;
        jakarta.inject.Provider<AgentCard> agentCardProvider = () -> agentCard;
        resource = new AgentExecutionResource(agentExecutorProvider, agentCardProvider);
        resource.init();
    }

    @Test
    void handle_shouldHandleGetTask() {
        // The task store is empty, so a properly parsed and routed request must answer with TaskNotFoundError (-32001)
        // rather than the InternalError (-32603) produced when the request cannot be processed at all.
        String response = handleNonStreaming("{\"jsonrpc\":\"2.0\",\"method\":\"GetTask\",\"params\":{\"id\":\"123\"},\"id\":1}");
        assertThat(response).contains("jsonrpc").contains("-32001");
    }

    @Test
    void handle_shouldHandleCancelTask() {
        String response = handleNonStreaming("{\"jsonrpc\":\"2.0\",\"method\":\"CancelTask\",\"params\":{\"id\":\"123\"},\"id\":3}");
        assertThat(response).contains("jsonrpc").contains("-32001");
    }

    @Test
    void handle_shouldRouteSendMessageToHandler() throws Exception {
        JSONRPCHandler mockHandler = injectMockJsonRpcHandler();
        Message reply = new Message(Message.Role.ROLE_AGENT, List.of(new TextPart("done", null)), "msg-2", null, null,
                null, null, null);
        when(mockHandler.onMessageSend(any(), any())).thenReturn(new SendMessageResponse(2, reply));
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"SendMessage\",\"params\":{\"message\":{\"messageId\":\"msg-1\"," +
                "\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"hello\"}]}},\"id\":2}";

        String response = handleNonStreaming(body);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(mockHandler).onMessageSend(requestCaptor.capture(), any());
        List<?> parts = requestCaptor.getValue().getParams().message().parts();
        assertThat(parts).hasSize(1);
        assertThat(((TextPart) parts.getFirst()).text()).isEqualTo("hello");
        assertThat(response).contains("jsonrpc").contains("done");
    }

    @Test
    void handle_shouldTranslateSpecSendMethodToHandler() throws Exception {
        // A generic A2A client sends the spec name "message/send"; it must be routed to the send handler.
        JSONRPCHandler mockHandler = injectMockJsonRpcHandler();
        Message reply = new Message(Message.Role.ROLE_AGENT, List.of(new TextPart("done", null)), "msg-2", null, null,
                null, null, null);
        when(mockHandler.onMessageSend(any(), any())).thenReturn(new SendMessageResponse(2, reply));
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"message/send\",\"params\":{\"message\":{\"messageId\":\"msg-1\"," +
                "\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"hello\"}]}},\"id\":2}";

        String response = handleNonStreaming(body);

        verify(mockHandler).onMessageSend(any(), any());
        assertThat(response).contains("jsonrpc").contains("done");
    }

    @Test
    void handle_shouldTranslateSpecCancelMethodToHandler() {
        // "tasks/cancel" must route to onCancelTask; the empty task store answers TaskNotFoundError (-32001) rather
        // than the MethodNotFoundError (-32601) returned when the method name is not recognized at all.
        String response = handleNonStreaming("{\"jsonrpc\":\"2.0\",\"method\":\"tasks/cancel\",\"params\":{\"id\":\"123\"},\"id\":3}");
        assertThat(response).contains("jsonrpc").contains("-32001");
    }

    @Test
    void handle_shouldTranslateSpecStreamMethodToStreamingHandler() throws Exception {
        // "message/stream" must reach the streaming handler (not be rejected as an unknown method); a thrown handler
        // error proves the request was routed to the streaming path.
        JSONRPCHandler mockHandler = injectMockJsonRpcHandler();
        when(mockHandler.onMessageSendStream(any(), any())).thenThrow(new RuntimeException("boom"));
        Context ctx = mock(Context.class);
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"message/stream\",\"params\":{\"message\":{\"messageId\":\"msg-1\"," +
                "\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"hello\"}]}},\"id\":6}";
        when(ctx.body()).thenReturn(body);

        resource.handle(ctx);

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(ctx).result(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).contains("error").contains("boom");
    }

    @Test
    void handle_shouldHandleUnknownMethod() {
        // An unknown JSON-RPC method must be answered with MethodNotFoundError (-32601), echoing the request id.
        String response = handleNonStreaming("{\"jsonrpc\":\"2.0\",\"method\":\"unknown\",\"id\":4}");
        assertThat(response).contains("error").contains("-32601");
    }

    @Test
    void handle_shouldRejectKnownButUnsupportedMethod() {
        // "ListTasks" is a valid A2A method, but this agent does not expose it, so it answers UnsupportedOperationError.
        String response = handleNonStreaming("{\"jsonrpc\":\"2.0\",\"method\":\"ListTasks\",\"params\":{},\"id\":7}");
        assertThat(response).contains("error").contains("not supported");
    }

    @Test
    void handle_shouldHandleInvalidJson() {
        assertThat(handleNonStreaming("invalid-json")).contains("error");
    }

    @Test
    void handle_shouldReturnJsonRpcErrorForMalformedStreamingRequest() {
        // "params" is a string where SendStreamingMessage expects an object, so parsing fails. The client must receive
        // a JSON-RPC error, not an empty 200.
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"SendStreamingMessage\",\"params\":\"oops\",\"id\":5}";
        Context ctx = mock(Context.class);
        when(ctx.body()).thenReturn(body);

        resource.handle(ctx);

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(ctx).result(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).contains("jsonrpc").contains("error");
    }

    @Test
    void handle_shouldReturnJsonRpcErrorWhenStreamingHandlerFails() throws Exception {
        JSONRPCHandler mockHandler = injectMockJsonRpcHandler();
        when(mockHandler.onMessageSendStream(any(), any())).thenThrow(new RuntimeException("boom"));
        Context ctx = mock(Context.class);
        when(ctx.body()).thenReturn(VALID_STREAMING_BODY);

        resource.handle(ctx);

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(ctx).result(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).contains("error").contains("boom");
    }

    @Test
    void handle_shouldWriteServerSentEventsAsynchronously() throws Exception {
        Context ctx = mock(Context.class);
        when(ctx.body()).thenReturn(VALID_STREAMING_BODY);

        jakarta.servlet.http.HttpServletRequest req = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse res = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.AsyncContext asyncCtx = mock(jakarta.servlet.AsyncContext.class);
        when(ctx.req()).thenReturn(req);
        when(ctx.res()).thenReturn(res);
        when(req.getAsyncContext()).thenReturn(asyncCtx);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        jakarta.servlet.ServletOutputStream servletOut = new jakarta.servlet.ServletOutputStream() {
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
            @Override
            public void write(int b) { out.write(b); }
        };
        when(ctx.outputStream()).thenReturn(servletOut);

        JSONRPCHandler mockHandler = injectMockJsonRpcHandler();

        // Capture the runnable passed to ctx.async
        ArgumentCaptor<io.javalin.util.function.ThrowingRunnable> runnableCaptor = ArgumentCaptor.forClass(io.javalin.util.function.ThrowingRunnable.class);
        doNothing().when(ctx).async(any(), any());

        // Setup publisher stubbing
        TaskStatusUpdateEvent streamedEvent =
                new TaskStatusUpdateEvent("123", new TaskStatus(TaskState.TASK_STATE_WORKING), "ctx-1", null);
        Flow.Publisher<SendStreamingMessageResponse> publisher = subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                // The subscriber requests the next item from within onNext, so emit only once to avoid recursion.
                private boolean emitted = false;

                @Override
                public void request(long n) {
                    if (emitted) {
                        return;
                    }
                    emitted = true;
                    subscriber.onNext(new SendStreamingMessageResponse("123", streamedEvent));
                    subscriber.onComplete();
                }
                @Override
                public void cancel() {}
            });
        };
        when(mockHandler.onMessageSendStream(any(), any())).thenReturn(publisher);

        resource.handle(ctx);

        // Verify async was invoked and capture its runnable
        verify(ctx).async(any(), runnableCaptor.capture());

        // Execute the async runnable
        runnableCaptor.getValue().run();

        // Verify status and headers are set, and SSE event was written
        verify(res).setStatus(200);
        verify(res).setContentType("text/event-stream");
        verify(asyncCtx).complete();
        assertThat(out.toString()).contains("data:");
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

    /**
     * Replaces the resource's real {@link JSONRPCHandler} with a mock so that a test can stub handler behavior without
     * involving the full request-handler machinery.
     */
    private JSONRPCHandler injectMockJsonRpcHandler() throws Exception {
        JSONRPCHandler mockHandler = mock(JSONRPCHandler.class);
        Field field = AgentExecutionResource.class.getDeclaredField("jsonRpcHandler");
        field.setAccessible(true);
        field.set(resource, mockHandler);
        return mockHandler;
    }

    @Test
    void getAgentCard_shouldReturnJson() {
        Context ctx = mock(Context.class);
        resource.getAgentCard(ctx);

        verify(ctx).json(any());
    }
}
