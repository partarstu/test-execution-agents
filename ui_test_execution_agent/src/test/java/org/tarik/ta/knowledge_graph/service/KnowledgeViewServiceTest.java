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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.dto.panel.knowledge.GraphNode;
import org.tarik.ta.dto.panel.knowledge.ProcedureDetails.ElementRef;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;
import org.tarik.ta.knowledge_graph.repository.KnowledgeViewRepository;
import org.tarik.ta.knowledge_graph.repository.KnowledgeViewRepository.LatestRun;
import org.tarik.ta.knowledge_graph.repository.KnowledgeViewRepository.ProcedureExtras;
import org.tarik.ta.knowledge_graph.repository.KnowledgeViewRepository.TrendRow;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureUsageByTestCaseTrackingRepository;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.knowledge_graph.service.KnowledgeViewService.MAX_MAP_NODES;

@ExtendWith(MockitoExtension.class)
class KnowledgeViewServiceTest {
    private static final UUID PROCEDURE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    // A Thursday, so the current ISO week starts on Monday 2026-09-28
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private KnowledgeViewRepository viewRepository;
    @Mock
    private ProcedureRepository procedureRepository;
    @Mock
    private ProcedureUsageByTestCaseTrackingRepository usageRepository;
    @Mock
    private UiElementRepository elementRepository;
    @Mock
    private UiTestAgentConfig config;

    private KnowledgeViewService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeViewService(viewRepository, procedureRepository, usageRepository, elementRepository, config,
                InstantSource.fixed(NOW));
    }

    @Test
    void neighbourhood_shouldKeepTheFirst300Nodes_andMarkTheSliceCapped_whenTheRepositoryReturnsMore() {
        var nodes = nodes(MAX_MAP_NODES + 1);
        when(viewRepository.neighbourhood(PROCEDURE_ID, MAX_MAP_NODES + 1)).thenReturn(nodes);
        when(viewRepository.edgesBetween(anyList(), anyInt())).thenReturn(List.of());

        var slice = service.neighbourhood(PROCEDURE_ID).orElseThrow();

        assertThat(slice.capped()).isTrue();
        assertThat(slice.nodes()).hasSize(MAX_MAP_NODES).isEqualTo(nodes.subList(0, MAX_MAP_NODES));
        verify(viewRepository).edgesBetween(eq(nodes.subList(0, MAX_MAP_NODES)), anyInt());
    }

    @Test
    void neighbourhood_shouldBeEmpty_whenTheProcedureDoesNotExist() {
        when(viewRepository.neighbourhood(PROCEDURE_ID, MAX_MAP_NODES + 1)).thenReturn(List.of());

        assertThat(service.neighbourhood(PROCEDURE_ID)).isEmpty();
    }

    @Test
    void search_shouldNotBeCapped_whenAtMost50HitsArrive() {
        when(viewRepository.search("save", KnowledgeViewService.MAX_SEARCH_HITS + 1)).thenReturn(nodes(3));

        var results = service.search("save");

        assertThat(results.capped()).isFalse();
        assertThat(results.hits()).hasSize(3);
    }

    @Test
    void testCaseLens_shouldCarryThePathOfTheLatestRun() {
        var firstChosen = UUID.randomUUID();
        when(viewRepository.testCaseLens("Checkout", MAX_MAP_NODES + 1)).thenReturn(nodes(2));
        when(viewRepository.edgesBetween(anyList(), anyInt())).thenReturn(List.of());
        var runId = UUID.randomUUID();
        when(viewRepository.latestRunPath("Checkout", MAX_MAP_NODES)).thenReturn(Optional.of(new LatestRun(runId, List.of(firstChosen))));

        var lens = service.testCaseLens("Checkout").orElseThrow();

        assertThat(lens.latestRunId()).isEqualTo(runId);
        assertThat(lens.latestRunPath()).containsExactly(firstChosen);
    }

    @Test
    void procedureDetails_shouldCombineTheProcedureWithItsExtras_parentsChildrenTestCasesAndResolutions() {
        var procedure = Procedure.createAtomic("Click Save", List.of(), "Saved", List.of("Form open"), List.of("Saved"), false);
        var id = procedure.id();
        var parent = Procedure.createComposite("Save the form", List.of(), "", List.of(), List.of(), false);
        var target = new ElementRef(UUID.randomUUID(), "Save button");
        when(procedureRepository.findById(id)).thenReturn(Optional.of(procedure));
        when(viewRepository.procedureExtras(id)).thenReturn(Optional.of(new ProcedureExtras(7, target, List.of())));
        when(procedureRepository.findParents(id)).thenReturn(List.of(parent));
        when(procedureRepository.findChildrenOrdered(id)).thenReturn(List.of());
        when(usageRepository.findTestCasesUsingProcedure(id)).thenReturn(List.of("Checkout"));
        when(viewRepository.resolutionsChoosing(id, KnowledgeViewService.MAX_CHOSEN_FOR)).thenReturn(List.of());

        var details = service.procedureDetails(id).orElseThrow();

        assertThat(details.description()).isEqualTo("Click Save");
        assertThat(details.successCount()).isEqualTo(7);
        assertThat(details.target()).isEqualTo(target);
        assertThat(details.prerequisites()).containsExactly("Form open");
        assertThat(details.parents()).extracting("description").containsExactly("Save the form");
        assertThat(details.testCases()).containsExactly("Checkout");
    }

    @Test
    void procedureDetails_shouldBeEmpty_whenTheProcedureDoesNotExist() {
        when(procedureRepository.findById(PROCEDURE_ID)).thenReturn(Optional.empty());
        when(viewRepository.procedureExtras(PROCEDURE_ID)).thenReturn(Optional.empty());

        assertThat(service.procedureDetails(PROCEDURE_ID)).isEmpty();
    }

    @Test
    void trends_shouldAskFromTheMondayOfTheFirstWeekInUtc_andAnswerEveryWeekIncludingEmptyOnes() {
        var firstWeek = LocalDate.parse("2026-09-14");
        when(viewRepository.trendRows(firstWeek, Instant.parse("2026-09-14T00:00:00Z"))).thenReturn(List.of(
                new TrendRow("PROCEDURES", firstWeek, null, 3),
                new TrendRow("ANOMALIES", LocalDate.parse("2026-09-28"), "STEP_FAILED", 2),
                new TrendRow("RESOLUTIONS", LocalDate.parse("2026-09-28"), "KNOWLEDGE", 4),
                new TrendRow("OUTCOMES", LocalDate.parse("2026-09-28"), "FAILED", 1)));

        var trends = service.trends(3);

        assertThat(trends.weeks()).extracting(week -> week.start().toString()).containsExactly("2026-09-14", "2026-09-21", "2026-09-28");
        assertThat(trends.weeks().get(0).proceduresCreated()).isEqualTo(3);
        assertThat(trends.weeks().get(1).proceduresCreated()).isZero();
        assertThat(trends.weeks().get(1).anomalies()).isEmpty();
        var current = trends.weeks().get(2);
        assertThat(current.anomalies()).isEqualTo(Map.of(ExecutionAnomaly.Kind.STEP_FAILED, 2));
        assertThat(current.resolutions()).isEqualTo(Map.of(StepResolution.Source.KNOWLEDGE, 4));
        assertThat(current.outcomes()).isEqualTo(Map.of(StepResolution.Outcome.FAILED, 1));
    }

    @Test
    void trends_shouldLeaveOutAKeyThisVersionDoesNotKnow() {
        var week = LocalDate.parse("2026-09-28");
        when(viewRepository.trendRows(any(), any())).thenReturn(List.of(new TrendRow("ANOMALIES", week, "FROM_A_NEWER_AGENT", 5)));

        var trends = service.trends(1);

        assertThat(trends.weeks()).singleElement().satisfies(only -> assertThat(only.anomalies()).isEmpty());
    }

    @Test
    void failures_shouldCarryTheStaleDaysAndTheHints_orBeEmptyForAnUnknownProcedure() {
        var procedure = Procedure.createAtomic("Click Save", List.of(), "", List.of(), List.of(), false);
        when(procedureRepository.findById(procedure.id())).thenReturn(Optional.of(procedure));
        when(procedureRepository.findById(PROCEDURE_ID)).thenReturn(Optional.empty());
        when(config.getFailureHintsStaleDays()).thenReturn(90);
        when(viewRepository.failureHints(eq(procedure.id()), anyInt())).thenReturn(List.of());

        var history = service.failures(procedure.id()).orElseThrow();

        assertThat(history.staleDays()).isEqualTo(90);
        assertThat(history.procedure().description()).isEqualTo("Click Save");
        assertThat(service.failures(PROCEDURE_ID)).isEmpty();
    }

    private static List<GraphNode> nodes(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new GraphNode("n" + index, GraphNode.Kind.ATOMIC, "Node " + index, Set.of(), 0))
                .toList();
    }
}
