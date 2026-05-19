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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.jsonrpc.common.wrappers.A2AErrorResponse;
import io.a2a.jsonrpc.common.wrappers.A2AResponse;
import io.a2a.jsonrpc.common.wrappers.CancelTaskRequest;
import io.a2a.jsonrpc.common.wrappers.GetTaskRequest;
import io.a2a.jsonrpc.common.wrappers.SendMessageRequest;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
     * Handles incoming non-streaming requests to the main A2A endpoint.
     *
     * @return the JSON-RPC response which may be an error response
     */
    public String handleNonStreamingRequests(@NotNull Context context) {
        try {
            var body = context.body();
            var request = objectMapper.readValue(body, Map.class);
            var method = (String) request.get("method");
            ServerCallContext serverCallContext = new ServerCallContext(UnauthenticatedUser.INSTANCE, new HashMap<>(),
                    Set.of());
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

    public void getAgentCard(@NotNull Context context) {
        context.json(jsonRpcHandler.getAgentCard());
    }
}
