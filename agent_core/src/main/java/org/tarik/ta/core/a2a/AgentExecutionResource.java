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
package org.tarik.ta.core.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.spec.*;
import io.a2a.spec.InternalError;
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
import java.util.Set;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import java.util.Map;

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
        var executor = newSingleThreadExecutor();
        var taskStore = new InMemoryTaskStore();
        var queueManager = new InMemoryQueueManager(taskStore);
        DefaultRequestHandler httpRequestHandler = DefaultRequestHandler.create(agentExecutorProvider.get(),
                taskStore, queueManager, pushNotificationConfigStore,
                new BasePushNotificationSender(pushNotificationConfigStore), executor);
        this.jsonRpcHandler = new JSONRPCHandler(agentCardProvider.get(), httpRequestHandler, executor);
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
            JSONRPCResponse<?> response = switch (method) {
                case GetTaskRequest.METHOD ->
                    jsonRpcHandler.onGetTask(objectMapper.readValue(body, GetTaskRequest.class), serverCallContext);
                case CancelTaskRequest.METHOD ->
                    jsonRpcHandler.onCancelTask(objectMapper.readValue(body, CancelTaskRequest.class),
                            serverCallContext);
                case SendMessageRequest.METHOD ->
                    jsonRpcHandler.onMessageSend(objectMapper.readValue(body, SendMessageRequest.class),
                            serverCallContext);
                default -> new JSONRPCErrorResponse(null, new UnsupportedOperationError());
            };
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            try {
                LOG.error("Got error while processing agent task request", e);
                return objectMapper
                        .writeValueAsString(new JSONRPCErrorResponse(null, new InternalError(e.getMessage())));
            } catch (Exception ex) {
                return "{}";
            }
        }
    }

    public void getAgentCard(@NotNull Context context) {
        context.json(jsonRpcHandler.getAgentCard());
    }
}
