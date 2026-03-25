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
package org.tarik.ta.knowledge_graph.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.knowledge_graph.location_history.LocationStrategy;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.ExecutableQuery;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.repository.Neo4jRepositorySupport;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProcedureRepositoryTest {

    private ProcedureRepository procedureRepository;

    @Mock private Neo4jRepositorySupport mockRepositorySupport;
    @Mock
    private UiTestAgentConfig configMock;

    @BeforeEach
    void setUp() {
        when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        
        lenient().when(configMock.getNeo4jDatabase()).thenReturn("neo4j");
        lenient().when(configMock.getStabilityEwmaAlpha()).thenReturn(0.3);
        lenient().when(configMock.getTimingEwmaAlpha()).thenReturn(0.2);

        procedureRepository = new ProcedureRepository(mockRepositorySupport, configMock);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("saveWithParent should persist child and create CONTAINS relationship")
    void saveWithParent_shouldPersistChildAndCreateRelationship() {
        UUID parentId = UUID.randomUUID();
        Procedure child = Procedure.createAtomic("child", List.of(), "results", List.of(), List.of(), false);
        float[] embedding = new float[]{0.1f, 0.2f};

        procedureRepository.saveWithParent(child, embedding, parentId, 1);

        verify(mockRepositorySupport, times(2)).executeSingleWriteQuery(anyString(), anyMap());
    }

    @Test
    @DisplayName("findById should return empty when not found in Neo4j")
    void findById_shouldReturnEmpty_whenNotFound() {
        UUID id = UUID.randomUUID();

        Optional<Procedure> result = procedureRepository.findById(id);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("updateElementStability should call executeWriteWithoutResult on session")
    void updateElementStability_shouldCallExecuteWrite() {
        UUID elementId = UUID.randomUUID();

        verify(mockRepositorySupport).executeSingleWriteQuery(contains("SET el.stabilityScore"), anyMap());
    }

    @Test
    @DisplayName("updateTimingProfile should compute EWMA and write new timing values")
    void updateTimingProfile_shouldComputeEwmaAndWriteValues() {
        UUID id = UUID.randomUUID();

        org.neo4j.driver.Record mockRecord = mock(org.neo4j.driver.Record.class);
        when(mockRecord.get("avgExecMs")).thenReturn(org.neo4j.driver.Values.value(1000L));
        when(mockRecord.get("avgDelayMs")).thenReturn(org.neo4j.driver.Values.value(500L));
        when(mockRecord.get("maxDelayMs")).thenReturn(org.neo4j.driver.Values.value(2000L));
        when(mockRecord.get("lastUpdate")).thenReturn(org.neo4j.driver.Values.value(Instant.now().toString()));

        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap()))
                .thenReturn(List.of(mockRecord));

        procedureRepository.updateTimingProfile(id, 2000, 1000);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockRepositorySupport).executeSingleWriteQuery(contains("SET n.avgExecutionMs"), paramsCaptor.capture());

        var params = paramsCaptor.getValue();
        // alpha=0.2, existing=1000, actual=2000 → 1000*(0.8) + 2000*(0.2) = 1200
        assertThat(params.get("avgExecutionMs")).isEqualTo(1200L);
    }
}
