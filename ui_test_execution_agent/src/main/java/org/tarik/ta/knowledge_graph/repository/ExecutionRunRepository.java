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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.model.ContextEntry.StepType;
import org.tarik.ta.knowledge_graph.model.node.ExecutionRun;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Candidate;
import org.tarik.ta.knowledge_graph.model.MatchConfidence;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.tarik.ta.knowledge_graph.model.GraphRelationships.PROP_SEQUENCE;
import static org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.PROP_TEST_CASE_NAME;
import static org.tarik.ta.knowledge_graph.model.node.ExecutionRun.PROP_FINISHED_AT;
import static org.tarik.ta.knowledge_graph.model.node.ExecutionRun.PROP_STARTED_AT;
import static org.tarik.ta.knowledge_graph.model.node.ExecutionRun.PROP_STATUS;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.PROP_MODE;
import static org.tarik.ta.knowledge_graph.model.node.IEntity.PROP_ID;
import static org.tarik.ta.knowledge_graph.model.node.StepResolution.*;

/** Records the runs of test cases with their step resolutions and keeps only the newest {@code run.history.size} runs. */
@Singleton
public class ExecutionRunRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionRunRepository.class);
    private static final ObjectMapper CANDIDATES_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Candidate>> CANDIDATE_LIST = new TypeReference<>() {
    };
    static final String PARAM_RUN_ID = "runId";
    static final String PARAM_CHOSEN_PROCEDURE_ID = "chosenProcedureId";
    static final String PARAM_KEEP = "keep";

    private final Neo4jRepositorySupport repositorySupport;
    private final UiTestAgentConfig config;
    private final String createRun;
    private final String appendResolution;
    private final String finishRun;
    private final String trimRuns;

    public ExecutionRunRepository(Neo4jRepositorySupport repositorySupport, UiTestAgentConfig config) {
        this.repositorySupport = repositorySupport;
        this.config = config;
        // The test case is merged by name, as for USES_PROCEDURE, so a run of a test case without usage edges still has its node
        this.createRun = repositorySupport.cypher("""
                MERGE (tc:${LABEL_TEST_CASE} {${PROP_NAME}: $testCaseName})
                CREATE (tc)-[:${REL_HAS_RUN}]->(:${LABEL_EXECUTION_RUN} {${PROP_ID}: $id, ${PROP_TEST_CASE_NAME}: $testCaseName,
                        ${PROP_STARTED_AT}: $startedAt, ${PROP_MODE}: $mode})
                """);
        this.appendResolution = repositorySupport.cypher("""
                MATCH (run:${LABEL_EXECUTION_RUN} {${PROP_ID}: $runId})
                CREATE (run)-[:${REL_HAS_RESOLUTION}]->(resolution:${LABEL_STEP_RESOLUTION} {${PROP_ID}: $id,
                        ${PROP_SEQUENCE}: $sequence, ${PROP_ITEM_TYPE}: $itemType, ${PROP_ITEM_DESCRIPTION}: $itemDescription,
                        ${PROP_SOURCE}: $source, ${PROP_CONFIDENCE}: $confidence, ${PROP_CANDIDATES}: $candidates,
                        ${PROP_WAS_DEMOTED}: $wasDemoted, ${PROP_DEMOTED_DUE_TO_PREREQUISITES}: $demotedDueToPrerequisites,
                        ${PROP_OUTCOME}: $outcome, ${PROP_DURATION_MS}: $durationMs})
                WITH resolution
                OPTIONAL MATCH (chosen:${LABEL_PROCEDURE} {${PROP_ID}: $chosenProcedureId})
                FOREACH (_ IN CASE WHEN chosen IS NULL THEN [] ELSE [1] END | CREATE (resolution)-[:${REL_CHOSE}]->(chosen))
                """);
        this.finishRun = repositorySupport.cypher("""
                MATCH (run:${LABEL_EXECUTION_RUN} {${PROP_ID}: $id})
                SET run.${PROP_FINISHED_AT} = $finishedAt, run.${PROP_STATUS} = $status
                """);
        this.trimRuns = repositorySupport.cypher("""
                MATCH (run:${LABEL_EXECUTION_RUN})
                WITH run ORDER BY run.${PROP_STARTED_AT} DESC
                SKIP $keep
                OPTIONAL MATCH (run)-[:${REL_HAS_RESOLUTION}]->(resolution:${LABEL_STEP_RESOLUTION})
                DETACH DELETE resolution, run
                """);
    }

    public void createRun(@NotNull ExecutionRun run) {
        repositorySupport.executeSingleWriteQuery(createRun, Map.of(PROP_ID, run.id().toString(), PROP_TEST_CASE_NAME, run.testCaseName(),
                PROP_STARTED_AT, run.startedAt().toEpochMilli(), PROP_MODE, run.mode().name()));
        LOG.debug("Recorded the start of run {} of test case '{}'", run.id(), run.testCaseName());
    }

    /** Appends the resolution to the run, keeping the first {@code knowledge.match.top.n} candidates. */
    public void appendResolution(@NotNull UUID runId, @NotNull StepResolution resolution) {
        var params = new HashMap<String, Object>();
        params.put(PARAM_RUN_ID, runId.toString());
        params.put(PROP_ID, resolution.id().toString());
        params.put(PROP_SEQUENCE, resolution.sequence());
        params.put(PROP_ITEM_TYPE, resolution.itemType().name());
        params.put(PROP_ITEM_DESCRIPTION, resolution.itemDescription());
        params.put(PROP_SOURCE, resolution.source().name());
        params.put(PROP_CONFIDENCE, resolution.confidence() == null ? null : resolution.confidence().name());
        params.put(PROP_CANDIDATES, candidatesJson(resolution.candidates()));
        params.put(PROP_WAS_DEMOTED, resolution.wasDemoted());
        params.put(PROP_DEMOTED_DUE_TO_PREREQUISITES, resolution.demotedDueToPrerequisites());
        params.put(PROP_OUTCOME, resolution.outcome().name());
        params.put(PROP_DURATION_MS, resolution.durationMs());
        params.put(PARAM_CHOSEN_PROCEDURE_ID, resolution.chosenProcedureId() == null ? null : resolution.chosenProcedureId().toString());
        repositorySupport.executeSingleWriteQuery(appendResolution, params);
    }

    /** Stores the run's status and, in the same transaction, deletes the runs beyond the newest {@code run.history.size}. */
    public void finishRun(@NotNull UUID runId, @NotNull Instant finishedAt, ExecutionRun.@NotNull Status status) {
        repositorySupport.executeComplexWriteQuery(tx -> {
            tx.run(finishRun, Map.of(PROP_ID, runId.toString(), PROP_FINISHED_AT, finishedAt.toEpochMilli(), PROP_STATUS, status.name()))
                    .consume();
            tx.run(trimRuns, Map.of(PARAM_KEEP, config.getRunHistorySize())).consume();
        });
        LOG.debug("Recorded the end of run {} with status {}", runId, status);
    }

    static StepResolution resolutionFromNode(@NotNull Node node, @Nullable String chosenProcedureId) {
        var confidence = node.get(PROP_CONFIDENCE);
        return new StepResolution(
                UUID.fromString(node.get(PROP_ID).asString()),
                node.get(PROP_SEQUENCE).asInt(),
                StepType.valueOf(node.get(PROP_ITEM_TYPE).asString()),
                node.get(PROP_ITEM_DESCRIPTION).asString(),
                Source.valueOf(node.get(PROP_SOURCE).asString()),
                confidence.isNull() ? null : MatchConfidence.valueOf(confidence.asString()),
                candidatesFromJson(node.get(PROP_CANDIDATES).asString("[]")),
                node.get(PROP_WAS_DEMOTED).asBoolean(false),
                node.get(PROP_DEMOTED_DUE_TO_PREREQUISITES).asBoolean(false),
                chosenProcedureId == null ? null : UUID.fromString(chosenProcedureId),
                Outcome.valueOf(node.get(PROP_OUTCOME).asString()),
                node.get(PROP_DURATION_MS).asLong(0));
    }

    private String candidatesJson(List<Candidate> candidates) {
        var kept = candidates.subList(0, Math.min(candidates.size(), config.getKnowledgeMatchTopN()));
        try {
            return CANDIDATES_MAPPER.writeValueAsString(kept);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the candidates of a step resolution", e);
        }
    }

    private static List<Candidate> candidatesFromJson(String json) {
        try {
            return CANDIDATES_MAPPER.readValue(json, CANDIDATE_LIST);
        } catch (JsonProcessingException e) {
            LOG.warn("Ignoring the unreadable candidates of a step resolution", e);
            return List.of();
        }
    }
}
