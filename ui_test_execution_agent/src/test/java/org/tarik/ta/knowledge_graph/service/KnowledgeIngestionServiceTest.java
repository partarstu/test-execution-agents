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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.TransactionContext;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.Neo4jRepositorySupport;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @SuppressWarnings("unchecked")
    void ingest_shouldSaveTheNegatedEffectPhraseNodes_whenTheProcedureDeclaresNegatedEffects() {
        var procedure = Procedure.createAtomic("Close dialog", List.of(), "", List.of("the dialog is open"), List.of("the form is shown"),
                false).withNegatedEffects(List.of("the dialog is open"));
        when(mockEmbeddingService.embedBatch(List.of("Close dialog"))).thenReturn(List.of(new Embedding(new float[]{0.1f})));
        when(mockEmbeddingService.embedBatch(List.of("the dialog is open", "the form is shown", "the dialog is open"))).thenReturn(List.of(
                new Embedding(new float[]{0.2f}), new Embedding(new float[]{0.3f}), new Embedding(new float[]{0.4f})));
        var tx = mock(TransactionContext.class);
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionContext>>getArgument(0).accept(tx);
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(any(Consumer.class));

        knowledgeIngestionService.ingest(new IngestionNode.NewProcedure(procedure, null, List.of()));

        var negatedCaptor = ArgumentCaptor.<List<PhraseEmbedding>>captor();
        verify(mockPhraseEmbeddingRepository).saveBatchForProcedure(eq(procedure.id()), anyList(), anyList(), negatedCaptor.capture(), eq(tx));
        assertThat(negatedCaptor.getValue()).singleElement().satisfies(node -> {
            assertThat(node.phrase()).isEqualTo("the dialog is open");
            assertThat(node.type()).isEqualTo(PhraseType.NEGATED_EFFECT);
            assertThat(node.embedding()).containsExactly(0.4f);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void update_shouldReplaceThePhraseNodesWithTheNegatedEffects_whenTheUpdatedProcedureDeclaresNegatedEffects() {
        var existing = Procedure.createAtomic("Close dialog", List.of(), "", List.of(), List.of(), false);
        var edited = Procedure.createAtomic("Close dialog", List.of(), "", List.of(), List.of(), false)
                .withNegatedEffects(List.of("the dialog is open"));
        when(mockRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(mockEmbeddingService.embedBatch(List.of("Close dialog"))).thenReturn(List.of(new Embedding(new float[]{0.1f})));
        when(mockEmbeddingService.embedBatch(List.of("the dialog is open"))).thenReturn(List.of(new Embedding(new float[]{0.4f})));
        var tx = mock(TransactionContext.class);
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionContext>>getArgument(0).accept(tx);
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(any(Consumer.class));

        knowledgeIngestionService.update(existing.id(), new IngestionNode.NewProcedure(edited, null, List.of()));

        var negatedCaptor = ArgumentCaptor.<List<PhraseEmbedding>>captor();
        var inOrder = inOrder(mockPhraseEmbeddingRepository);
        inOrder.verify(mockPhraseEmbeddingRepository).deleteForProcedure(existing.id(), tx);
        inOrder.verify(mockPhraseEmbeddingRepository).saveBatchForProcedure(eq(existing.id()), eq(List.of()), eq(List.of()),
                negatedCaptor.capture(), eq(tx));
        assertThat(negatedCaptor.getValue()).singleElement().satisfies(node -> {
            assertThat(node.phrase()).isEqualTo("the dialog is open");
            assertThat(node.type()).isEqualTo(PhraseType.NEGATED_EFFECT);
        });
    }

    @Test
    void createAndSavePhraseNodes_shouldBackfillTheNegatedEffects_whenTheyAreTheOnlyPhrasesOfTheProcedure() {
        var procedure = Procedure.createAtomic("Close dialog", List.of(), "", List.of(), List.of(), false)
                .withNegatedEffects(List.of("the dialog is open"));
        when(mockEmbeddingService.embedBatch(List.of("the dialog is open"))).thenReturn(List.of(new Embedding(new float[]{0.4f})));

        knowledgeIngestionService.createAndSavePhraseNodes(procedure);

        var negatedCaptor = ArgumentCaptor.<List<PhraseEmbedding>>captor();
        verify(mockPhraseEmbeddingRepository).saveBatchForProcedure(eq(procedure.id()), eq(List.of()), eq(List.of()),
                negatedCaptor.capture());
        assertThat(negatedCaptor.getValue()).singleElement().satisfies(node -> {
            assertThat(node.phrase()).isEqualTo("the dialog is open");
            assertThat(node.type()).isEqualTo(PhraseType.NEGATED_EFFECT);
            assertThat(node.embedding()).containsExactly(0.4f);
        });
    }
}
