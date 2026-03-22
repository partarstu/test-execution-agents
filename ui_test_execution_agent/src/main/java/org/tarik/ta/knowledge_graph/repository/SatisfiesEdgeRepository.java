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

import org.neo4j.driver.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge.*;
import static org.tarik.ta.knowledge_graph.repository.Neo4jRepositorySupport.*;

public class SatisfiesEdgeRepository {
    private static final Logger LOG = LoggerFactory.getLogger(SatisfiesEdgeRepository.class);

    private static final String PERSIST_EDGES = cypher("""
            UNWIND $edges AS edge
            MATCH (producer:${LABEL_PROCEDURE} {${PROP_ID}: edge.producerId})
            MATCH (consumer:${LABEL_PROCEDURE} {${PROP_ID}: edge.consumerId})
            MERGE (producer)-[r:${REL_SATISFIES} {${PROP_EFFECT_PHRASE}: edge.effectPhrase, ${PROP_PREREQUISITE_PHRASE}: edge.prerequisitePhrase}]->(consumer)
            ON CREATE SET r.${PROP_SCORE} = edge.score, r.${PROP_CREATED_AT} = timestamp(), r.${PROP_LAST_VERIFIED_AT} = timestamp()
            ON MATCH SET r.${PROP_SCORE} = edge.score, r.${PROP_LAST_VERIFIED_AT} = timestamp()
            """);

    private static final String DELETE_EDGES = cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $id})-[r:${REL_SATISFIES}]-()
            DELETE r
            """);

    private static final String REFRESH_EDGE = cypher("""
            MATCH (producer:${LABEL_PROCEDURE} {${PROP_ID}: $producerId})-[r:${REL_SATISFIES}]->(consumer:${LABEL_PROCEDURE} {${PROP_ID}: $consumerId})
            SET r.${PROP_LAST_VERIFIED_AT} = timestamp()
            """);

    private static final String FIND_STALE_EDGES = cypher("""
            MATCH (producer:${LABEL_PROCEDURE})-[r:${REL_SATISFIES}]->(consumer:${LABEL_PROCEDURE})
            WHERE r.${PROP_LAST_VERIFIED_AT} < timestamp() - ($staleDays * 86400000)
            RETURN producer.${PROP_ID} AS producerId, consumer.${PROP_ID} AS consumerId,
                   r.${PROP_SCORE} AS score, r.${PROP_EFFECT_PHRASE} AS effectPhrase,
                   r.${PROP_PREREQUISITE_PHRASE} AS prerequisitePhrase
            """);

    private static final String HAS_EDGES = cypher("""
            MATCH ()-[r:${REL_SATISFIES}]->()
            RETURN count(r) > 0 AS hasEdges
            """);

    private static final String FIND_ORDERING_CONFLICTS = cypher("""
            UNWIND $ids AS pId
            MATCH (consumer:${LABEL_PROCEDURE} {${PROP_ID}: pId})
            MATCH (producer:${LABEL_PROCEDURE})-[r:${REL_SATISFIES}]->(consumer)
            WHERE producer.${PROP_ID} IN $ids
            RETURN producer.${PROP_ID} AS producerId, consumer.${PROP_ID} AS consumerId
            """);

    public void persistSatisfiesEdges(List<SatisfiesEdge> edges) {
        if (edges.isEmpty()) {
            return;
        }
        var mappedEdges = edges.stream().map(e -> Map.of(
                "producerId", e.producerId().toString(),
                "consumerId", e.consumerId().toString(),
                PROP_SCORE, e.score(),
                PROP_EFFECT_PHRASE, e.effectPhrase(),
                PROP_PREREQUISITE_PHRASE, e.prerequisitePhrase()
        )).toList();

        executeSingleWriteQuery(PERSIST_EDGES, Map.of("edges", mappedEdges));
        LOG.debug("Persisted {} SATISFIES edges", edges.size());
    }

    public void deleteSatisfiesEdges(UUID procedureId, TransactionContext tx) {
        requireNonNull(procedureId, "procedureId");
        tx.run(DELETE_EDGES, Map.of("id", procedureId.toString())).consume();
        LOG.debug("Deleted SATISFIES edges for procedure: {}", procedureId);
    }

    public void refreshSatisfiesEdge(UUID producerId, UUID consumerId) {
        requireNonNull(producerId, "producerId");
        requireNonNull(consumerId, "consumerId");
        executeSingleWriteQuery(REFRESH_EDGE, Map.of(
                "producerId", producerId.toString(),
                "consumerId", consumerId.toString()
        ));
    }

    public List<SatisfiesEdge> findStaleSatisfiesEdges(int staleDays) {
        return executeSingleReadQuery(FIND_STALE_EDGES, Map.of("staleDays", staleDays))
                .stream()
                .map(r -> new SatisfiesEdge(
                        UUID.fromString(r.get("producerId").asString()),
                        UUID.fromString(r.get("consumerId").asString()),
                        r.get(PROP_SCORE).asDouble(),
                        r.get(PROP_EFFECT_PHRASE).asString(),
                        r.get(PROP_PREREQUISITE_PHRASE).asString()
                ))
                .toList();
    }

    public boolean hasSatisfiesEdges() {
        var records = executeSingleReadQuery(HAS_EDGES);
        return !records.isEmpty() && records.getFirst().get("hasEdges").asBoolean();
    }

    public record OrderingConflict(UUID producerId, UUID consumerId) {}

    public List<OrderingConflict> findOrderingConflicts(List<UUID> orderedProcedureIds, Map<UUID, Integer> indexMap) {
        var idsAsStrings = orderedProcedureIds.stream().map(UUID::toString).toList();
        return executeSingleReadQuery(FIND_ORDERING_CONFLICTS, Map.of("ids", idsAsStrings))
                .stream()
                .map(r -> new OrderingConflict(
                        UUID.fromString(r.get("producerId").asString()),
                        UUID.fromString(r.get("consumerId").asString())))
                .filter(c -> indexMap.get(c.producerId()) > indexMap.get(c.consumerId()))
                .toList();
    }
}
