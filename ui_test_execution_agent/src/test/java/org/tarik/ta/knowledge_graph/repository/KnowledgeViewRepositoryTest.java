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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.dto.panel.knowledge.ElementPage.Sort;
import org.tarik.ta.dto.panel.knowledge.GraphEdge;
import org.tarik.ta.dto.panel.knowledge.GraphNode;
import org.tarik.ta.dto.panel.knowledge.GraphNode.Flag;
import org.tarik.ta.dto.panel.knowledge.GraphNode.Kind;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Outcome;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Source;
import org.tarik.ta.knowledge_graph.repository.KnowledgeViewRepository.TrendRow;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.neo4j.driver.Values.NULL;
import static org.neo4j.driver.Values.value;

@ExtendWith(MockitoExtension.class)
class KnowledgeViewRepositoryTest {
    private static final UUID PROCEDURE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ELEMENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Mock
    private Neo4jRepositorySupport repositorySupport;
    @Mock
    private UiTestAgentConfig config;

    private KnowledgeViewRepository repository;

    @BeforeEach
    void setUp() {
        when(repositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(config.getStabilityPenaltyThreshold()).thenReturn(0.5);
        lenient().when(config.getHealthRecentWindowDays()).thenReturn(30);
        lenient().when(config.getKnowledgeMaxDepth()).thenReturn(4);
        repository = new KnowledgeViewRepository(repositorySupport, config);
    }

    @Test
    void everyQuery_shouldBoundItsRows() {
        var queries = ArgumentCaptor.forClass(String.class);

        verify(repositorySupport, atLeastOnce()).cypher(queries.capture());

        assertThat(queries.getAllValues()).allSatisfy(query -> assertThat(query).containsAnyOf("LIMIT $limit", "LIMIT 1",
                "LIMIT $anomalyLimit"));
    }

    @Test
    void search_shouldLowercaseTheQuery_bindTheLimitAndTheFlagParameters_andMapTheFlags() {
        var row = record(Map.of("id", value(PROCEDURE_ID.toString()), "kind", value("ATOMIC"), "label", value("Click Save"),
                "noTarget", value(true), "noEffects", value(false), "lowStability", value(false), "recentAnomaly", value(true),
                "testCases", value(3)));
        when(repositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(row));

        var hits = repository.search("Click SAVE", 51);

        assertThat(hits).containsExactly(new GraphNode(PROCEDURE_ID.toString(), Kind.ATOMIC, "Click Save",
                Set.of(Flag.NO_TARGET, Flag.RECENT_ANOMALY), 3));
        verify(repositorySupport).executeSingleReadQuery(anyString(), argThat(params -> params.get("query").equals("click save")
                && params.get("limit").equals(51) && params.get("stabilityThreshold").equals(0.5) && params.get("windowDays").equals(30)
                && params.get("testCasePrefix").equals(GraphNode.TEST_CASE_ID_PREFIX)));
    }

    @Test
    void neighbourhood_shouldBindTheProcedureTheMaxDepthAndTheLimit() {
        when(repositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of());

        repository.neighbourhood(PROCEDURE_ID, 301);

        verify(repositorySupport).executeSingleReadQuery(argThat(query -> query.contains("length(path) <= $maxDepth")), argThat(params ->
                params.get("id").equals(PROCEDURE_ID.toString()) && params.get("maxDepth").equals(4) && params.get("limit").equals(301)));
    }

    @Test
    void edgesBetween_shouldSplitTestCasesFromOtherNodes_andMapTheEdgeValues() {
        var nodes = List.of(new GraphNode(PROCEDURE_ID.toString(), Kind.ATOMIC, "Click", Set.of(), 0),
                new GraphNode(GraphNode.TEST_CASE_ID_PREFIX + "Checkout", Kind.TEST_CASE, "Checkout", Set.of(), 0));
        var row = record(Map.of("source", value(PROCEDURE_ID.toString()), "target", value("other"), "kind", value("SATISFIES"),
                "sequence", NULL, "score", value(0.9), "effectPhrase", value("Saved"), "prerequisitePhrase", value("Data saved")));
        when(repositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(row));

        var edges = repository.edgesBetween(nodes, 1200);

        assertThat(edges).containsExactly(new GraphEdge(PROCEDURE_ID.toString(), "other", GraphEdge.Kind.SATISFIES, null, 0.9, "Saved",
                "Data saved"));
        verify(repositorySupport).executeSingleReadQuery(anyString(), argThat(params ->
                params.get("ids").equals(List.of(PROCEDURE_ID.toString())) && params.get("testCaseNames").equals(List.of("Checkout"))
                        && params.get("limit").equals(1200)));
    }

    @Test
    void procedureExtras_shouldMapTheSuccessCountTheTargetAndTheAnomalies() {
        var anomaly = value(Map.of("kind", "STEP_FAILED", "occurredAt", "2026-10-01T10:00:00Z", "testCaseName", "Checkout",
                "details", "Timed out"));
        var row = record(Map.of("successCount", value(7L), "elementId", value(ELEMENT_ID.toString()), "elementName", value("Save"),
                "anomalies", value(List.of(anomaly))));
        when(repositorySupport.executeSingleReadQuery(anyString(), eq(Map.of("id", PROCEDURE_ID.toString(), "anomalyLimit", 10))))
                .thenReturn(List.of(row));

        var extras = repository.procedureExtras(PROCEDURE_ID).orElseThrow();

        assertThat(extras.successCount()).isEqualTo(7);
        assertThat(extras.target().id()).isEqualTo(ELEMENT_ID);
        assertThat(extras.recentAnomalies()).singleElement().satisfies(found -> {
            assertThat(found.details()).isEqualTo("Timed out");
            assertThat(found.occurredAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        });
    }

    @Test
    void procedureExtras_shouldBeEmpty_forAnUnknownProcedure() {
        when(repositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of());

        assertThat(repository.procedureExtras(PROCEDURE_ID)).isEmpty();
    }

    @Test
    void elements_shouldUseTheQueryOfTheSort_andBindTheOffsetAndTheLimit() {
        var row = elementRow(NULL);
        when(repositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(row));

        var tiles = repository.elements(Sort.RETRIES, 60, 61);

        assertThat(tiles).singleElement().satisfies(tile -> {
            assertThat(tile.stability()).isNull();
            assertThat(tile.locationRetries()).isEqualTo(1.5);
            assertThat(tile.hasScreenshot()).isTrue();
        });
        verify(repositorySupport).executeSingleReadQuery(argThat(query -> query.contains("${PROP_FAILED_LOCATION_COUNT}, 0) DESC")),
                eq(Map.of("offset", 60, "limit", 61)));
    }

    @Test
    void failureHints_shouldMapCurrentAndExpiredHints() {
        var current = hintRow(NULL);
        var expired = hintRow(value(1_700_000_100_000L));
        when(repositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(current, expired));

        var hints = repository.failureHints(PROCEDURE_ID, 200);

        assertThat(hints.get(0).expiredAt()).isNull();
        assertThat(hints.get(1).expiredAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_100_000L));
        assertThat(hints.get(0).lastOccurred()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
    }

    @Test
    void trendRows_shouldBindTheStartAsADayAndAsEpochMillis_andMapTheRows() {
        var row = record(Map.of("series", value("ANOMALIES"), "week", value(LocalDate.parse("2026-09-28")), "key", value("STEP_FAILED"),
                "count", value(2L)));
        when(repositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(row));

        var rows = repository.trendRows(LocalDate.parse("2026-09-14"), Instant.parse("2026-09-14T00:00:00Z"));

        assertThat(rows).containsExactly(new TrendRow("ANOMALIES", LocalDate.parse("2026-09-28"), "STEP_FAILED", 2));
        verify(repositorySupport).executeSingleReadQuery(anyString(), argThat(params ->
                params.get("since").equals(LocalDate.parse("2026-09-14"))
                        && params.get("sinceMillis").equals(Instant.parse("2026-09-14T00:00:00Z").toEpochMilli())));
    }

    @Test
    void resolutionsChoosing_shouldMapTheRunAndTheResolution() {
        var node = mock(Node.class);
        var values = new HashMap<String, Value>();
        values.put("id", value(UUID.randomUUID().toString()));
        values.put("sequence", value(2));
        values.put("itemType", value("TEST_STEP"));
        values.put("itemDescription", value("Save the form"));
        values.put("source", value("KNOWLEDGE"));
        values.put("confidence", value("HIGH"));
        values.put("candidates", value("[]"));
        values.put("wasDemoted", value(false));
        values.put("demotedDueToPrerequisites", value(false));
        values.put("outcome", value("SUCCEEDED"));
        values.put("durationMs", value(1200L));
        values.forEach((key, entry) -> when(node.get(key)).thenReturn(entry));
        var resolutionValue = mock(Value.class);
        when(resolutionValue.asNode()).thenReturn(node);
        var row = record(Map.of("runId", value(RUN_ID.toString()), "testCaseName", value("Checkout"),
                "startedAt", value(1_700_000_000_000L), "resolution", resolutionValue));
        when(repositorySupport.executeSingleReadQuery(anyString(), anyMap())).thenReturn(List.of(row));

        var views = repository.resolutionsChoosing(PROCEDURE_ID, 20);

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.runId()).isEqualTo(RUN_ID);
            assertThat(view.runStartedAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
            assertThat(view.resolution().source()).isEqualTo(Source.KNOWLEDGE);
            assertThat(view.resolution().outcome()).isEqualTo(Outcome.SUCCEEDED);
            assertThat(view.resolution().chosenProcedureId()).isEqualTo(PROCEDURE_ID);
        });
    }

    @Test
    void latestRunPath_shouldKeepTheOrderOfTheRows_orBeEmptyWithoutARun() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var rows = List.of(pathRow(first), pathRow(second));
        when(repositorySupport.executeSingleReadQuery(anyString(), anyMap()))
                .thenReturn(rows)
                .thenReturn(List.of());

        var path = repository.latestRunPath("Checkout", 300).orElseThrow();

        assertThat(path.runId()).isEqualTo(RUN_ID);
        assertThat(path.procedureIds()).containsExactly(first, second);
        assertThat(repository.latestRunPath("Unknown", 300)).isEmpty();
    }

    private static Record pathRow(UUID procedureId) {
        var row = mock(Record.class);
        lenient().when(row.get("runId")).thenReturn(value(RUN_ID.toString()));
        when(row.get("procedureId")).thenReturn(value(procedureId.toString()));
        return row;
    }

    private static Record elementRow(Value stability) {
        return record(Map.of("id", value(ELEMENT_ID.toString()), "name", value("Save"), "stability", stability,
                "avgLocationTimeMs", value(120L), "locationRetries", value(1.5), "dataDependent", value(false),
                "updatedAt", value("2026-09-30T08:00:00Z"), "lastLocatedAt", NULL, "hasScreenshot", value(true)));
    }

    private static Record hintRow(Value expiredAt) {
        return record(Map.of("id", value(UUID.randomUUID().toString()), "category", value("TIMING"), "symptom", value("Slow"),
                "resolution", value(""), "occurrences", value(3), "lastOccurred", value(1_700_000_000_000L), "expiredAt", expiredAt,
                "mode", value("UNATTENDED")));
    }

    private static Record record(Map<String, Value> values) {
        var record = mock(Record.class);
        values.forEach((alias, value) -> when(record.get(alias)).thenReturn(value));
        return record;
    }
}
