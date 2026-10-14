/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.schema.SchemaMigrationManager;

@Factory
public class Neo4jFactory implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Neo4jFactory.class);

    private final String databaseName;
    private final UiTestAgentConfig uiTestAgentConfig;
    private Driver driver;
    private Neo4jMigrationState migrationState;

    public Neo4jFactory(UiTestAgentConfig uiTestAgentConfig) {
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.databaseName = uiTestAgentConfig.getNeo4jDatabase();
    }

    @Bean
    @Singleton
    public Driver driver() {
        this.driver = Neo4jDrivers.create(uiTestAgentConfig);
        return this.driver;
    }

    @Bean
    @Singleton
    public Neo4jMigrationState migrationState(Driver driver) {
        this.migrationState = new Neo4jMigrationState(driver, databaseName);
        return this.migrationState;
    }

    @PostConstruct
    public void initSchema() {
        var migrationThread = Thread.ofVirtual().start(() -> {
            try {
                SchemaMigrationManager.migrateOnStartup(this.driver, this.databaseName);
                migrationState.markSucceeded();
                LOG.info("Schema migration completed successfully on startup");
            } catch (Exception e) {
                LOG.error("Schema migration failed at startup — will be retried on first DB operation: {}", e.getMessage());
            }
        });
        try {
            migrationThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while waiting for schema migration thread to complete", e);
        }
    }

    @Override
    public void close() {
        if (this.driver != null) {
            LOG.info("Closing Neo4j driver connection");
            this.driver.close();
        }
    }
}