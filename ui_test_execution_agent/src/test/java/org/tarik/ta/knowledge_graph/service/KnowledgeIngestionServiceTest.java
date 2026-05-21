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
import org.neo4j.driver.TransactionContext;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.Neo4jRepositorySupport;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    private KnowledgeIngestionService knowledgeIngestionService;

    @Mock
    private ProcedureRepository mockRepository;
    @Mock
    private EmbeddingService mockEmbeddingService;
    @Mock
    private DecompositionService mockDecompositionService;
    @Mock
    private SatisfiesEdgeRepository mockSatisfiesEdgeRepository;
    @Mock
    private FailureContextService mockFailureContextService;
    @Mock
    private PhraseEmbeddingRepository mockPhraseEmbeddingRepository;
    @Mock
    private Neo4jRepositorySupport mockRepositorySupport;

    @BeforeEach
    void setUp() {
        knowledgeIngestionService = new KnowledgeIngestionService(mockRepository, mockEmbeddingService, mockDecompositionService, mockSatisfiesEdgeRepository, mockFailureContextService, mockPhraseEmbeddingRepository, mockRepositorySupport);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("ingest should persist procedure tree")
    @SuppressWarnings("unchecked")
    void ingest_shouldPersistProcedureTree() {
        var procedure = Procedure.createAtomic("root", List.of(), "", List.of(), List.of(), false);
        IngestionNode node = new IngestionNode.NewProcedure(procedure, null, List.of());
        Embedding mockEmbedding = new Embedding(new float[]{0.1f});

        when(mockEmbeddingService.embedBatch(anyList())).thenReturn(List.of(mockEmbedding));

        // Mock repositorySupport.executeComplexWriteQuery to call the callback
        doAnswer(invocation -> {
            Consumer<TransactionContext> tx = invocation.getArgument(0);
            tx.accept(mock(TransactionContext.class));
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(any(Consumer.class));

        knowledgeIngestionService.ingest(node);

        verify(mockRepository).save(any(Procedure.class), any(float[].class), any(TransactionContext.class));
        verify(mockDecompositionService).invalidateCache();
    }
}
