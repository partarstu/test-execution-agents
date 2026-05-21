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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.knowledge_graph.repository.ProcedureUsageByTestCaseTrackingRepository.QueryAliases.*;

/**
 * Repository for {@code USES_PROCEDURE} edge operations between {@code TestCase} and {@code Procedure} nodes.
 */
@Singleton
public class ProcedureUsageByTestCaseTrackingRepository {
    public ProcedureUsageByTestCaseTrackingRepository(Neo4jRepositorySupport repositorySupport) {
        this.repositorySupport = repositorySupport;
        this.MERGE_USES_PROCEDURE = repositorySupport.cypher("""
            MERGE (tc:${LABEL_TEST_CASE} {${PROP_NAME}: $testCaseName})
            WITH tc
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            MERGE (tc)-[:${REL_USES_PROCEDURE}]->(p)
            """);
        this.FIND_TEST_CASES_USING_PROCEDURE = repositorySupport.cypher("""
            MATCH (tc:${LABEL_TEST_CASE})-[:${REL_USES_PROCEDURE}]->(p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            RETURN tc.${PROP_NAME} AS testCaseName
            ORDER BY tc.${PROP_NAME} ASC
            """);
        this.CLEANUP_STALE_USES_PROCEDURE = repositorySupport.cypher("""
            MATCH (tc:${LABEL_TEST_CASE} {${PROP_NAME}: $testCaseName})-[r:${REL_USES_PROCEDURE}]->(p:${LABEL_PROCEDURE})
            WHERE NOT p.${PROP_ID} IN $usedProcedureIds
            DELETE r
            """);
    }

    private final Neo4jRepositorySupport repositorySupport;


    static final class QueryAliases {
        static final String ALIAS_TEST_CASE_NAME = "testCaseName";

        private QueryAliases() {}
    }

    private final String MERGE_USES_PROCEDURE;

    private final String FIND_TEST_CASES_USING_PROCEDURE;

    private final String CLEANUP_STALE_USES_PROCEDURE;

    public void mergeUsesProcedure(String testCaseName, UUID procedureId) {
        requireNonNull(testCaseName, "testCaseName");
        requireNonNull(procedureId, "procedureId");
        repositorySupport.executeSingleWriteQuery(MERGE_USES_PROCEDURE, Map.of(
                "testCaseName", testCaseName,
                "procedureId", procedureId.toString()
        ));
    }

    public List<String> findTestCasesUsingProcedure(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        return repositorySupport.executeSingleReadQuery(FIND_TEST_CASES_USING_PROCEDURE, Map.of("procedureId", procedureId.toString()))
                .stream()
                .map(r -> r.get(ALIAS_TEST_CASE_NAME).asString())
                .toList();
    }

    public void cleanupStaleUsesProcedure(String testCaseName, List<UUID> usedProcedureIds) {
        requireNonNull(testCaseName, "testCaseName");
        requireNonNull(usedProcedureIds, "usedProcedureIds");
        repositorySupport.executeSingleWriteQuery(CLEANUP_STALE_USES_PROCEDURE, Map.of(
                "testCaseName", testCaseName,
                "usedProcedureIds", usedProcedureIds.stream().map(UUID::toString).toList()
        ));
    }
}
