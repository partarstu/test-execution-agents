/*
 * knowledge-graph-core - Knowledge graph model, repositories and embeddings shared by the agents that use the knowledge graph.
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
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;

import org.neo4j.driver.TransactionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.tarik.ta.knowledge_graph.model.GraphRelationships.PROP_SEQUENCE;
import static org.tarik.ta.knowledge_graph.model.node.Embeddable.PROP_EMBEDDING;
import static org.tarik.ta.knowledge_graph.model.node.IEntity.PROP_ID;
import static org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PROP_PHRASE;
import static org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PROP_TYPE;
import static org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository.QueryAliases.*;
import static org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository.QueryParams.*;

/**
 * Repository for {@link PhraseEmbedding} persistence in Neo4j.
 *
 * <p>Handles creation of native {@code PhraseEmbedding} nodes with vector-indexed embeddings,
 * relationship linking to {@code Procedure} nodes, and Cypher-native vector similarity queries.</p>
 */
@Singleton
public class PhraseEmbeddingRepository {
    public PhraseEmbeddingRepository(Neo4jRepositorySupport repositorySupport) {
        this.repositorySupport = repositorySupport;
        this.CREATE_NODE = repositorySupport.cypher("""
            CREATE (pe:${LABEL_PHRASE_EMBEDDING} {${PROP_ID}: $id, ${PROP_PHRASE}: $phrase, ${PROP_EMBEDDING}: $embedding, ${PROP_TYPE}: $type})
            """);
        this.BATCH_CREATE_PREREQUISITES = repositorySupport.cypher("""
            UNWIND $nodes AS node
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            CREATE (pe:${LABEL_PHRASE_EMBEDDING} {${PROP_ID}: node.${PROP_ID}, ${PROP_PHRASE}: node.${PROP_PHRASE}, ${PROP_EMBEDDING}: node.${PROP_EMBEDDING}, ${PROP_TYPE}: $phraseType})
            CREATE (p)-[:${REL_HAS_PREREQUISITE} {${PROP_SEQUENCE}: node.${PROP_SEQUENCE}}]->(pe)
            """);
        this.BATCH_CREATE_EFFECTS = repositorySupport.cypher("""
            UNWIND $nodes AS node
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            CREATE (pe:${LABEL_PHRASE_EMBEDDING} {${PROP_ID}: node.${PROP_ID}, ${PROP_PHRASE}: node.${PROP_PHRASE}, ${PROP_EMBEDDING}: node.${PROP_EMBEDDING}, ${PROP_TYPE}: $phraseType})
            CREATE (p)-[:${REL_HAS_EFFECT} {${PROP_SEQUENCE}: node.${PROP_SEQUENCE}}]->(pe)
            """);
        this.BATCH_CREATE_NEGATED_EFFECTS = repositorySupport.cypher("""
            UNWIND $nodes AS node
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})
            CREATE (pe:${LABEL_PHRASE_EMBEDDING} {${PROP_ID}: node.${PROP_ID}, ${PROP_PHRASE}: node.${PROP_PHRASE}, ${PROP_EMBEDDING}: node.${PROP_EMBEDDING}, ${PROP_TYPE}: $phraseType})
            CREATE (p)-[:${REL_HAS_NEGATED_EFFECT} {${PROP_SEQUENCE}: node.${PROP_SEQUENCE}}]->(pe)
            """);
        this.FIND_PREREQUISITES_FOR_PROCEDURE = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})-[r:${REL_HAS_PREREQUISITE}]->(pe:${LABEL_PHRASE_EMBEDDING})
            RETURN pe
            ORDER BY r.${PROP_SEQUENCE} ASC
            """);
        this.FIND_EFFECTS_FOR_PROCEDURE = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})-[r:${REL_HAS_EFFECT}]->(pe:${LABEL_PHRASE_EMBEDDING})
            RETURN pe
            ORDER BY r.${PROP_SEQUENCE} ASC
            """);
        this.FIND_NEGATED_EFFECTS_FOR_PROCEDURE = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})-[r:${REL_HAS_NEGATED_EFFECT}]->(pe:${LABEL_PHRASE_EMBEDDING})
            RETURN pe
            ORDER BY r.${PROP_SEQUENCE} ASC
            """);
        this.FIND_SIMILAR_TO_VECTOR = repositorySupport.cypher("""
            MATCH (node:${LABEL_PHRASE_EMBEDDING})
              SEARCH node IN (VECTOR INDEX ${INDEX_PHRASE_EMBEDDING} FOR $queryVector LIMIT $topN) SCORE AS score
            WHERE score >= $minScore
            RETURN node, score
            ORDER BY score DESC
            """);
        this.DELETE_FOR_PROCEDURE = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})-[:${REL_HAS_PREREQUISITE}|${REL_HAS_EFFECT}|${REL_HAS_NEGATED_EFFECT}]->(pe:${LABEL_PHRASE_EMBEDDING})
            DETACH DELETE pe
            """);
        // The search runs in a subquery because the Neo4j 2026.01 planner otherwise pushes the score filter into a search whose
        // vector comes from an earlier match, drops it there and fails the query
        this.FIND_PREREQUISITES_SATISFIED_BY_PRODUCER = repositorySupport.cypher("""
            MATCH (producer:${LABEL_PROCEDURE} {${PROP_ID}: $producerId})-[:${REL_HAS_EFFECT}]->(eff:${LABEL_PHRASE_EMBEDDING})
            CALL (eff) {
              MATCH (prereqNode:${LABEL_PHRASE_EMBEDDING})
                SEARCH prereqNode IN (VECTOR INDEX ${INDEX_PHRASE_EMBEDDING} FOR eff.${PROP_EMBEDDING} LIMIT $topN) SCORE AS score
              RETURN prereqNode, score
            }
            WITH eff, prereqNode, score
            WHERE score >= $threshold AND prereqNode.${PROP_TYPE} = $phraseType
            MATCH (consumer:${LABEL_PROCEDURE})-[:${REL_HAS_PREREQUISITE}]->(prereqNode)
            WHERE consumer.${PROP_ID} <> $producerId
            RETURN consumer.${PROP_ID} AS consumerId, eff.${PROP_PHRASE} AS effectPhrase,
                   prereqNode.${PROP_PHRASE} AS prerequisitePhrase, prereqNode.${PROP_ID} AS prereqNodeId, score
            ORDER BY score DESC
            """);
        // A subquery for the same reason as in FIND_PREREQUISITES_SATISFIED_BY_PRODUCER
        this.IS_PREREQUISITE_MET_BY_EFFECTS = repositorySupport.cypher("""
            MATCH (pe:${LABEL_PHRASE_EMBEDDING} {${PROP_ID}: $prereqNodeId})
            CALL (pe) {
              MATCH (node:${LABEL_PHRASE_EMBEDDING})
                SEARCH node IN (VECTOR INDEX ${INDEX_PHRASE_EMBEDDING} FOR pe.${PROP_EMBEDDING} LIMIT $topN) SCORE AS score
              RETURN node, score
            }
            WITH node, score
            WHERE score >= $threshold AND node.${PROP_TYPE} = $phraseType AND node.${PROP_ID} IN $effectNodeIds
            RETURN count(node) > 0 AS isMet
            """);
        // Scores every current effect node instead of querying the vector index, whose nearest neighbours across the whole graph
        // can crowd out the matching effect before the filter to the current effects applies
        this.IS_PREREQUISITE_MET_BATCH = repositorySupport.cypher("""
            OPTIONAL MATCH (effNode:${LABEL_PHRASE_EMBEDDING})
            WHERE effNode.${PROP_ID} IN $effectNodeIds AND effNode.${PROP_TYPE} = $phraseType
            WITH collect(effNode) AS effNodes
            UNWIND $procedureIds AS pId
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: pId})-[r:${REL_HAS_PREREQUISITE}]->(pe:${LABEL_PHRASE_EMBEDDING})
            RETURN pId AS procedureId, pe.${PROP_ID} AS prereqNodeId, pe.${PROP_PHRASE} AS phrase,
                   any(effNode IN effNodes WHERE vector.similarity.cosine(pe.${PROP_EMBEDDING}, effNode.${PROP_EMBEDDING}) >= $threshold)
                       AS isMet
            """);
        // Scores the current effect nodes directly for the same crowd-out reason as in IS_PREREQUISITE_MET_BATCH
        this.FIND_EFFECTS_NEGATED_BY_PROCEDURE = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $procedureId})-[:${REL_HAS_NEGATED_EFFECT}]->(neg:${LABEL_PHRASE_EMBEDDING})
            MATCH (effNode:${LABEL_PHRASE_EMBEDDING})
            WHERE effNode.${PROP_ID} IN $effectNodeIds AND effNode.${PROP_TYPE} = $phraseType
              AND vector.similarity.cosine(neg.${PROP_EMBEDDING}, effNode.${PROP_EMBEDDING}) >= $threshold
            RETURN DISTINCT effNode.${PROP_ID} AS effectNodeId
            """);
    }

    private final Neo4jRepositorySupport repositorySupport;

    private static final Logger LOG = LoggerFactory.getLogger(PhraseEmbeddingRepository.class);

    static final class QueryAliases {
        static final String ALIAS_NODE = "node";
        static final String ALIAS_SCORE = "score";
        static final String ALIAS_PE = "pe";
        static final String ALIAS_PROCEDURE_ID = "procedureId";
        static final String ALIAS_CONSUMER_ID = "consumerId";
        static final String ALIAS_EFFECT_PHRASE = "effectPhrase";
        static final String ALIAS_PREREQUISITE_PHRASE = "prerequisitePhrase";
        static final String ALIAS_PREREQ_NODE_ID = "prereqNodeId";
        static final String ALIAS_PHRASE = "phrase";
        static final String ALIAS_IS_MET = "isMet";
        static final String ALIAS_EFFECT_NODE_ID = "effectNodeId";

        private QueryAliases() {}
    }

    static final class QueryParams {
        static final String PARAM_TOP_N = "topN";
        static final String PARAM_QUERY_VECTOR = "queryVector";
        static final String PARAM_MIN_SCORE = "minScore";
        static final String PARAM_PROCEDURE_ID = "procedureId";
        static final String PARAM_PROCEDURE_IDS = "procedureIds";
        static final String PARAM_NODES = "nodes";
        static final String PARAM_PHRASE_TYPE = "phraseType";
        static final String PARAM_PRODUCER_ID = "producerId";
        static final String PARAM_PREREQ_NODE_ID = "prereqNodeId";
        static final String PARAM_THRESHOLD = "threshold";
        static final String PARAM_EFFECT_NODE_IDS = "effectNodeIds";

        private QueryParams() {}
    }

    private final String CREATE_NODE;

    private final String BATCH_CREATE_PREREQUISITES;

    private final String BATCH_CREATE_EFFECTS;

    private final String BATCH_CREATE_NEGATED_EFFECTS;

    private final String FIND_PREREQUISITES_FOR_PROCEDURE;

    private final String FIND_EFFECTS_FOR_PROCEDURE;

    private final String FIND_NEGATED_EFFECTS_FOR_PROCEDURE;

    private final String FIND_EFFECTS_NEGATED_BY_PROCEDURE;

    private final String FIND_SIMILAR_TO_VECTOR;

    private final String DELETE_FOR_PROCEDURE;

    /**
     * For each effect of the given producer procedure, queries the vector index for similar prerequisite nodes
     * and returns matches representing potential SATISFIES edges.
     * Replaces the O(effects × all prerequisites) Java cosine similarity loop in {@code SatisfiesEdgeService}.
     */
    private final String FIND_PREREQUISITES_SATISFIED_BY_PRODUCER;

    /**
     * Checks whether the given prerequisite node is semantically matched by any of the supplied effect node IDs.
     * Queries the vector index with the prerequisite's embedding and checks if any top-N result is an effect
     * node present in the accumulated effects set.
     */
    private final String IS_PREREQUISITE_MET_BY_EFFECTS;

    private final String IS_PREREQUISITE_MET_BATCH;

    /**
     * Creates a standalone {@link PhraseEmbedding} in Neo4j without linking it to any Procedure.
     * Use {@link #saveBatchForProcedure} to atomically create nodes and relationships together.
     */
    public void save(PhraseEmbedding node) {
        requireNonNull(node, "node");
        repositorySupport.executeSingleWriteQuery(CREATE_NODE, Map.of(
                PROP_ID, node.id().toString(),
                PROP_PHRASE, node.phrase(),
                PROP_EMBEDDING, node.embedding(),
                PROP_TYPE, node.type().name()
        ));
        LOG.debug("Saved PhraseEmbeddingNode id={} type={}", node.id(), node.type());
    }

    /**
     * Batch-creates {@link PhraseEmbedding} standalone nodes without relationships.
     * Use {@link #saveBatchForProcedure} to atomically create nodes and relationships together.
     */
    public void saveBatch(List<PhraseEmbedding> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        var nodeParams = nodes.stream()
                .map(n -> Map.of(
                        PROP_ID, n.id().toString(),
                        PROP_PHRASE, n.phrase(),
                        PROP_EMBEDDING, n.embedding(),
                        PROP_TYPE, n.type().name()
                ))
                .toList();
        repositorySupport.executeSingleWriteQuery(
                repositorySupport.cypher("UNWIND $nodes AS node CREATE (pe:${LABEL_PHRASE_EMBEDDING} {${PROP_ID}: node.${PROP_ID}, ${PROP_PHRASE}: node.${PROP_PHRASE}, ${PROP_EMBEDDING}: node.${PROP_EMBEDDING}, ${PROP_TYPE}: node.${PROP_TYPE}})"),
                Map.of(PARAM_NODES, nodeParams));
        LOG.debug("Batch-saved {} PhraseEmbeddingNode(s)", nodes.size());
    }

    /**
     * Atomically creates all prerequisite, effect and negated-effect {@link PhraseEmbedding} nodes for a given procedure and links
     * them via {@code HAS_PREREQUISITE}, {@code HAS_EFFECT} and {@code HAS_NEGATED_EFFECT} relationships with sequence ordering.
     */
    public void saveBatchForProcedure(UUID procedureId, List<PhraseEmbedding> prerequisites, List<PhraseEmbedding> effects,
                                      List<PhraseEmbedding> negatedEffects) {
        requireNonNull(procedureId, "procedureId");
        requireNonNull(prerequisites, "prerequisites");
        requireNonNull(effects, "effects");
        requireNonNull(negatedEffects, "negatedEffects");
        var idStr = procedureId.toString();
        if (!prerequisites.isEmpty()) {
            repositorySupport.executeSingleWriteQuery(BATCH_CREATE_PREREQUISITES, Map.of(
                    PARAM_PROCEDURE_ID, idStr,
                    PARAM_NODES, toNodeParams(prerequisites),
                    PARAM_PHRASE_TYPE, PhraseType.PREREQUISITE.name()
            ));
        }
        if (!effects.isEmpty()) {
            repositorySupport.executeSingleWriteQuery(BATCH_CREATE_EFFECTS, Map.of(
                    PARAM_PROCEDURE_ID, idStr,
                    PARAM_NODES, toNodeParams(effects),
                    PARAM_PHRASE_TYPE, PhraseType.EFFECT.name()
            ));
        }
        if (!negatedEffects.isEmpty()) {
            repositorySupport.executeSingleWriteQuery(BATCH_CREATE_NEGATED_EFFECTS, Map.of(
                    PARAM_PROCEDURE_ID, idStr,
                    PARAM_NODES, toNodeParams(negatedEffects),
                    PARAM_PHRASE_TYPE, PhraseType.NEGATED_EFFECT.name()
            ));
        }
        LOG.debug("Saved {} prerequisite(s), {} effect(s) and {} negated effect(s) for procedure id={}",
                prerequisites.size(), effects.size(), negatedEffects.size(), procedureId);
    }

    /** Transaction-aware variant of {@link #saveBatchForProcedure} — runs inside the caller's tx. */
    public void saveBatchForProcedure(UUID procedureId, List<PhraseEmbedding> prerequisites,
                                      List<PhraseEmbedding> effects, List<PhraseEmbedding> negatedEffects, TransactionContext tx) {
        requireNonNull(procedureId, "procedureId");
        requireNonNull(prerequisites, "prerequisites");
        requireNonNull(effects, "effects");
        requireNonNull(negatedEffects, "negatedEffects");
        var idStr = procedureId.toString();
        if (!prerequisites.isEmpty()) {
            tx.run(BATCH_CREATE_PREREQUISITES, Map.of(
                    PARAM_PROCEDURE_ID, idStr,
                    PARAM_NODES, toNodeParams(prerequisites),
                    PARAM_PHRASE_TYPE, PhraseType.PREREQUISITE.name()
            )).consume();
        }
        if (!effects.isEmpty()) {
            tx.run(BATCH_CREATE_EFFECTS, Map.of(
                    PARAM_PROCEDURE_ID, idStr,
                    PARAM_NODES, toNodeParams(effects),
                    PARAM_PHRASE_TYPE, PhraseType.EFFECT.name()
            )).consume();
        }
        if (!negatedEffects.isEmpty()) {
            tx.run(BATCH_CREATE_NEGATED_EFFECTS, Map.of(
                    PARAM_PROCEDURE_ID, idStr,
                    PARAM_NODES, toNodeParams(negatedEffects),
                    PARAM_PHRASE_TYPE, PhraseType.NEGATED_EFFECT.name()
            )).consume();
        }
        LOG.debug("Saved {} prerequisite(s), {} effect(s) and {} negated effect(s) for procedure id={}",
                prerequisites.size(), effects.size(), negatedEffects.size(), procedureId);
    }

    /**
     * Returns all prerequisite phrase nodes linked to the given procedure, ordered by sequence.
     */
    public List<PhraseEmbedding> findPrerequisitesForProcedure(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        return queryNodes(FIND_PREREQUISITES_FOR_PROCEDURE, Map.of(PARAM_PROCEDURE_ID, procedureId.toString()));
    }

    /**
     * Returns all effect phrase nodes linked to the given procedure, ordered by sequence.
     */
    public List<PhraseEmbedding> findEffectsForProcedure(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        return queryNodes(FIND_EFFECTS_FOR_PROCEDURE, Map.of(PARAM_PROCEDURE_ID, procedureId.toString()));
    }

    public List<PhraseEmbedding> findNegatedEffectsForProcedure(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        return queryNodes(FIND_NEGATED_EFFECTS_FOR_PROCEDURE, Map.of(PARAM_PROCEDURE_ID, procedureId.toString()));
    }

    /** Returns the ids of the given effect nodes that a negated effect of the procedure matches at the threshold or above. */
    public Set<UUID> findEffectNodesNegatedBy(UUID procedureId, Set<UUID> effectNodeIds, double threshold) {
        requireNonNull(procedureId, "procedureId");
        requireNonNull(effectNodeIds, "effectNodeIds");
        if (effectNodeIds.isEmpty()) {
            return Set.of();
        }
        var effectIdStrings = effectNodeIds.stream().map(UUID::toString).toList();
        return repositorySupport.executeSingleReadQuery(FIND_EFFECTS_NEGATED_BY_PROCEDURE, Map.of(
                PARAM_PROCEDURE_ID, procedureId.toString(),
                PARAM_EFFECT_NODE_IDS, effectIdStrings,
                PARAM_THRESHOLD, threshold,
                PARAM_PHRASE_TYPE, PhraseType.EFFECT.name()
        )).stream()
                .map(r -> UUID.fromString(r.get(ALIAS_EFFECT_NODE_ID).asString()))
                .collect(toUnmodifiableSet());
    }

    /**
     * Uses the Neo4j vector index to find phrase nodes whose embeddings are similar to the given query vector.
     *
     * @param queryVector the 384-dimensional query vector
     * @param minScore    minimum cosine similarity threshold (0.0–1.0)
     * @param topN        maximum number of results to return
     */
    public List<SimilarPhraseMatch> findSimilarToVector(float[] queryVector, double minScore, int topN) {
        requireNonNull(queryVector, "queryVector");
        return repositorySupport.executeSingleReadQuery(FIND_SIMILAR_TO_VECTOR, Map.of(
                PARAM_TOP_N, topN,
                PARAM_QUERY_VECTOR, queryVector,
                PARAM_MIN_SCORE, minScore
        )).stream()
                .map(r -> new SimilarPhraseMatch(fromNode(r.get(ALIAS_NODE).asNode()), r.get(ALIAS_SCORE).asDouble()))
                .toList();
    }

    /**
     * Deletes all {@link PhraseEmbedding} nodes connected to the given procedure
     * via {@code HAS_PREREQUISITE}, {@code HAS_EFFECT} or {@code HAS_NEGATED_EFFECT} relationships.
     */
    public void deleteForProcedure(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        repositorySupport.executeSingleWriteQuery(DELETE_FOR_PROCEDURE, Map.of(PARAM_PROCEDURE_ID, procedureId.toString()));
        LOG.debug("Deleted phrase embedding nodes for procedure id={}", procedureId);
    }

    /** Transaction-aware variant of {@link #deleteForProcedure(UUID)} — runs inside the caller's tx. */
    public void deleteForProcedure(UUID procedureId, TransactionContext tx) {
        requireNonNull(procedureId, "procedureId");
        tx.run(DELETE_FOR_PROCEDURE, Map.of(PARAM_PROCEDURE_ID, procedureId.toString())).consume();
        LOG.debug("Deleted phrase embedding nodes for procedure id={}", procedureId);
    }

    /**
     * Finds all consumer procedures whose prerequisites are semantically satisfied by the given producer's effects.
     * Uses Neo4j native vector index — replaces the O(effects × all prerequisites) Java loop.
     *
     * @param producerId the UUID of the executed producer procedure
     * @param threshold  minimum cosine similarity score (0.0–1.0)
     * @param topN       maximum number of vector index results per effect
     */
    public List<SatisfiesMatch> findPrerequisitesSatisfiedByProducer(UUID producerId, double threshold, int topN) {
        requireNonNull(producerId, "producerId");
        return repositorySupport.executeSingleReadQuery(FIND_PREREQUISITES_SATISFIED_BY_PRODUCER, Map.of(
                PARAM_PRODUCER_ID, producerId.toString(),
                PARAM_TOP_N, topN,
                PARAM_THRESHOLD, threshold,
                PARAM_PHRASE_TYPE, PhraseType.PREREQUISITE.name()
        )).stream()
                .map(r -> new SatisfiesMatch(
                        UUID.fromString(r.get(ALIAS_CONSUMER_ID).asString()),
                        r.get(ALIAS_EFFECT_PHRASE).asString(),
                        r.get(ALIAS_PREREQUISITE_PHRASE).asString(),
                        UUID.fromString(r.get(ALIAS_PREREQ_NODE_ID).asString()),
                        r.get(ALIAS_SCORE).asDouble()))
                .toList();
    }

    public boolean isPrerequisiteMetByEffects(UUID prereqNodeId, java.util.Set<UUID> effectNodeIds, double threshold) {
        requireNonNull(prereqNodeId, "prereqNodeId");
        requireNonNull(effectNodeIds, "effectNodeIds");
        if (effectNodeIds.isEmpty()) {
            return false;
        }
        // topN must be large enough to find all relevant effects
        int topN = Math.max(effectNodeIds.size() + 10, 50);
        var effectIdStrings = effectNodeIds.stream().map(UUID::toString).toList();
        var records = repositorySupport.executeSingleReadQuery(IS_PREREQUISITE_MET_BY_EFFECTS, Map.of(
                PARAM_PREREQ_NODE_ID, prereqNodeId.toString(),
                PARAM_TOP_N, topN,
                PARAM_THRESHOLD, threshold,
                PARAM_EFFECT_NODE_IDS, effectIdStrings,
                PARAM_PHRASE_TYPE, PhraseType.EFFECT.name()
        ));
        return !records.isEmpty() && records.getFirst().get(ALIAS_IS_MET).asBoolean(false);
    }

    /**
     * Checks prerequisite satisfaction for multiple procedures in a single database round-trip.
     * Replaces iterative O(N) calls to {@link #isPrerequisiteMetByEffects}.
     *
     * @param procedureIds  list of procedure UUIDs to check
     * @param effectNodeIds set of accumulated effect phrase UUIDs
     * @param threshold     minimum cosine similarity for a semantic match
     * @return list of results mapping each procedure-prerequisite pair to its satisfaction status
     */
    public List<PrerequisiteSatisfaction> findPrerequisitesSatisfactionBatch(List<UUID> procedureIds,
                                                                              java.util.Set<UUID> effectNodeIds,
                                                                              double threshold) {
        requireNonNull(procedureIds, "procedureIds");
        requireNonNull(effectNodeIds, "effectNodeIds");
        if (procedureIds.isEmpty() || effectNodeIds.isEmpty()) {
            return List.of();
        }

        var procIdStrings = procedureIds.stream().map(UUID::toString).toList();
        var effectIdStrings = effectNodeIds.stream().map(UUID::toString).toList();

        return repositorySupport.executeSingleReadQuery(IS_PREREQUISITE_MET_BATCH, Map.of(
                PARAM_PROCEDURE_IDS, procIdStrings,
                PARAM_THRESHOLD, threshold,
                PARAM_EFFECT_NODE_IDS, effectIdStrings,
                PARAM_PHRASE_TYPE, PhraseType.EFFECT.name()
        )).stream()
                .map(r -> new PrerequisiteSatisfaction(
                        UUID.fromString(r.get(ALIAS_PROCEDURE_ID).asString()),
                        UUID.fromString(r.get(ALIAS_PREREQ_NODE_ID).asString()),
                        r.get(ALIAS_PHRASE).asString(),
                        r.get(ALIAS_IS_MET).asBoolean(false)
                ))
                .toList();
    }

    public record PrerequisiteSatisfaction(UUID procedureId, UUID prereqNodeId, String phrase, boolean isMet) {}

    /** Result of a SATISFIES edge candidate match from the vector index. */
    public record SatisfiesMatch(UUID consumerId, String effectPhrase, String prerequisitePhrase, UUID prereqNodeId, double score) {}

    private List<PhraseEmbedding> queryNodes(String cypher, Map<String, Object> params) {
        return repositorySupport.executeSingleReadQuery(cypher, params).stream()
                .map(r -> fromNode(r.get(ALIAS_PE).asNode()))
                .toList();
    }

    private static PhraseEmbedding fromNode(org.neo4j.driver.types.Node node) {
        var id = UUID.fromString(node.get(PROP_ID).asString());
        var phrase = node.get(PROP_PHRASE).asString();
        var type = PhraseType.valueOf(node.get(PROP_TYPE).asString());
        var embeddingValues = node.get(PROP_EMBEDDING).asList(Value::asFloat);
        float[] embedding = new float[embeddingValues.size()];
        for (int i = 0; i < embeddingValues.size(); i++) {
            embedding[i] = embeddingValues.get(i);
        }
        return new PhraseEmbedding(id, phrase, embedding, type);
    }

    private static List<Map<String, Object>> toNodeParams(List<PhraseEmbedding> nodes) {
        List<Map<String, Object>> params = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            var n = nodes.get(i);
            Map<String, Object> entry = new HashMap<>();
            entry.put(PROP_ID, n.id().toString());
            entry.put(PROP_PHRASE, n.phrase());
            entry.put(PROP_EMBEDDING, n.embedding());
            entry.put(PROP_SEQUENCE, i);
            params.add(entry);
        }
        return params;
    }

    /** Result of a vector similarity search on phrase embeddings. */
    public record SimilarPhraseMatch(PhraseEmbedding node, double score) {
    }
}
