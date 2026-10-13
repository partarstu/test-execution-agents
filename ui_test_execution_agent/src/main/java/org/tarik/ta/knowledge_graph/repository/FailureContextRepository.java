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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;

import org.neo4j.driver.TransactionContext;

import java.time.Instant;
import java.util.HashMap;
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
        // Writing a property takes the procedure's write lock until the transaction ends, which serialises concurrent captures
        // that would otherwise each find no current hint and create one
        this.LOCK_PROCEDURE = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            SET p._LOCK_ = true
            REMOVE p._LOCK_
            """);
        this.FIND_CURRENT_FAILURE_CONTEXT = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})-[:${REL_HAS_FAILURE_CONTEXT}]->(fc:${LABEL_FAILURE_CONTEXT}
                  {${PROP_CATEGORY}: $category, ${PROP_SYMPTOM_NORMALIZED}: $symptomNormalized})
            WHERE fc.${PROP_EXPIRED_AT} IS NULL
            RETURN fc.${PROP_ID} AS id, coalesce(fc.${PROP_RESOLUTION}, '') AS resolution
            LIMIT 1
            """);
        this.CREATE_FAILURE_CONTEXT = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            CREATE (p)-[:${REL_HAS_FAILURE_CONTEXT}]->(fc:${LABEL_FAILURE_CONTEXT} {${PROP_ID}: $id, ${PROP_CATEGORY}: $category,
                    ${PROP_SYMPTOM_NORMALIZED}: $symptomNormalized, ${PROP_SYMPTOM}: $symptom, ${PROP_RESOLUTION}: $resolution,
                    ${PROP_OCCURRENCES}: 1, ${PROP_LAST_OCCURRED}: timestamp(), ${PROP_MODE}: $mode})
            """);
        this.RECORD_OCCURRENCE = repositorySupport.cypher("""
            MATCH (fc:${LABEL_FAILURE_CONTEXT} {${PROP_ID}: $id})
            SET fc.${PROP_OCCURRENCES} = fc.${PROP_OCCURRENCES} + 1, fc.${PROP_LAST_OCCURRED} = timestamp(), fc.${PROP_MODE} = $mode,
                fc.${PROP_RESOLUTION} = CASE WHEN $resolution <> '' THEN $resolution ELSE fc.${PROP_RESOLUTION} END
            """);
        this.EXPIRE_FAILURE_CONTEXT = repositorySupport.cypher("""
            MATCH (fc:${LABEL_FAILURE_CONTEXT} {${PROP_ID}: $id})
            SET fc.${PROP_EXPIRED_AT} = timestamp()
            """);
        this.FIND_FAILURE_CONTEXTS = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})-[:${REL_HAS_FAILURE_CONTEXT}]->(fc:${LABEL_FAILURE_CONTEXT})
            WHERE fc.${PROP_EXPIRED_AT} IS NULL AND trim(coalesce(fc.${PROP_RESOLUTION}, '')) <> ''
            RETURN fc.${PROP_ID} AS id, fc.${PROP_SYMPTOM} AS symptom, fc.${PROP_CATEGORY} AS category,
                   fc.${PROP_RESOLUTION} AS resolution, fc.${PROP_OCCURRENCES} AS occurrences,
                   fc.${PROP_LAST_OCCURRED} AS lastOccurred, fc.${PROP_MODE} AS mode
            ORDER BY occurrences DESC, lastOccurred DESC
            LIMIT $limit
            """);
        this.EXPIRE_STALE = repositorySupport.cypher("""
            MATCH (fc:${LABEL_FAILURE_CONTEXT})
            WHERE fc.${PROP_EXPIRED_AT} IS NULL AND fc.${PROP_LAST_OCCURRED} < timestamp() - $staleMs
            SET fc.${PROP_EXPIRED_AT} = timestamp()
            RETURN count(fc) AS expiredCount
            """);
        this.DELETE_LONG_EXPIRED = repositorySupport.cypher("""
            MATCH (fc:${LABEL_FAILURE_CONTEXT})
            WHERE fc.${PROP_EXPIRED_AT} < timestamp() - $staleMs
            DETACH DELETE fc
            RETURN count(fc) AS deletedCount
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

    private static final long MS_PER_DAY = 86_400_000L;

    private final String LOCK_PROCEDURE;

    private final String FIND_CURRENT_FAILURE_CONTEXT;

    private final String CREATE_FAILURE_CONTEXT;

    private final String RECORD_OCCURRENCE;

    private final String EXPIRE_FAILURE_CONTEXT;

    private final String EXPIRE_STALE;

    private final String DELETE_LONG_EXPIRED;

    private final String FIND_FAILURE_CONTEXTS;

    private final String DELETE_ORPHANED;

    /**
     * Records a failure against the current hint of the same category and symptom, or, when the failure carries a resolution that
     * differs from the current one, expires the current hint and starts a new one.
     */
    public void persistFailureContext(UUID procedureId, FailureContext fc) {
        requireNonNull(procedureId, "procedureId cannot be null");
        requireNonNull(fc, "FailureContext cannot be null");

        var matchParams = Map.<String, Object>of(
                "procedureId", procedureId.toString(),
                PROP_CATEGORY, fc.category().name(),
                PROP_SYMPTOM_NORMALIZED, fc.symptom().trim().toLowerCase());
        repositorySupport.executeComplexWriteQuery(tx -> {
            tx.run(LOCK_PROCEDURE, Map.of("procedureId", procedureId.toString())).consume();
            var current =tx.run(FIND_CURRENT_FAILURE_CONTEXT, matchParams).list();
            if (current.isEmpty()) {
                createFailureContext(tx, matchParams, fc);
                return;
            }
            var currentId = current.getFirst().get(PROP_ID).asString();
            var currentResolution = current.getFirst().get(PROP_RESOLUTION).asString().strip();
            var newResolution = fc.resolution().strip();
            if (newResolution.isEmpty() || currentResolution.isEmpty() || newResolution.equals(currentResolution)) {
                tx.run(RECORD_OCCURRENCE, Map.of(PROP_ID, currentId, PROP_RESOLUTION, fc.resolution(), PROP_MODE, fc.mode().name()))
                        .consume();
                return;
            }
            tx.run(EXPIRE_FAILURE_CONTEXT, Map.of(PROP_ID, currentId)).consume();
            createFailureContext(tx, matchParams, fc);
            LOG.info("Expired failure context {} of procedure {}: its resolution was replaced", currentId, procedureId);
        });
        LOG.debug("Persisted failure context for procedure: {}", procedureId);
    }

    private void createFailureContext(TransactionContext tx, Map<String, Object> matchParams, FailureContext fc) {
        var params = new HashMap<>(matchParams);
        params.put(PROP_ID, fc.id().toString());
        params.put(PROP_SYMPTOM, fc.symptom());
        params.put(PROP_RESOLUTION, fc.resolution());
        params.put(PROP_MODE, fc.mode().name());
        tx.run(CREATE_FAILURE_CONTEXT, params).consume();
    }

    /** Returns the current hints of the procedure that carry a resolution, the most frequent and then the most recent first. */
    public List<FailureContext> findFailureContexts(UUID procedureId, int limit) {
        requireNonNull(procedureId, "procedureId cannot be null");

        return repositorySupport.executeSingleReadQuery(FIND_FAILURE_CONTEXTS, Map.of("procedureId", procedureId.toString(), "limit", limit))
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

    /** Expires the current hints that have not recurred within the given number of days and returns their count. */
    public int expireStale(int staleDays) {
        var records = repositorySupport.executeSingleWriteQuery(EXPIRE_STALE, Map.of("staleMs", staleDays * MS_PER_DAY));
        return records.isEmpty() ? 0 : records.getFirst().get("expiredCount").asInt();
    }

    /** Deletes the hints that expired more than the given number of days ago and returns their count. */
    public int deleteLongExpired(int staleDays) {
        var records = repositorySupport.executeSingleWriteQuery(DELETE_LONG_EXPIRED, Map.of("staleMs", staleDays * MS_PER_DAY));
        return records.isEmpty() ? 0 : records.getFirst().get("deletedCount").asInt();
    }

    public void deleteOrphanedFailureContexts() {
        var records = repositorySupport.executeSingleWriteQuery(DELETE_ORPHANED);
        int deletedCount = records.isEmpty() ? 0 : records.getFirst().get("deletedCount").asInt();
        if (deletedCount > 0) {
            LOG.info("Cleaned up {} orphaned FailureContext nodes", deletedCount);
        }
    }
}
