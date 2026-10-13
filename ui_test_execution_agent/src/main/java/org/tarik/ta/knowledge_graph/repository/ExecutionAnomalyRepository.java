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

import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import java.util.HashMap;
import java.util.Map;

import static org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.*;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.PROP_MODE;
import static org.tarik.ta.knowledge_graph.model.node.IEntity.PROP_ID;

/** Persists {@link ExecutionAnomaly} nodes linked to their subject and keeps only the newest ones per subject. */
@Singleton
public class ExecutionAnomalyRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionAnomalyRepository.class);
    private static final String PERSIST_TEMPLATE = """
            MATCH (s:%s {${PROP_ID}: $subjectId})
            CREATE (:${LABEL_EXECUTION_ANOMALY} {${PROP_ID}: $id, ${PROP_KIND}: $kind, ${PROP_SUBJECT_ID}: $subjectId,
                    ${PROP_OCCURRED_AT}: $occurredAt, ${PROP_TEST_CASE_NAME}: $testCaseName, ${PROP_DETAILS}: $details,
                    ${PROP_MODE}: $mode})-[:${REL_OBSERVED_ON}]->(s)
            """;

    private final Neo4jRepositorySupport repositorySupport;
    private final UiTestAgentConfig config;
    private final Map<String, String> persistBySubjectLabel;
    private final String trimForSubject;

    public ExecutionAnomalyRepository(Neo4jRepositorySupport repositorySupport, UiTestAgentConfig config) {
        this.repositorySupport = repositorySupport;
        this.config = config;
        // One query per subject label, so the subject lookup uses the label's id index
        this.persistBySubjectLabel = Map.of(
                UiElement.LABEL, repositorySupport.cypher(PERSIST_TEMPLATE.formatted(UiElement.LABEL)),
                Procedure.LABEL, repositorySupport.cypher(PERSIST_TEMPLATE.formatted(Procedure.LABEL)));
        this.trimForSubject = repositorySupport.cypher("""
                MATCH (a:${LABEL_EXECUTION_ANOMALY} {${PROP_SUBJECT_ID}: $subjectId})
                WITH a ORDER BY datetime(a.${PROP_OCCURRED_AT}) DESC
                WITH collect(a) AS anomalies
                UNWIND anomalies[$keep..] AS expired
                DETACH DELETE expired
                """);
    }

    public void persist(@NotNull ExecutionAnomaly anomaly) {
        var params = new HashMap<String, Object>();
        params.put(PROP_ID, anomaly.id().toString());
        params.put(PROP_KIND, anomaly.kind().name());
        params.put(PROP_SUBJECT_ID, anomaly.subjectId().toString());
        params.put(PROP_OCCURRED_AT, anomaly.occurredAt().toString());
        params.put(PROP_TEST_CASE_NAME, anomaly.testCaseName());
        params.put(PROP_DETAILS, anomaly.details());
        params.put(PROP_MODE, anomaly.mode());
        var persist = persistBySubjectLabel.get(anomaly.kind().subjectLabel());
        repositorySupport.executeComplexWriteQuery(tx -> {
            tx.run(persist, params).consume();
            tx.run(trimForSubject, Map.of(PROP_SUBJECT_ID, anomaly.subjectId().toString(), "keep", config.getAnomalyHistorySize()))
                    .consume();
        });
        LOG.debug("Persisted {} anomaly for subject {}", anomaly.kind(), anomaly.subjectId());
    }
}
