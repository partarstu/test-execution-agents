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
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    private KnowledgeService knowledgeService;

    @Mock private ProcedureRepository mockRepository;
    @Mock private EmbeddingService mockEmbeddingService;
    @Mock private DecompositionService mockDecompositionService;
    @Mock private PhraseEmbeddingRepository mockPhraseEmbeddingRepository;

    private MockedStatic<UiTestAgentConfig> configMock;

    @BeforeEach
    void setUp() {
        configMock = mockStatic(UiTestAgentConfig.class);
        configMock.when(UiTestAgentConfig::getKnowledgeMatchConfidenceLow).thenReturn(0.5);
        configMock.when(UiTestAgentConfig::getKnowledgeMatchConfidenceHigh).thenReturn(0.85);
        configMock.when(UiTestAgentConfig::getKnowledgeMatchTopN).thenReturn(5);
        configMock.when(UiTestAgentConfig::getStabilityPenaltyThreshold).thenReturn(0.5);

        knowledgeService = new KnowledgeService(mockRepository, mockEmbeddingService, mockDecompositionService, mockPhraseEmbeddingRepository);
    }

    @AfterEach
    void tearDown() {
        configMock.close();
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
                .thenReturn(List.of(new ProcedureRepository.CandidateContext(mockProcedure.id(), Optional.empty(), Optional.empty(), 0)));
        when(mockPhraseEmbeddingRepository.findPrerequisitesForProcedure(mockProcedure.id())).thenReturn(List.of());

        Optional<KnowledgeService.MatchResult> result = knowledgeService.findBestMatch(description, Set.of(), Set.of());

        assertThat(result).isPresent();
        assertThat(result.get().procedure()).isEqualTo(mockProcedure);
    }
}
