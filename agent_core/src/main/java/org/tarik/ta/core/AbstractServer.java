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
package org.tarik.ta.core;

import io.avaje.inject.BeanScope;
import io.a2a.spec.AgentCard;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.a2a.AgentExecutionResource;
import org.tarik.ta.core.a2a.AgentExecutor;

import static io.javalin.Javalin.create;

/**
 * Abstract base class for agent servers. Bootstraps the application DI container and provides
 * common server initialization logic while allowing subclasses to specify agent-specific components.
 */
public abstract class AbstractServer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractServer.class);
    private static final long MAX_REQUEST_SIZE = 10000000;
    private static final String MAIN_PATH = "/";
    private static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";

    protected final BeanScope appScope;
    private final AgentConfig agentConfig;

    protected AbstractServer() {
        this.appScope = BeanScope.builder().build();
        this.agentConfig = appScope.get(AgentConfig.class);
    }

    protected abstract AgentExecutor createAgentExecutor();

    protected abstract AgentCard createAgentCard();

    protected abstract String getStartupLogMessage(String host, int port);

    public void start() {
        int port = agentConfig.getStartPort();
        String host = agentConfig.getHost();
        AgentExecutionResource agentExecutionResource = new AgentExecutionResource(createAgentExecutor(), createAgentCard());

        create(config -> {
            config.http.maxRequestSize = MAX_REQUEST_SIZE;
            config.jsonMapper(new JavalinJackson());
            config.routes.post(MAIN_PATH, ctx -> ctx.result(agentExecutionResource.handleNonStreamingRequests(ctx)));
            config.routes.get(AGENT_CARD_PATH, agentExecutionResource::getAgentCard);
        }).start(host, port);

        LOG.info(getStartupLogMessage(host, port));
    }
}
