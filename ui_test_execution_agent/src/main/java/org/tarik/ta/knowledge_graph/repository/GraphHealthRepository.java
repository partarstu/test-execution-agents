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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.tarik.ta.knowledge_graph.repository.Neo4jRepositorySupport.*;
import static org.tarik.ta.knowledge_graph.repository.GraphHealthRepository.QueryAliases.*;

/**
 * Read-only health-check queries for the knowledge graph.
 *
 * <p>Runs independent queries with no cross-repository dependencies.
 * All methods return findings formatted as human-readable strings for report inclusion.</p>
 */
public class GraphHealthRepository {
    private static final Logger LOG = LoggerFactory.getLogger(GraphHealthRepository.class);

    private static final long MS_PER_DAY = 86_400_000L;

    static final class QueryAliases {
        static final String ALIAS_ID = "id";
        static final String ALIAS_LABEL = "label";
        static final String ALIAS_DESCRIPTION = "description";
        static final String ALIAS_DEPTH = "depth";
        static final String ALIAS_PRODUCER_DESC = "producerDesc";
        static final String ALIAS_CONSUMER_DESC = "consumerDesc";
        static final String ALIAS_SCORE = "score";
        static final String ALIAS_DELETED_COUNT = "deletedCount";

        private QueryAliases() {}
    }

    private static final String FIND_ORPHANED_UI_ELEMENTS = cypher("""
            MATCH (el:${LABEL_UI_ELEMENT})
            WHERE NOT ()-[:${REL_TARGETS}]->(el)
            RETURN el.${PROP_ID} AS id, coalesce(el.${PROP_NAME}, el.${PROP_ID}) AS label
            ORDER BY label ASC
            """);

    private static final String FIND_LEAF_PROCEDURES_WITHOUT_ELEMENT = cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_IS_ATOMIC}: true})
            WHERE NOT (p)-[:${REL_TARGETS}]->()
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description
            ORDER BY description ASC
            """);

    private static final String FIND_DEEP_HIERARCHIES = cypher("""
            MATCH path = (root:${LABEL_PROCEDURE})-[:${REL_CONTAINS}*1..]->(leaf:${LABEL_PROCEDURE})
            WHERE NOT ()-[:${REL_CONTAINS}]->(root)
            WITH root, max(length(path)) AS depth
            WHERE depth > $maxDepth
            RETURN root.${PROP_ID} AS id, root.${PROP_DESCRIPTION} AS description, depth
            ORDER BY depth DESC
            """);

    private static final String FIND_DISCONNECTED_PROCEDURES = cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_IS_ATOMIC}: false})
            WHERE NOT ()-[:${REL_CONTAINS}]->(p) AND NOT (p)-[:${REL_CONTAINS}]->()
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description
            ORDER BY description ASC
            """);

    private static final String FIND_PROCEDURES_WITH_MISSING_EFFECTS = cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_IS_ATOMIC}: true})
            WHERE NOT (p)-[:${REL_HAS_EFFECT}]->()
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description
            ORDER BY description ASC
            """);

    private static final String FIND_ORPHANED_PHRASE_EMBEDDINGS = cypher("""
            MATCH (pe:${LABEL_PHRASE_EMBEDDING})
            WHERE NOT ()-[:${REL_HAS_PREREQUISITE}|${REL_HAS_EFFECT}]->(pe)
            RETURN pe.${PROP_ID} AS id, coalesce(pe.${PROP_PHRASE}, pe.${PROP_ID}) AS label
            ORDER BY label ASC
            """);

    private static final String FIND_STALE_SATISFIES_EDGES = cypher("""
            MATCH (producer:${LABEL_PROCEDURE})-[r:${REL_SATISFIES}]->(consumer:${LABEL_PROCEDURE})
            WHERE r.${PROP_LAST_VERIFIED_AT} < timestamp() - $staleMs
            RETURN producer.${PROP_ID} AS producerId, producer.${PROP_DESCRIPTION} AS producerDesc,
                   consumer.${PROP_ID} AS consumerId, consumer.${PROP_DESCRIPTION} AS consumerDesc,
                   r.${PROP_SCORE} AS score
            """);

    private static final String FIND_ORPHANED_FAILURE_CONTEXTS = cypher("""
            MATCH (fc:${LABEL_FAILURE_CONTEXT})
            WHERE NOT ()-[:${REL_HAS_FAILURE_CONTEXT}]->(fc)
            RETURN fc.${PROP_ID} AS id, coalesce(fc.${PROP_SYMPTOM}, fc.${PROP_ID}) AS label
            ORDER BY label ASC
            """);

    private static final String DELETE_STALE_SATISFIES_EDGES = cypher("""
            MATCH ()-[r:${REL_SATISFIES}]->()
            WHERE r.${PROP_LAST_VERIFIED_AT} < timestamp() - $staleMs
            DELETE r
            RETURN count(r) AS deletedCount
            """);

    public List<String> findOrphanedUiElements() {
        return executeSingleReadQuery(FIND_ORPHANED_UI_ELEMENTS).stream()
                .map(r -> "UiElement[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_LABEL).asString()))
                .toList();
    }

    public List<String> findLeafProceduresWithoutElement() {
        return executeSingleReadQuery(FIND_LEAF_PROCEDURES_WITHOUT_ELEMENT).stream()
                .map(r -> "Procedure[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString()))
                .toList();
    }

    public List<String> findDeepHierarchies(int maxDepth) {
        return executeSingleReadQuery(FIND_DEEP_HIERARCHIES, Map.of("maxDepth", maxDepth))
                .stream()
                .map(r -> "Procedure[%s] '%s': depth=%d".formatted(
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString(), r.get(ALIAS_DEPTH).asInt()))
                .toList();
    }

    public List<String> findDisconnectedProcedures() {
        return executeSingleReadQuery(FIND_DISCONNECTED_PROCEDURES).stream()
                .map(r -> "Procedure[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString()))
                .toList();
    }

    public List<String> findProceduresWithMissingEffects() {
        return executeSingleReadQuery(FIND_PROCEDURES_WITH_MISSING_EFFECTS).stream()
                .map(r -> "Procedure[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString()))
                .toList();
    }

    public List<String> findOrphanedPhraseEmbeddings() {
        return executeSingleReadQuery(FIND_ORPHANED_PHRASE_EMBEDDINGS).stream()
                .map(r -> "PhraseEmbedding[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_LABEL).asString()))
                .toList();
    }

    public List<String> findStaleSatisfiesEdges(int staleDays) {
        return executeSingleReadQuery(FIND_STALE_SATISFIES_EDGES, Map.of("staleMs", (long) staleDays * MS_PER_DAY))
                .stream()
                .map(r -> "SATISFIES: '%s' -> '%s' (score=%.3f, stale)".formatted(
                        r.get(ALIAS_PRODUCER_DESC).asString(), r.get(ALIAS_CONSUMER_DESC).asString(), r.get(ALIAS_SCORE).asDouble()))
                .toList();
    }

    public List<String> findOrphanedFailureContexts() {
        return executeSingleReadQuery(FIND_ORPHANED_FAILURE_CONTEXTS).stream()
                .map(r -> "FailureContext[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_LABEL).asString()))
                .toList();
    }

    public int deleteStaleSatisfiesEdges(int staleDays) {
        var records = executeSingleWriteQuery(DELETE_STALE_SATISFIES_EDGES, Map.of("staleMs", (long) staleDays * MS_PER_DAY));
        int deleted = records.isEmpty() ? 0 : records.getFirst().get(ALIAS_DELETED_COUNT).asInt();
        LOG.debug("Deleted {} stale SATISFIES edges older than {} days", deleted, staleDays);
        return deleted;
    }

}
