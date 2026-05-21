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
import org.neo4j.driver.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge.*;

@Singleton
public class SatisfiesEdgeRepository {
    public SatisfiesEdgeRepository(Neo4jRepositorySupport repositorySupport) {
        this.repositorySupport = repositorySupport;
        this.PERSIST_EDGES = repositorySupport.cypher("""
            UNWIND $edges AS edge
            MATCH (producer:${LABEL_PROCEDURE} {${PROP_ID}: edge.producerId})
            MATCH (consumer:${LABEL_PROCEDURE} {${PROP_ID}: edge.consumerId})
            MERGE (producer)-[r:${REL_SATISFIES} {${PROP_PREREQUISITE_NODE_ID}: edge.prerequisiteNodeId}]->(consumer)
            ON CREATE SET r.${PROP_SCORE} = edge.score, r.${PROP_EFFECT_PHRASE} = edge.effectPhrase,
                          r.${PROP_PREREQUISITE_PHRASE} = edge.prerequisitePhrase,
                          r.${PROP_CREATED_AT} = timestamp(), r.${PROP_LAST_VERIFIED_AT} = timestamp()
            ON MATCH SET r.${PROP_SCORE} = edge.score, r.${PROP_EFFECT_PHRASE} = edge.effectPhrase,
                         r.${PROP_PREREQUISITE_PHRASE} = edge.prerequisitePhrase,
                         r.${PROP_LAST_VERIFIED_AT} = timestamp()
            """);
        this.DELETE_EDGES = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $id})-[r:${REL_SATISFIES}]-()
            DELETE r
            """);
        this.REFRESH_EDGE = repositorySupport.cypher("""
            MATCH (producer:${LABEL_PROCEDURE} {${PROP_ID}: $producerId})-[r:${REL_SATISFIES}]->(consumer:${LABEL_PROCEDURE} {${PROP_ID}: $consumerId})
            SET r.${PROP_LAST_VERIFIED_AT} = timestamp()
            """);
        this.FIND_STALE_EDGES = repositorySupport.cypher("""
            MATCH (producer:${LABEL_PROCEDURE})-[r:${REL_SATISFIES}]->(consumer:${LABEL_PROCEDURE})
            WHERE r.${PROP_LAST_VERIFIED_AT} < timestamp() - ($staleDays * 86400000)
            RETURN producer.${PROP_ID} AS producerId, consumer.${PROP_ID} AS consumerId,
                   r.${PROP_SCORE} AS score, r.${PROP_EFFECT_PHRASE} AS effectPhrase,
                   r.${PROP_PREREQUISITE_PHRASE} AS prerequisitePhrase,
                   r.${PROP_PREREQUISITE_NODE_ID} AS prerequisiteNodeId
            """);
        this.HAS_EDGES = repositorySupport.cypher("""
            MATCH ()-[r:${REL_SATISFIES}]->()
            RETURN count(r) > 0 AS hasEdges
            """);
        this.FIND_ORDERING_CONFLICTS = repositorySupport.cypher("""
            UNWIND $ids AS pId
            MATCH (consumer:${LABEL_PROCEDURE} {${PROP_ID}: pId})
            MATCH (producer:${LABEL_PROCEDURE})-[r:${REL_SATISFIES}]->(consumer)
            WHERE producer.${PROP_ID} IN $ids
            RETURN producer.${PROP_ID} AS producerId, consumer.${PROP_ID} AS consumerId
            """);
        this.FIND_UNSATISFIED_PREREQUISITES = repositorySupport.cypher("""
            MATCH (consumer:${LABEL_PROCEDURE} {${PROP_ID}: $consumerId})-[:${REL_HAS_PREREQUISITE}]->(prereq:${LABEL_PHRASE_EMBEDDING})
            CALL {
                WITH prereq
                CALL db.index.vector.queryNodes($indexName, $topN, prereq.${PROP_EMBEDDING})
                YIELD node AS effNode, score
                WHERE effNode.${PROP_ID} IN $effectNodeIds
                RETURN max(score) AS bestScore
            }
            WITH prereq, coalesce(bestScore, 0.0) AS bestScore
            WHERE bestScore < $threshold
            RETURN prereq.${PROP_PHRASE} AS phrase, bestScore
            """);
    }

    private final Neo4jRepositorySupport repositorySupport;

    private static final Logger LOG = LoggerFactory.getLogger(SatisfiesEdgeRepository.class);

    private final String PERSIST_EDGES;

    private final String DELETE_EDGES;

    private final String REFRESH_EDGE;

    private final String FIND_STALE_EDGES;

    private final String HAS_EDGES;

    private final String FIND_ORDERING_CONFLICTS;

    private final String FIND_UNSATISFIED_PREREQUISITES;

    private static final String VECTOR_INDEX_NAME = "phrase_embedding_vector_index";

    private static final int UNSATISFIED_TOP_N = 50;

    public void persistSatisfiesEdges(List<SatisfiesEdge> edges) {
        if (edges.isEmpty()) {
            return;
        }
        var mappedEdges = edges.stream().map(e -> Map.of(
                "producerId", e.producerId().toString(),
                "consumerId", e.consumerId().toString(),
                PROP_SCORE, e.score(),
                PROP_EFFECT_PHRASE, e.effectPhrase(),
                PROP_PREREQUISITE_PHRASE, e.prerequisitePhrase(),
                PROP_PREREQUISITE_NODE_ID, e.prerequisiteNodeId().toString()
        )).toList();

        repositorySupport.executeSingleWriteQuery(PERSIST_EDGES, Map.of("edges", mappedEdges));
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
        repositorySupport.executeSingleWriteQuery(REFRESH_EDGE, Map.of(
                "producerId", producerId.toString(),
                "consumerId", consumerId.toString()
        ));
    }

    public List<SatisfiesEdge> findStaleSatisfiesEdges(int staleDays) {
        return repositorySupport.executeSingleReadQuery(FIND_STALE_EDGES, Map.of("staleDays", staleDays))
                .stream()
                .map(r -> {
                    var prereqNodeIdVal = r.get(PROP_PREREQUISITE_NODE_ID);
                    var prereqNodeId = prereqNodeIdVal.isNull() ? null : UUID.fromString(prereqNodeIdVal.asString());
                    return new SatisfiesEdge(
                            UUID.fromString(r.get("producerId").asString()),
                            UUID.fromString(r.get("consumerId").asString()),
                            r.get(PROP_SCORE).asDouble(),
                            r.get(PROP_EFFECT_PHRASE).asString(),
                            r.get(PROP_PREREQUISITE_PHRASE).asString(),
                            prereqNodeId
                    );
                })
                .toList();
    }

    public record UnsatisfiedPrerequisite(String phrase, double bestScore, String bestEffectPhrase) {}

    /**
     * Returns the prerequisites of the given consumer procedure that are not yet covered by
     * the accumulated effects via vector similarity, together with the best score found for each.
     */
    public List<UnsatisfiedPrerequisite> findUnsatisfiedPrerequisites(UUID consumerId, Set<UUID> effectNodeIds, double threshold) {
        requireNonNull(consumerId, "consumerId");
        requireNonNull(effectNodeIds, "effectNodeIds");
        var effectIdStrings = effectNodeIds.stream().map(UUID::toString).toList();
        return repositorySupport.executeSingleReadQuery(FIND_UNSATISFIED_PREREQUISITES, Map.of(
                "consumerId", consumerId.toString(),
                "effectNodeIds", effectIdStrings,
                "indexName", VECTOR_INDEX_NAME,
                "topN", UNSATISFIED_TOP_N,
                "threshold", threshold
        )).stream()
                .map(r -> new UnsatisfiedPrerequisite(
                        r.get("phrase").asString(),
                        r.get("bestScore").asDouble(),
                        r.get("bestEffectPhrase").isNull() ? null : r.get("bestEffectPhrase").asString()))
                .toList();
    }

    public boolean hasSatisfiesEdges() {
        var records = repositorySupport.executeSingleReadQuery(HAS_EDGES);
        return !records.isEmpty() && records.getFirst().get("hasEdges").asBoolean();
    }

    public record OrderingConflict(UUID producerId, UUID consumerId) {}

    public List<OrderingConflict> findOrderingConflicts(List<UUID> orderedProcedureIds, Map<UUID, Integer> indexMap) {
        var idsAsStrings = orderedProcedureIds.stream().map(UUID::toString).toList();
        return repositorySupport.executeSingleReadQuery(FIND_ORDERING_CONFLICTS, Map.of("ids", idsAsStrings))
                .stream()
                .map(r -> new OrderingConflict(
                        UUID.fromString(r.get("producerId").asString()),
                        UUID.fromString(r.get("consumerId").asString())))
                .filter(c -> indexMap.get(c.producerId()) > indexMap.get(c.consumerId()))
                .toList();
    }
}
