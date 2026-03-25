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

        service = new GraphHealthService(repository);
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

        service.generateHtmlReport(outputPath);

        assertThat(Files.exists(outputPath)).isTrue();
    }
}
