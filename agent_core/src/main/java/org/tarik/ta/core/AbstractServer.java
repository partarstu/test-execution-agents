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
package org.tarik.ta.core;

import io.a2a.spec.AgentCard;
import io.javalin.json.JavalinJackson;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.a2a.AgentExecutionResource;

import static io.javalin.Javalin.create;

/**
 * Base class for agent servers. Bootstraps the application DI container and provides
 * common server initialization logic.
 */
@Singleton
public class AbstractServer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractServer.class);
    private static final long MAX_REQUEST_SIZE = 10000000;
    private static final String MAIN_PATH = "/";
    private static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";

    private final AgentConfig agentConfig;
    private final AgentExecutionResource agentExecutionResource;

    @Inject
    public AbstractServer(AgentExecutionResource agentExecutionResource, AgentConfig agentConfig) {
        this.agentExecutionResource = agentExecutionResource;
        this.agentConfig = agentConfig;
    }

    public void start() {
        int port = agentConfig.getStartPort();
        String host = agentConfig.getHost();

        create(config -> {
            config.http.maxRequestSize = MAX_REQUEST_SIZE;
            config.jsonMapper(new JavalinJackson());
            config.routes.post(MAIN_PATH, agentExecutionResource::handle);
            config.routes.get(AGENT_CARD_PATH, agentExecutionResource::getAgentCard);
            // Guard against the recycled-response failure mode: once a response is recycled, Javalin's default
            // error handling reads from the (also recycled) request and throws again, which it then re-handles
            // recursively in an unbounded loop that floods the log. Handling the IllegalStateException here
            // without touching the recycled context breaks that recursion.
            config.routes.exception(IllegalStateException.class, (exception, context) ->
                    LOG.warn("Suppressing an IllegalStateException from a recycled/committed response to "
                            + "prevent a cascading exception-handler loop.", exception));
        }).start(host, port);

        LOG.info("Agent server started on {}:{}", host, port);
    }
}