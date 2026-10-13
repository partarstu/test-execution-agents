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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository.UnsatisfiedPrerequisite;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SatisfiesEdgeRepositoryTest {

    @Mock
    private Neo4jRepositorySupport mockRepositorySupport;

    private SatisfiesEdgeRepository satisfiesEdgeRepository;

    @BeforeEach
    void setUp() {
        lenient().when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        satisfiesEdgeRepository = new SatisfiesEdgeRepository(mockRepositorySupport);
    }

    @Test
    @DisplayName("findUnsatisfiedPrerequisites queries the phrase of the best-scoring effect")
    void findUnsatisfiedPrerequisites_shouldQueryBestEffectPhrase() {
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of());

        satisfiesEdgeRepository.findUnsatisfiedPrerequisites(UUID.randomUUID(), Set.of(UUID.randomUUID()), 0.9);

        var queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRepositorySupport).executeSingleReadQuery(queryCaptor.capture(), anyMap());
        assertThat(queryCaptor.getValue())
                .contains("head(collect(effNode.${PROP_PHRASE})) AS bestEffectPhrase")
                .contains("RETURN prereq.${PROP_PHRASE} AS phrase, bestScore, bestEffectPhrase");
    }

    @Test
    @DisplayName("findUnsatisfiedPrerequisites scores the prerequisites against the current effect nodes only, without the vector index")
    void findUnsatisfiedPrerequisites_shouldScoreAgainstCurrentEffectNodesOnly() {
        var effectId = UUID.randomUUID();
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of());

        satisfiesEdgeRepository.findUnsatisfiedPrerequisites(UUID.randomUUID(), Set.of(effectId), 0.9);

        var queryCaptor = ArgumentCaptor.forClass(String.class);
        var paramsCaptor = ArgumentCaptor.<Map<String, Object>>captor();
        verify(mockRepositorySupport).executeSingleReadQuery(queryCaptor.capture(), paramsCaptor.capture());
        assertThat(queryCaptor.getValue())
                .contains("WHERE effNode.${PROP_ID} IN $effectNodeIds")
                .contains("vector.similarity.cosine(prereq.${PROP_EMBEDDING}, effNode.${PROP_EMBEDDING})")
                .doesNotContain("db.index.vector.queryNodes");
        assertThat(paramsCaptor.getValue())
                .containsEntry("effectNodeIds", List.of(effectId.toString()))
                .containsEntry("threshold", 0.9)
                .doesNotContainKeys("indexName", "topN");
    }

    @Test
    @DisplayName("findUnsatisfiedPrerequisites maps the best effect phrase into the result")
    void findUnsatisfiedPrerequisites_shouldMapBestEffectPhrase() {
        var matchedRecord = mockRecord("Dashboard page is opened", 0.87, Values.value("Dashboard is displayed"));
        var unmatchedRecord = mockRecord("User is logged in", 0.0, Values.NULL);
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap()))
                .thenReturn(List.of(matchedRecord, unmatchedRecord));

        var result = satisfiesEdgeRepository.findUnsatisfiedPrerequisites(UUID.randomUUID(), Set.of(), 0.9);

        assertThat(result).containsExactly(
                new UnsatisfiedPrerequisite("Dashboard page is opened", 0.87, "Dashboard is displayed"),
                new UnsatisfiedPrerequisite("User is logged in", 0.0, null));
    }

    private static Record mockRecord(String phrase, double bestScore, Value bestEffectPhrase) {
        var record = mock(Record.class);
        when(record.get("phrase")).thenReturn(Values.value(phrase));
        when(record.get("bestScore")).thenReturn(Values.value(bestScore));
        when(record.get("bestEffectPhrase")).thenReturn(bestEffectPhrase);
        return record;
    }
}
