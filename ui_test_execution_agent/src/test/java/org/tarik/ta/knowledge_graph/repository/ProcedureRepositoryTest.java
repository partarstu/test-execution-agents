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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
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
import static org.neo4j.driver.Values.NULL;
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
    @DisplayName("updateElementStability leaves the last-located timestamp untouched when the element was not located")
    void updateElementStability_shouldKeepPreviousTimestamp_whenElementWasNotLocated() {
        var write = captureElementStabilityWrite(false);

        assertThat(write.params().get("located")).isEqualTo(false);
        assertThat(write.query()).contains("CASE WHEN $located THEN $lastLocatedAt");
    }

    @Test
    @DisplayName("updateElementStability seeds the last-located timestamp when a first location attempt fails")
    void updateElementStability_shouldSeedTimestamp_whenFirstLocationAttemptFailsOnAnElementWithoutHistory() {
        var write = captureElementStabilityWrite(false);

        // Cypher deletes a property that is SET to null, so the element would otherwise end up with a stability score
        // but no timestamp, which the next read of it cannot parse.
        assertThat(write.query()).contains("ELSE coalesce(el.").contains(", $lastLocatedAt) END");
        assertThat(write.params().get("lastLocatedAt")).isNotNull();
    }

    @Test
    @DisplayName("updateElementStability advances the last-located timestamp when the element was located")
    void updateElementStability_shouldAdvanceTimestamp_whenElementWasLocated() {
        var write = captureElementStabilityWrite(true);

        assertThat(write.params().get("located")).isEqualTo(true);
        assertThat(write.params().get("lastLocatedAt")).isNotNull();
    }

    private record StabilityWrite(String query, Map<String, Object> params) {}

    private StabilityWrite captureElementStabilityWrite(boolean located) {
        TransactionContext mockTx = mock(TransactionContext.class);
        lenient().when(mockTx.run(anyString(), anyMap())).thenReturn(mock(Result.class));
        doAnswer(invocation -> {
            Consumer<TransactionContext> consumer = invocation.getArgument(0);
            consumer.accept(mockTx);
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(ArgumentMatchers.<Consumer<TransactionContext>>any());

        procedureRepository.updateElementStability(UUID.randomUUID(), located, 100);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockTx, atLeastOnce()).run(queryCaptor.capture(), paramsCaptor.capture());
        for (int i = 0; i < paramsCaptor.getAllValues().size(); i++) {
            if (paramsCaptor.getAllValues().get(i).containsKey("located")) {
                return new StabilityWrite(queryCaptor.getAllValues().get(i), paramsCaptor.getAllValues().get(i));
            }
        }
        throw new AssertionError("UPDATE_ELEMENT_STABILITY was not called");
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

        doAnswer(invocation -> invocation.<TransactionCallback<?>>getArgument(0).execute(mockTx))
                .when(mockRepositorySupport).executeComplexWriteQuery(ArgumentMatchers.<TransactionCallback<?>>any());

        var previousProfile = procedureRepository.updateTimingProfile(id, 2000, 1000);

        assertThat(previousProfile).hasValueSatisfying(profile -> assertThat(profile.avgExecutionMs()).isEqualTo(1000L));
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
    void updateTimingProfile_shouldIncrementTheSuccessCount_inTheSameWriteAsTheTiming() {
        var mockTx = mock(TransactionContext.class);
        var mockResult = mock(Result.class);
        when(mockTx.run(anyString(), anyMap())).thenReturn(mockResult);
        doAnswer(invocation -> invocation.<TransactionCallback<?>>getArgument(0).execute(mockTx))
                .when(mockRepositorySupport).executeComplexWriteQuery(ArgumentMatchers.<TransactionCallback<?>>any());

        procedureRepository.updateTimingProfile(UUID.randomUUID(), 2000, 1000);

        verify(mockTx).run(argThat((String query) -> query.contains("n.${PROP_LAST_TIMING_UPDATE} = $lastTimingUpdate")
                && query.contains("n.${PROP_SUCCESS_COUNT} = coalesce(n.${PROP_SUCCESS_COUNT}, 0) + 1")), anyMap());
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
    @DisplayName("updatePhraseProperties should call executeSingleWriteQuery with id, prerequisites, effects and negated effects")
    void updatePhraseProperties_shouldPassCorrectParams() {
        UUID id = UUID.randomUUID();
        List<String> prereqs = List.of("prereq1");
        List<String> effects = List.of("effect1", "effect2");
        List<String> negatedEffects = List.of("dialog is open");

        procedureRepository.updatePhraseProperties(id, prereqs, effects, negatedEffects);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockRepositorySupport).executeSingleWriteQuery(anyString(), paramsCaptor.capture());
        var params = paramsCaptor.getValue();
        assertThat(params.get("id")).isEqualTo(id.toString());
        assertThat(params.get("prerequisites")).isEqualTo(prereqs);
        assertThat(params.get("effects")).isEqualTo(effects);
        assertThat(params.get("negatedEffects")).isEqualTo(negatedEffects);
    }

    @Test
    @DisplayName("save(tx) should persist the negated effects")
    void save_shouldPersistNegatedEffects() {
        TransactionContext tx = mock(TransactionContext.class);
        when(tx.run(anyString(), anyMap())).thenReturn(mock(Result.class));
        Procedure procedure = Procedure.createAtomic("Close dialog", List.of(), "", List.of(), List.of(), false)
                .withNegatedEffects(List.of("dialog is open"));

        procedureRepository.save(procedure, new float[]{0.1f}, tx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(tx).run(queryCaptor.capture(), paramsCaptor.capture());
        assertThat(queryCaptor.getValue()).contains("${PROP_NEGATED_EFFECTS} = $negatedEffects");
        assertThat(paramsCaptor.getValue().get("negatedEffects")).isEqualTo(List.of("dialog is open"));
    }

    @Test
    @DisplayName("findById should read a missing negatedEffects property as an empty list")
    void findById_shouldReadMissingNegatedEffectsAsEmptyList() {
        UUID id = UUID.randomUUID();
        Map<String, Value> properties = Map.of(
                "id", value(id.toString()),
                "description", value("Open dialog"),
                "isAtomic", value(true),
                "effects", value(List.of("dialog is open")));
        Node node = mock(Node.class);
        when(node.containsKey(anyString())).thenAnswer(invocation -> properties.containsKey(invocation.<String>getArgument(0)));
        when(node.get(anyString())).thenAnswer(invocation -> properties.getOrDefault(invocation.<String>getArgument(0), NULL));
        Value nodeValue = mock(Value.class);
        when(nodeValue.asNode()).thenReturn(node);
        Record mockRecord = mock(Record.class);
        when(mockRecord.get("n")).thenReturn(nodeValue);
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(mockRecord));

        var result = procedureRepository.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().effects()).containsExactly("dialog is open");
        assertThat(result.get().negatedEffects()).isEmpty();
    }

    @Test
    @DisplayName("findByIdWithPhrases should collect each phrase list in its own subquery instead of a cross product")
    void findByIdWithPhrases_shouldCollectEachPhraseListInItsOwnSubquery() {
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of());

        procedureRepository.findByIdWithPhrases(UUID.randomUUID());

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRepositorySupport).executeSingleReadQuery(queryCaptor.capture(), anyMap());
        assertThat(queryCaptor.getValue())
                .doesNotContain("OPTIONAL MATCH")
                .contains("} AS prerequisites", "} AS effects", "} AS negatedEffects");
        assertThat(queryCaptor.getValue().split("COLLECT \\{", -1)).hasSize(4);
    }
}
