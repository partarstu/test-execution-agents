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

import org.a2aproject.sdk.spec.AgentCard;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.json.JavalinJackson;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.a2a.AgentExecutionResource;

import java.security.MessageDigest;
import java.util.Optional;

import static io.javalin.Javalin.create;
import static java.nio.charset.StandardCharsets.UTF_8;

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
    private static final String BEARER_PREFIX = "Bearer ";

    private final AgentConfig agentConfig;
    private final AgentExecutionResource agentExecutionResource;

    @Inject
    public AbstractServer(AgentExecutionResource agentExecutionResource, AgentConfig agentConfig) {
        this.agentExecutionResource = agentExecutionResource;
        this.agentConfig = agentConfig;
    }

    public Javalin start() {
        int port = agentConfig.getStartPort();
        String host = agentConfig.getHost();
        Optional<String> authToken = agentConfig.getAuthToken();
        if (authToken.isEmpty()) {
            LOG.warn("No agent auth token configured (agent.auth.token / AGENT_AUTH_TOKEN); the main endpoint accepts "
                    + "unauthenticated requests. Configure a token for any non-local deployment.");
        }

        Javalin app = create(config -> {
            config.http.maxRequestSize = MAX_REQUEST_SIZE;
            config.jsonMapper(new JavalinJackson());
            // The agent-card discovery endpoint stays public; only the high-privilege main endpoint is guarded.
            authToken.ifPresent(token -> config.routes.before(MAIN_PATH, context -> requireValidToken(context, token)));
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
        return app;
    }

    /**
     * Rejects the request with {@code 401} unless it carries a {@code Authorization: Bearer <token>} header matching the
     * configured secret. The comparison uses {@link MessageDigest#isEqual} to avoid leaking the token through timing.
     */
    static void requireValidToken(@NotNull Context context, @NotNull String expectedToken) {
        String header = context.header("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedResponse();
        }
        String providedToken = header.substring(BEARER_PREFIX.length());
        if (!MessageDigest.isEqual(expectedToken.getBytes(UTF_8), providedToken.getBytes(UTF_8))) {
            throw new UnauthorizedResponse();
        }
    }
}