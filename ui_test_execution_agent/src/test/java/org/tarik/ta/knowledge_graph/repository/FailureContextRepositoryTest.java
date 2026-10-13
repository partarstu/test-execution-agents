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
package org.tarik.ta.knowledge_graph.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.TransactionContext;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.neo4j.driver.Values.value;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FailureContextRepositoryTest {
    private static final UUID PROCEDURE_ID = UUID.randomUUID();
    private static final String CURRENT_ID = UUID.randomUUID().toString();

    @Mock
    private Neo4jRepositorySupport mockRepositorySupport;
    @Mock
    private TransactionContext mockTx;

    private FailureContextRepository repository;

    @BeforeEach
    void setUp() {
        when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mockTx.run(anyString(), anyMap())).thenReturn(mock(Result.class));
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionContext>>getArgument(0).accept(mockTx);
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(ArgumentMatchers.<Consumer<TransactionContext>>any());
        repository = new FailureContextRepository(mockRepositorySupport);
    }

    @Test
    void persistFailureContext_shouldCreateAHint_whenNoCurrentHintExists() {
        currentHint(null);

        repository.persistFailureContext(PROCEDURE_ID, failure("wait 3s"));

        assertThat(writeQueries()).singleElement().satisfies(query -> assertThat(query).contains("CREATE (p)-[:${REL_HAS_FAILURE_CONTEXT}]->"));
    }

    @Test
    void persistFailureContext_shouldCountTheOccurrence_whenTheResolutionIsUnchanged() {
        currentHint("wait 3s");

        repository.persistFailureContext(PROCEDURE_ID, failure(" wait 3s "));

        assertThat(writeQueries()).singleElement().satisfies(query -> assertThat(query).contains("+ 1"));
    }

    @Test
    void persistFailureContext_shouldCountTheOccurrence_whenTheFailureCarriesNoResolution() {
        currentHint("wait 3s");

        repository.persistFailureContext(PROCEDURE_ID, failure(""));

        assertThat(writeQueries()).singleElement().satisfies(query -> assertThat(query).contains("+ 1"));
    }

    @Test
    void persistFailureContext_shouldFillInTheResolution_whenTheCurrentHintHasNone() {
        currentHint("");

        repository.persistFailureContext(PROCEDURE_ID, failure("wait 3s"));

        assertThat(writeQueries()).singleElement().satisfies(query -> assertThat(query).contains("+ 1"));
    }

    @Test
    void persistFailureContext_shouldExpireTheCurrentHintAndCreateANewOne_whenTheResolutionChanged() {
        currentHint("wait 3s");

        repository.persistFailureContext(PROCEDURE_ID, failure("scroll down first"));

        var queries = writeQueries();
        assertThat(queries).hasSize(2);
        assertThat(queries.getFirst()).contains("SET fc.${PROP_EXPIRED_AT} = timestamp()");
        assertThat(queries.getLast()).contains("CREATE (p)-[:${REL_HAS_FAILURE_CONTEXT}]->");
    }

    @Test
    void persistFailureContext_shouldLockTheProcedureBeforeLookingUpTheCurrentHint() {
        currentHint(null);

        repository.persistFailureContext(PROCEDURE_ID, failure("wait 3s"));

        var queryCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.<Map<String, Object>>captor();
        verify(mockTx, atLeastOnce()).run(queryCaptor.capture(), paramsCaptor.capture());
        assertThat(queryCaptor.getAllValues().getFirst()).contains("SET p._LOCK_ = true");
        assertThat(paramsCaptor.getAllValues().getFirst()).containsEntry("procedureId", PROCEDURE_ID.toString());
        assertThat(queryCaptor.getAllValues().get(1)).contains("LIMIT 1");
    }

    @Test
    void findFailureContexts_shouldReturnOnlyCurrentResolvedHints_rankedAndLimited() {
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of());

        repository.findFailureContexts(PROCEDURE_ID, 5);

        var queryCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.<Map<String, Object>>captor();
        verify(mockRepositorySupport).executeSingleReadQuery(queryCaptor.capture(), paramsCaptor.capture());
        assertThat(queryCaptor.getValue())
                .contains("fc.${PROP_EXPIRED_AT} IS NULL AND trim(coalesce(fc.${PROP_RESOLUTION}, '')) <> ''")
                .contains("ORDER BY occurrences DESC, lastOccurred DESC")
                .contains("LIMIT $limit");
        assertThat(paramsCaptor.getValue()).containsEntry("limit", 5).containsEntry("procedureId", PROCEDURE_ID.toString());
    }

    @Test
    void expireStale_shouldPassTheStaleWindowInMillisAndReturnTheExpiredCount() {
        var record = mock(Record.class);
        when(record.get("expiredCount")).thenReturn(value(2));
        when(mockRepositorySupport.executeSingleWriteQuery(anyString(), anyMap())).thenReturn(List.of(record));

        int expired = repository.expireStale(90);

        assertThat(expired).isEqualTo(2);
        verify(mockRepositorySupport).executeSingleWriteQuery(anyString(), eq(Map.of("staleMs", 90 * 86_400_000L)));
    }

    @Test
    void deleteLongExpired_shouldDeleteOnlyHintsExpiredBeforeTheStaleWindowAndReturnTheirCount() {
        var record = mock(Record.class);
        when(record.get("deletedCount")).thenReturn(value(3));
        when(mockRepositorySupport.executeSingleWriteQuery(anyString(), anyMap())).thenReturn(List.of(record));

        int deleted = repository.deleteLongExpired(90);

        assertThat(deleted).isEqualTo(3);
        var queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRepositorySupport).executeSingleWriteQuery(queryCaptor.capture(), eq(Map.of("staleMs", 90 * 86_400_000L)));
        assertThat(queryCaptor.getValue())
                .contains("WHERE fc.${PROP_EXPIRED_AT} < timestamp() - $staleMs")
                .contains("DETACH DELETE fc");
    }

    private void currentHint(String resolution) {
        var readResult = mock(Result.class);
        if (resolution == null) {
            when(readResult.list()).thenReturn(List.of());
        } else {
            var record = mock(Record.class);
            when(record.get("id")).thenReturn(value(CURRENT_ID));
            when(record.get("resolution")).thenReturn(value(resolution));
            when(readResult.list()).thenReturn(List.of(record));
        }
        when(mockTx.run(argThat((String query) -> query.contains("LIMIT 1")), anyMap())).thenReturn(readResult);
    }

    private static FailureContext failure(String resolution) {
        return new FailureContext(UUID.randomUUID(), "Slow element", ErrorCategory.TRANSIENT_TOOL_ERROR, resolution, 1, Instant.now(),
                FailureContext.Mode.UNATTENDED);
    }

    private List<String> writeQueries() {
        var queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTx, atLeastOnce()).run(queryCaptor.capture(), anyMap());
        return queryCaptor.getAllValues().stream().filter(query -> !query.contains("LIMIT 1") && !query.contains("_LOCK_")).toList();
    }
}
