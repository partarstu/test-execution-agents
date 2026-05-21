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
package org.tarik.ta.knowledge_graph.service;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchingEnhancementsTest {

    private KnowledgeService knowledgeService;

    @Mock private ProcedureRepository mockRepository;
    @Mock private EmbeddingService mockEmbeddingService;
    @Mock private DecompositionService mockDecompositionService;
    @Mock private PhraseEmbeddingRepository mockPhraseEmbeddingRepository;

    @Mock
    private UiTestAgentConfig configMock;

    @BeforeEach
    void setUp() {
        
        lenient().when(configMock.getKnowledgeMatchConfidenceLow()).thenReturn(0.5);
        lenient().when(configMock.getKnowledgeMatchConfidenceHigh()).thenReturn(0.85);
        lenient().when(configMock.getKnowledgeMatchTopN()).thenReturn(5);
        lenient().when(configMock.getStabilityPenaltyThreshold()).thenReturn(0.5);

        knowledgeService = new KnowledgeService(mockRepository, mockEmbeddingService, mockDecompositionService, mockPhraseEmbeddingRepository, configMock);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("reRank should apply ancestry affinity boost")
    void reRank_shouldApplyAncestryAffinityBoost() {
        UUID parentId = UUID.randomUUID();
        Set<UUID> recentParents = Set.of(parentId);

        Procedure p1 = Procedure.createAtomic("p1", List.of(), "results", List.of(), List.of(), false);
        Procedure p2 = Procedure.createAtomic("p2", List.of(), "results", List.of(), List.of(), false);

        when(mockEmbeddingService.embed(anyString())).thenReturn(new Embedding(new float[0]));
        when(mockRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(p1, 0.9), new ProcedureMatch(p2, 0.9)));

        // p2 has a shared parent, p1 doesn't
        when(mockRepository.findCandidateContextBatch(anyList(), eq(recentParents)))
                .thenReturn(List.of(
                        new ProcedureRepository.CandidateContext(p1.id(), Optional.empty(), Optional.empty(), 0),
                        new ProcedureRepository.CandidateContext(p2.id(), Optional.empty(), Optional.empty(), 1)
                ));
        when(mockPhraseEmbeddingRepository.findPrerequisitesForProcedure(any())).thenReturn(List.of());

        var result = knowledgeService.findBestMatch("test", Set.of(), recentParents);

        assertThat(result).isPresent();
        assertThat(result.get().procedure()).isEqualTo(p2);
    }

    @Test
    @DisplayName("reRank should apply stability penalty")
    void reRank_shouldApplyStabilityPenalty() {
        Procedure p1 = Procedure.createAtomic("p1", List.of(), "results", List.of(), List.of(), false);
        Procedure p2 = Procedure.createAtomic("p2", List.of(), "results", List.of(), List.of(), false);

        UUID el1 = UUID.randomUUID();
        UUID el2 = UUID.randomUUID();

        when(mockEmbeddingService.embed(anyString())).thenReturn(new Embedding(new float[0]));
        when(mockRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(p1, 0.9), new ProcedureMatch(p2, 0.9)));

        // p1 is unstable (0.3 < 0.5 threshold), p2 is stable (1.0)
        when(mockRepository.findCandidateContextBatch(anyList(), anySet()))
                .thenReturn(List.of(
                        new ProcedureRepository.CandidateContext(p1.id(), Optional.of(el1), Optional.of(new ElementLocationHistory(0.3, 100, 0, Instant.now())), 0),
                        new ProcedureRepository.CandidateContext(p2.id(), Optional.of(el2), Optional.of(new ElementLocationHistory(1.0, 100, 0, Instant.now())), 0)
                ));
        when(mockPhraseEmbeddingRepository.findPrerequisitesForProcedure(any())).thenReturn(List.of());

        var result = knowledgeService.findBestMatch("test", Set.of(), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().procedure()).isEqualTo(p1);
        assertThat(result.get().selectedHasLowStability()).isTrue();
    }

    @Test
    @DisplayName("reRank should prefer state compatibility over ancestry and stability")
    void reRank_shouldPreferStateCompatibility() {
        UUID effectId = UUID.randomUUID();
        Set<UUID> effectNodeIds = Set.of(effectId);

        PhraseEmbedding precond = PhraseEmbedding.createPrerequisite("user logged in", new float[]{1.0f});

        // p1 has a met precondition; p2 has ancestry boost but no preconditions
        Procedure p1 = Procedure.createAtomic("p1", List.of(), "results", List.of("user logged in"), List.of(), false);
        Procedure p2 = Procedure.createAtomic("p2", List.of(), "results", List.of(), List.of(), false);

        when(mockEmbeddingService.embed(anyString())).thenReturn(new Embedding(new float[0]));
        when(mockRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(p1, 0.9), new ProcedureMatch(p2, 0.9)));

        when(mockRepository.findCandidateContextBatch(anyList(), anySet()))
                .thenReturn(List.of(
                        new ProcedureRepository.CandidateContext(p1.id(), Optional.empty(), Optional.empty(), 0),
                        new ProcedureRepository.CandidateContext(p2.id(), Optional.empty(), Optional.empty(), 1)
                ));

        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(anyList(), eq(effectNodeIds), anyDouble()))
                .thenReturn(List.of(
                        new PhraseEmbeddingRepository.PrerequisiteSatisfaction(p1.id(), precond.id(), precond.phrase(), true),
                        new PhraseEmbeddingRepository.PrerequisiteSatisfaction(p2.id(), UUID.randomUUID(), "unmet condition", false)
                ));

        var result = knowledgeService.findBestMatch("test", effectNodeIds, Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().procedure()).isEqualTo(p1);
    }
}
