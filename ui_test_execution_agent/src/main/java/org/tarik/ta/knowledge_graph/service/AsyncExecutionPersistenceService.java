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

import io.avaje.inject.Component;
import jakarta.annotation.PreDestroy;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.Kind;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.time.Duration;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.tarik.ta.core.utils.CommonUtils.shutDownGracefully;

/**
 * Request-scoped persistence of execution-related graph data, asynchronous in unattended mode and synchronous in supervised mode.
 * The writes still in flight are drained when the request ends.
 */
@Component
@UiAgentRequestScope
public class AsyncExecutionPersistenceService {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncExecutionPersistenceService.class);

    /**
     * Upper bound on waiting for in-flight writes at the end of a run, generous enough for one slow graph round trip but
     * never a retry storm.
     */
    private static final Duration WRITE_DRAIN_TIMEOUT = Duration.ofSeconds(10);

    private final UiTestAgentConfig config;
    private final SatisfiesEdgeService satisfiesEdgeService;
    private final KnowledgeService knowledgeService;
    private final ProcedureRepository procedureRepository;
    private final FailureContextService failureContextService;
    private final ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService;
    private final ExecutionAnomalyService executionAnomalyService;
    private final ExecutorService writeExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Queue<Future<?>> pendingUsageWrites = new ConcurrentLinkedQueue<>();
    private CompletableFuture<Void> lastRunHistoryWrite = CompletableFuture.completedFuture(null);

    public AsyncExecutionPersistenceService(UiTestAgentConfig config,
                                            SatisfiesEdgeService satisfiesEdgeService,
                                            KnowledgeService knowledgeService,
                                            ProcedureRepository procedureRepository,
                                            FailureContextService failureContextService,
                                            ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService,
                                            ExecutionAnomalyService executionAnomalyService) {
        this.config = config;
        this.satisfiesEdgeService = satisfiesEdgeService;
        this.knowledgeService = knowledgeService;
        this.procedureRepository = procedureRepository;
        this.failureContextService = failureContextService;
        this.procedureUsageByTestCaseTrackingService = procedureUsageByTestCaseTrackingService;
        this.executionAnomalyService = executionAnomalyService;
    }

    /**
     * Checks if async persistence should be used.
     * True only if the mode is unattended.
     */
    public boolean isAsyncPersistenceEnabled() {
        return config.getExecutionMode() == ExecutionMode.UNATTENDED;
    }

    public void persistSatisfiesEdges(UUID executedProcedureId) {
        if (isAsyncPersistenceEnabled()) {
            runAsync(() -> satisfiesEdgeService.persistSatisfiesEdges(executedProcedureId),
                    "persistSatisfiesEdges for id %s".formatted(executedProcedureId));
        } else {
            satisfiesEdgeService.persistSatisfiesEdges(executedProcedureId);
        }
    }

    /** Updates the timing profile of a successful step and records a timing outlier when it took far longer than on average. */
    public void updateTimingProfile(UUID id, long actualExecutionMs, long actualVerificationDelayMs) {
        if (isAsyncPersistenceEnabled()) {
            runAsync(() -> updateTimingProfileAndRecordOutlier(id, actualExecutionMs, actualVerificationDelayMs),
                    "updateTimingProfile for id %s".formatted(id));
        } else {
            updateTimingProfileAndRecordOutlier(id, actualExecutionMs, actualVerificationDelayMs);
        }
    }

    private void updateTimingProfileAndRecordOutlier(UUID id, long actualExecutionMs, long actualVerificationDelayMs) {
        var previousProfile = knowledgeService.updateTimingProfile(id, actualExecutionMs, actualVerificationDelayMs);
        double outlierFactor = config.getAnomalyTimingOutlierFactor();
        previousProfile
                .filter(profile -> profile.avgExecutionMs() > 0 && actualExecutionMs > outlierFactor * profile.avgExecutionMs())
                .ifPresent(profile -> executionAnomalyService.record(Kind.TIMING_OUTLIER, id,
                        "Execution took %d ms, more than %.1f times the average of %d ms"
                                .formatted(actualExecutionMs, outlierFactor, profile.avgExecutionMs())));
    }

    /**
     * Records the outcome of an element location attempt: a failed attempt always becomes an anomaly, while the stability metrics
     * are written only when location history collection is enabled.
     */
    public void updateElementStability(UUID elementId, boolean located, long locationTimeMs) {
        if (isAsyncPersistenceEnabled()) {
            runAsync(() -> recordLocationAttempt(elementId, located, locationTimeMs),
                    "updateElementStability for id %s".formatted(elementId));
        } else {
            recordLocationAttempt(elementId, located, locationTimeMs);
        }
    }

    private void recordLocationAttempt(UUID elementId, boolean located, long locationTimeMs) {
        if (config.isLocationHistoryAndFailureHintsCollectionEnabled()) {
            procedureRepository.updateElementStability(elementId, located, locationTimeMs);
        }
        if (!located) {
            executionAnomalyService.record(Kind.ELEMENT_LOCATION_FAILED, elementId,
                    "Element not located after %d ms".formatted(locationTimeMs));
        }
    }

    public void bindTestCase(String testCaseName) {
        executionAnomalyService.bindTestCase(testCaseName);
    }

    public void recordAnomaly(Kind kind, UUID subjectId, String details) {
        if (isAsyncPersistenceEnabled()) {
            runAsync(() -> executionAnomalyService.record(kind, subjectId, details),
                    "recordAnomaly %s for id %s".formatted(kind, subjectId));
        } else {
            executionAnomalyService.record(kind, subjectId, details);
        }
    }

    /**
     * Records off the critical path that the test case uses the procedure, which nothing in the execution reads; callers must
     * invoke {@link #awaitPendingUsageWrites()} before the run ends.
     */
    public void mergeUsesProcedure(String testCaseName, UUID procedureId) {
        if (isAsyncPersistenceEnabled()) {
            pendingUsageWrites.add(runAsync(() -> procedureUsageByTestCaseTrackingService.mergeUsesProcedure(testCaseName, procedureId),
                    "mergeUsesProcedure for test case '%s' and id %s".formatted(testCaseName, procedureId)));
        } else {
            procedureUsageByTestCaseTrackingService.mergeUsesProcedure(testCaseName, procedureId);
        }
    }

    /**
     * Writes run history off the critical path in unattended mode, each write after the previous one, because a step resolution
     * needs its run node; callers must invoke {@link #awaitPendingUsageWrites()} before the run ends.
     */
    public synchronized void persistRunHistory(Runnable write, String description) {
        if (!isAsyncPersistenceEnabled()) {
            write.run();
            return;
        }
        lastRunHistoryWrite = lastRunHistoryWrite.thenRunAsync(() -> runLogged(write, description), writeExecutor);
        pendingUsageWrites.add(lastRunHistoryWrite);
    }

    /**
     * Waits for the usage and run-history writes started by {@link #mergeUsesProcedure} and {@link #persistRunHistory} to land, so
     * that they are neither lost to the graph driver closing nor still in flight when stale edges are cleaned up.
     */
    public void awaitPendingUsageWrites() {
        Future<?> pending;
        while ((pending = pendingUsageWrites.poll()) != null) {
            if (!await(pending)) {
                return;
            }
        }
    }

    /** Waits for the run-history writes queued by {@link #persistRunHistory} to land, so a write that needs the run node finds it. */
    public void awaitRunHistoryWrites() {
        CompletableFuture<Void> lastWrite;
        synchronized (this) {
            lastWrite = lastRunHistoryWrite;
        }
        await(lastWrite);
    }

    // False when interrupted, which leaves the interrupt flag set
    private static boolean await(Future<?> pending) {
        try {
            pending.get(WRITE_DRAIN_TIMEOUT.toMillis(), MILLISECONDS);
        } catch (TimeoutException _) {
            LOG.warn("Timed out after {} waiting for a pending write to complete", WRITE_DRAIN_TIMEOUT);
        } catch (ExecutionException e) {
            LOG.warn("A pending write failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for pending writes", e);
            return false;
        }
        return true;
    }

    public void captureFailureContext(UUID procedureId, FailureContext failureContext) {
        if (isAsyncPersistenceEnabled()) {
            runAsync(() -> failureContextService.captureFailureContext(procedureId, failureContext),
                    "captureFailureContext for id %s".formatted(procedureId));
        } else {
            failureContextService.captureFailureContext(procedureId, failureContext);
        }
    }

    /** Gives the writes still in flight at the end of the request the time to land before the graph driver closes. */
    @PreDestroy
    public void close() {
        shutDownGracefully(writeExecutor, WRITE_DRAIN_TIMEOUT);
    }

    private Future<?> runAsync(Runnable write, String description) {
        return writeExecutor.submit(() -> runLogged(write, description));
    }

    private static void runLogged(Runnable write, String description) {
        try {
            write.run();
        } catch (Exception e) {
            LOG.error("Async persistence failed: {}", description, e);
        }
    }
}
