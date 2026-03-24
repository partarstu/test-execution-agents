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

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.knowledge_graph.model.node.FailureContext.*;
import static org.tarik.ta.knowledge_graph.model.node.IEntity.PROP_ID;

@Singleton
public class FailureContextRepository {
    public FailureContextRepository(Neo4jRepositorySupport repositorySupport) {
        this.repositorySupport = repositorySupport;
        this.PERSIST_FAILURE_CONTEXT = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            MERGE (p)-[:${REL_HAS_FAILURE_CONTEXT}]->(fc:${LABEL_FAILURE_CONTEXT} {${PROP_CATEGORY}: $category, ${PROP_SYMPTOM_NORMALIZED}: $symptomNormalized})
            ON CREATE SET fc.${PROP_ID} = $id, fc.${PROP_SYMPTOM} = $symptom, fc.${PROP_RESOLUTION} = $resolution,
                          fc.${PROP_OCCURRENCES} = 1, fc.${PROP_LAST_OCCURRED} = timestamp(), fc.${PROP_MODE} = $mode
            ON MATCH SET fc.${PROP_OCCURRENCES} = fc.${PROP_OCCURRENCES} + 1, fc.${PROP_LAST_OCCURRED} = timestamp(),
                         fc.${PROP_RESOLUTION} = CASE WHEN $resolution <> '' THEN $resolution ELSE fc.${PROP_RESOLUTION} END,
                         fc.${PROP_MODE} = $mode
            """);
        this.FIND_FAILURE_CONTEXTS = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})-[:${REL_HAS_FAILURE_CONTEXT}]->(fc:${LABEL_FAILURE_CONTEXT})
            RETURN fc.${PROP_ID} AS id, fc.${PROP_SYMPTOM} AS symptom, fc.${PROP_CATEGORY} AS category,
                   fc.${PROP_RESOLUTION} AS resolution, fc.${PROP_OCCURRENCES} AS occurrences,
                   fc.${PROP_LAST_OCCURRED} AS lastOccurred, fc.${PROP_MODE} AS mode
            """);
        this.DELETE_ORPHANED = repositorySupport.cypher("""
            MATCH (fc:${LABEL_FAILURE_CONTEXT})
            WHERE NOT ()-[:${REL_HAS_FAILURE_CONTEXT}]->(fc)
            DELETE fc
            RETURN count(fc) AS deletedCount
            """);
    }

    private final Neo4jRepositorySupport repositorySupport;

    private static final Logger LOG = LoggerFactory.getLogger(FailureContextRepository.class);

    private final String PERSIST_FAILURE_CONTEXT;

    private final String FIND_FAILURE_CONTEXTS;

    private final String DELETE_ORPHANED;

    public void persistFailureContext(UUID procedureId, FailureContext fc) {
        requireNonNull(procedureId, "procedureId cannot be null");
        requireNonNull(fc, "FailureContext cannot be null");

        String symptomNormalized = fc.symptom().trim().toLowerCase();

        repositorySupport.executeSingleWriteQuery(PERSIST_FAILURE_CONTEXT, Map.of(
                "procedureId", procedureId.toString(),
                PROP_CATEGORY, fc.category().name(),
                PROP_SYMPTOM_NORMALIZED, symptomNormalized,
                PROP_ID, fc.id().toString(),
                PROP_SYMPTOM, fc.symptom(),
                PROP_RESOLUTION, fc.resolution(),
                PROP_MODE, fc.mode().name()
        ));
        LOG.debug("Persisted failure context for procedure: {}", procedureId);
    }

    public List<FailureContext> findFailureContexts(UUID procedureId) {
        requireNonNull(procedureId, "procedureId cannot be null");

        return repositorySupport.executeSingleReadQuery(FIND_FAILURE_CONTEXTS, Map.of("procedureId", procedureId.toString()))
                .stream()
                .map(r -> new FailureContext(
                        UUID.fromString(r.get(PROP_ID).asString()),
                        r.get(PROP_SYMPTOM).asString(),
                        ErrorCategory.valueOf(r.get(PROP_CATEGORY).asString()),
                        r.get(PROP_RESOLUTION).asString(),
                        r.get(PROP_OCCURRENCES).asInt(),
                        Instant.ofEpochMilli(r.get(PROP_LAST_OCCURRED).asLong()),
                        FailureContext.Mode.valueOf(r.get(PROP_MODE).asString())
                )).toList();
    }

    public void deleteOrphanedFailureContexts() {
        var records = repositorySupport.executeSingleWriteQuery(DELETE_ORPHANED);
        int deletedCount = records.isEmpty() ? 0 : records.getFirst().get("deletedCount").asInt();
        if (deletedCount > 0) {
            LOG.info("Cleaned up {} orphaned FailureContext nodes", deletedCount);
        }
    }
}
