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

import com.google.common.base.Enums;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.dto.panel.knowledge.ElementDetails;
import org.tarik.ta.dto.panel.knowledge.ElementDetails.Appearance;
import org.tarik.ta.dto.panel.knowledge.ElementPage;
import org.tarik.ta.dto.panel.knowledge.ElementPage.Sort;
import org.tarik.ta.dto.panel.knowledge.FailureHistory;
import org.tarik.ta.dto.panel.knowledge.GraphNode;
import org.tarik.ta.dto.panel.knowledge.GraphSlice;
import org.tarik.ta.dto.panel.knowledge.KnowledgeSummary;
import org.tarik.ta.dto.panel.knowledge.ProcedureDetails;
import org.tarik.ta.dto.panel.knowledge.ProcedureRef;
import org.tarik.ta.dto.panel.knowledge.SearchResults;
import org.tarik.ta.dto.panel.knowledge.TestCaseLens;
import org.tarik.ta.dto.panel.knowledge.WeeklyTrends;
import org.tarik.ta.dto.panel.knowledge.WeeklyTrends.Week;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.Screenshot;
import org.tarik.ta.knowledge_graph.model.node.UiElementAppearance;
import org.tarik.ta.knowledge_graph.repository.KnowledgeViewRepository;
import org.tarik.ta.knowledge_graph.repository.KnowledgeViewRepository.TrendRow;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureUsageByTestCaseTrackingRepository;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;

import java.time.InstantSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.time.DayOfWeek.MONDAY;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

/** Answers the questions of the panel's *Knowledge* tab from the graph, within the fixed caps of each view. */
@Singleton
public class KnowledgeViewService {
    public static final int MAX_SEARCH_HITS = 50;
    public static final int MAX_MAP_NODES = 300;
    public static final int MAX_OVERVIEW_NODES = 5000;
    public static final int ELEMENT_PAGE_SIZE = 60;
    static final int MAX_CHOSEN_FOR = 20;
    private static final int MAX_FAILURE_HINTS = 200;
    // Edges grow with the nodes they connect; four per node bounds the densest graphs the views can still draw
    private static final int EDGES_PER_NODE = 4;

    private final KnowledgeViewRepository viewRepository;
    private final ProcedureRepository procedureRepository;
    private final ProcedureUsageByTestCaseTrackingRepository usageRepository;
    private final UiElementRepository elementRepository;
    private final UiTestAgentConfig config;
    private final InstantSource clock;

    @Inject
    KnowledgeViewService(KnowledgeViewRepository viewRepository, ProcedureRepository procedureRepository,
                         ProcedureUsageByTestCaseTrackingRepository usageRepository, UiElementRepository elementRepository,
                         UiTestAgentConfig config) {
        this(viewRepository, procedureRepository, usageRepository, elementRepository, config, InstantSource.system());
    }

    public KnowledgeViewService(@NotNull KnowledgeViewRepository viewRepository, @NotNull ProcedureRepository procedureRepository,
                                @NotNull ProcedureUsageByTestCaseTrackingRepository usageRepository,
                                @NotNull UiElementRepository elementRepository, @NotNull UiTestAgentConfig config,
                                @NotNull InstantSource clock) {
        this.viewRepository = viewRepository;
        this.procedureRepository = procedureRepository;
        this.usageRepository = usageRepository;
        this.elementRepository = elementRepository;
        this.config = config;
        this.clock = clock;
    }

    public KnowledgeSummary summary() {
        return viewRepository.summary();
    }

    public SearchResults search(@NotNull String query) {
        var hits = viewRepository.search(query, MAX_SEARCH_HITS + 1);
        return new SearchResults(capped(hits, MAX_SEARCH_HITS), hits.size() > MAX_SEARCH_HITS);
    }

    public Optional<GraphSlice> neighbourhood(@NotNull UUID procedureId) {
        var nodes = viewRepository.neighbourhood(procedureId, MAX_MAP_NODES + 1);
        return nodes.isEmpty() ? Optional.empty() : Optional.of(slice(nodes, MAX_MAP_NODES));
    }

    public Optional<TestCaseLens> testCaseLens(@NotNull String testCaseName) {
        var nodes = viewRepository.testCaseLens(testCaseName, MAX_MAP_NODES + 1);
        if (nodes.isEmpty()) {
            return Optional.empty();
        }
        var latestRun = viewRepository.latestRunPath(testCaseName, MAX_MAP_NODES);
        return Optional.of(new TestCaseLens(slice(nodes, MAX_MAP_NODES),
                latestRun.map(KnowledgeViewRepository.LatestRun::runId).orElse(null),
                latestRun.map(KnowledgeViewRepository.LatestRun::procedureIds).orElse(List.of())));
    }

    public GraphSlice overview() {
        return slice(viewRepository.overview(MAX_OVERVIEW_NODES + 1), MAX_OVERVIEW_NODES);
    }

    public Optional<ProcedureDetails> procedureDetails(@NotNull UUID procedureId) {
        var procedure = procedureRepository.findById(procedureId);
        var extras = viewRepository.procedureExtras(procedureId);
        if (procedure.isEmpty() || extras.isEmpty()) {
            return Optional.empty();
        }
        var found = procedure.get();
        return Optional.of(new ProcedureDetails(found.id(), found.description(), found.isAtomic(), found.isPrecondition(),
                found.optional(), found.prerequisites(), found.effects(), found.negatedEffects(), found.testData(),
                found.expectedResults(), found.additionalInfo(), found.createdAt(), found.updatedAt(), extras.get().successCount(),
                found.timingProfile(), refs(procedureRepository.findParents(procedureId)),
                refs(procedureRepository.findChildrenOrdered(procedureId)), extras.get().target(),
                usageRepository.findTestCasesUsingProcedure(procedureId), extras.get().recentAnomalies(),
                viewRepository.resolutionsChoosing(procedureId, MAX_CHOSEN_FOR)));
    }

    public ElementPage elements(@NotNull Sort sort, int offset) {
        var tiles = viewRepository.elements(sort, offset, ELEMENT_PAGE_SIZE + 1);
        return new ElementPage(capped(tiles, ELEMENT_PAGE_SIZE), offset, tiles.size() > ELEMENT_PAGE_SIZE);
    }

    public Optional<ElementDetails> elementDetails(@NotNull UUID elementId) {
        var extras = viewRepository.elementExtras(elementId);
        var element = elementRepository.findById(elementId);
        if (extras.isEmpty() || element.isEmpty()) {
            return Optional.empty();
        }
        var appearances = elementRepository.findExpiredAppearances(elementId).stream()
                .map(appearance -> new Appearance(appearance.id(), appearance.ownDescription(), appearance.anchorsDescription(),
                        appearance.parentElementSummary(), appearance.validFrom(), appearance.validTo(), appearance.screenshot() != null))
                .toList();
        var found = element.get();
        return Optional.of(new ElementDetails(extras.get().tile(), found.description(), found.locationDetails(),
                found.parentElementSummary(), extras.get().targetedBy(), appearances));
    }

    public Optional<Screenshot> elementScreenshot(@NotNull UUID elementId) {
        return elementRepository.findById(elementId).map(UiElement::screenshot);
    }

    public Optional<Screenshot> appearanceScreenshot(@NotNull UUID elementId, @NotNull UUID appearanceId) {
        return elementRepository.findExpiredAppearances(elementId).stream()
                .filter(appearance -> appearance.id().equals(appearanceId))
                .findFirst()
                .map(UiElementAppearance::screenshot);
    }

    /** Returns the given number of ISO weeks in UTC up to the current one, each present even without events. */
    public WeeklyTrends trends(int weeks) {
        var currentWeek = LocalDate.ofInstant(clock.instant(), UTC).with(previousOrSame(MONDAY));
        var firstWeek = currentWeek.minusWeeks(weeks - 1L);
        var rowsByWeek = new HashMap<LocalDate, List<TrendRow>>();
        viewRepository.trendRows(firstWeek, firstWeek.atStartOfDay(UTC).toInstant())
                .forEach(row -> rowsByWeek.computeIfAbsent(row.week(), _ -> new ArrayList<>()).add(row));
        var result = new ArrayList<Week>(weeks);
        for (var week = firstWeek; !week.isAfter(currentWeek); week = week.plusWeeks(1)) {
            result.add(weekOf(week, rowsByWeek.getOrDefault(week, List.of())));
        }
        return new WeeklyTrends(result);
    }

    public Optional<FailureHistory> failures(@NotNull UUID procedureId) {
        return procedureRepository.findById(procedureId)
                .map(procedure -> new FailureHistory(new ProcedureRef(procedure.id(), procedure.description()),
                        config.getFailureHintsStaleDays(), viewRepository.failureHints(procedureId, MAX_FAILURE_HINTS)));
    }

    private static Week weekOf(LocalDate start, List<TrendRow> rows) {
        int procedures = 0;
        var anomalies = new EnumMap<ExecutionAnomaly.Kind, Integer>(ExecutionAnomaly.Kind.class);
        var resolutions = new EnumMap<StepResolution.Source, Integer>(StepResolution.Source.class);
        var outcomes = new EnumMap<StepResolution.Outcome, Integer>(StepResolution.Outcome.class);
        for (var row : rows) {
            int count = Math.toIntExact(row.count());
            switch (row.series()) {
                case "PROCEDURES" -> procedures += count;
                case "ANOMALIES" -> addCount(anomalies, ExecutionAnomaly.Kind.class, row.key(), count);
                case "RESOLUTIONS" -> addCount(resolutions, StepResolution.Source.class, row.key(), count);
                case "OUTCOMES" -> addCount(outcomes, StepResolution.Outcome.class, row.key(), count);
                default -> throw new IllegalStateException("Unknown trend series: %s".formatted(row.series()));
            }
        }
        return new Week(start, procedures, anomalies, resolutions, outcomes);
    }

    // A value the graph holds but this version does not know, e.g. from a newer agent, is left out rather than failing the view
    private static <E extends Enum<E>> void addCount(Map<E, Integer> counts, Class<E> type, @Nullable String key, int count) {
        if (key != null) {
            Enums.getIfPresent(type, key).toJavaUtil().ifPresent(value -> counts.merge(value, count, Integer::sum));
        }
    }

    private GraphSlice slice(List<GraphNode> nodes, int cap) {
        var kept = capped(nodes, cap);
        return new GraphSlice(kept, viewRepository.edgesBetween(kept, cap * EDGES_PER_NODE), nodes.size() > cap);
    }

    private static <T> List<T> capped(List<T> rows, int cap) {
        return rows.size() > cap ? rows.subList(0, cap) : rows;
    }

    private static List<ProcedureRef> refs(List<Procedure> procedures) {
        return procedures.stream().map(procedure -> new ProcedureRef(procedure.id(), procedure.description())).toList();
    }
}
