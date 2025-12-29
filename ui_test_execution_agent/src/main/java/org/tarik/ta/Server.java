/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta;

import org.tarik.ta.core.a2a.AgentExecutor;
import org.tarik.ta.a2a.AgentCardProducer;
import org.tarik.ta.a2a.UiAgentExecutor;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.a2a.AgentExecutionResource;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.javalin.Javalin.create;
import static org.tarik.ta.core.AgentConfig.getStartPort;
import static org.tarik.ta.UiTestAgentConfig.isUnattendedMode;

public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final long MAX_REQUEST_SIZE = 10000000;
    private static final String MAIN_PATH = "/";
    private static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";
    private static final boolean UNATTENDED_MODE = isUnattendedMode();

    static void main() {
        int port = getStartPort();
        String host = AgentConfig.getHost();
        AgentExecutor executor = new UiAgentExecutor();
        AgentExecutionResource agentExecutionResource = new AgentExecutionResource(executor,
                AgentCardProducer.agentCard());

        create(config -> {
            config.http.maxRequestSize = MAX_REQUEST_SIZE;
            config.jsonMapper(new JavalinJackson());
        })
                .post(MAIN_PATH, ctx -> ctx.result(agentExecutionResource.handleNonStreamingRequests(ctx)))
                .get(AGENT_CARD_PATH, agentExecutionResource::getAgentCard)
                .start(host, port);

        LOG.info("Agent server started on host {} and port {} in {} mode", host, port,
                UNATTENDED_MODE ? "unattended" : "attended");
    }
}