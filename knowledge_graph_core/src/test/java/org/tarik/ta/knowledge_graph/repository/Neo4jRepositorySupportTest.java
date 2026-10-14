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
package org.tarik.ta.knowledge_graph.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.ExecutableQuery;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.GraphSchemaGuard;
import org.tarik.ta.knowledge_graph.KnowledgeGraphConfig;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Neo4jRepositorySupportTest {
    private Neo4jRepositorySupport repositorySupport;

    private Driver driver;
    private GraphSchemaGuard schemaGuard;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        schemaGuard = mock(GraphSchemaGuard.class);
        var config = mock(KnowledgeGraphConfig.class);
        when(config.getNeo4jDatabase()).thenReturn("neo4j");
        repositorySupport = new Neo4jRepositorySupport(driver, config, schemaGuard);
    }

    @Test
    @DisplayName("single queries run after the schema check and return the records")
    void executeSingleQueries_shouldReturnTheRecords_whenTheSchemaIsReady() {
        var record = mock(Record.class);
        var query = mock(ExecutableQuery.class, RETURNS_SELF);
        var result = mock(EagerResult.class);
        when(driver.executableQuery(anyString())).thenReturn(query);
        when(query.execute()).thenReturn(result);
        when(result.records()).thenReturn(List.of(record));

        assertThat(repositorySupport.executeSingleReadQuery("RETURN 1")).containsExactly(record);
        assertThat(repositorySupport.executeSingleWriteQuery("CREATE (n)")).containsExactly(record);
        verify(schemaGuard, times(2)).ensureSchemaReady();
    }

    @Test
    @DisplayName("an unavailable database is reported as a connection failure for single and complex queries")
    void executeQueries_shouldReportAConnectionFailure_whenTheDatabaseIsUnavailable() {
        var query = mock(ExecutableQuery.class, RETURNS_SELF);
        when(driver.executableQuery(anyString())).thenReturn(query);
        when(query.execute()).thenThrow(new ServiceUnavailableException("down"));
        when(driver.session(any(SessionConfig.class))).thenThrow(new ServiceUnavailableException("down"));
        TransactionCallback<String> readCallback = _ -> "value";
        Consumer<TransactionContext> writeAction = _ -> {
        };

        assertThatThrownBy(() -> repositorySupport.executeSingleReadQuery("RETURN 1")).isInstanceOf(DatabaseConnectionException.class);
        assertThatThrownBy(() -> repositorySupport.executeSingleWriteQuery("CREATE (n)")).isInstanceOf(DatabaseConnectionException.class);
        assertThatThrownBy(() -> repositorySupport.executeComplexReadQuery(readCallback)).isInstanceOf(DatabaseConnectionException.class);
        assertThatThrownBy(() -> repositorySupport.executeComplexWriteQuery(writeAction)).isInstanceOf(DatabaseConnectionException.class);
        assertThatThrownBy(() -> repositorySupport.executeComplexWriteQuery(readCallback)).isInstanceOf(DatabaseConnectionException.class);
    }

    @Test
    @DisplayName("complex queries run in a managed transaction of a session")
    void executeComplexQueries_shouldRunInAManagedTransaction() {
        var session = mock(Session.class);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        TransactionCallback<String> callback = _ -> "value";
        Consumer<TransactionContext> writeAction = _ -> {
        };
        when(session.executeRead(callback)).thenReturn("read");
        when(session.executeWrite(callback)).thenReturn("written");

        assertThat(repositorySupport.executeComplexReadQuery(callback)).isEqualTo("read");
        assertThat(repositorySupport.executeComplexWriteQuery(callback)).isEqualTo("written");
        repositorySupport.executeComplexWriteQuery(writeAction);

        verify(session).executeWriteWithoutResult(writeAction);
        verify(session, times(3)).close();
    }

    @Test
    @DisplayName("a query does not reach the database when the schema guard reports the schema is not ready")
    void executeSingleReadQuery_shouldNotQuery_whenSchemaIsNotReady() {
        doThrow(new DatabaseConnectionException("not ready", new IllegalStateException())).when(schemaGuard).ensureSchemaReady();

        assertThatThrownBy(() -> repositorySupport.executeSingleReadQuery("RETURN 1"))
                .isInstanceOf(DatabaseConnectionException.class);
        verifyNoInteractions(driver);
    }

    @Test
    @DisplayName("cypher selects Cypher 25 for every query, independent of the database default language")
    void cypher_shouldPrefixTheQueryWithCypher25() {
        assertThat(repositorySupport.cypher("MATCH (n:${LABEL_PROCEDURE}) RETURN n"))
                .isEqualTo("CYPHER 25 MATCH (n:Procedure) RETURN n");
    }

    @Test
    @DisplayName("cypher resolves the vector index tokens to the index names the schema migrations create")
    void cypher_shouldResolveVectorIndexTokens() {
        assertThat(repositorySupport.cypher("${INDEX_PHRASE_EMBEDDING} ${INDEX_PROCEDURE_EMBEDDING} ${INDEX_UI_ELEMENT_EMBEDDING}"))
                .isEqualTo("CYPHER 25 phrase_embedding_vector_index procedure_embedding_index ui_element_embedding_index")
                .contains(PhraseEmbedding.VECTOR_INDEX, Procedure.VECTOR_INDEX, UiElement.VECTOR_INDEX);
    }
}
