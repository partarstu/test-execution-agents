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

import org.a2aproject.sdk.jsonrpc.common.json.IdJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.InvalidParamsJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.MethodNotFoundJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2ARequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.StreamingJSONRPCRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SubscribeToTaskRequest;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.util.sse.SseFormatter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.a2aproject.sdk.spec.JSONParseError;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static org.a2aproject.sdk.common.A2AHeaders.A2A_VERSION;
import static org.a2aproject.sdk.grpc.utils.JSONRPCUtils.parseRequestBody;
import static org.a2aproject.sdk.jsonrpc.common.json.JsonUtil.toJson;
import static org.a2aproject.sdk.spec.A2AMethods.CANCEL_TASK_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.GET_TASK_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SEND_MESSAGE_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SEND_STREAMING_MESSAGE_METHOD;
import static org.a2aproject.sdk.spec.A2AMethods.SUBSCRIBE_TO_TASK_METHOD;
import static org.a2aproject.sdk.spec.AgentInterface.CURRENT_PROTOCOL_VERSION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newCachedThreadPool;

@Singleton
public class AgentExecutionResource {
    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutionResource.class);
    private static final PushNotificationConfigStore pushNotificationConfigStore = new InMemoryPushNotificationConfigStore();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Maps the standard A2A-spec JSON-RPC method names this agent advertises (and which generic A2A clients send) to
     * the proto-style names the SDK's {@link org.a2aproject.sdk.grpc.utils.JSONRPCUtils} parser recognizes. Clients
     * using the matching SDK already send the proto-style names, so those are left untouched.
     */
    private static final Map<String, String> SPEC_TO_SDK_METHOD_NAMES = Map.of(
            "message/send", SEND_MESSAGE_METHOD,
            "message/stream", SEND_STREAMING_MESSAGE_METHOD,
            "tasks/get", GET_TASK_METHOD,
            "tasks/cancel", CANCEL_TASK_METHOD,
            "tasks/resubscribe", SUBSCRIBE_TO_TASK_METHOD);

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
        // Both executors must allow on-demand thread growth: the SDK runs the (long, blocking) agent execution on
        // taskExecutor and schedules the SSE subscription + EventConsumer polling loop on these same pools. A single
        // thread lets the blocking agent starve the streaming consumption, so live progress only reaches the client in
        // a burst at task end. Cached pools match the SDK's own default executor semantics.
        var taskExecutor = newCachedThreadPool();
        var eventConsumerExecutor = newCachedThreadPool();
        var taskStore = new InMemoryTaskStore();
        var mainEventBus = new MainEventBus();
        var queueManager = new InMemoryQueueManager(taskStore, mainEventBus);
        var pushNotificationSender = new BasePushNotificationSender(pushNotificationConfigStore);
        var mainEventBusProcessor = new MainEventBusProcessor(mainEventBus, taskStore, pushNotificationSender, queueManager);
        // The SDK starts the processor's background thread from its @PostConstruct, which only runs under a Jakarta CDI
        // container. This agent wires the processor manually via avaje-inject, so that callback never fires and
        // ensureStarted() is a no-op proxy hook. Without a running processor the MainEventBus is never drained and no
        // events ever reach subscribers, so the thread must be started explicitly here.
        var eventBusProcessorThread = new Thread(mainEventBusProcessor, "MainEventBusProcessor");
        eventBusProcessorThread.setDaemon(true);
        eventBusProcessorThread.start();
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
            A2ARequest<?> request = parseRequestBody(normalizeMethodName(context.body()), null);
            if (request instanceof StreamingJSONRPCRequest<?> streamingRequest) {
                handleStreamingRequest(context, streamingRequest);
            } else {
                context.result(handleNonStreamingRequest(context, request));
            }
        } catch (Exception e) {
            LOG.error("Got error while processing agent task request", e);
            context.result(toErrorResponse(e));
        }
    }

    /**
     * Rewrites the request's {@code method} field from the standard A2A-spec name (e.g. {@code message/stream}) to the
     * proto-style name the SDK parser expects (e.g. {@code SendStreamingMessage}). Bodies whose method is already a
     * proto-style name (or any other value) are returned unchanged, as are bodies that cannot be parsed here so that
     * the SDK parser can still raise its spec-compliant parse error.
     */
    private static String normalizeMethodName(@NotNull String body) {
        try {
            if (JSON_MAPPER.readTree(body) instanceof ObjectNode root) {
                JsonNode methodNode = root.get("method");
                if (methodNode != null && methodNode.isTextual()) {
                    String sdkMethod = SPEC_TO_SDK_METHOD_NAMES.get(methodNode.asText());
                    if (sdkMethod != null) {
                        root.put("method", sdkMethod);
                        return JSON_MAPPER.writeValueAsString(root);
                    }
                }
            }
        } catch (IOException e) {
            LOG.debug("Could not pre-parse the request body for method normalization; passing it through unchanged.", e);
        }
        return body;
    }

    /**
     * Handles a single non-streaming JSON-RPC request.
     *
     * @return the JSON-RPC response which may be an error response
     */
    private String handleNonStreamingRequest(@NotNull Context context, A2ARequest<?> request) {
        try {
            ServerCallContext serverCallContext = newCallContext(context);
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
            ServerCallContext serverCallContext = newCallContext(context);
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

            // Use the raw servlet output stream rather than ctx.outputStream(): Javalin's wrapper does not override
            // flush(), so per-event flushes are no-ops and the SSE events stay buffered until the response closes,
            // making live progress arrive in a burst at task end. The raw stream's flush() reaches the socket.
            SseSubscriber subscriber = new SseSubscriber(response.getOutputStream(), completionFuture);
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

    private ServerCallContext newCallContext(@NotNull Context context) {
        return new ServerCallContext(UnauthenticatedUser.INSTANCE, new HashMap<>(), Set.of(), resolveProtocolVersion(context));
    }

    /**
     * Resolves the A2A protocol version of the incoming request from the {@code A2A-Version} header. When the header is
     * absent, the version this agent advertises is assumed instead of the SDK's spec-default {@code 0.3}, which would
     * otherwise be rejected by {@link org.a2aproject.sdk.server.version.A2AVersionValidator} against the agent card and
     * break version-less A2A clients.
     */
    static String resolveProtocolVersion(@NotNull Context context) {
        String requestedVersion = context.header(A2A_VERSION);
        return requestedVersion == null || requestedVersion.isBlank() ? CURRENT_PROTOCOL_VERSION : requestedVersion;
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
