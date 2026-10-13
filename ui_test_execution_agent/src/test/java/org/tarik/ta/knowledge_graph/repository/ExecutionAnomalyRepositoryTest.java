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
import org.neo4j.driver.Result;
import org.neo4j.driver.TransactionContext;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.Kind;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionAnomalyRepositoryTest {

    @Mock
    private Neo4jRepositorySupport mockRepositorySupport;
    @Mock
    private UiTestAgentConfig mockConfig;
    @Mock
    private TransactionContext mockTx;

    private ExecutionAnomalyRepository repository;

    @BeforeEach
    void setUp() {
        when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        repository = new ExecutionAnomalyRepository(mockRepositorySupport, mockConfig);
    }

    @Test
    void persist_shouldLinkTheAnomalyToItsSubjectAndTrimTheSubjectHistory() {
        when(mockConfig.getAnomalyHistorySize()).thenReturn(20);
        when(mockTx.run(anyString(), anyMap())).thenReturn(mock(Result.class));
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionContext>>getArgument(0).accept(mockTx);
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(ArgumentMatchers.<Consumer<TransactionContext>>any());
        var subjectId = UUID.randomUUID();
        var anomaly = new ExecutionAnomaly(UUID.randomUUID(), Kind.ELEMENT_LOCATION_FAILED, subjectId, Instant.now(), "Login test",
                "Element not located after 900 ms", "UNATTENDED");

        repository.persist(anomaly);

        var queryCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.<Map<String, Object>>captor();
        verify(mockTx, times(2)).run(queryCaptor.capture(), paramsCaptor.capture());
        assertThat(queryCaptor.getAllValues().getFirst()).contains("MATCH (s:UiElement {${PROP_ID}: $subjectId})")
                .contains("-[:${REL_OBSERVED_ON}]->(s)");
        assertThat(paramsCaptor.getAllValues().getFirst())
                .containsEntry("kind", "ELEMENT_LOCATION_FAILED")
                .containsEntry("subjectId", subjectId.toString())
                .containsEntry("testCaseName", "Login test");
        assertThat(queryCaptor.getAllValues().getLast()).contains("UNWIND anomalies[$keep..] AS expired");
        assertThat(paramsCaptor.getAllValues().getLast()).containsEntry("keep", 20);
    }

    @Test
    void persist_shouldMatchAProcedureSubject_whenTheAnomalyIsAboutAStep() {
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionContext>>getArgument(0).accept(mockTx);
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(ArgumentMatchers.<Consumer<TransactionContext>>any());
        when(mockTx.run(anyString(), anyMap())).thenReturn(mock(Result.class));
        var anomaly = new ExecutionAnomaly(UUID.randomUUID(), Kind.STEP_FAILED, UUID.randomUUID(), Instant.now(), null, "failed",
                "SUPERVISED");

        repository.persist(anomaly);

        verify(mockTx).run(argThat((String query) -> query.contains("MATCH (s:Procedure {${PROP_ID}: $subjectId})")), anyMap());
    }
}
