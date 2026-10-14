/*
 * knowledge-graph-core - Knowledge graph model, repositories and embeddings shared by the agents that use the knowledge graph.
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
package org.tarik.ta.knowledge_graph;

import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Creates the Neo4j driver of the knowledge graph from its configuration.
 */
public final class Neo4jDrivers {
    private static final Logger LOG = LoggerFactory.getLogger(Neo4jDrivers.class);

    private Neo4jDrivers() {
    }

    public static @NotNull Driver create(@NotNull KnowledgeGraphConfig config) {
        var uri = config.getVectorDbUrl();
        var username = config.getNeo4jUsername();
        var password = config.getVectorDbToken();
        LOG.info("Initializing Neo4j driver connection to '{}' as user '{}'", uri, username);

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Neo4j password is not configured. Set VECTOR_DB_KEY environment variable or vector.db.key in config.properties");
        }

        Config driverConfig = Config.builder()
                .withMaxConnectionPoolSize(config.getNeo4jMaxConnectionPoolSize())
                .withConnectionAcquisitionTimeout(config.getNeo4jConnectionAcquisitionTimeoutSeconds(), TimeUnit.SECONDS)
                .withConnectionTimeout(config.getNeo4jConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                // Allow the driver to retry failed managed transactions (~10 connection-timeout cycles by default)
                .withMaxTransactionRetryTime(config.getNeo4jMaxTransactionRetryTimeSeconds(), TimeUnit.SECONDS)
                .build();

        try {
            var driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), driverConfig);
            LOG.info("Neo4j driver created for '{}' as user '{}'", uri, username);
            return driver;
        } catch (AuthenticationException e) {
            throw new IllegalStateException(
                    "Neo4j authentication failed for user '%s' at '%s'. Verify VECTOR_DB_KEY matches the server's configured password"
                            .formatted(username, uri), e);
        }
    }
}
