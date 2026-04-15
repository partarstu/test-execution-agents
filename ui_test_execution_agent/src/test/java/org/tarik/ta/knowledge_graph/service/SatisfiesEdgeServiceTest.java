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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

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

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("persistSatisfiesEdgesAsync should persist edges from repo matches")
    void persistSatisfiesEdgesAsync_shouldPersistEdgesFromRepoMatches() throws InterruptedException {
        UUID producerId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();

        UUID prereqNodeId = UUID.randomUUID();
        var match = new PhraseEmbeddingRepository.SatisfiesMatch(consumerId, "user logged in", "user logged in", prereqNodeId, 0.95);
        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfiedByProducer(eq(producerId), anyDouble(), anyInt()))
                .thenReturn(List.of(match));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockSatisfiesEdgeRepository).persistSatisfiesEdges(anyList());

        satisfiesEdgeService.persistSatisfiesEdgesAsync(producerId);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        ArgumentCaptor<List<SatisfiesEdge>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockSatisfiesEdgeRepository).persistSatisfiesEdges(captor.capture());

        List<SatisfiesEdge> edges = captor.getValue();
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).producerId()).isEqualTo(producerId);
        assertThat(edges.get(0).consumerId()).isEqualTo(consumerId);
        assertThat(edges.get(0).score()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("persistSatisfiesEdgesAsync should not call repo when no matches found")
    void persistSatisfiesEdgesAsync_shouldNotPersistWhenNoMatches() throws InterruptedException {
        UUID producerId = UUID.randomUUID();

        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfiedByProducer(eq(producerId), anyDouble(), anyInt()))
                .thenReturn(List.of());

        // Give the async thread time to complete
        Thread.sleep(200);

        satisfiesEdgeService.persistSatisfiesEdgesAsync(producerId);
        Thread.sleep(200);

        verify(mockSatisfiesEdgeRepository, never()).persistSatisfiesEdges(anyList());
    }

    @Test
    @DisplayName("persistSatisfiesEdgesAsync should deduplicate by max score per consumer")
    void persistSatisfiesEdgesAsync_shouldDeduplicateByMaxScore() throws InterruptedException {
        UUID producerId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();

        // Two matches for the same (consumer, prerequisiteNodeId) — the higher-scored one should win
        UUID prereqNodeId = UUID.randomUUID();
        var match1 = new PhraseEmbeddingRepository.SatisfiesMatch(consumerId, "effect 1", "prereq 1", prereqNodeId, 0.90);
        var match2 = new PhraseEmbeddingRepository.SatisfiesMatch(consumerId, "effect 2", "prereq 1", prereqNodeId, 0.95);
        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfiedByProducer(eq(producerId), anyDouble(), anyInt()))
                .thenReturn(List.of(match1, match2));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockSatisfiesEdgeRepository).persistSatisfiesEdges(anyList());

        satisfiesEdgeService.persistSatisfiesEdgesAsync(producerId);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

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
        List<String> expectedMissing = List.of("missing prereq 1");

        when(mockSatisfiesEdgeRepository.findUnsatisfiedPrerequisites(eq(stepId), eq(effectIds), anyDouble()))
                .thenReturn(expectedMissing);

        List<String> actualMissing = satisfiesEdgeService.findUnsatisfiedPrerequisites(stepId, effectIds);

        assertThat(actualMissing).isEqualTo(expectedMissing);
        verify(mockSatisfiesEdgeRepository).findUnsatisfiedPrerequisites(eq(stepId), eq(effectIds), anyDouble());
    }
}
