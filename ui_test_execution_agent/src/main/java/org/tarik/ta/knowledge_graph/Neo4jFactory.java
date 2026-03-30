package org.tarik.ta.knowledge_graph;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.schema.SchemaMigrationManager;

import java.util.concurrent.TimeUnit;

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
        var uri = uiTestAgentConfig.getVectorDbUrl();
        var username = uiTestAgentConfig.getNeo4jUsername();
        var password = uiTestAgentConfig.getVectorDbToken();
        LOG.info("Initializing Neo4j driver connection to '{}' as user '{}'", uri, username);

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Neo4j password is not configured. Set VECTOR_DB_KEY environment variable or vector.db.key in config.properties");
        }

        Config config = Config.builder()
                .withMaxConnectionPoolSize(50)
                .withConnectionAcquisitionTimeout(30, TimeUnit.SECONDS)
                .withConnectionTimeout(10, TimeUnit.SECONDS)
                // Allow the driver to retry failed managed transactions for up to 120s (~10 connection-timeout cycles)
                .withMaxTransactionRetryTime(120, TimeUnit.SECONDS)
                .build();

        try {
            this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
            LOG.info("Neo4j driver created for '{}' as user '{}' — connectivity will be verified during schema migration", uri, username);
            return this.driver;
        } catch (AuthenticationException e) {
            throw new IllegalStateException(
                    "Neo4j authentication failed for user '%s' at '%s'. Verify VECTOR_DB_KEY matches the server's configured password"
                            .formatted(username, uri), e);
        }
    }

    @Bean
    @Singleton
    public Neo4jMigrationState migrationState() {
        this.migrationState = new Neo4jMigrationState();
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