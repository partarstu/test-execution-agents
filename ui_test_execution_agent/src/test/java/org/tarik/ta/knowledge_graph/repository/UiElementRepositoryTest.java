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
import org.tarik.ta.knowledge_graph.service.UiElementCache;

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
    @Mock private UiElementCache mockUiElementCache;
    @SuppressWarnings("rawtypes")
    @Mock private Response mockEmbeddingResponse;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        when(mockEmbeddingModel.embed(anyString())).thenReturn(mockEmbeddingResponse);
        when(mockEmbeddingResponse.content()).thenReturn(new dev.langchain4j.data.embedding.Embedding(new float[384]));

        repository = new UiElementRepository(mockRepositorySupport, mockEmbeddingModel, mockUiElementCache);
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
