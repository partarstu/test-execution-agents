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

import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.health.GraphHealthReport;
import org.tarik.ta.knowledge_graph.health.HealthCheckCategory;
import org.tarik.ta.knowledge_graph.health.HealthFinding;
import org.tarik.ta.knowledge_graph.repository.GraphHealthRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static java.lang.String.format;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;

/**
 * Runs knowledge graph health checks, generates structured reports, and exports them as HTML.
 */
@Singleton
public class GraphHealthService {
    private static final Logger LOG = LoggerFactory.getLogger(GraphHealthService.class);

    private final GraphHealthRepository repository;
    private final GraphHealthHtmlReportGenerator reportGenerator;

    private final UiTestAgentConfig config;
    private final FailureContextService failureContextService;

    GraphHealthService(GraphHealthRepository repository, GraphHealthHtmlReportGenerator reportGenerator, UiTestAgentConfig config,
                       FailureContextService failureContextService) {
        this.repository = repository;
        this.reportGenerator = reportGenerator;
        this.config = config;
        this.failureContextService = failureContextService;
    }

    @PostConstruct
    void runStartupCleanup() {
        StartupDatabaseTasks.runUnlessDatabaseUnreachable("startup cleanup of the knowledge graph", () -> {
            runStaleSatisfiesEdgeCleanup();
            failureContextService.expireStaleHints();
            int deletedAnomalies = repository.deleteOrphanedExecutionAnomalies();
            LOG.info("Orphaned execution anomaly cleanup: deleted {} anomaly node(s)", deletedAnomalies);
        });
    }

    public GraphHealthReport runFullHealthCheck() {
        int warnThreshold = config.getHealthWarningThreshold();
        int critThreshold = config.getHealthCriticalThreshold();
        LOG.debug("Running full knowledge graph health check (warn threshold={}, critical threshold={})", warnThreshold, critThreshold);
        int staleDays = config.getSatisfiesStaleDays();
        int maxDepth = config.getKnowledgeMaxDepth();
        int recentWindowDays = config.getHealthRecentWindowDays();
        double stabilityThreshold = config.getStabilityPenaltyThreshold();
        double satisfiesThreshold = config.getSatisfiesSimilarityThreshold();
        double duplicateThreshold = config.getKnowledgeMatchConfidenceHigh();

        var categories = List.of(
                HealthCheckCategory.of("Orphaned UI Elements",
                        "UiElement nodes not targeted by any procedure",
                        repository.findOrphanedUiElements(), warnThreshold, critThreshold),
                HealthCheckCategory.of("Leaf Procedures Without Target",
                        "Atomic procedures missing a TARGETS relationship",
                        repository.findLeafProceduresWithoutElement(), warnThreshold, critThreshold),
                HealthCheckCategory.of("Deep Hierarchies",
                        "Procedure trees exceeding max depth of %d".formatted(maxDepth),
                        repository.findDeepHierarchies(maxDepth), warnThreshold, critThreshold),
                HealthCheckCategory.of("Unused Procedures",
                        "Procedures not used by any test case, directly or through a parent procedure",
                        repository.findUnusedProcedures(), warnThreshold, critThreshold),
                HealthCheckCategory.of("Missing Effects",
                        "Atomic procedures with no effect phrases",
                        repository.findProceduresWithMissingEffects(), warnThreshold, critThreshold),
                HealthCheckCategory.of("Stale SATISFIES Edges",
                        "SATISFIES edges not verified in %d days".formatted(staleDays),
                        repository.findStaleSatisfiesEdges(staleDays), warnThreshold, critThreshold),
                HealthCheckCategory.of("Orphaned Failure Contexts",
                        "FailureContext nodes with no parent procedure",
                        repository.findOrphanedFailureContexts(), warnThreshold, critThreshold),
                HealthCheckCategory.of("Orphaned Phrase Embeddings",
                        "PhraseEmbedding nodes not linked to any procedure via HAS_PREREQUISITE, HAS_EFFECT or HAS_NEGATED_EFFECT",
                        repository.findOrphanedPhraseEmbeddings(), warnThreshold, critThreshold),
                HealthCheckCategory.of("Volatile UI Elements",
                        "UI elements whose appearance changed at least twice in %d days".formatted(recentWindowDays),
                        repository.findVolatileUiElements(recentWindowDays), warnThreshold, critThreshold),
                HealthCheckCategory.of("Unresolved Failure Hints",
                        "Current failure hints without a resolution, which are never passed to the prompts",
                        repository.findUnresolvedFailureHints(), warnThreshold, critThreshold),
                HealthCheckCategory.of("Recent Execution Anomalies",
                        "Failed element locations, failed steps and timing outliers in %d days".formatted(recentWindowDays),
                        repository.findRecentExecutionAnomalies(recentWindowDays), warnThreshold, critThreshold),
                HealthCheckCategory.of("Low-Stability Elements by Blast Radius",
                        format(ROOT, "UI elements with a stability below %.2f, by the atomic procedures and test cases depending on them",
                                stabilityThreshold),
                        repository.findLowStabilityElementsByBlastRadius(stabilityThreshold), warnThreshold, critThreshold),
                HealthCheckCategory.of("Failing Procedures by Blast Radius",
                        "Procedures with failed steps in %d days, by the test cases depending on them".formatted(recentWindowDays),
                        repository.findFailingProceduresByBlastRadius(recentWindowDays), warnThreshold, critThreshold),
                HealthCheckCategory.of("Unproven Procedures",
                        "Atomic procedures without a successful execution in %d days".formatted(recentWindowDays),
                        repository.findProceduresWithoutRecentSuccess(recentWindowDays), warnThreshold, critThreshold),
                HealthCheckCategory.of("Unsatisfiable Prerequisites",
                        format(ROOT, "Prerequisites that no effect of another procedure matches with a similarity of at least %.2f",
                                satisfiesThreshold),
                        repository.findUnsatisfiablePrerequisites(satisfiesThreshold), warnThreshold, critThreshold),
                HealthCheckCategory.of("Duplicate Atomic Procedures",
                        format(ROOT, "Atomic procedures on the same UI element whose descriptions have a similarity of at least %.2f",
                                duplicateThreshold),
                        repository.findDuplicateAtomicProcedures(duplicateThreshold), warnThreshold, critThreshold)
        );
        return new GraphHealthReport(categories, Instant.now());
    }

    void runStaleSatisfiesEdgeCleanup() {
        int staleDays = config.getSatisfiesStaleDays();
        int deleted = repository.deleteStaleSatisfiesEdges(staleDays);
        LOG.info("Stale SATISFIES edge cleanup: deleted {} edge(s) older than {} days", deleted, staleDays);
    }

    void logHealthReport() {
        var report = runFullHealthCheck();
        LOG.info("=== Knowledge Graph Health Report (generated {}) ===", report.generatedAt());
        LOG.info("Total findings: {}", report.totalFindings());
        for (var category : report.categories()) {
            LOG.info("[{}] {} — {} finding(s): {}",
                    category.severity(), category.name(),
                    category.findings().size(),
                    category.findings().isEmpty() ? "none" : category.findings().stream().map(HealthFinding::text).collect(joining("; ")));
        }
    }

    /**
     * Runs a full health check, generates an HTML report, and writes it to {@code outputPath}.
     *
     * @return the resolved output path for caller convenience
     */
    Path generateHtmlReport(Path outputPath) throws IOException {
        var report = runFullHealthCheck();
        var html = reportGenerator.generateHtml(report);
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
        LOG.info("Health report written to: {}", outputPath.toAbsolutePath());
        return outputPath;
    }
}
