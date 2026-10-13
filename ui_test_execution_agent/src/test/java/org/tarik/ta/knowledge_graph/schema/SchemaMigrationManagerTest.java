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
package org.tarik.ta.knowledge_graph.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.ExecutableQuery;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaMigrationManagerTest {

    @Test
    @DisplayName("migrateOnStartup runs every migration query as Cypher 25 and creates the vector indexes the repositories search")
    void migrateOnStartup_shouldRunEveryQueryAsCypher25() {
        var driver = mock(Driver.class);
        var query = mock(ExecutableQuery.class, RETURNS_SELF);
        var result = mock(EagerResult.class);
        when(driver.executableQuery(anyString())).thenReturn(query);
        when(query.execute()).thenReturn(result);
        when(result.records()).thenReturn(List.of());

        SchemaMigrationManager.migrateOnStartup(driver, "neo4j");

        var queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(driver, atLeastOnce()).executableQuery(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues()).allMatch(cypher -> cypher.startsWith("CYPHER 25 "));
        assertThat(queryCaptor.getAllValues())
                .anyMatch(cypher -> cypher.contains("CREATE VECTOR INDEX " + PhraseEmbedding.VECTOR_INDEX + " "))
                .anyMatch(cypher -> cypher.contains("CREATE VECTOR INDEX " + Procedure.VECTOR_INDEX + " "))
                .anyMatch(cypher -> cypher.contains("CREATE VECTOR INDEX " + UiElement.VECTOR_INDEX + " "))
                .anyMatch(cypher -> cypher.contains("CREATE INDEX execution_anomaly_subject_id IF NOT EXISTS FOR (n:ExecutionAnomaly) ON (n.subjectId)"));
    }

    @Test
    @DisplayName("migrateOnStartup applies every migration of a fresh database in version order, so the stored version only grows")
    void migrateOnStartup_shouldStoreTheVersionsInAscendingOrder_whenTheDatabaseIsFresh() {
        var query = mockQueryReturningTheCurrentVersion(0);

        SchemaMigrationManager.migrateOnStartup(query.driver(), "neo4j");

        assertThat(storedVersions(query.executableQuery())).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
    }

    @Test
    @DisplayName("migrateOnStartup applies only the migrations above the stored version, the data migration v9 before the DDL v10 and v11")
    void migrateOnStartup_shouldApplyOnlyTheNewerMigrationsInOrder_whenTheDatabaseIsAtVersion8() {
        var query = mockQueryReturningTheCurrentVersion(8);

        SchemaMigrationManager.migrateOnStartup(query.driver(), "neo4j");

        assertThat(storedVersions(query.executableQuery())).containsExactly(9, 10, 11);
    }

    private record MockedQuery(Driver driver, ExecutableQuery executableQuery) {
    }

    private static MockedQuery mockQueryReturningTheCurrentVersion(int currentVersion) {
        var driver = mock(Driver.class);
        var query = mock(ExecutableQuery.class, RETURNS_SELF);
        var versionResult = mock(EagerResult.class);
        var emptyResult = mock(EagerResult.class);
        var versionRecord = mock(Record.class);
        when(versionRecord.get("version")).thenReturn(Values.value(currentVersion));
        when(versionResult.records()).thenReturn(List.of(versionRecord));
        when(emptyResult.records()).thenReturn(List.of());
        when(driver.executableQuery(anyString())).thenReturn(query);
        // The first query reads the stored version, every later one is a migration or a version update
        when(query.execute()).thenReturn(versionResult, emptyResult);
        return new MockedQuery(driver, query);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> storedVersions(ExecutableQuery query) {
        ArgumentCaptor<Map<String, Object>> parametersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(query, atLeastOnce()).withParameters(parametersCaptor.capture());
        return parametersCaptor.getAllValues().stream()
                .filter(parameters -> parameters.containsKey("version"))
                .map(parameters -> parameters.get("version"))
                .toList();
    }
}
