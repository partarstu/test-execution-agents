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
package org.tarik.ta;

import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.a2a.UiAgentExecutor;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.a2a.AgentExecutor;
import org.tarik.ta.knowledge_graph.Neo4jConnectionManager;
import org.tarik.ta.knowledge_graph.schema.SchemaMigrationManager;

import static org.tarik.ta.UiTestAgentConfig.getExecutionMode;

import static org.tarik.ta.UiTestAgentConfig.getNeo4jUsername;
import static org.tarik.ta.UiTestAgentConfig.isNeo4jAuthConfigured;
import org.tarik.ta.core.AgentConfig;

public class Server extends AbstractServer {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    static void main() {
        var server = new Server();
        server.initKnowledgePersistence();
        server.start();
    }

    @Override
    protected AgentExecutor createAgentExecutor() {
        return new UiAgentExecutor(appScope);
    }

    @Override
    protected AgentCard createAgentCard() {
        return new org.tarik.ta.a2a.AgentCardProducer(AgentConfig.getExternalUrl()).agentCard();
    }

    @Override
    protected String getStartupLogMessage(String host, int port) {
        String mode = getExecutionMode().name().toLowerCase().replace('_', '-');
        return "Agent server started on host %s and port %d in %s mode".formatted(host, port, mode);
    }

    private void initKnowledgePersistence() {
        if (!isNeo4jAuthConfigured()) {
            LOG.warn("Neo4j password is set but username is blank — authentication may fail");
        }

        LOG.info("Initializing knowledge persistence layer (uri={}, user={})", AgentConfig.getVectorDbUrl(), getNeo4jUsername());
        SchemaMigrationManager.migrateOnStartup();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down Neo4j connection");
            Neo4jConnectionManager.close();
        }, "neo4j-shutdown"));
        LOG.info("Knowledge persistence layer initialized successfully with authenticated connection");
    }
}