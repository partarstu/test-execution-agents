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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.model.node.ExecutionRun;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Candidate;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Outcome;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Source;
import org.tarik.ta.knowledge_graph.repository.ExecutionRunRepository;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.MatchResult;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.ScoredProcedure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.tarik.ta.core.model.ContextEntry.StepType.PRECONDITION;
import static org.tarik.ta.core.model.ContextEntry.StepType.TEST_STEP;

/**
 * Records the current run with how each of its items was resolved to a procedure. The start and the resolutions are written like
 * the other execution data, the end always synchronously; a failure to store any of them never changes the outcome of the run.
 */
@UiAgentRequestScope
public class ExecutionRunService {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionRunService.class);

    private final ExecutionRunRepository repository;
    private final AsyncExecutionPersistenceService persistence;
    private final UiTestAgentConfig config;
    private final AtomicInteger nextSequence = new AtomicInteger();
    private volatile @Nullable UUID runId;

    public ExecutionRunService(ExecutionRunRepository repository, AsyncExecutionPersistenceService persistence, UiTestAgentConfig config) {
        this.repository = repository;
        this.persistence = persistence;
        this.config = config;
    }

    public void start(@NotNull String testCaseName) {
        var run = new ExecutionRun(UUID.randomUUID(), testCaseName, Instant.now(), null, config.getExecutionMode(), null);
        runId = run.id();
        persistence.persistRunHistory(() -> store(() -> repository.createRun(run), "the start of run %s".formatted(run.id())),
                "createRun %s".formatted(run.id()));
        LOG.info("Recording run {} of test case '{}'", run.id(), testCaseName);
    }

    /** Builds the item's resolution and stores it with the run, if one was started; the match offers its candidates, if any. */
    public StepResolution recordResolution(@NotNull ExecutionItem item, @NotNull Source source, @Nullable MatchResult match,
                                           @Nullable UUID chosenProcedureId, @NotNull Outcome outcome, long durationMs) {
        var itemType = switch (item) {
            case ExecutionItem.PreconditionItem _ -> PRECONDITION;
            case ExecutionItem.TestStepItem _ -> TEST_STEP;
        };
        var candidates = match == null ? List.<Candidate>of() : match.allMatches().stream().map(ExecutionRunService::candidateOf).toList();
        var resolution = new StepResolution(UUID.randomUUID(), nextSequence.getAndIncrement(), itemType, item.getDescription(), source,
                match == null ? null : match.confidence(), candidates, match != null && match.wasDemoted(),
                match != null && match.demotedDueToPrerequisites(), chosenProcedureId, outcome, durationMs);
        var currentRunId = runId;
        if (currentRunId != null) {
            var description = "resolution %d of run %s".formatted(resolution.sequence(), currentRunId);
            persistence.persistRunHistory(() -> store(() -> repository.appendResolution(currentRunId, resolution), description),
                    "appendResolution %s".formatted(resolution.id()));
        }
        return resolution;
    }

    /** Stores the end of the run synchronously, once its queued writes have landed, so the run node it updates exists. */
    public void finish(ExecutionRun.@NotNull Status status) {
        var currentRunId = runId;
        if (currentRunId == null) {
            return;
        }
        // A cancelled run ends with the interrupt flag set, which would cut the wait short and fail the blocking write of the driver
        boolean interrupted = Thread.interrupted();
        try {
            persistence.awaitRunHistoryWrites();
            store(() -> repository.finishRun(currentRunId, Instant.now(), status), "the end of run %s".formatted(currentRunId));
            LOG.info("Recorded the end of run {} with status {}", currentRunId, status);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Candidate candidateOf(ScoredProcedure scored) {
        var procedure = scored.procedure();
        return new Candidate(procedure.id(), procedure.description(), scored.semanticScore(), scored.satisfied(), scored.totalPrereqs(),
                scored.sharedParentCount(), scored.lowStability(), scored.unmetPrerequisites());
    }

    // The run history explains runs after the fact, so failing to store it must never fail the run it describes
    private static void store(Runnable write, String description) {
        try {
            write.run();
        } catch (DatabaseConnectionException | Neo4jException e) {
            LOG.error("Could not record {}", description, e);
        }
    }
}
