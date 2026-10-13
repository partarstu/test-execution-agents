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
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.Kind;
import org.tarik.ta.knowledge_graph.repository.ExecutionAnomalyRepository;

import java.time.Instant;
import java.util.UUID;

/** Records the execution anomalies of the current run, attributed to the test case bound at its start. */
@UiAgentRequestScope
public class ExecutionAnomalyService {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionAnomalyService.class);
    // Details come from tool errors and model output, so they are capped before they are stored
    private static final int MAX_DETAILS_LENGTH = 500;

    private final ExecutionAnomalyRepository repository;
    private final UiTestAgentConfig config;
    private volatile @Nullable String testCaseName;

    public ExecutionAnomalyService(ExecutionAnomalyRepository repository, UiTestAgentConfig config) {
        this.repository = repository;
        this.config = config;
    }

    public void bindTestCase(@NotNull String testCaseName) {
        this.testCaseName = testCaseName;
    }

    public void record(@NotNull Kind kind, @NotNull UUID subjectId, @NotNull String details) {
        if (!config.isAnomalyRecordingEnabled()) {
            return;
        }
        var anomaly = new ExecutionAnomaly(UUID.randomUUID(), kind, subjectId, Instant.now(), testCaseName,
                details.substring(0, Math.min(details.length(), MAX_DETAILS_LENGTH)), config.getExecutionMode().name());
        try {
            repository.persist(anomaly);
        } catch (DatabaseConnectionException | Neo4jException e) {
            // An anomaly is diagnostic data, so failing to store it must never replace the outcome of the step that caused it
            LOG.error("Could not record {} anomaly for {} in test case '{}'", kind, subjectId, testCaseName, e);
            return;
        }
        LOG.info("Recorded {} anomaly for {} in test case '{}'", kind, subjectId, testCaseName);
    }
}
