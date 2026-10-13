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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Result;
import org.neo4j.driver.TransactionContext;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.ExecutionRun;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Candidate;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Outcome;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Source;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.MatchConfidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.model.ContextEntry.StepType.TEST_STEP;

@ExtendWith(MockitoExtension.class)
class ExecutionRunRepositoryTest {
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Mock
    private Neo4jRepositorySupport repositorySupport;
    @Mock
    private UiTestAgentConfig config;
    @Mock
    private TransactionContext tx;

    private ExecutionRunRepository repository;

    @BeforeEach
    void setUp() {
        when(repositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        repository = new ExecutionRunRepository(repositorySupport, config);
    }

    @Test
    void createRun_shouldMergeTheTestCaseByName_andStoreTheStartAsEpochMillis() {
        var startedAt = Instant.parse("2026-10-01T10:00:00Z");

        repository.createRun(new ExecutionRun(RUN_ID, "Checkout", startedAt, null, ExecutionMode.UNATTENDED, null));

        verify(repositorySupport).executeSingleWriteQuery(argThat(query -> query.contains("MERGE (tc:${LABEL_TEST_CASE} {${PROP_NAME}: "
                + "$testCaseName})") && query.contains("-[:${REL_HAS_RUN}]->")), eq(Map.of("id", RUN_ID.toString(),
                "testCaseName", "Checkout", "startedAt", startedAt.toEpochMilli(), "mode", "UNATTENDED")));
    }

    @Test
    void appendResolution_shouldKeepTheFirstTopNCandidatesAsJson_andLinkTheChosenProcedure() {
        when(config.getKnowledgeMatchTopN()).thenReturn(2);
        var chosen = UUID.randomUUID();
        var candidates = IntStream.range(0, 3)
                .mapToObj(index -> new Candidate(UUID.randomUUID(), "Candidate " + index, 0.9 - index * 0.1, 1, 2, index, false,
                        List.of("Form open")))
                .toList();
        var resolution = new StepResolution(UUID.randomUUID(), 3, TEST_STEP, "Save the form", Source.KNOWLEDGE, MatchConfidence.HIGH,
                candidates, true, false, chosen, Outcome.SUCCEEDED, 1500);

        repository.appendResolution(RUN_ID, resolution);

        var params = ArgumentCaptor.<Map<String, Object>>captor();
        verify(repositorySupport).executeSingleWriteQuery(argThat(query -> query.contains("CREATE (resolution)-[:${REL_CHOSE}]->(chosen)")),
                params.capture());
        assertThat(params.getValue())
                .containsEntry("runId", RUN_ID.toString())
                .containsEntry("chosenProcedureId", chosen.toString())
                .containsEntry("sequence", 3)
                .containsEntry("source", "KNOWLEDGE")
                .containsEntry("confidence", "HIGH")
                .containsEntry("outcome", "SUCCEEDED");
        var json = (String) params.getValue().get("candidates");
        assertThat(json).contains("Candidate 0", "Candidate 1").doesNotContain("Candidate 2").contains("\"semanticScore\":0.9");
    }

    @Test
    void stepResolution_shouldCapItsItemDescriptionAt500Characters() {
        var resolution = new StepResolution(UUID.randomUUID(), 0, TEST_STEP, "x".repeat(600), Source.UNRESOLVED, null, List.of(),
                false, false, null, Outcome.FAILED, 0);

        assertThat(resolution.itemDescription()).hasSize(StepResolution.MAX_ITEM_DESCRIPTION_LENGTH);
    }

    @Test
    void finishRun_shouldStoreTheStatus_andTrimToTheNewestRunsWithTheirResolutions_inOneTransaction() {
        when(config.getRunHistorySize()).thenReturn(500);
        when(tx.run(anyString(), anyMap())).thenReturn(mock(Result.class));
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionContext>>getArgument(0).accept(tx);
            return null;
        }).when(repositorySupport).executeComplexWriteQuery(ArgumentMatchers.<Consumer<TransactionContext>>any());
        var finishedAt = Instant.parse("2026-10-01T10:05:00Z");

        repository.finishRun(RUN_ID, finishedAt, ExecutionRun.Status.PASSED);

        var queries = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.<Map<String, Object>>captor();
        verify(tx, times(2)).run(queries.capture(), params.capture());
        assertThat(params.getAllValues().getFirst()).isEqualTo(Map.of("id", RUN_ID.toString(), "finishedAt", finishedAt.toEpochMilli(),
                "status", "PASSED"));
        assertThat(queries.getAllValues().getLast()).contains("ORDER BY run.${PROP_STARTED_AT} DESC", "SKIP $keep",
                "DETACH DELETE resolution, run");
        assertThat(params.getAllValues().getLast()).isEqualTo(Map.of("keep", 500));
    }
}
