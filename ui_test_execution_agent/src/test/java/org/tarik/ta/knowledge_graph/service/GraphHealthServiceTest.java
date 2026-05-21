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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.health.HealthCheckCategory;
import org.tarik.ta.knowledge_graph.repository.GraphHealthRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private GraphHealthService service;

    @BeforeEach
    void setUp() {
        lenient().when(configMock.getHealthWarningThreshold()).thenReturn(3);
        lenient().when(configMock.getHealthCriticalThreshold()).thenReturn(10);
        lenient().when(configMock.getSatisfiesStaleDays()).thenReturn(30);
        lenient().when(configMock.getKnowledgeMaxDepth()).thenReturn(3);

        when(repository.findOrphanedUiElements()).thenReturn(List.of());
        when(repository.findLeafProceduresWithoutElement()).thenReturn(List.of());
        when(repository.findDeepHierarchies(3)).thenReturn(List.of());
        when(repository.findDisconnectedProcedures()).thenReturn(List.of());
        when(repository.findProceduresWithMissingEffects()).thenReturn(List.of());
        when(repository.findStaleSatisfiesEdges(30)).thenReturn(List.of());
        when(repository.findOrphanedFailureContexts()).thenReturn(List.of());
        when(repository.findOrphanedPhraseEmbeddings()).thenReturn(List.of());

        service = new GraphHealthService(repository, reportGenerator, configMock);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("runFullHealthCheck returns report with all 8 categories")
    void runFullHealthCheck_returnsReportWithAllEightCategories() {
        var report = service.runFullHealthCheck();

        assertThat(report).isNotNull();
        assertThat(report.categories()).hasSize(8);
        assertThat(report.generatedAt()).isNotNull();
        assertThat(report.totalFindings()).isZero();
    }

    @Test
    @DisplayName("runFullHealthCheck computes severity from finding counts")
    void runFullHealthCheck_computesSeverityFromFindingCounts() {
        // 0 findings → OK, 5 findings → WARNING (>warningThreshold=3, ≤criticalThreshold=10), 11 findings → CRITICAL (>10)
        when(repository.findOrphanedUiElements()).thenReturn(List.of("el1", "el2", "el3", "el4", "el5"));
        when(repository.findLeafProceduresWithoutElement())
                .thenReturn(List.of("p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10", "p11"));

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
}
