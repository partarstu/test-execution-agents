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

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.CandidateContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

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
    @DisplayName("findBestMatch should return match result when found")
    void findBestMatch_shouldReturnMatchResult_whenFound() {
        String description = "test desc";
        Embedding mockEmbedding = new Embedding(new float[]{0.1f});
        Procedure mockProcedure = Procedure.createAtomic("test", List.of(), "results", List.of(), List.of(), false);

        when(mockEmbeddingService.embed(description)).thenReturn(mockEmbedding);
        when(mockRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(mockProcedure, 0.9)));
        when(mockRepository.findCandidateContextBatch(anyList(), anySet()))
                .thenReturn(List.of(new CandidateContext(mockProcedure.id(), Optional.empty(), Optional.empty(), 0)));
        when(mockPhraseEmbeddingRepository.findPrerequisitesForProcedure(mockProcedure.id())).thenReturn(List.of());

        Optional<KnowledgeService.MatchResult> result = knowledgeService.findBestMatch(description, Set.of(), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().procedure()).isEqualTo(mockProcedure);
    }

    @Test
    @DisplayName("demotedDueToPrerequisites is true when top semantic match has unmet prerequisites")
    void findBestMatch_demotedDueToPrerequisites_trueWhenTopMatchHasUnmetPrereqs() {
        // topCandidate (higher score, 0/1 prereqs met) gets demoted by lowCandidate (lower score, 1/1 prereqs met)
        Procedure topCandidate = Procedure.createAtomic("set the pick-up date", List.of(), "results", List.of(), List.of(), false);
        Procedure lowCandidate = Procedure.createAtomic("click the pick-up date field", List.of(), "results", List.of(), List.of(), false);
        UUID prereqNodeId = UUID.randomUUID();
        PhraseEmbedding prereqNode = new PhraseEmbedding(prereqNodeId, "page is open", new float[]{0.1f}, PhraseType.PREREQUISITE);
        UUID effectNodeId = UUID.randomUUID();

        when(mockEmbeddingService.embed(anyString())).thenReturn(new Embedding(new float[]{0.1f}));
        when(mockRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(topCandidate, 0.97), new ProcedureMatch(lowCandidate, 0.90)));
        when(mockRepository.findCandidateContextBatch(anyList(), anySet())).thenReturn(List.of(
                new CandidateContext(topCandidate.id(), Optional.empty(), Optional.empty(), 0),
                new CandidateContext(lowCandidate.id(), Optional.empty(), Optional.empty(), 0)));
        // topCandidate has 1 prerequisite that is NOT met
        when(mockPhraseEmbeddingRepository.findPrerequisitesForProcedure(topCandidate.id())).thenReturn(List.of(prereqNode));
        when(mockPhraseEmbeddingRepository.isPrerequisiteMetByEffects(eq(prereqNodeId), anySet(), anyDouble())).thenReturn(false);
        // lowCandidate has no prerequisites → proportion = 1.0, always feasible
        when(mockPhraseEmbeddingRepository.findPrerequisitesForProcedure(lowCandidate.id())).thenReturn(List.of());

        var result = knowledgeService.findBestMatch("set the pick-up date", Set.of(effectNodeId), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().wasDemoted()).isTrue();
        assertThat(result.get().demotedDueToPrerequisites()).isTrue();
    }

    @Test
    @DisplayName("demotedDueToPrerequisites is false when top semantic match has all prerequisites met but loses on ancestry tiebreaker")
    void findBestMatch_demotedDueToPrerequisites_falseWhenDemotedByAncestry() {
        // Both candidates have equal semantic score and proportion=1.0; lowCandidate wins via parentSharedCount
        Procedure topCandidate = Procedure.createAtomic("set the pick-up date", List.of(), "results", List.of(), List.of(), false);
        Procedure lowCandidate = Procedure.createAtomic("click the pick-up date field", List.of(), "results", List.of(), List.of(), false);

        when(mockEmbeddingService.embed(anyString())).thenReturn(new Embedding(new float[]{0.1f}));
        // Equal semantic scores so topCandidate stays first in semantic results
        when(mockRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(topCandidate, 0.90), new ProcedureMatch(lowCandidate, 0.90)));
        when(mockRepository.findCandidateContextBatch(anyList(), anySet())).thenReturn(List.of(
                new CandidateContext(topCandidate.id(), Optional.empty(), Optional.empty(), 0),
                // lowCandidate shares 1 parent → ancestry boost
                new CandidateContext(lowCandidate.id(), Optional.empty(), Optional.empty(), 1)));
        // Both have no prerequisites → proportion = 1.0 for both
        when(mockPhraseEmbeddingRepository.findPrerequisitesForProcedure(topCandidate.id())).thenReturn(List.of());
        when(mockPhraseEmbeddingRepository.findPrerequisitesForProcedure(lowCandidate.id())).thenReturn(List.of());

        var result = knowledgeService.findBestMatch("set the pick-up date", Set.of(), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().wasDemoted()).isTrue();
        assertThat(result.get().demotedDueToPrerequisites()).isFalse();
    }
}
