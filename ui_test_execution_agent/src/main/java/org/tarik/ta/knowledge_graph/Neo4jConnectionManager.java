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
package org.tarik.ta.knowledge_graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.ExecutableQuery;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.AuthenticationException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.tarik.ta.UiTestAgentConfig.getNeo4jDatabase;
import static org.tarik.ta.UiTestAgentConfig.getNeo4jUsername;
import static org.tarik.ta.UiTestAgentConfig.getVectorDbToken;
import static org.tarik.ta.UiTestAgentConfig.getVectorDbUrl;

/**
 * Singleton connection manager for the Neo4j driver lifecycle.
 * The shared {@link Driver} instance is used by both {@code Neo4jEmbeddingStore} instances
 * and custom Cypher queries for relationship management.
 */
public class Neo4jConnectionManager {
    private static final Logger LOG = LoggerFactory.getLogger(Neo4jConnectionManager.class);

    private static volatile Driver driver;

    private Neo4jConnectionManager() {
        // utility class
    }

    /**
     * Returns the shared Neo4j {@link Driver} instance, creating it lazily if needed.
     * The driver is configured with connection pooling for optimal performance.
     */
    public static Driver getDriver() {
        if (driver == null) {
            synchronized (Neo4jConnectionManager.class) {
                if (driver == null) {
                    var uri = getVectorDbUrl();
                    var username = getNeo4jUsername();
                    var password = getVectorDbToken();
                    LOG.info("Initializing Neo4j driver connection to '{}' as user '{}'", uri, username);

                    if (password.isBlank()) {
                        throw new IllegalStateException(
                                "Neo4j password is not configured. Set VECTOR_DB_KEY environment variable or vector.db.key in config.properties");
                    }

                    Config config = Config.builder()
                            .withMaxConnectionPoolSize(50)
                            .withConnectionAcquisitionTimeout(30, TimeUnit.SECONDS)
                            .withConnectionTimeout(10, TimeUnit.SECONDS)
                            .build();

                    try {
                        driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
                        driver.verifyConnectivity();
                        LOG.info("Neo4j driver authenticated and connected successfully to '{}' as user '{}' with pool size 50", uri,
                                username);
                    } catch (AuthenticationException e) {
                        driver = null;
                        throw new IllegalStateException(
                                "Neo4j authentication failed for user '%s' at '%s'. Verify VECTOR_DB_KEY matches the server's configured password"
                                        .formatted(username, uri), e);
                    } catch (ServiceUnavailableException e) {
                        driver = null;
                        throw new IllegalStateException(
                                "Neo4j server is unreachable at '%s'. Verify the VECTOR_DB_URL and that the Neo4j VM is running".formatted(
                                        uri), e);
                    }
                }
            }
        }
        return driver;
    }

    /**
     * Opens a new {@link Session} on the configured database for custom Cypher queries.
     * Callers are responsible for closing the returned session.
     */
    public static Session getSession() {
        return getDriver().session(SessionConfig.forDatabase(getNeo4jDatabase()));
    }

    /**
     * Returns an {@link ExecutableQuery} configured with the target database.
     */
    public static ExecutableQuery executableQuery(String cypher) {
        return getDriver().executableQuery(cypher).withConfig(QueryConfig.builder().withDatabase(getNeo4jDatabase()).build());
    }

    /**
     * Closes the Neo4j driver connection. Intended for shutdown hooks.
     */
    public static void close() {
        synchronized (Neo4jConnectionManager.class) {
            if (driver != null) {
                LOG.info("Closing Neo4j driver connection");
                driver.close();
                driver = null;
            }
        }
    }
}