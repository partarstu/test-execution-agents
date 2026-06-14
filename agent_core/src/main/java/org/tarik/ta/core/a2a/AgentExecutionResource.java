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

import io.a2a.jsonrpc.common.json.IdJsonMappingException;
import io.a2a.jsonrpc.common.json.InvalidParamsJsonMappingException;
import io.a2a.jsonrpc.common.json.JsonMappingException;
import io.a2a.jsonrpc.common.json.JsonProcessingException;
import io.a2a.jsonrpc.common.json.MethodNotFoundJsonMappingException;
import io.a2a.jsonrpc.common.wrappers.A2AErrorResponse;
import io.a2a.jsonrpc.common.wrappers.A2ARequest;
import io.a2a.jsonrpc.common.wrappers.A2AResponse;
import io.a2a.jsonrpc.common.wrappers.CancelTaskRequest;
import io.a2a.jsonrpc.common.wrappers.GetTaskRequest;
import io.a2a.jsonrpc.common.wrappers.SendMessageRequest;
import io.a2a.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import io.a2a.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import io.a2a.jsonrpc.common.wrappers.StreamingJSONRPCRequest;
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
import io.a2a.spec.A2AError;
import io.a2a.spec.AgentCard;
import io.a2a.spec.InternalError;
import io.a2a.spec.InvalidParamsError;
import io.a2a.spec.InvalidRequestError;
import io.a2a.spec.JSONParseError;
import io.a2a.spec.MethodNotFoundError;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static io.a2a.grpc.utils.JSONRPCUtils.parseRequestBody;
import static io.a2a.jsonrpc.common.json.JsonUtil.toJson;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

@Singleton
public class AgentExecutionResource {
    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutionResource.class);
    private static final PushNotificationConfigStore pushNotificationConfigStore = new InMemoryPushNotificationConfigStore();
    private final Provider<AgentExecutor> agentExecutorProvider;
    private final Provider<AgentCard> agentCardProvider;
    private JSONRPCHandler jsonRpcHandler;

    @Inject
    public AgentExecutionResource(Provider<AgentExecutor> agentExecutorProvider, Provider<AgentCard> agentCardProvider) {
        this.agentExecutorProvider = agentExecutorProvider;
        this.agentCardProvider = agentCardProvider;
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
     * Entry point for the main A2A endpoint. The body is parsed once with the A2A SDK's JSON-RPC parser into a typed
     * {@link A2ARequest}; streaming requests ({@code SendStreamingMessage}, {@code SubscribeToTask}) are answered with
     * a Server-Sent Events stream, every other request with a single JSON-RPC response.
     */
    public void handle(@NotNull Context context) {
        try {
            A2ARequest<?> request = parseRequestBody(context.body(), null);
            if (request instanceof StreamingJSONRPCRequest<?> streamingRequest) {
                handleStreamingRequest(context, streamingRequest);
            } else {
                context.result(handleNonStreamingRequest(request));
            }
        } catch (Exception e) {
            LOG.error("Got error while processing agent task request", e);
            context.result(toErrorResponse(e));
        }
    }

    /**
     * Handles a single non-streaming JSON-RPC request.
     *
     * @return the JSON-RPC response which may be an error response
     */
    private String handleNonStreamingRequest(A2ARequest<?> request) {
        try {
            ServerCallContext serverCallContext = newCallContext();
            A2AResponse<?> response = switch (request) {
                case GetTaskRequest getTaskRequest -> jsonRpcHandler.onGetTask(getTaskRequest, serverCallContext);
                case CancelTaskRequest cancelTaskRequest -> jsonRpcHandler.onCancelTask(cancelTaskRequest, serverCallContext);
                case SendMessageRequest sendMessageRequest -> jsonRpcHandler.onMessageSend(sendMessageRequest, serverCallContext);
                default -> new A2AErrorResponse(request.getId(), new UnsupportedOperationError());
            };
            return toJson(response);
        } catch (Exception e) {
            LOG.error("Got error while processing agent task request", e);
            return toErrorResponse(e);
        }
    }

    /**
     * Maps a failure to its JSON-RPC spec error and serializes it as an {@link A2AErrorResponse}, falling back to an
     * empty JSON object if even the error serialization fails. SDK parse exceptions carry the request id when it could
     * be extracted, so it is echoed back as required by the JSON-RPC spec.
     */
    private String toErrorResponse(@NotNull Throwable e) {
        Object requestId = e instanceof IdJsonMappingException idAware ? idAware.getId() : null;
        A2AError error = switch (e) {
            case A2AError a2aError -> a2aError;
            case MethodNotFoundJsonMappingException ignored -> new MethodNotFoundError();
            case InvalidParamsJsonMappingException invalidParams -> new InvalidParamsError(invalidParams.getMessage());
            case JsonMappingException invalidRequest -> new InvalidRequestError(invalidRequest.getMessage());
            case JsonProcessingException unparseable -> new JSONParseError(unparseable.getMessage());
            default -> new InternalError(e.getMessage());
        };
        try {
            return toJson(new A2AErrorResponse(requestId, error));
        } catch (Exception ex) {
            LOG.error("Failed to serialize the JSON-RPC error response.", ex);
            return "{}";
        }
    }

    /**
     * Subscribes to the publisher produced by the streaming handler and writes each emitted event to the client as a
     * Server-Sent Event.
     */
    private void handleStreamingRequest(@NotNull Context context, StreamingJSONRPCRequest<?> request) {
        try {
            ServerCallContext serverCallContext = newCallContext();
            Flow.Publisher<SendStreamingMessageResponse> publisher = switch (request) {
                case SendStreamingMessageRequest sendStreamingMessageRequest ->
                    jsonRpcHandler.onMessageSendStream(sendStreamingMessageRequest, serverCallContext);
                case SubscribeToTaskRequest subscribeToTaskRequest ->
                    jsonRpcHandler.onSubscribeToTask(subscribeToTaskRequest, serverCallContext);
            };
            writeServerSentEvents(context, publisher);
        } catch (Exception e) {
            LOG.error("Got error while processing streaming agent task request", e);
            context.result(toErrorResponse(e));
        }
    }

    private void writeServerSentEvents(@NotNull Context context,
                                       Flow.Publisher<SendStreamingMessageResponse> publisher) {
        CompletableFuture<Void> completionFuture = new CompletableFuture<>();
        AtomicReference<SseSubscriber> subscriberRef = new AtomicReference<>();

        context.async(config -> {
            config.timeout = 0; // unlimited timeout
            config.onTimeout(ctx -> {
                LOG.warn("Async timeout reached for SSE stream.");
                SseSubscriber sub = subscriberRef.get();
                if (sub != null) {
                    sub.cancelSubscription();
                } else {
                    completionFuture.complete(null);
                }
            });
        }, () -> {
            HttpServletResponse response = context.res();
            response.setStatus(200);
            response.setContentType("text/event-stream");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");

            SseSubscriber subscriber = new SseSubscriber(context.outputStream(), completionFuture);
            subscriberRef.set(subscriber);
            publisher.subscribe(subscriber);
            try {
                completionFuture.get();
            } catch (Exception e) {
                LOG.warn("SSE streaming async task interrupted or failed.", e);
            }
        });
    }

    public void getAgentCard(@NotNull Context context) {
        context.json(jsonRpcHandler.getAgentCard());
    }

    private ServerCallContext newCallContext() {
        return new ServerCallContext(UnauthenticatedUser.INSTANCE, new HashMap<>(), Set.of());
    }

    /**
     * Writes each streamed {@link SendStreamingMessageResponse} to the client output stream as a Server-Sent Event,
     * applying back-pressure by requesting one event at a time and cancelling the upstream execution if the client
     * disconnects.
     */
    private static final class SseSubscriber implements Flow.Subscriber<SendStreamingMessageResponse> {
        private final ServletOutputStream outputStream;
        private final CompletableFuture<Void> completionFuture;
        private Flow.Subscription subscription;
        private long eventId = 0;

        private SseSubscriber(ServletOutputStream outputStream, CompletableFuture<Void> completionFuture) {
            this.outputStream = outputStream;
            this.completionFuture = completionFuture;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(SendStreamingMessageResponse item) {
            try {
                outputStream.write(SseFormatter.formatResponseAsSSE(item, eventId++).getBytes(UTF_8));
                outputStream.flush();
                subscription.request(1);
            } catch (IOException e) {
                LOG.warn("Failed to write a Server-Sent Event to the client. Cancelling the stream.", e);
                cancelSubscription();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            LOG.error("Error while streaming Server-Sent Events to the client.", throwable);
            completeContext();
        }

        @Override
        public void onComplete() {
            completeContext();
        }

        private void completeContext() {
            // Only signal the async task to return; Javalin owns the AsyncContext and completes it once the
            // task finishes. Completing it here as well caused a double-completion that recycled the response
            // before Javalin's post-handler tasks ran, triggering a cascading exception-handler failure.
            completionFuture.complete(null);
        }

        public void cancelSubscription() {
            if (subscription != null) {
                subscription.cancel();
            }
            completeContext();
        }
    }
}
