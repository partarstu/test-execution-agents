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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.jsonrpc.common.wrappers.A2AErrorResponse;
import io.a2a.jsonrpc.common.wrappers.A2AResponse;
import io.a2a.jsonrpc.common.wrappers.CancelTaskRequest;
import io.a2a.jsonrpc.common.wrappers.GetTaskRequest;
import io.a2a.jsonrpc.common.wrappers.SendMessageRequest;
import io.a2a.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import io.a2a.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import io.a2a.jsonrpc.common.wrappers.SubscribeToTaskRequest;
import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.events.MainEventBus;
import io.a2a.server.events.MainEventBusProcessor;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.util.sse.SseFormatter;
import io.a2a.spec.A2AMethods;
import io.a2a.spec.AgentCard;
import io.a2a.spec.InternalError;
import io.a2a.spec.UnsupportedOperationError;
import io.a2a.transport.jsonrpc.handler.JSONRPCHandler;
import io.javalin.http.Context;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

@Singleton
public class AgentExecutionResource {
    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutionResource.class);
    private static final PushNotificationConfigStore pushNotificationConfigStore = new InMemoryPushNotificationConfigStore();
    private final Provider<AgentExecutor> agentExecutorProvider;
    private final Provider<AgentCard> agentCardProvider;
    private final ObjectMapper objectMapper;
    private JSONRPCHandler jsonRpcHandler;

    @Inject
    public AgentExecutionResource(Provider<AgentExecutor> agentExecutorProvider, Provider<AgentCard> agentCardProvider) {
        this.agentExecutorProvider = agentExecutorProvider;
        this.agentCardProvider = agentCardProvider;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @PostConstruct
    void init() {
        var taskExecutor = newSingleThreadExecutor();
        var eventConsumerExecutor = newSingleThreadExecutor();
        var taskStore = new InMemoryTaskStore();
        var mainEventBus = new MainEventBus();
        var queueManager = new InMemoryQueueManager(taskStore, mainEventBus);
        var pushNotificationSender = new BasePushNotificationSender(pushNotificationConfigStore);
        var mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, pushNotificationSender, queueManager);
        mainEventBusProcessor.ensureStarted();
        DefaultRequestHandler httpRequestHandler = DefaultRequestHandler.create(agentExecutorProvider.get(),
                taskStore, queueManager, pushNotificationConfigStore,
                mainEventBusProcessor, taskExecutor, eventConsumerExecutor);
        this.jsonRpcHandler = new JSONRPCHandler(agentCardProvider.get(), httpRequestHandler, taskExecutor);
    }

    /**
     * Entry point for the main A2A endpoint. Streaming methods ({@code SendStreamingMessage}, {@code SubscribeToTask})
     * are answered with a Server-Sent Events stream; every other method is answered with a single JSON-RPC response.
     */
    public void handle(@NotNull Context context) {
        var body = context.body();
        if (isStreamingMethod(extractMethod(body))) {
            handleStreamingRequest(context, body);
        } else {
            context.result(handleNonStreamingRequest(body));
        }
    }

    /**
     * Handles a single non-streaming JSON-RPC request.
     *
     * @return the JSON-RPC response which may be an error response
     */
    private String handleNonStreamingRequest(String body) {
        try {
            var method = extractMethod(body);
            ServerCallContext serverCallContext = newCallContext();
            A2AResponse<?> response = switch (method) {
                case A2AMethods.GET_TASK_METHOD ->
                    jsonRpcHandler.onGetTask(objectMapper.readValue(body, GetTaskRequest.class), serverCallContext);
                case A2AMethods.CANCEL_TASK_METHOD ->
                    jsonRpcHandler.onCancelTask(objectMapper.readValue(body, CancelTaskRequest.class),
                            serverCallContext);
                case A2AMethods.SEND_MESSAGE_METHOD ->
                    jsonRpcHandler.onMessageSend(objectMapper.readValue(body, SendMessageRequest.class),
                            serverCallContext);
                default -> new A2AErrorResponse(null, new UnsupportedOperationError());
            };
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            try {
                LOG.error("Got error while processing agent task request", e);
                return objectMapper
                        .writeValueAsString(new A2AErrorResponse(null, new InternalError(e.getMessage())));
            } catch (Exception ex) {
                return "{}";
            }
        }
    }

    /**
     * Subscribes to the publisher produced by the streaming handler and writes each emitted event to the client as a
     * Server-Sent Event. The request thread is held open until the stream completes, errors or the client disconnects.
     */
    private void handleStreamingRequest(@NotNull Context context, String body) {
        try {
            ServerCallContext serverCallContext = newCallContext();
            var method = extractMethod(body);
            Flow.Publisher<SendStreamingMessageResponse> publisher = switch (method) {
                case A2AMethods.SEND_STREAMING_MESSAGE_METHOD ->
                    jsonRpcHandler.onMessageSendStream(objectMapper.readValue(body, SendStreamingMessageRequest.class),
                            serverCallContext);
                case A2AMethods.SUBSCRIBE_TO_TASK_METHOD ->
                    jsonRpcHandler.onSubscribeToTask(objectMapper.readValue(body, SubscribeToTaskRequest.class),
                            serverCallContext);
                default -> null;
            };
            if (publisher == null) {
                context.result(objectMapper.writeValueAsString(new A2AErrorResponse(null, new UnsupportedOperationError())));
                return;
            }
            writeServerSentEvents(context, publisher);
        } catch (Exception e) {
            LOG.error("Got error while processing streaming agent task request", e);
        }
    }

    private void writeServerSentEvents(@NotNull Context context,
                                       Flow.Publisher<SendStreamingMessageResponse> publisher) {
        HttpServletResponse response = context.res();
        response.setStatus(200);
        response.setContentType("text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        var completion = new CountDownLatch(1);
        publisher.subscribe(new SseSubscriber(context.outputStream(), completion));
        try {
            completion.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while streaming Server-Sent Events to the client.", e);
        }
    }

    public void getAgentCard(@NotNull Context context) {
        context.json(jsonRpcHandler.getAgentCard());
    }

    private ServerCallContext newCallContext() {
        return new ServerCallContext(UnauthenticatedUser.INSTANCE, new HashMap<>(), Set.of());
    }

    private String extractMethod(String body) {
        try {
            return (String) objectMapper.readValue(body, Map.class).get("method");
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isStreamingMethod(String method) {
        return A2AMethods.SEND_STREAMING_MESSAGE_METHOD.equals(method)
                || A2AMethods.SUBSCRIBE_TO_TASK_METHOD.equals(method);
    }

    /**
     * Writes each streamed {@link SendStreamingMessageResponse} to the client output stream as a Server-Sent Event,
     * applying back-pressure by requesting one event at a time and cancelling the upstream execution if the client
     * disconnects.
     */
    private static final class SseSubscriber implements Flow.Subscriber<SendStreamingMessageResponse> {
        private final ServletOutputStream outputStream;
        private final CountDownLatch completion;
        private final AtomicLong eventId = new AtomicLong();
        private Flow.Subscription subscription;

        private SseSubscriber(ServletOutputStream outputStream, CountDownLatch completion) {
            this.outputStream = outputStream;
            this.completion = completion;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(SendStreamingMessageResponse item) {
            try {
                outputStream.write(SseFormatter.formatResponseAsSSE(item, eventId.getAndIncrement()).getBytes(UTF_8));
                outputStream.flush();
                subscription.request(1);
            } catch (IOException e) {
                LOG.warn("Failed to write a Server-Sent Event to the client. Cancelling the stream.", e);
                subscription.cancel();
                completion.countDown();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            LOG.error("Error while streaming Server-Sent Events to the client.", throwable);
            completion.countDown();
        }

        @Override
        public void onComplete() {
            completion.countDown();
        }
    }
}
