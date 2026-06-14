/*
 * ui-test-execution-agent - ${project.description}
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
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private UiTestAgentConfig mockConfig;

    @Mock
    private MultilingualE5SmallEmbeddingModel mockE5Model;

    @Test
    @DisplayName("EmbeddingService selects BgeStrategy when configured for bge-small-en-v15")
    void constructor_selectsBgeStrategy() {
        when(mockConfig.getKnowledgeEmbeddingModel()).thenReturn("bge-small-en-v15");

        EmbeddingService service = new EmbeddingService(mockConfig);

        assertThat(service.strategy).isInstanceOf(BgeStrategy.class);
        assertThat(service.getModel()).isNotNull();

        // Perform a quick embedding to verify BGE works offline
        Embedding embedding = service.embed("test BGE");
        assertThat(embedding).isNotNull();
        assertThat(embedding.dimension()).isEqualTo(384);
    }

    @Test
    @DisplayName("E5Strategy delegates document and query embedding calls correctly to MultilingualE5SmallEmbeddingModel")
    void e5Strategy_delegatesCorrectly() {
        E5Strategy strategy = new E5Strategy(mockE5Model);
        Embedding expectedEmbedding = new Embedding(new float[]{1.0f, 2.0f});

        when(mockE5Model.embedDocumentBatch(List.of("doc1"))).thenReturn(List.of(expectedEmbedding));
        when(mockE5Model.embedQueryBatch(List.of("query1"))).thenReturn(List.of(expectedEmbedding));

        List<Embedding> docResult = strategy.embedDocuments(List.of("doc1"));
        List<Embedding> queryResult = strategy.embedQueries(List.of("query1"));

        assertThat(docResult).containsExactly(expectedEmbedding);
        assertThat(queryResult).containsExactly(expectedEmbedding);

        verify(mockE5Model).embedDocumentBatch(List.of("doc1"));
        verify(mockE5Model).embedQueryBatch(List.of("query1"));
    }

    @Test
    @DisplayName("E5Strategy documentModel adapter delegates embed and embedAll correctly")
    void e5Strategy_documentModelAdapter_delegatesCorrectly() {
        E5Strategy strategy = new E5Strategy(mockE5Model);
        Embedding expectedEmbedding = new Embedding(new float[]{1.0f, 2.0f});

        when(mockE5Model.embedDocument("hello")).thenReturn(expectedEmbedding);
        when(mockE5Model.embedDocumentBatch(List.of("hello"))).thenReturn(List.of(expectedEmbedding));

        EmbeddingModel adapter = strategy.documentModel();

        Response<Embedding> singleResponse = adapter.embed("hello");
        assertThat(singleResponse.content()).isEqualTo(expectedEmbedding);

        Response<List<Embedding>> batchResponse = adapter.embedAll(List.of(TextSegment.from("hello")));
        assertThat(batchResponse.content()).containsExactly(expectedEmbedding);

        verify(mockE5Model).embedDocument("hello");
        verify(mockE5Model).embedDocumentBatch(List.of("hello"));
    }
}
