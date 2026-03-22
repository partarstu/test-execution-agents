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
import org.tarik.ta.knowledge_graph.Neo4jConnectionManager;
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

@ExtendWith(MockitoExtension.class)
class ProcedureRepositoryTest {

    private ProcedureRepository procedureRepository;

    @Mock private Session mockSession;
    @Mock private org.neo4j.driver.Driver mockDriver;
    @Mock private ExecutableQuery mockExecutableQuery;
    @Mock private EagerResult mockEagerResult;

    private MockedStatic<Neo4jConnectionManager> connectionManagerMock;
    private MockedStatic<UiTestAgentConfig> configMock;

    @BeforeEach
    void setUp() {
        connectionManagerMock = mockStatic(Neo4jConnectionManager.class);
        connectionManagerMock.when(Neo4jConnectionManager::getSession).thenReturn(mockSession);
        connectionManagerMock.when(Neo4jConnectionManager::getDriver).thenReturn(mockDriver);
        connectionManagerMock.when(() -> Neo4jConnectionManager.executableQuery(anyString())).thenReturn(mockExecutableQuery);
        lenient().when(mockDriver.executableQuery(anyString())).thenReturn(mockExecutableQuery);
        lenient().when(mockExecutableQuery.withConfig(any())).thenReturn(mockExecutableQuery);
        lenient().when(mockExecutableQuery.withParameters(anyMap())).thenReturn(mockExecutableQuery);
        lenient().when(mockExecutableQuery.execute()).thenReturn(mockEagerResult);
        lenient().when(mockEagerResult.records()).thenReturn(List.of());

        configMock = mockStatic(UiTestAgentConfig.class);
        configMock.when(UiTestAgentConfig::getNeo4jDatabase).thenReturn("neo4j");
        configMock.when(UiTestAgentConfig::getStabilityEwmaAlpha).thenReturn(0.3);
        configMock.when(UiTestAgentConfig::getTimingEwmaAlpha).thenReturn(0.2);

        procedureRepository = new ProcedureRepository();
    }

    @AfterEach
    void tearDown() {
        connectionManagerMock.close();
        configMock.close();
    }

    @Test
    @DisplayName("saveWithParent should persist child and create CONTAINS relationship")
    void saveWithParent_shouldPersistChildAndCreateRelationship() {
        UUID parentId = UUID.randomUUID();
        Procedure child = Procedure.createAtomic("child", List.of(), "results", List.of(), List.of(), false);
        float[] embedding = new float[]{0.1f, 0.2f};

        procedureRepository.saveWithParent(child, embedding, parentId, 1);

        // save() + linkToParent() each call executableQuery → 2 execute() calls
        verify(mockExecutableQuery, atLeast(2)).execute();
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

        ArgumentCaptor<Consumer<TransactionContext>> captor = ArgumentCaptor.forClass(Consumer.class);

        procedureRepository.updateElementStability(elementId, true, 100, LocationStrategy.HYBRID);

        verify(mockSession).executeWriteWithoutResult(captor.capture());

        TransactionContext mockTx = mock(TransactionContext.class);
        Result mockRes = mock(Result.class);
        when(mockRes.hasNext()).thenReturn(false);
        when(mockTx.run(anyString(), (Map<String, Object>) any())).thenReturn(mockRes);

        captor.getValue().accept(mockTx);

        verify(mockTx).run(contains("SET el.stabilityScore"), (Map<String, Object>) any());
    }

    @Test
    @DisplayName("updateTimingProfile should compute EWMA and write new timing values")
    void updateTimingProfile_shouldComputeEwmaAndWriteValues() {
        UUID id = UUID.randomUUID();

        ArgumentCaptor<Consumer<TransactionContext>> captor = ArgumentCaptor.forClass(Consumer.class);

        procedureRepository.updateTimingProfile(id, 2000, 1000);

        verify(mockSession).executeWriteWithoutResult(captor.capture());

        TransactionContext mockTx = mock(TransactionContext.class);
        Result mockGetRes = mock(Result.class);
        Record mockRecord = mock(Record.class);
        Value mockAvgExec = mock(Value.class);
        Value mockAvgDelay = mock(Value.class);
        Value mockMaxDelay = mock(Value.class);
        Value mockLastUpdate = mock(Value.class);

        when(mockTx.run(anyString(), (Map<String, Object>) any())).thenReturn(mockGetRes);
        when(mockGetRes.hasNext()).thenReturn(true);
        when(mockGetRes.next()).thenReturn(mockRecord);
        when(mockRecord.get("avgExecMs")).thenReturn(mockAvgExec);
        when(mockRecord.get("avgDelayMs")).thenReturn(mockAvgDelay);
        when(mockRecord.get("maxDelayMs")).thenReturn(mockMaxDelay);
        when(mockRecord.get("lastUpdate")).thenReturn(mockLastUpdate);
        when(mockAvgExec.isNull()).thenReturn(false);
        when(mockAvgExec.asLong()).thenReturn(1000L);
        when(mockAvgDelay.asLong()).thenReturn(500L);
        when(mockMaxDelay.asLong()).thenReturn(2000L);
        when(mockLastUpdate.asString()).thenReturn(Instant.now().toString());

        captor.getValue().accept(mockTx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockTx, atLeastOnce()).run(contains("SET n.avgExecutionMs"), paramsCaptor.capture());

        var params = paramsCaptor.getValue();
        // alpha=0.2, existing=1000, actual=2000 → 1000*(0.8) + 2000*(0.2) = 1200
        assertThat(params.get("avgExecutionMs")).isEqualTo(1200L);
    }
}
