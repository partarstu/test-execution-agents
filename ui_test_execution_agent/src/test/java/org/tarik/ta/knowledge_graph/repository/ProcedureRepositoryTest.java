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
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
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
import static org.neo4j.driver.Values.value;

@ExtendWith(MockitoExtension.class)
class ProcedureRepositoryTest {

    private ProcedureRepository procedureRepository;

    @Mock private Neo4jRepositorySupport mockRepositorySupport;
    @Mock
    private UiTestAgentConfig configMock;

    @BeforeEach
    void setUp() {
        lenient().when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        lenient().doAnswer(invocation -> {
            Consumer<TransactionContext> consumer = invocation.getArgument(0);
            TransactionContext mockTx = mock(TransactionContext.class);
            org.neo4j.driver.Result mockResult = mock(org.neo4j.driver.Result.class);
            when(mockTx.run(anyString(), anyMap())).thenReturn(mockResult);
            consumer.accept(mockTx);
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(any(Consumer.class));

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

        procedureRepository.updateElementStability(elementId, true, 100);

        verify(mockRepositorySupport).executeComplexWriteQuery(any(Consumer.class));
    }

    @Test
    @DisplayName("updateTimingProfile should compute EWMA and write new timing values")
    void updateTimingProfile_shouldComputeEwmaAndWriteValues() {
        UUID id = UUID.randomUUID();

        TransactionContext mockTx = mock(TransactionContext.class);
        org.neo4j.driver.Result mockResult = mock(org.neo4j.driver.Result.class);
        org.neo4j.driver.Record mockRecord = mock(org.neo4j.driver.Record.class);

        lenient().when(mockTx.run(anyString(), anyMap())).thenReturn(mockResult);
        lenient().when(mockResult.hasNext()).thenReturn(true);
        lenient().when(mockResult.next()).thenReturn(mockRecord);

        lenient().when(mockRecord.get("avgExecMs")).thenReturn(org.neo4j.driver.Values.value(1000L));
        lenient().when(mockRecord.get("avgDelayMs")).thenReturn(org.neo4j.driver.Values.value(500L));
        lenient().when(mockRecord.get("maxDelayMs")).thenReturn(org.neo4j.driver.Values.value(2000L));
        lenient().when(mockRecord.get("lastUpdate")).thenReturn(org.neo4j.driver.Values.value(Instant.now().toString()));

        doAnswer(invocation -> {
            Consumer<TransactionContext> consumer = invocation.getArgument(0);
            consumer.accept(mockTx);
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(any(Consumer.class));

        procedureRepository.updateTimingProfile(id, 2000, 1000);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockTx, atLeastOnce()).run(anyString(), paramsCaptor.capture());

        var params = paramsCaptor.getAllValues().stream()
                .filter(p -> p.containsKey("avgExecutionMs"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("UPDATE_TIMING_PROFILE not called with expected params"));

        // alpha=0.2, existing=1000, actual=2000 → 1000*(0.8) + 2000*(0.2) = 1200
        assertThat(params.get("avgExecutionMs")).isEqualTo(1200L);
    }

    @Test
    @DisplayName("save(tx) should delegate to TransactionContext.run without calling executeSingleWriteQuery")
    void save_withTx_shouldRunInTransaction() {
        TransactionContext tx = mock(TransactionContext.class);
        Result mockResult = mock(Result.class);
        when(tx.run(anyString(), anyMap())).thenReturn(mockResult);

        Procedure procedure = Procedure.createAtomic("desc", List.of(), "res", List.of(), List.of(), false);
        procedureRepository.save(procedure, new float[]{0.1f}, tx);

        verify(tx).run(anyString(), anyMap());
        verify(mockRepositorySupport, never()).executeSingleWriteQuery(anyString(), anyMap());
    }

    @Test
    @DisplayName("update(tx) should delegate to TransactionContext.run without calling executeSingleWriteQuery")
    void update_withTx_shouldRunInTransaction() {
        TransactionContext tx = mock(TransactionContext.class);
        Result mockResult = mock(Result.class);
        when(tx.run(anyString(), anyMap())).thenReturn(mockResult);

        Procedure procedure = Procedure.createAtomic("desc", List.of(), "res", List.of(), List.of(), false);
        procedureRepository.update(procedure, new float[]{0.1f}, tx);

        verify(tx).run(anyString(), anyMap());
        verify(mockRepositorySupport, never()).executeSingleWriteQuery(anyString(), anyMap());
    }

    @Test
    @DisplayName("deleteProcedure(tx) should delegate to TransactionContext.run without calling executeSingleWriteQuery")
    void deleteProcedure_withTx_shouldRunInTransaction() {
        TransactionContext tx = mock(TransactionContext.class);
        Result mockResult = mock(Result.class);
        when(tx.run(anyString(), anyMap())).thenReturn(mockResult);

        procedureRepository.deleteProcedure(UUID.randomUUID(), tx);

        verify(tx).run(anyString(), anyMap());
        verify(mockRepositorySupport, never()).executeSingleWriteQuery(anyString(), anyMap());
    }

    @Test
    @DisplayName("deleteTargets(tx) should delegate to TransactionContext.run without calling executeSingleWriteQuery")
    void deleteTargets_withTx_shouldRunInTransaction() {
        TransactionContext tx = mock(TransactionContext.class);
        Result mockResult = mock(Result.class);
        when(tx.run(anyString(), anyMap())).thenReturn(mockResult);

        procedureRepository.deleteTargets(UUID.randomUUID(), tx);

        verify(tx).run(anyString(), anyMap());
        verify(mockRepositorySupport, never()).executeSingleWriteQuery(anyString(), anyMap());
    }

    @Test
    @DisplayName("findWithPhrasePropertyMismatches should delegate to executeSingleReadQuery with no params")
    void findWithPhrasePropertyMismatches_shouldQueryWithNoParams() {
        when(mockRepositorySupport.executeSingleReadQuery(anyString())).thenReturn(List.of());

        var result = procedureRepository.findWithPhrasePropertyMismatches();

        assertThat(result).isEmpty();
        verify(mockRepositorySupport).executeSingleReadQuery(anyString());
    }

    @Test
    @DisplayName("linkToUiElement should delete old TARGETS edge and return only the new target element")
    void linkToUiElement_shouldDeleteOldTargetsEdge_andReturnOnlyNewTarget() {
        UUID procedureId = UUID.randomUUID();
        UUID elementAId = UUID.randomUUID();
        UUID elementBId = UUID.randomUUID();

        procedureRepository.linkToUiElement(procedureId, elementAId);
        procedureRepository.linkToUiElement(procedureId, elementBId);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRepositorySupport, times(2)).executeSingleWriteQuery(queryCaptor.capture(), anyMap());
        queryCaptor.getAllValues().forEach(query -> {
            assertThat(query).contains("OPTIONAL MATCH");
            assertThat(query).contains("DELETE old");
        });

        Record mockRecord = mock(Record.class);
        when(mockRecord.get("elementId")).thenReturn(value(elementBId.toString()));
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(mockRecord));

        var result = procedureRepository.findTargetedUiElementId(procedureId);

        assertThat(result).isPresent().contains(elementBId);
    }

    @Test
    @DisplayName("updatePhraseProperties should call executeSingleWriteQuery with id, prerequisites, and effects")
    void updatePhraseProperties_shouldPassCorrectParams() {
        UUID id = UUID.randomUUID();
        List<String> prereqs = List.of("prereq1");
        List<String> effects = List.of("effect1", "effect2");

        procedureRepository.updatePhraseProperties(id, prereqs, effects);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockRepositorySupport).executeSingleWriteQuery(anyString(), paramsCaptor.capture());
        var params = paramsCaptor.getValue();
        assertThat(params.get("id")).isEqualTo(id.toString());
        assertThat(params.get("prerequisites")).isEqualTo(prereqs);
        assertThat(params.get("effects")).isEqualTo(effects);
    }
}
