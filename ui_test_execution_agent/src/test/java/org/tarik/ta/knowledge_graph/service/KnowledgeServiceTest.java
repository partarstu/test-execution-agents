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
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository.PrerequisiteSatisfaction;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.CandidateContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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

        lenient().when(configMock.getKnowledgeMatchConfidenceLow()).thenReturn(0.88);
        lenient().when(configMock.getKnowledgeMatchConfidenceHigh()).thenReturn(0.97);
        lenient().when(configMock.getKnowledgeMatchTopN()).thenReturn(5);
        lenient().when(configMock.getStabilityPenaltyThreshold()).thenReturn(0.5);

        knowledgeService = new KnowledgeService(mockRepository, mockEmbeddingService, mockDecompositionService,
                mockPhraseEmbeddingRepository, configMock);
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
        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(anyList(), anySet(), anyDouble()))
                .thenReturn(List.of());

        Optional<KnowledgeService.MatchResult> result = knowledgeService.findBestMatch(description, Set.of(), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().procedure()).isEqualTo(mockProcedure);
    }

    @Test
    @DisplayName("findBestMatch returns LOW confidence when the score stays below the high threshold")
    void findBestMatch_shouldReturnLowConfidence_whenScoreBelowHighThreshold() {
        String description = "Select 'Germany' in the country dropdown";
        var candidate = Procedure.createAtomic("Select 'France' in the country dropdown", List.of(), "", List.of(), List.of(), false);
        stubSemanticSearch(candidate, 0.966);

        var result = knowledgeService.findBestMatch(description, Set.of(), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isEqualTo(KnowledgeService.MatchConfidence.LOW);
    }

    @Test
    @DisplayName("findBestMatch returns HIGH confidence when the score reaches the high threshold")
    void findBestMatch_shouldReturnHighConfidence_whenScoreReachesHighThreshold() {
        String description = "Select 'Germany' in the country dropdown";
        var candidate = Procedure.createAtomic("Select 'Germany' in the country dropdown", List.of(), "", List.of(), List.of(), false);
        stubSemanticSearch(candidate, 0.975);

        var result = knowledgeService.findBestMatch(description, Set.of(), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isEqualTo(KnowledgeService.MatchConfidence.HIGH);
    }

    private void stubSemanticSearch(Procedure candidate, double score) {
        when(mockEmbeddingService.embed(anyString())).thenReturn(new Embedding(new float[]{0.1f}));
        when(mockRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(candidate, score)));
        when(mockRepository.findCandidateContextBatch(anyList(), anySet()))
                .thenReturn(List.of(new CandidateContext(candidate.id(), Optional.empty(), Optional.empty(), 0)));
        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(anyList(), anySet(), anyDouble()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("demotedDueToPrerequisites is true when top semantic match has unmet prerequisites")
    void findBestMatch_demotedDueToPrerequisites_trueWhenTopMatchHasUnmetPrereqs() {
        // topCandidate (higher score, 0/1 prereqs met) gets demoted by lowCandidate (lower score, 1/1 prereqs met)
        Procedure topCandidate = Procedure.createAtomic("set the pick-up date", List.of(), "results", List.of(), List.of(), false);
        Procedure lowCandidate = Procedure.createAtomic("click the pick-up date field", List.of(), "results", List.of(), List.of(), false);
        UUID prereqNodeId = UUID.randomUUID();
        UUID effectNodeId = UUID.randomUUID();

        when(mockEmbeddingService.embed(anyString())).thenReturn(new Embedding(new float[]{0.1f}));
        when(mockRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(topCandidate, 0.97), new ProcedureMatch(lowCandidate, 0.90)));
        when(mockRepository.findCandidateContextBatch(anyList(), anySet())).thenReturn(List.of(
                new CandidateContext(topCandidate.id(), Optional.empty(), Optional.empty(), 0),
                new CandidateContext(lowCandidate.id(), Optional.empty(), Optional.empty(), 0)));

        // topCandidate has 1 prerequisite that is NOT met; lowCandidate has no prerequisites (implicitly handled by empty results in batch)
        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(anyList(), anySet(), anyDouble()))
                .thenReturn(List.of(new PrerequisiteSatisfaction(topCandidate.id(), prereqNodeId, "page is open", false)));

        var result = knowledgeService.findBestMatch("set the pick-up date", Set.of(effectNodeId), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().wasDemoted()).isTrue();
        assertThat(result.get().demotedDueToPrerequisites()).isTrue();
    }

    @Test
    @DisplayName("Each ranked candidate keeps the semantic score, shared parents, low stability and unmet prerequisites of its re-ranking")
    void findBestMatch_shouldKeepTheReRankingValuesOfEveryCandidate() {
        Procedure topCandidate = Procedure.createAtomic("set the pick-up date", List.of(), "results", List.of(), List.of(), false);
        Procedure lowCandidate = Procedure.createAtomic("click the pick-up date field", List.of(), "results", List.of(), List.of(), false);
        when(mockEmbeddingService.embed(anyString())).thenReturn(new Embedding(new float[]{0.1f}));
        when(mockRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(topCandidate, 0.97), new ProcedureMatch(lowCandidate, 0.90)));
        var unstable = new ElementLocationHistory(0.2, 900, 2.0, Instant.parse("2026-10-01T10:00:00Z"));
        when(mockRepository.findCandidateContextBatch(anyList(), anySet())).thenReturn(List.of(
                new CandidateContext(topCandidate.id(), Optional.empty(), Optional.empty(), 0),
                new CandidateContext(lowCandidate.id(), Optional.of(UUID.randomUUID()), Optional.of(unstable), 2)));
        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(anyList(), anySet(), anyDouble()))
                .thenReturn(List.of(new PrerequisiteSatisfaction(topCandidate.id(), UUID.randomUUID(), "page is open", false)));

        var ranked = knowledgeService.findBestMatch("set the pick-up date", Set.of(UUID.randomUUID()), Set.of()).orElseThrow().allMatches();

        assertThat(ranked).extracting(scored -> scored.procedure().id()).containsExactly(lowCandidate.id(), topCandidate.id());
        var low = ranked.getFirst();
        assertThat(low.semanticScore()).isEqualTo(0.90);
        assertThat(low.sharedParentCount()).isEqualTo(2);
        assertThat(low.lowStability()).isTrue();
        assertThat(low.unmetPrerequisites()).isEmpty();
        var top = ranked.getLast();
        assertThat(top.semanticScore()).isEqualTo(0.97);
        assertThat(top.satisfied()).isZero();
        assertThat(top.totalPrereqs()).isEqualTo(1);
        assertThat(top.lowStability()).isFalse();
        assertThat(top.unmetPrerequisites()).containsExactly("page is open");
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

        // Both have no prerequisites
        when(mockPhraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(anyList(), anySet(), anyDouble()))
                .thenReturn(List.of());

        var result = knowledgeService.findBestMatch("set the pick-up date", Set.of(), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().wasDemoted()).isTrue();
        assertThat(result.get().demotedDueToPrerequisites()).isFalse();
    }
}
