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
package org.tarik.ta.knowledge_graph.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.knowledge_graph.repository.Neo4jRepositorySupport.*;
import static org.tarik.ta.knowledge_graph.repository.ProcedureUsageByTestCaseTrackingRepository.QueryAliases.*;

/**
 * Repository for {@code USES_PROCEDURE} edge operations between {@code TestCase} and {@code Procedure} nodes.
 */
public class ProcedureUsageByTestCaseTrackingRepository {

    static final class QueryAliases {
        static final String ALIAS_TEST_CASE_NAME = "testCaseName";

        private QueryAliases() {}
    }

    private static final String MERGE_USES_PROCEDURE = cypher("""
            MERGE (tc:${LABEL_TEST_CASE} {${PROP_NAME}: $testCaseName})
            WITH tc
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            MERGE (tc)-[:${REL_USES_PROCEDURE}]->(p)
            """);

    private static final String FIND_TEST_CASES_USING_PROCEDURE = cypher("""
            MATCH (tc:${LABEL_TEST_CASE})-[:${REL_USES_PROCEDURE}]->(p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            RETURN tc.${PROP_NAME} AS testCaseName
            ORDER BY tc.${PROP_NAME} ASC
            """);

    private static final String CLEANUP_STALE_USES_PROCEDURE = cypher("""
            MATCH (tc:${LABEL_TEST_CASE} {${PROP_NAME}: $testCaseName})-[r:${REL_USES_PROCEDURE}]->(p:${LABEL_PROCEDURE})
            WHERE NOT p.${PROP_ID} IN $usedProcedureIds
            DELETE r
            """);

    public void mergeUsesProcedure(String testCaseName, UUID procedureId) {
        requireNonNull(testCaseName, "testCaseName");
        requireNonNull(procedureId, "procedureId");
        executeSingleWriteQuery(MERGE_USES_PROCEDURE, Map.of(
                "testCaseName", testCaseName,
                "procedureId", procedureId.toString()
        ));
    }

    public List<String> findTestCasesUsingProcedure(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        return executeSingleReadQuery(FIND_TEST_CASES_USING_PROCEDURE, Map.of("procedureId", procedureId.toString()))
                .stream()
                .map(r -> r.get(ALIAS_TEST_CASE_NAME).asString())
                .toList();
    }

    public void cleanupStaleUsesProcedure(String testCaseName, List<UUID> usedProcedureIds) {
        requireNonNull(testCaseName, "testCaseName");
        requireNonNull(usedProcedureIds, "usedProcedureIds");
        executeSingleWriteQuery(CLEANUP_STALE_USES_PROCEDURE, Map.of(
                "testCaseName", testCaseName,
                "usedProcedureIds", usedProcedureIds.stream().map(UUID::toString).toList()
        ));
    }
}
