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
package org.tarik.ta.knowledge_graph.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository.UnsatisfiedPrerequisite;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfiesEdgeServiceTest {

    private SatisfiesEdgeService satisfiesEdgeService;

    @Mock
    private SatisfiesEdgeRepository mockSatisfiesEdgeRepository;
    @Mock
    private PhraseEmbeddingRepository mockPhraseEmbeddingRepository;
    @Mock
    private UiTestAgentConfig configMock;

    @BeforeEach
    void setUp() {
        lenient().when(configMock.getSatisfiesSimilarityThreshold()).thenReturn(0.85);
        satisfiesEdgeService = new SatisfiesEdgeService(mockSatisfiesEdgeRepository, mockPhraseEmbeddingRepository, configMock);
    }

    @Test
    @DisplayName("persistSatisfiesEdges should persist edges from repo matches")
    void persistSatisfiesEdges_shouldPersistEdgesFromRepoMatches() {
        UUID producerId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        UUID prereqNodeId = UUID.randomUUID();
        var match = new PhraseEmbeddingRepository.SatisfiesMatch(consumerId, "user logged in", "user logged in", prereqNodeId, 0.95);
        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfiedByProducer(eq(producerId), anyDouble(), anyInt()))
                .thenReturn(List.of(match));

        satisfiesEdgeService.persistSatisfiesEdges(producerId);

        ArgumentCaptor<List<SatisfiesEdge>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockSatisfiesEdgeRepository).persistSatisfiesEdges(captor.capture());

        List<SatisfiesEdge> edges = captor.getValue();
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).producerId()).isEqualTo(producerId);
        assertThat(edges.get(0).consumerId()).isEqualTo(consumerId);
        assertThat(edges.get(0).score()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("persistSatisfiesEdges should not call repo when no matches found")
    void persistSatisfiesEdges_shouldNotPersistWhenNoMatches() {
        UUID producerId = UUID.randomUUID();

        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfiedByProducer(eq(producerId), anyDouble(), anyInt()))
                .thenReturn(List.of());

        satisfiesEdgeService.persistSatisfiesEdges(producerId);

        verify(mockSatisfiesEdgeRepository, never()).persistSatisfiesEdges(anyList());
    }

    @Test
    @DisplayName("persistSatisfiesEdges should deduplicate by max score per consumer")
    void persistSatisfiesEdges_shouldDeduplicateByMaxScore() {
        UUID producerId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        UUID prereqNodeId = UUID.randomUUID();

        var match1 = new PhraseEmbeddingRepository.SatisfiesMatch(consumerId, "effect 1", "prereq 1", prereqNodeId, 0.90);
        var match2 = new PhraseEmbeddingRepository.SatisfiesMatch(consumerId, "effect 2", "prereq 1", prereqNodeId, 0.95);
        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfiedByProducer(eq(producerId), anyDouble(), anyInt()))
                .thenReturn(List.of(match1, match2));

        satisfiesEdgeService.persistSatisfiesEdges(producerId);

        ArgumentCaptor<List<SatisfiesEdge>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockSatisfiesEdgeRepository).persistSatisfiesEdges(captor.capture());

        List<SatisfiesEdge> edges = captor.getValue();
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).score()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("findUnsatisfiedPrerequisites should delegate to repository")
    void findUnsatisfiedPrerequisites_shouldDelegateToRepository() {
        UUID stepId = UUID.randomUUID();
        java.util.Set<UUID> effectIds = java.util.Set.of(UUID.randomUUID(), UUID.randomUUID());
        var repoResult = List.of(new UnsatisfiedPrerequisite("missing prereq 1", 0.0, null));

        when(mockSatisfiesEdgeRepository.findUnsatisfiedPrerequisites(eq(stepId), eq(effectIds), anyDouble()))
                .thenReturn(repoResult);

        List<UnsatisfiedPrerequisite> actualMissing = satisfiesEdgeService.findUnsatisfiedPrerequisites(stepId, effectIds);

        assertThat(actualMissing).isEqualTo(repoResult);
        verify(mockSatisfiesEdgeRepository).findUnsatisfiedPrerequisites(eq(stepId), eq(effectIds), anyDouble());
    }
}
