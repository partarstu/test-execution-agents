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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.health.HealthCheckCategory;
import org.tarik.ta.knowledge_graph.health.HealthFinding;
import org.tarik.ta.knowledge_graph.repository.GraphHealthRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphHealthServiceTest {

    @Mock
    private GraphHealthRepository repository;

    @Mock
    private GraphHealthHtmlReportGenerator reportGenerator;

    @Mock
    private UiTestAgentConfig configMock;
    @Mock
    private FailureContextService failureContextService;
    private GraphHealthService service;

    @BeforeEach
    void setUp() {
        lenient().when(configMock.getHealthWarningThreshold()).thenReturn(3);
        lenient().when(configMock.getHealthCriticalThreshold()).thenReturn(10);
        lenient().when(configMock.getSatisfiesStaleDays()).thenReturn(30);
        lenient().when(configMock.getKnowledgeMaxDepth()).thenReturn(3);
        lenient().when(configMock.getHealthRecentWindowDays()).thenReturn(30);

        when(repository.findOrphanedUiElements()).thenReturn(List.of());
        when(repository.findLeafProceduresWithoutElement()).thenReturn(List.of());
        when(repository.findDeepHierarchies(3)).thenReturn(List.of());
        when(repository.findUnusedProcedures()).thenReturn(List.of());
        when(repository.findProceduresWithMissingEffects()).thenReturn(List.of());
        when(repository.findStaleSatisfiesEdges(30)).thenReturn(List.of());
        when(repository.findOrphanedFailureContexts()).thenReturn(List.of());
        when(repository.findOrphanedPhraseEmbeddings()).thenReturn(List.of());

        service = new GraphHealthService(repository, reportGenerator, configMock, failureContextService);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("runFullHealthCheck returns report with all categories")
    void runFullHealthCheck_returnsReportWithAllCategories() {
        var report = service.runFullHealthCheck();

        assertThat(report).isNotNull();
        assertThat(report.categories()).extracting(HealthCheckCategory::name).containsExactly("Orphaned UI Elements",
                "Leaf Procedures Without Target", "Deep Hierarchies", "Unused Procedures", "Missing Effects",
                "Stale SATISFIES Edges", "Orphaned Failure Contexts", "Orphaned Phrase Embeddings", "Volatile UI Elements",
                "Unresolved Failure Hints", "Recent Execution Anomalies", "Low-Stability Elements by Blast Radius",
                "Failing Procedures by Blast Radius", "Unproven Procedures", "Unsatisfiable Prerequisites", "Duplicate Atomic Procedures");
        assertThat(report.generatedAt()).isNotNull();
        assertThat(report.totalFindings()).isZero();
    }

    @Test
    @DisplayName("runFullHealthCheck computes severity from finding counts")
    void runFullHealthCheck_computesSeverityFromFindingCounts() {
        // 0 findings → OK, 5 findings → WARNING (>warningThreshold=3, ≤criticalThreshold=10), 11 findings → CRITICAL (>10)
        when(repository.findOrphanedUiElements()).thenReturn(findings("el1", "el2", "el3", "el4", "el5"));
        when(repository.findLeafProceduresWithoutElement())
                .thenReturn(findings("p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10", "p11"));

        var report = service.runFullHealthCheck();

        var orphanedUiElements = report.categories().get(0);
        assertThat(orphanedUiElements.severity()).isEqualTo(HealthCheckCategory.Severity.WARNING);

        var leafProcedures = report.categories().get(1);
        assertThat(leafProcedures.severity()).isEqualTo(HealthCheckCategory.Severity.CRITICAL);

        var deepHierarchies = report.categories().get(2);
        assertThat(deepHierarchies.severity()).isEqualTo(HealthCheckCategory.Severity.OK);
    }

    @Test
    @DisplayName("runStaleSatisfiesEdgeCleanup delegates to repository with configured stale days")
    void runStaleSatisfiesEdgeCleanup_delegatesToRepository() {
        when(repository.deleteStaleSatisfiesEdges(30)).thenReturn(5);

        service.runStaleSatisfiesEdgeCleanup();

        verify(repository).deleteStaleSatisfiesEdges(30);
    }

    @Test
    void runFullHealthCheck_shouldReportVolatileElementsAndUnresolvedHints_overTheRecentWindow() {
        when(repository.findVolatileUiElements(30)).thenReturn(findings("UiElement[1] 'Save': 2 appearance changes in 30 days"));
        when(repository.findUnresolvedFailureHints()).thenReturn(findings("hint"));

        var report = service.runFullHealthCheck();

        assertThat(report.categories()).filteredOn(category -> category.name().equals("Volatile UI Elements")).singleElement()
                .satisfies(category -> assertThat(category.findings()).hasSize(1));
        assertThat(report.categories()).filteredOn(category -> category.name().equals("Unresolved Failure Hints")).singleElement()
                .satisfies(category -> assertThat(category.findings()).extracting(HealthFinding::text).containsExactly("hint"));
    }

    @Test
    void runFullHealthCheck_shouldPassTheConfiguredThresholdsAndWindow_toTheBlastRadiusEvidenceAndGapQueries() {
        when(configMock.getStabilityPenaltyThreshold()).thenReturn(0.5);
        when(configMock.getSatisfiesSimilarityThreshold()).thenReturn(0.9);
        when(configMock.getKnowledgeMatchConfidenceHigh()).thenReturn(0.95);
        when(repository.findLowStabilityElementsByBlastRadius(0.5)).thenReturn(findings("element"));
        when(repository.findFailingProceduresByBlastRadius(30)).thenReturn(findings("failing"));
        when(repository.findProceduresWithoutRecentSuccess(30)).thenReturn(findings("unproven"));
        when(repository.findUnsatisfiablePrerequisites(0.9)).thenReturn(findings("gap"));
        when(repository.findDuplicateAtomicProcedures(0.95)).thenReturn(findings("duplicate"));

        var report = service.runFullHealthCheck();

        assertThat(report.categories()).filteredOn(category -> category.findings().size() == 1)
                .extracting(HealthCheckCategory::name, category -> category.findings().getFirst().text())
                .containsExactly(tuple("Low-Stability Elements by Blast Radius", "element"),
                        tuple("Failing Procedures by Blast Radius", "failing"), tuple("Unproven Procedures", "unproven"),
                        tuple("Unsatisfiable Prerequisites", "gap"), tuple("Duplicate Atomic Procedures", "duplicate"));
    }

    @Test
    void runStartupCleanup_shouldExpireStaleFailureHintsAndDeleteOrphanedAnomalies() {
        service.runStartupCleanup();

        verify(repository).deleteStaleSatisfiesEdges(30);
        verify(failureContextService).expireStaleHints();
        verify(repository).deleteOrphanedExecutionAnomalies();
    }

    @Test
    void runStartupCleanup_shouldSkipTheCleanupWithoutThrowing_whenNeo4jIsUnreachable() {
        when(repository.deleteStaleSatisfiesEdges(30)).thenThrow(new DatabaseConnectionException("unreachable", new RuntimeException()));

        assertThatCode(() -> service.runStartupCleanup()).doesNotThrowAnyException();

        verifyNoInteractions(failureContextService);
        verify(repository, never()).deleteOrphanedExecutionAnomalies();
    }

    @Test
    @DisplayName("generateHtmlReport writes a non-empty HTML file to the given path")
    void generateHtmlReport_writesFileToPath(@TempDir Path tempDir) throws IOException {
        var outputPath = tempDir.resolve("health-report.html");
        when(reportGenerator.generateHtml(any())).thenReturn("<!DOCTYPE html><html><body>Knowledge Graph Health Report</body></html>");

        service.generateHtmlReport(outputPath);

        assertThat(Files.exists(outputPath)).isTrue();
        var content = Files.readString(outputPath);
        assertThat(content).contains("<!DOCTYPE html>");
        assertThat(content).contains("Knowledge Graph Health Report");
    }

    @Test
    @DisplayName("generateHtmlReport creates parent directories if they don't exist")
    void generateHtmlReport_createsParentDirectories(@TempDir Path tempDir) throws IOException {
        var outputPath = tempDir.resolve("nested/sub/report.html");
        when(reportGenerator.generateHtml(any())).thenReturn("<html></html>");

        service.generateHtmlReport(outputPath);

        assertThat(Files.exists(outputPath)).isTrue();
    }

    @Test
    void logHealthReport_shouldLogTheFindingTextsOfEachCategory_joinedBySemicolons() {
        when(repository.findOrphanedUiElements()).thenReturn(findings("UiElement[1]: Save", "UiElement[2]: Cancel"));
        var logger = (Logger) LoggerFactory.getLogger(GraphHealthService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.logHealthReport();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .contains("[OK] Orphaned UI Elements — 2 finding(s): UiElement[1]: Save; UiElement[2]: Cancel",
                        "[OK] Deep Hierarchies — 0 finding(s): none");
    }

    private static List<HealthFinding> findings(String... texts) {
        return Arrays.stream(texts).map(text -> new HealthFinding(text, List.of())).toList();
    }
}
