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
package org.tarik.ta.panel;

import io.javalin.http.Context;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.health.GraphHealthReport;
import org.tarik.ta.knowledge_graph.service.GraphHealthService;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Serves the knowledge graph health report on demand. A request while a check runs joins it, so the heavy check never runs twice
 * at once.
 */
@Singleton
final class GraphHealthRoute {
    private static final Logger LOG = LoggerFactory.getLogger(GraphHealthRoute.class);
    // Every query of the check is bounded by the Neo4j query timeout; this only bounds the wait of a joining request on top of it
    private static final Duration MAX_JOIN_WAIT = Duration.ofMinutes(15);
    private static final Map<String, String> UNAVAILABLE_BODY = Map.of("error", "Knowledge graph unavailable");

    private final GraphHealthService healthService;
    private final AtomicReference<CompletableFuture<GraphHealthReport>> runningCheck = new AtomicReference<>();

    GraphHealthRoute(GraphHealthService healthService) {
        this.healthService = healthService;
    }

    void handle(@NotNull Context context) {
        long startNanos = System.nanoTime();
        var ownCheck = new CompletableFuture<GraphHealthReport>();
        var joinedCheck = runningCheck.compareAndExchange(null, ownCheck);
        try {
            var report = joinedCheck == null ? runCheck(ownCheck) : joinedCheck.get(MAX_JOIN_WAIT.toMillis(), MILLISECONDS);
            // Findings carry LLM output and tool error texts, so they pass the same redaction as everything else sent to the browser
            context.json(PanelJson.redactedTree(report));
            LOG.info("Graph health check served: {} categories, {} findings, {} ms, {}", report.categories().size(),
                    report.totalFindings(), Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                    joinedCheck == null ? "started" : "joined");
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            LOG.error("The knowledge graph health check failed", e);
            context.status(SERVICE_UNAVAILABLE).json(UNAVAILABLE_BODY);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            context.status(SERVICE_UNAVAILABLE).json(UNAVAILABLE_BODY);
        }
    }

    private GraphHealthReport runCheck(CompletableFuture<GraphHealthReport> ownCheck) {
        try {
            var report = healthService.runFullHealthCheck();
            ownCheck.complete(report);
            return report;
        } catch (RuntimeException e) {
            ownCheck.completeExceptionally(e);
            throw e;
        } finally {
            runningCheck.compareAndSet(ownCheck, null);
        }
    }
}
