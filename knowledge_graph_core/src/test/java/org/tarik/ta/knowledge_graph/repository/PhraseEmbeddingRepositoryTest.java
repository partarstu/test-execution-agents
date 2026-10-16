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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository.PrerequisiteSatisfaction;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhraseEmbeddingRepositoryTest {

    @Mock
    private Neo4jRepositorySupport mockRepositorySupport;

    private PhraseEmbeddingRepository phraseEmbeddingRepository;

    @BeforeEach
    void setUp() {
        lenient().when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        phraseEmbeddingRepository = new PhraseEmbeddingRepository(mockRepositorySupport);
    }

    @Test
    @DisplayName("findPrerequisitesSatisfactionBatch scores the prerequisites against the current effect nodes only, not the vector index")
    void findPrerequisitesSatisfactionBatch_shouldScoreAgainstCurrentEffectNodesOnly() {
        var procedureId = UUID.randomUUID();
        var effectId = UUID.randomUUID();
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of());

        phraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(List.of(procedureId), Set.of(effectId), 0.85);

        var queryCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.<Map<String, Object>>captor();
        verify(mockRepositorySupport).executeSingleReadQuery(queryCaptor.capture(), paramsCaptor.capture());
        assertThat(queryCaptor.getValue())
                .contains("WHERE effNode.${PROP_ID} IN $effectNodeIds AND effNode.${PROP_TYPE} = $phraseType")
                .contains("vector.similarity.cosine(pe.${PROP_EMBEDDING}, effNode.${PROP_EMBEDDING}) >= $threshold")
                .doesNotContain("db.index.vector.queryNodes");
        assertThat(paramsCaptor.getValue())
                .containsEntry("procedureIds", List.of(procedureId.toString()))
                .containsEntry("effectNodeIds", List.of(effectId.toString()))
                .containsEntry("threshold", 0.85)
                .containsEntry("phraseType", "EFFECT")
                .doesNotContainKeys("indexName", "topN");
    }

    @Test
    @DisplayName("findPrerequisitesSatisfactionBatch maps each procedure prerequisite to its satisfaction, treating a null result as unmet")
    void findPrerequisitesSatisfactionBatch_shouldMapTheSatisfactionOfEachPrerequisite() {
        var procedureId = UUID.randomUUID();
        var metPrerequisiteId = UUID.randomUUID();
        var unmetPrerequisiteId = UUID.randomUUID();
        var metRecord = mockRecord(procedureId, metPrerequisiteId, "The toggle is visible", true);
        var unmetRecord = mockRecord(procedureId, unmetPrerequisiteId, "The user is logged in", null);
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(metRecord, unmetRecord));

        var result = phraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(List.of(procedureId), Set.of(UUID.randomUUID()), 0.85);

        assertThat(result).containsExactly(
                new PrerequisiteSatisfaction(procedureId, metPrerequisiteId, "The toggle is visible", true),
                new PrerequisiteSatisfaction(procedureId, unmetPrerequisiteId, "The user is logged in", false));
    }

    @Test
    @DisplayName("findPrerequisitesSatisfactionBatch skips the query when there are no current effects")
    void findPrerequisitesSatisfactionBatch_shouldReturnEmpty_whenThereAreNoEffects() {
        var result = phraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(List.of(UUID.randomUUID()), Set.of(), 0.85);

        assertThat(result).isEmpty();
        verify(mockRepositorySupport, never()).executeSingleReadQuery(anyString(), anyMap());
    }

    @Test
    @DisplayName("findEffectNodesNegatedBy scores the procedure negated effects against the current effect nodes only")
    void findEffectNodesNegatedBy_shouldReturnTheMatchedCurrentEffectIds() {
        var procedureId = UUID.randomUUID();
        var negatedEffectId = UUID.randomUUID();
        var record = mock(Record.class);
        when(record.get("effectNodeId")).thenReturn(Values.value(negatedEffectId.toString()));
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(record));

        var result = phraseEmbeddingRepository.findEffectNodesNegatedBy(procedureId, Set.of(negatedEffectId, UUID.randomUUID()), 0.9);

        assertThat(result).containsExactly(negatedEffectId);
        var queryCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.<Map<String, Object>>captor();
        verify(mockRepositorySupport).executeSingleReadQuery(queryCaptor.capture(), paramsCaptor.capture());
        assertThat(queryCaptor.getValue())
                .contains("-[:${REL_HAS_NEGATED_EFFECT}]->(neg:${LABEL_PHRASE_EMBEDDING})")
                .contains("vector.similarity.cosine(neg.${PROP_EMBEDDING}, effNode.${PROP_EMBEDDING}) >= $threshold");
        assertThat(paramsCaptor.getValue())
                .containsEntry("procedureId", procedureId.toString())
                .containsEntry("threshold", 0.9)
                .containsEntry("phraseType", "EFFECT");
    }

    @Test
    @DisplayName("findEffectNodesNegatedBy skips the query when there are no current effects")
    void findEffectNodesNegatedBy_shouldReturnEmpty_whenThereAreNoEffects() {
        var result = phraseEmbeddingRepository.findEffectNodesNegatedBy(UUID.randomUUID(), Set.of(), 0.9);

        assertThat(result).isEmpty();
        verify(mockRepositorySupport, never()).executeSingleReadQuery(anyString(), anyMap());
    }

    @Test
    @DisplayName("saveBatchForProcedure links the negated effects with HAS_NEGATED_EFFECT and the NEGATED_EFFECT type")
    void saveBatchForProcedure_shouldSaveTheNegatedEffects() {
        var procedureId = UUID.randomUUID();
        var negated = PhraseEmbedding.createNegatedEffect("the dialog is open", new float[]{0.1f});

        phraseEmbeddingRepository.saveBatchForProcedure(procedureId, List.of(), List.of(), List.of(negated));

        var queryCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.<Map<String, Object>>captor();
        verify(mockRepositorySupport).executeSingleWriteQuery(queryCaptor.capture(), paramsCaptor.capture());
        assertThat(queryCaptor.getValue()).contains("CREATE (p)-[:${REL_HAS_NEGATED_EFFECT}");
        assertThat(paramsCaptor.getValue()).containsEntry("phraseType", "NEGATED_EFFECT");
    }

    private static Record mockRecord(UUID procedureId, UUID prerequisiteId, String phrase, Boolean isMet) {
        var record = mock(Record.class);
        when(record.get("procedureId")).thenReturn(Values.value(procedureId.toString()));
        when(record.get("prereqNodeId")).thenReturn(Values.value(prerequisiteId.toString()));
        when(record.get("phrase")).thenReturn(Values.value(phrase));
        when(record.get("isMet")).thenReturn(isMet == null ? Values.NULL : Values.value(isMet));
        return record;
    }
}
