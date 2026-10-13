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
package org.tarik.ta.knowledge_graph.repository;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElementAppearance;
import org.tarik.ta.knowledge_graph.service.UiElementCache;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.neo4j.driver.Values.NULL;
import static org.neo4j.driver.Values.value;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiElementRepositoryTest {

    private UiElementRepository repository;

    @Mock private EmbeddingModel mockEmbeddingModel;
    @Mock private Neo4jRepositorySupport mockRepositorySupport;
    @Mock private UiElementCache mockUiElementCache;
    @Mock private UiTestAgentConfig mockConfig;
    @Mock private TransactionContext mockTx;
    @SuppressWarnings("rawtypes")
    @Mock private Response mockEmbeddingResponse;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        when(mockEmbeddingModel.embed(anyString())).thenReturn(mockEmbeddingResponse);
        when(mockEmbeddingResponse.content()).thenReturn(new dev.langchain4j.data.embedding.Embedding(new float[384]));

        when(mockConfig.getElementAppearanceHistorySize()).thenReturn(3);
        when(mockTx.run(anyString(), anyMap())).thenReturn(mock(Result.class));
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionContext>>getArgument(0).accept(mockTx);
            return null;
        }).when(mockRepositorySupport).executeComplexWriteQuery(ArgumentMatchers.<Consumer<TransactionContext>>any());

        repository = new UiElementRepository(mockRepositorySupport, mockEmbeddingModel, mockUiElementCache, mockConfig);
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
    @DisplayName("remove should delete the element together with its expired appearances")
    void remove_shouldExecuteDeleteQuery() {
        UiElement element = new UiElement(UUID.randomUUID(), "test", "desc", "details", "parent", null, false);

        repository.remove(element);

        var queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRepositorySupport).executeSingleWriteQuery(queryCaptor.capture(), anyMap());
        assertThat(queryCaptor.getValue()).contains("-[:${REL_HAD_APPEARANCE}]->(ap:${LABEL_UI_ELEMENT_APPEARANCE})")
                .contains("DETACH DELETE ap, n");
    }

    @Test
    void update_shouldExpireTheStoredAppearanceAndTrimTheHistory_whenTheAppearanceChanged() {
        var id = UUID.randomUUID();
        storedElement(id, "old description", "near header", "main page", "2026-09-01T10:00:00Z");
        var updated = new UiElement(id, "Save", "new description", "near header", "main page", null, false);

        repository.update(updated, false);

        var queries = executedQueries();
        assertThat(queries).anySatisfy(query -> assertThat(query).contains("CREATE (n)-[:${REL_HAD_APPEARANCE}]->"));
        assertThat(queries).anySatisfy(query -> assertThat(query).contains("UNWIND appearances[$keep..] AS expired"));
        assertThat(queries.getLast()).contains("${PROP_UPDATED_AT} = $updatedAt");
        verify(mockTx).run(argThat((String query) -> query.contains("${PROP_UPDATED_AT} = $updatedAt")),
                argThat((Map<String, Object> params) -> params.get("updatedAt") instanceof String updatedAt
                        && Instant.parse(updatedAt).isAfter(Instant.parse("2026-09-01T10:00:00Z"))));
        verify(mockTx).run(argThat((String query) -> query.contains("UNWIND appearances")),
                argThat((Map<String, Object> params) -> Integer.valueOf(3).equals(params.get("keep"))));
    }

    @Test
    void update_shouldNotExpireTheStoredAppearanceNorMoveItsTimestamp_whenTheAppearanceIsUnchanged() {
        var id = UUID.randomUUID();
        storedElement(id, "a button", "near header", "main page", "2026-09-01T10:00:00Z");

        repository.update(new UiElement(id, "Renamed button", "a button", "near header", "main page", null, false), false);

        assertThat(executedQueries()).noneSatisfy(query -> assertThat(query).contains("${REL_HAD_APPEARANCE}"));
        verify(mockTx).run(argThat((String query) -> query.contains("${PROP_UPDATED_AT} = $updatedAt")),
                argThat((Map<String, Object> params) -> "2026-09-01T10:00:00Z".equals(params.get("updatedAt"))));
    }

    @Test
    void update_shouldNotExpireTheStoredAppearance_whenTheStoredAppearanceIsEmpty() {
        var id = UUID.randomUUID();
        storedElement(id, "", " ", "", "2026-09-01T10:00:00Z");

        repository.update(new UiElement(id, "Save", "a button", "near header", "main page", null, false), false);

        assertThat(executedQueries()).noneSatisfy(query -> assertThat(query).contains("${REL_HAD_APPEARANCE}"));
    }

    @Test
    void findExpiredAppearances_shouldMapTheAppearances_whenTheValidFromIsMissing() {
        var appearanceId = UUID.randomUUID();
        var validTo = Instant.parse("2026-09-01T10:00:00Z");
        var node = mockNode(Map.of("id", value(appearanceId.toString()), "ownDescription", value("old"),
                "anchorsDescription", value("near header"), "parentElementSummary", value("main page"),
                "validTo", value(validTo.toString())));
        var record = mock(Record.class);
        var nodeValue = mock(Value.class);
        when(nodeValue.asNode()).thenReturn(node);
        when(record.get("ap")).thenReturn(nodeValue);
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(record));

        var appearances = repository.findExpiredAppearances(UUID.randomUUID());

        assertThat(appearances).containsExactly(new UiElementAppearance(appearanceId, "old", "near header", "main page", null, null,
                validTo));
    }

    private void storedElement(UUID id, String ownDescription, String anchorsDescription, String parentElementSummary,
                               String updatedAt) {
        var node = mockNode(Map.of("id", value(id.toString()), "name", value("Save"), "ownDescription", value(ownDescription),
                "anchorsDescription", value(anchorsDescription), "parentElementSummary", value(parentElementSummary),
                "updatedAt", value(updatedAt)));
        var record = mock(Record.class);
        var nodeValue = mock(Value.class);
        when(nodeValue.asNode()).thenReturn(node);
        when(record.get("n")).thenReturn(nodeValue);
        var readResult = mock(Result.class);
        when(readResult.list()).thenReturn(List.of(record));
        when(mockTx.run(argThat((String query) -> query.endsWith("RETURN n")), anyMap())).thenReturn(readResult);
    }

    private static Node mockNode(Map<String, Value> properties) {
        var node = mock(Node.class);
        when(node.containsKey(anyString())).thenAnswer(invocation -> properties.containsKey(invocation.<String>getArgument(0)));
        when(node.get(anyString())).thenAnswer(invocation -> properties.getOrDefault(invocation.<String>getArgument(0), NULL));
        return node;
    }

    private List<String> executedQueries() {
        var queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTx, atLeastOnce()).run(queryCaptor.capture(), anyMap());
        return queryCaptor.getAllValues();
    }
}
