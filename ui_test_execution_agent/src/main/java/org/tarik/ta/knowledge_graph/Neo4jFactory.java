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
import org.neo4j.driver.exceptions.ServiceUnavailableException;
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
                .build();

        try {
            this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
            this.driver.verifyConnectivity();
            LOG.info("Neo4j driver authenticated and connected successfully to '{}' as user '{}' with pool size 50", uri, username);
            return this.driver;
        } catch (AuthenticationException e) {
            throw new IllegalStateException(
                    "Neo4j authentication failed for user '%s' at '%s'. Verify VECTOR_DB_KEY matches the server's configured password"
                            .formatted(username, uri), e);
        } catch (ServiceUnavailableException e) {
            throw new IllegalStateException(
                    "Neo4j server is unreachable at '%s'. Verify the VECTOR_DB_URL and that the Neo4j VM is running".formatted(uri), e);
        }
    }

    @PostConstruct
    public void initSchema() {
        SchemaMigrationManager.migrateOnStartup(this.driver, this.databaseName);
    }

    @Override
    public void close() {
        if (this.driver != null) {
            LOG.info("Closing Neo4j driver connection");
            this.driver.close();
        }
    }
}