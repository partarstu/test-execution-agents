/*
 * ui-test-execution-agent - ${project.description}
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
package org.tarik.ta.knowledge_graph.repository;

import jakarta.inject.Singleton;
import org.apache.commons.text.StringSubstitutor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Record;
import org.neo4j.driver.RoutingControl;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.SchemaVersion;
import org.tarik.ta.knowledge_graph.model.node.TestCase;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.exceptions.SessionExpiredException;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.Neo4jMigrationState;
import org.tarik.ta.knowledge_graph.schema.SchemaMigrationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.tarik.ta.knowledge_graph.model.GraphRelationships.*;
import static org.tarik.ta.knowledge_graph.model.node.IEntity.PROP_ID;
import static org.tarik.ta.knowledge_graph.model.node.Embeddable.PROP_EMBEDDING;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_DESCRIPTION;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_IS_ATOMIC;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_TEST_DATA;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_EXPECTED_RESULTS;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_IS_PRECONDITION;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_OPTIONAL;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_PREREQUISITES;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_EFFECTS;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_CREATED_AT;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_UPDATED_AT;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_AVG_EXECUTION_MS;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_AVG_VERIFICATION_DELAY_MS;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_MAX_VERIFICATION_DELAY_MS;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_ADDITIONAL_INFO;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.PROP_LAST_TIMING_UPDATE;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_NAME;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_OWN_DESCRIPTION;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_ANCHORS_DESCRIPTION;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_PARENT_ELEMENT_SUMMARY;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_SCREENSHOT_FILE_EXTENSION;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_SCREENSHOT_MIME_TYPE;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_SCREENSHOT_IMAGE;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_IS_DATA_DEPENDENT;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_STABILITY_SCORE;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_AVG_LOCATION_TIME_MS;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_FAILED_LOCATION_COUNT;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_LAST_LOCATED_AT;
import static org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PROP_PHRASE;
import static org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PROP_TYPE;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.PROP_SYMPTOM;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.PROP_SYMPTOM_NORMALIZED;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.PROP_CATEGORY;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.PROP_RESOLUTION;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.PROP_OCCURRENCES;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.PROP_LAST_OCCURRED;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.PROP_MODE;
import static org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge.PROP_SCORE;
import static org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge.PROP_EFFECT_PHRASE;
import static org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge.PROP_PREREQUISITE_PHRASE;
import static org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge.PROP_PREREQUISITE_NODE_ID;
import static org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge.PROP_LAST_VERIFIED_AT;

/**
 * Shared query-execution helpers for all Neo4j repositories.
 * <p>Two execution strategies are provided:
 * <ul>
 *   <li><b>Single-query</b> ({@code executeSingleReadQuery}/{@code executeSingleWriteQuery}) — uses
 *       {@code driver.executableQuery()} with explicit routing. Use for any operation that fits in
 *       one Cypher statement.</li>
 *   <li><b>Complex-query</b> ({@code executeComplexReadQuery}/{@code executeComplexWriteQuery}) — uses a
 *       managed session transaction. Use only when multiple queries must share a single transaction
 *       with client-side logic between them.</li>
 * </ul>
 */
@Singleton
public class Neo4jRepositorySupport {

    /**
     * Maps node/relationship constant names to their values for use in {@link #cypher(String)} templates.
     */
    private static final Map<String, String> QUERY_TOKENS = Map.<String, String>ofEntries(
            Map.entry("LABEL_PROCEDURE", Procedure.LABEL),
            Map.entry("LABEL_UI_ELEMENT", UiElement.LABEL),
            Map.entry("LABEL_SCHEMA_VERSION", SchemaVersion.LABEL),
            Map.entry("LABEL_FAILURE_CONTEXT", FailureContext.LABEL),
            Map.entry("LABEL_TEST_CASE", TestCase.LABEL),
            Map.entry("LABEL_PHRASE_EMBEDDING", PhraseEmbedding.LABEL),
            Map.entry("REL_CONTAINS", REL_CONTAINS),
            Map.entry("REL_TARGETS", REL_TARGETS),
            Map.entry("REL_SATISFIES", REL_SATISFIES),
            Map.entry("REL_USES_PROCEDURE", REL_USES_PROCEDURE),
            Map.entry("REL_HAS_FAILURE_CONTEXT", REL_HAS_FAILURE_CONTEXT),
            Map.entry("REL_HAS_PREREQUISITE", REL_HAS_PREREQUISITE),
            Map.entry("REL_HAS_EFFECT", REL_HAS_EFFECT),
            Map.entry("PROP_ID", PROP_ID),
            Map.entry("PROP_EMBEDDING", PROP_EMBEDDING),
            Map.entry("PROP_NAME", PROP_NAME),
            Map.entry("PROP_DESCRIPTION", PROP_DESCRIPTION),
            Map.entry("PROP_IS_ATOMIC", PROP_IS_ATOMIC),
            Map.entry("PROP_TEST_DATA", PROP_TEST_DATA),
            Map.entry("PROP_EXPECTED_RESULTS", PROP_EXPECTED_RESULTS),
            Map.entry("PROP_IS_PRECONDITION", PROP_IS_PRECONDITION),
            Map.entry("PROP_OPTIONAL", PROP_OPTIONAL),
            Map.entry("PROP_PREREQUISITES", PROP_PREREQUISITES),
            Map.entry("PROP_EFFECTS", PROP_EFFECTS),
            Map.entry("PROP_ADDITIONAL_INFO", PROP_ADDITIONAL_INFO),
            Map.entry("PROP_CREATED_AT", PROP_CREATED_AT),
            Map.entry("PROP_UPDATED_AT", PROP_UPDATED_AT),
            Map.entry("PROP_AVG_EXECUTION_MS", PROP_AVG_EXECUTION_MS),
            Map.entry("PROP_AVG_VERIFICATION_DELAY_MS", PROP_AVG_VERIFICATION_DELAY_MS),
            Map.entry("PROP_MAX_VERIFICATION_DELAY_MS", PROP_MAX_VERIFICATION_DELAY_MS),
            Map.entry("PROP_LAST_TIMING_UPDATE", PROP_LAST_TIMING_UPDATE),
            Map.entry("PROP_OWN_DESCRIPTION", PROP_OWN_DESCRIPTION),
            Map.entry("PROP_ANCHORS_DESCRIPTION", PROP_ANCHORS_DESCRIPTION),
            Map.entry("PROP_PARENT_ELEMENT_SUMMARY", PROP_PARENT_ELEMENT_SUMMARY),
            Map.entry("PROP_SCREENSHOT_FILE_EXTENSION", PROP_SCREENSHOT_FILE_EXTENSION),
            Map.entry("PROP_SCREENSHOT_MIME_TYPE", PROP_SCREENSHOT_MIME_TYPE),
            Map.entry("PROP_SCREENSHOT_IMAGE", PROP_SCREENSHOT_IMAGE),
            Map.entry("PROP_IS_DATA_DEPENDENT", PROP_IS_DATA_DEPENDENT),
            Map.entry("PROP_STABILITY_SCORE", PROP_STABILITY_SCORE),
            Map.entry("PROP_AVG_LOCATION_TIME_MS", PROP_AVG_LOCATION_TIME_MS),
            Map.entry("PROP_FAILED_LOCATION_COUNT", PROP_FAILED_LOCATION_COUNT),
            Map.entry("PROP_LAST_LOCATED_AT", PROP_LAST_LOCATED_AT),
            Map.entry("PROP_PHRASE", PROP_PHRASE),
            Map.entry("PROP_TYPE", PROP_TYPE),
            Map.entry("PROP_SEQUENCE", PROP_SEQUENCE),
            Map.entry("PROP_SYMPTOM", PROP_SYMPTOM),
            Map.entry("PROP_SYMPTOM_NORMALIZED", PROP_SYMPTOM_NORMALIZED),
            Map.entry("PROP_CATEGORY", PROP_CATEGORY),
            Map.entry("PROP_RESOLUTION", PROP_RESOLUTION),
            Map.entry("PROP_OCCURRENCES", PROP_OCCURRENCES),
            Map.entry("PROP_LAST_OCCURRED", PROP_LAST_OCCURRED),
            Map.entry("PROP_MODE", PROP_MODE),
            Map.entry("PROP_SCORE", PROP_SCORE),
            Map.entry("PROP_EFFECT_PHRASE", PROP_EFFECT_PHRASE),
            Map.entry("PROP_PREREQUISITE_PHRASE", PROP_PREREQUISITE_PHRASE),
            Map.entry("PROP_PREREQUISITE_NODE_ID", PROP_PREREQUISITE_NODE_ID),
            Map.entry("PROP_LAST_VERIFIED_AT", PROP_LAST_VERIFIED_AT)
    );

    private final Driver driver;
    private final String databaseName;
    private final Neo4jMigrationState migrationState;
    private final ReentrantLock migrationRetryLock = new ReentrantLock();

    public Neo4jRepositorySupport(Driver driver, UiTestAgentConfig uiTestAgentConfig, Neo4jMigrationState migrationState) {
        this.driver = driver;
        this.databaseName = uiTestAgentConfig.getNeo4jDatabase();
        this.migrationState = migrationState;
    }

    /**
     * Resolves {@code ${CONSTANT_NAME}} tokens in a Cypher query template using the graph schema constants
     * from node classes and {@link org.tarik.ta.knowledge_graph.model.GraphRelationships}.
     * Unresolved tokens are left unchanged.
     */
    public String cypher(String template) {
        return StringSubstitutor.replace(template, QUERY_TOKENS);
    }

    public List<Record> executeSingleReadQuery(String cypher, Map<String, Object> params) {
        ensureMigrationCompleted();
        try {
            return driver.executableQuery(cypher)
                    .withConfig(QueryConfig.builder().withDatabase(databaseName).withRouting(RoutingControl.READ).build())
                    .withParameters(params)
                    .execute().records();
        } catch (ServiceUnavailableException | SessionExpiredException e) {
            throw new DatabaseConnectionException("Neo4j connection failed during read query execution", e);
        }
    }

    public List<Record> executeSingleReadQuery(String cypher) {
        return executeSingleReadQuery(cypher, Map.of());
    }

    public List<Record> executeSingleWriteQuery(String cypher, Map<String, Object> params) {
        ensureMigrationCompleted();
        try {
            return driver.executableQuery(cypher)
                    .withConfig(QueryConfig.builder().withDatabase(databaseName).withRouting(RoutingControl.WRITE).build())
                    .withParameters(params)
                    .execute().records();
        } catch (ServiceUnavailableException | SessionExpiredException e) {
            throw new DatabaseConnectionException("Neo4j connection failed during write query execution", e);
        }
    }

    public List<Record> executeSingleWriteQuery(String cypher) {
        return executeSingleWriteQuery(cypher, Map.of());
    }

    public <T> T executeComplexReadQuery(TransactionCallback<T> action) {
        ensureMigrationCompleted();
        try (var session = driver.session(SessionConfig.forDatabase(databaseName))) {
            return session.executeRead(action);
        } catch (ServiceUnavailableException | SessionExpiredException e) {
            throw new DatabaseConnectionException("Neo4j connection failed during complex read query execution", e);
        }
    }

    public void executeComplexWriteQuery(Consumer<TransactionContext> action) {
        ensureMigrationCompleted();
        try (var session = driver.session(SessionConfig.forDatabase(databaseName))) {
            session.executeWriteWithoutResult(action);
        } catch (ServiceUnavailableException | SessionExpiredException e) {
            throw new DatabaseConnectionException("Neo4j connection failed during complex write query execution", e);
        }
    }

    public <T> T executeComplexWriteQuery(TransactionCallback<T> action) {
        ensureMigrationCompleted();
        try (var session = driver.session(SessionConfig.forDatabase(databaseName))) {
            return session.executeWrite(action);
        } catch (ServiceUnavailableException | SessionExpiredException e) {
            throw new DatabaseConnectionException("Neo4j connection failed during complex write query execution", e);
        }
    }

    /**
     * Ensures schema migration has completed before any DB operation is executed.
     * If migration failed at startup, retries it now — failure here aborts the current request thread.
     */
    private void ensureMigrationCompleted() {
        if (migrationState.isSucceeded()) {
            return;
        }
        migrationRetryLock.lock();
        try {
            // Double-check after acquiring the lock to avoid duplicate migration attempts
            if (migrationState.isSucceeded()) {
                return;
            }
            try {
                SchemaMigrationManager.migrateOnStartup(driver, databaseName);
                migrationState.markSucceeded();
            } catch (Exception e) {
                throw new DatabaseConnectionException("Schema migration failed during request — cannot proceed without DB", e);
            }
        } finally {
            migrationRetryLock.unlock();
        }
    }
}