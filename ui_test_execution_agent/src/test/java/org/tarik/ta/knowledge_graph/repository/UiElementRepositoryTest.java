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
package org.tarik.ta.knowledge_graph.repository;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiElementRepositoryTest {

    private UiElementRepository repository;

    @Mock private EmbeddingModel mockEmbeddingModel;
    @Mock private Neo4jRepositorySupport mockRepositorySupport;
    @SuppressWarnings("rawtypes")
    @Mock private Response mockEmbeddingResponse;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        when(mockEmbeddingModel.embed(anyString())).thenReturn(mockEmbeddingResponse);
        when(mockEmbeddingResponse.content()).thenReturn(new dev.langchain4j.data.embedding.Embedding(new float[384]));

        repository = new UiElementRepository(mockRepositorySupport, mockEmbeddingModel);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("save should compute embedding and execute Neo4j query")
    void create_shouldComputeEmbeddingAndExecuteQuery() {
        UiElement element = new UiElement(UUID.randomUUID(), "test-button", "a button", "near header", "main page", null, false);

        repository.create(element);

        verify(mockEmbeddingModel).embed(element.name());
        verify(mockRepositorySupport).executeSingleWriteQuery(anyString(), anyMap());
    }

    @Test
    @DisplayName("findBySemanticSearch should return empty list when no matches")
    void findBySemanticSearch_shouldReturnEmptyList_whenNoMatches() {
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of());
        List<UiElementRepository.UiElementMatch> result = repository.findBySemanticSearch("query text", 5, 0.8);

        assertThat(result).isEmpty();
        verify(mockEmbeddingModel).embed("query text");
    }

    @Test
    @DisplayName("remove should execute DETACH DELETE query")
    void remove_shouldExecuteDeleteQuery() {
        UiElement element = new UiElement(UUID.randomUUID(), "test", "desc", "details", "parent", null, false);

        repository.remove(element);

        verify(mockRepositorySupport).executeSingleWriteQuery(anyString(), anyMap());
    }
}
