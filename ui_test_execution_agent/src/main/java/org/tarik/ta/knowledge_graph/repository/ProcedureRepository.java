/*
 * ui-test-execution-agent - ${project.description}
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
import org.jspecify.annotations.NonNull;
import org.neo4j.driver.Record;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.model.node.Procedure.TimingProfile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.knowledge_graph.model.node.IEntity.PROP_ID;
import static org.tarik.ta.knowledge_graph.model.node.Embeddable.PROP_EMBEDDING;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.*;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_STABILITY_SCORE;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_AVG_LOCATION_TIME_MS;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_FAILED_LOCATION_COUNT;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.PROP_LAST_LOCATED_AT;
import static org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PROP_PHRASE;
import static org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PROP_TYPE;
import static org.tarik.ta.knowledge_graph.repository.ProcedureRepository.QueryAliases.*;
import static org.tarik.ta.knowledge_graph.repository.ProcedureRepository.QueryParams.*;

/**
 * Repository for {@link Procedure} persistence using direct Neo4j Cypher queries.
 *
 * <p>All Procedure fields are stored as flat Neo4j node properties (no JSON blob).
 * Semantic vector search uses {@code CALL db.index.vector.queryNodes(...)}, following the same
 * pattern as {@link PhraseEmbeddingRepository}. The vector index is created by
 * {@link org.tarik.ta.knowledge_graph.schema.SchemaMigrationManager}.</p>
 */
@Singleton
public class ProcedureRepository {
    private final Neo4jRepositorySupport repositorySupport;
    private final UiTestAgentConfig config;

    public ProcedureRepository(Neo4jRepositorySupport repositorySupport, UiTestAgentConfig config) {
        this.repositorySupport = repositorySupport;
        this.config = config;
        this.FIND_BY_ID = repositorySupport.cypher("MATCH (n:${LABEL_PROCEDURE} {${PROP_ID}: $id}) RETURN n");
        this.FIND_BY_ID_WITH_PHRASES = buildFindByIdWithPhrases();
        this.FIND_CHILDREN_ORDERED = repositorySupport.cypher("""
            MATCH (parent:${LABEL_PROCEDURE} {${PROP_ID}: $parentId})-[r:${REL_CONTAINS}]->(child:${LABEL_PROCEDURE})
            RETURN child AS n
            ORDER BY r.${PROP_SEQUENCE} ASC
            """);
        this.FIND_PARENTS = repositorySupport.cypher("""
            MATCH (parent:${LABEL_PROCEDURE})-[:${REL_CONTAINS}]->(child:${LABEL_PROCEDURE} {${PROP_ID}: $childId})
            RETURN parent AS n
            """);
        this.FIND_ALL_TOP_LEVEL = repositorySupport.cypher("""
            MATCH (n:${LABEL_PROCEDURE})
            WHERE NOT ()-[:${REL_CONTAINS}]->(n)
            RETURN n
            ORDER BY n.${PROP_DESCRIPTION} ASC
            """);
        this.LINK_TO_PARENT = repositorySupport.cypher("""
            MATCH (parent:${LABEL_PROCEDURE} {${PROP_ID}: $parentId})
            MATCH (child:${LABEL_PROCEDURE} {${PROP_ID}: $childId})
            MERGE (parent)-[r:${REL_CONTAINS}]->(child)
            SET r.${PROP_SEQUENCE} = $sequence
            """);
        this.SAVE_PROCEDURE = repositorySupport.cypher("""
            MERGE (n:${LABEL_PROCEDURE} {${PROP_ID}: $id})
            SET n.${PROP_DESCRIPTION} = $description,
                n.${PROP_EMBEDDING} = $embedding,
                n.${PROP_TEST_DATA} = $testData,
                n.${PROP_EXPECTED_RESULTS} = $expectedResults,
                n.${PROP_IS_ATOMIC} = $isAtomic,
                n.${PROP_IS_PRECONDITION} = $isPrecondition,
                n.${PROP_OPTIONAL} = $optional,
                n.${PROP_PREREQUISITES} = $prerequisites,
                n.${PROP_EFFECTS} = $effects,
                n.${PROP_ADDITIONAL_INFO} = $additionalInfo,
                n.${PROP_CREATED_AT} = $createdAt,
                n.${PROP_UPDATED_AT} = $updatedAt
            """);
        this.SAVE_PROCEDURE_BATCH = repositorySupport.cypher("""
            UNWIND $nodes AS node
            MERGE (n:${LABEL_PROCEDURE} {${PROP_ID}: node.${PROP_ID}})
            SET n.${PROP_DESCRIPTION} = node.${PROP_DESCRIPTION},
                n.${PROP_EMBEDDING} = node.${PROP_EMBEDDING},
                n.${PROP_TEST_DATA} = node.${PROP_TEST_DATA},
                n.${PROP_EXPECTED_RESULTS} = node.${PROP_EXPECTED_RESULTS},
                n.${PROP_IS_ATOMIC} = node.${PROP_IS_ATOMIC},
                n.${PROP_IS_PRECONDITION} = node.${PROP_IS_PRECONDITION},
                n.${PROP_OPTIONAL} = node.${PROP_OPTIONAL},
                n.${PROP_PREREQUISITES} = node.${PROP_PREREQUISITES},
                n.${PROP_EFFECTS} = node.${PROP_EFFECTS},
                n.${PROP_ADDITIONAL_INFO} = node.${PROP_ADDITIONAL_INFO},
                n.${PROP_CREATED_AT} = node.${PROP_CREATED_AT},
                n.${PROP_UPDATED_AT} = node.${PROP_UPDATED_AT}
            """);
        this.UPDATE_PROCEDURE = repositorySupport.cypher("""
            MATCH (n:${LABEL_PROCEDURE} {${PROP_ID}: $id})
            SET n.${PROP_DESCRIPTION} = $description,
                n.${PROP_EMBEDDING} = $embedding,
                n.${PROP_TEST_DATA} = $testData,
                n.${PROP_EXPECTED_RESULTS} = $expectedResults,
                n.${PROP_IS_ATOMIC} = $isAtomic,
                n.${PROP_IS_PRECONDITION} = $isPrecondition,
                n.${PROP_OPTIONAL} = $optional,
                n.${PROP_PREREQUISITES} = $prerequisites,
                n.${PROP_EFFECTS} = $effects,
                n.${PROP_ADDITIONAL_INFO} = $additionalInfo,
                n.${PROP_CREATED_AT} = $createdAt,
                n.${PROP_UPDATED_AT} = $updatedAt
            """);
        this.REMOVE_REMAINING_CONTAINS = repositorySupport.cypher("""
            MATCH (root:${LABEL_PROCEDURE} {${PROP_ID}: $rootId})-[r:${REL_CONTAINS}]->()
            DELETE r
            """);
        this.LINK_TO_UI_ELEMENT = repositorySupport.cypher("""
            MATCH (sp:${LABEL_PROCEDURE} {${PROP_ID}: $spId})
            MATCH (el:${LABEL_UI_ELEMENT} {${PROP_ID}: $elId})
            MERGE (sp)-[:${REL_TARGETS}]->(el)
            """);
        this.FIND_TARGETED_UI_ELEMENT_ID = repositorySupport.cypher("""
            MATCH (sp:${LABEL_PROCEDURE} {${PROP_ID}: $spId})-[:${REL_TARGETS}]->(el:${LABEL_UI_ELEMENT})
            RETURN el.${PROP_ID} AS elementId
            """);
        this.DELETE_TARGETS = repositorySupport.cypher("""
            MATCH (sp:${LABEL_PROCEDURE} {${PROP_ID}: $spId})-[r:${REL_TARGETS}]->()
            DELETE r
            """);
        this.FIND_SHARED_PARENT_COUNT = repositorySupport.cypher("""
            MATCH (candidate:${LABEL_PROCEDURE} {${PROP_ID}: $candidateId})<-[:${REL_CONTAINS}]-(parent:${LABEL_PROCEDURE})
            WHERE parent.${PROP_ID} IN $recentParentIds
            RETURN count(parent) AS sharedCount
            """);
        this.UPDATE_ELEMENT_STABILITY = repositorySupport.cypher("""
            MATCH (el:${LABEL_UI_ELEMENT} {${PROP_ID}: $elementId})
            SET el.${PROP_STABILITY_SCORE} = $score,
                el.${PROP_AVG_LOCATION_TIME_MS} = $avgTime,
                el.${PROP_FAILED_LOCATION_COUNT} = $failedCount,
                el.${PROP_LAST_LOCATED_AT} = $lastLocatedAt
            """);
        this.UPDATE_TIMING_PROFILE = repositorySupport.cypher("""
            MATCH (n:${LABEL_PROCEDURE} {${PROP_ID}: $id})
            SET n.${PROP_AVG_EXECUTION_MS} = $avgExecutionMs,
                n.${PROP_AVG_VERIFICATION_DELAY_MS} = $avgVerificationDelayMs,
                n.${PROP_MAX_VERIFICATION_DELAY_MS} = $maxVerificationDelayMs,
                n.${PROP_LAST_TIMING_UPDATE} = $lastTimingUpdate
            """);
        this.GET_TIMING_PROFILE = repositorySupport.cypher("""
            MATCH (n:${LABEL_PROCEDURE} {${PROP_ID}: $id})
            RETURN n.${PROP_AVG_EXECUTION_MS} AS avgExecMs,
                   n.${PROP_AVG_VERIFICATION_DELAY_MS} AS avgDelayMs,
                   n.${PROP_MAX_VERIFICATION_DELAY_MS} AS maxDelayMs,
                   n.${PROP_LAST_TIMING_UPDATE} AS lastUpdate
            """);
        this.FIND_ALL = repositorySupport.cypher("MATCH (n:${LABEL_PROCEDURE}) RETURN n");
        this.FIND_WITH_MISSING_PHRASE_NODES = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE})
            WHERE (size(p.${PROP_EFFECTS}) > 0 AND NOT (p)-[:${REL_HAS_EFFECT}]->(:${LABEL_PHRASE_EMBEDDING}))
               OR (size(p.${PROP_PREREQUISITES}) > 0 AND NOT (p)-[:${REL_HAS_PREREQUISITE}]->(:${LABEL_PHRASE_EMBEDDING}))
            RETURN p AS n
            """);
        this.FIND_WITH_PHRASE_PROPERTY_MISMATCHES = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE})
            CALL {
              WITH p
              OPTIONAL MATCH (p)-[rp:${REL_HAS_PREREQUISITE}]->(prereq:${LABEL_PHRASE_EMBEDDING})
              WITH prereq.${PROP_PHRASE} AS phrase, rp.${PROP_SEQUENCE} AS seq
              ORDER BY seq ASC
              RETURN collect(phrase) AS prereqPhrases
            }
            CALL {
              WITH p
              OPTIONAL MATCH (p)-[re:${REL_HAS_EFFECT}]->(eff:${LABEL_PHRASE_EMBEDDING})
              WITH eff.${PROP_PHRASE} AS phrase, re.${PROP_SEQUENCE} AS seq
              ORDER BY seq ASC
              RETURN collect(phrase) AS effPhrases
            }
            WITH p, prereqPhrases, effPhrases
            WHERE (size(prereqPhrases) > 0 OR size(effPhrases) > 0)
              AND (prereqPhrases <> p.${PROP_PREREQUISITES} OR effPhrases <> p.${PROP_EFFECTS})
            RETURN p AS n, prereqPhrases, effPhrases
            """);
        this.UPDATE_PHRASE_PROPERTIES = repositorySupport.cypher("""
            MATCH (n:${LABEL_PROCEDURE} {${PROP_ID}: $id})
            SET n.${PROP_PREREQUISITES} = $prerequisites, n.${PROP_EFFECTS} = $effects
            """);
        this.FIND_CANDIDATE_CONTEXT_BATCH = buildFindCandidateContextBatch();
        this.GET_ELEMENT_STABILITY = repositorySupport.cypher("MATCH (el:${LABEL_UI_ELEMENT} {${PROP_ID}: $elementId}) RETURN el");
        this.FIND_BY_SEMANTIC_SEARCH = repositorySupport.cypher("""
            CALL db.index.vector.queryNodes($indexName, $topN, $queryVector)
            YIELD node, score
            WHERE score >= $minScore
            RETURN node AS n, score
            """);
        this.DELETE_PROCEDURE = repositorySupport.cypher("""
            MATCH (n:${LABEL_PROCEDURE} {${PROP_ID}: $id})
            DETACH DELETE n
            """);
    }

    private static final Logger LOG = LoggerFactory.getLogger(ProcedureRepository.class);
    private static final String VECTOR_INDEX_NAME = "procedure_embedding_index";

    /**
     * Query result column aliases — distinct from node property names.
     */
    static final class QueryAliases {
        static final String ALIAS_PREREQUISITES = "prerequisites";
        static final String ALIAS_EFFECTS = "effects";
        static final String ALIAS_SEQ = "seq";
        static final String ALIAS_ELEMENT_ID = "elementId";
        static final String ALIAS_CANDIDATE_ID = "candidateId";
        static final String ALIAS_SHARED_COUNT = "sharedCount";
        static final String ALIAS_N = "n";
        static final String ALIAS_EL = "el";
        static final String ALIAS_SCORE = "score";
        static final String ALIAS_AVG_EXEC_MS = "avgExecMs";
        static final String ALIAS_AVG_DELAY_MS = "avgDelayMs";
        static final String ALIAS_MAX_DELAY_MS = "maxDelayMs";
        static final String ALIAS_LAST_UPDATE = "lastUpdate";
        static final String ALIAS_PHRASE_PREREQUISITES = "prereqPhrases";
        static final String ALIAS_PHRASE_EFFECTS = "effPhrases";

        private QueryAliases() {
        }
    }

    static final class QueryParams {
        static final String PARAM_INDEX_NAME = "indexName";
        static final String PARAM_TOP_N = "topN";
        static final String PARAM_QUERY_VECTOR = "queryVector";
        static final String PARAM_MIN_SCORE = "minScore";
        static final String PARAM_PARENT_ID = "parentId";
        static final String PARAM_CHILD_ID = "childId";
        static final String PARAM_SEQUENCE = "sequence";
        static final String PARAM_SP_ID = "spId";
        static final String PARAM_EL_ID = "elId";
        static final String PARAM_ROOT_ID = "rootId";
        static final String PARAM_IDS = "ids";
        static final String PARAM_CANDIDATE_ID = "candidateId";
        static final String PARAM_CANDIDATE_IDS = "candidateIds";
        static final String PARAM_RECENT_PARENT_IDS = "recentParentIds";
        static final String PARAM_NODES = "nodes";
        static final String PARAM_ELEMENT_ID = "elementId";
        static final String PARAM_SCORE = "score";
        static final String PARAM_AVG_TIME = "avgTime";
        static final String PARAM_FAILED_COUNT = "failedCount";
        static final String PARAM_LAST_LOCATED_AT = "lastLocatedAt";

        private QueryParams() {
        }
    }

    private final String FIND_BY_ID;

    private final String FIND_BY_ID_WITH_PHRASES;

    private final String FIND_CHILDREN_ORDERED;

    private final String FIND_PARENTS;

    private final String FIND_ALL_TOP_LEVEL;

    private final String LINK_TO_PARENT;

    private final String SAVE_PROCEDURE;

    private final String SAVE_PROCEDURE_BATCH;

    private final String UPDATE_PROCEDURE;

    private final String REMOVE_REMAINING_CONTAINS;

    private final String LINK_TO_UI_ELEMENT;

    private final String FIND_TARGETED_UI_ELEMENT_ID;

    private final String DELETE_TARGETS;

    private final String FIND_SHARED_PARENT_COUNT;

    private final String UPDATE_ELEMENT_STABILITY;

    private final String UPDATE_TIMING_PROFILE;

    private final String GET_TIMING_PROFILE;

    private final String FIND_ALL;

    private final String FIND_WITH_MISSING_PHRASE_NODES;

    private final String FIND_WITH_PHRASE_PROPERTY_MISMATCHES;

    private final String UPDATE_PHRASE_PROPERTIES;

    private final String FIND_CANDIDATE_CONTEXT_BATCH;

    private final String GET_ELEMENT_STABILITY;

    // No-arg vector search — index name and params supplied at call time
    private final String FIND_BY_SEMANTIC_SEARCH;

    private final String DELETE_PROCEDURE;

    /**
     * Persists a {@link Procedure} as a Neo4j node with all its fields as flat properties.
     *
     * @return the string ID used for storage
     */
    public String save(Procedure procedure, float[] embedding) {
        requireNonNull(procedure, "procedure");
        requireNonNull(embedding, "embedding");
        var id = procedure.id().toString();
        repositorySupport.executeSingleWriteQuery(SAVE_PROCEDURE, buildParams(procedure, embedding));
        LOG.info("Saved Procedure '{}' (id={}, atomic={})", procedure.description(), id, procedure.isAtomic());
        return id;
    }

    /** Transaction-aware variant of {@link #save(Procedure, float[])} — runs inside the caller's tx. */
    public void save(Procedure procedure, float[] embedding, TransactionContext tx) {
        requireNonNull(procedure, "procedure");
        requireNonNull(embedding, "embedding");
        tx.run(SAVE_PROCEDURE, buildParams(procedure, embedding)).consume();
        LOG.info("Saved Procedure '{}' (id={}, atomic={})", procedure.description(), procedure.id(), procedure.isAtomic());
    }

    /**
     * Persists multiple {@link Procedure} nodes in a single batch Cypher call.
     */
    public void saveAll(List<Procedure> procedures, List<float[]> embeddings) {
        requireNonNull(procedures, "procedures");
        requireNonNull(embeddings, "embeddings");
        var nodes = new ArrayList<Map<String, Object>>(procedures.size());
        for (int i = 0; i < procedures.size(); i++) {
            nodes.add(buildParams(procedures.get(i), embeddings.get(i)));
        }
        repositorySupport.executeSingleWriteQuery(SAVE_PROCEDURE_BATCH, Map.of(PARAM_NODES, nodes));
        LOG.info("Batch-saved {} Procedure node(s)", procedures.size());
    }

    /**
     * Saves a child procedure and creates a {@code CONTAINS} relationship from the parent
     * with the given sequence number for ordering.
     */
    public void saveWithParent(Procedure child, float[] embedding, UUID parentId, int sequence) {
        requireNonNull(child, "child");
        requireNonNull(parentId, "parentId");
        save(child, embedding);
        linkToParent(child.id(), parentId, sequence);
    }

    /**
     * Creates a {@code CONTAINS} relationship from an existing parent to an existing child
     * without saving any new node. Used when a child step references an already-persisted procedure.
     */
    public void linkToParent(UUID childId, UUID parentId, int sequence) {
        requireNonNull(childId, "childId");
        requireNonNull(parentId, "parentId");
        repositorySupport.executeSingleWriteQuery(LINK_TO_PARENT, getLinkToParentParams(childId, parentId, sequence));
        LOG.debug("Created CONTAINS relationship: parent={} -> child={} (sequence={})", parentId, childId, sequence);
    }

    private static @NonNull Map<String, Object> getLinkToParentParams(UUID childId, UUID parentId, int sequence) {
        return Map.of(
                PARAM_PARENT_ID, parentId.toString(),
                PARAM_CHILD_ID, childId.toString(),
                PARAM_SEQUENCE, sequence
        );
    }

    public void linkToParent(UUID childId, UUID parentId, int sequence, TransactionContext tx) {
        requireNonNull(childId, "childId");
        requireNonNull(parentId, "parentId");
        tx.run(LINK_TO_PARENT, getLinkToParentParams(childId, parentId, sequence)).consume();
        LOG.debug("Created CONTAINS relationship: parent={} -> child={} (sequence={})", parentId, childId, sequence);
    }

    /**
     * Finds a procedure by its UUID.
     */
    public Optional<Procedure> findById(UUID id) {
        requireNonNull(id, "id");
        var records = repositorySupport.executeSingleReadQuery(FIND_BY_ID, Map.of(PROP_ID, id.toString()));
        if (!records.isEmpty()) {
            return Optional.of(fromDbRecord(records.getFirst()));
        }
        return Optional.empty();
    }

    /**
     * Finds a procedure by its UUID along with its prerequisite and effect phrase embedding nodes.
     * When phrase nodes exist they are the authoritative source: the returned {@link Procedure}'s
     * prerequisites/effects lists are rebuilt from the ordered phrase nodes so they can never be stale.
     * Prefer {@link #findById} when phrase embeddings are not needed to avoid the extra graph traversal.
     */
    public Optional<ProcedureWithPhrases> findByIdWithPhrases(UUID id) {
        requireNonNull(id, "id");
        var records = repositorySupport.executeSingleReadQuery(FIND_BY_ID_WITH_PHRASES, Map.of(PROP_ID, id.toString()));
        if (records.isEmpty()) {
            return Optional.empty();
        }
        var record = records.getFirst();
        var procedure = fromDbRecord(record);
        var prerequisites = parsePhraseNodes(record.get(ALIAS_PREREQUISITES).asList(v -> v), PhraseType.PREREQUISITE);
        var effects = parsePhraseNodes(record.get(ALIAS_EFFECTS).asList(v -> v), PhraseType.EFFECT);
        if (!prerequisites.isEmpty() || !effects.isEmpty()) {
            var prereqPhrases = prerequisites.stream().map(PhraseEmbedding::phrase).toList();
            var effectPhrases = effects.stream().map(PhraseEmbedding::phrase).toList();
            procedure = procedure.withPhrases(prereqPhrases, effectPhrases);
        }
        return Optional.of(new ProcedureWithPhrases(procedure, prerequisites, effects));
    }

    public record ProcedureWithPhrases(Procedure procedure, List<PhraseEmbedding> prerequisites,
                                       List<PhraseEmbedding> effects) {
    }

    /**
     * Performs semantic vector search on procedure descriptions using the Neo4j vector index.
     * Returns each match paired with its cosine similarity score.
     */
    public List<ProcedureMatch> findBySemanticSearch(float[] queryVector, int topN, double minScore) {
        requireNonNull(queryVector, "queryVector");
        return repositorySupport.executeSingleReadQuery(FIND_BY_SEMANTIC_SEARCH, Map.of(
                PARAM_INDEX_NAME, VECTOR_INDEX_NAME,
                PARAM_TOP_N, topN,
                PARAM_QUERY_VECTOR, queryVector,
                PARAM_MIN_SCORE, minScore
        )).stream()
                .map(r -> new ProcedureMatch(fromDbRecord(r), r.get(ALIAS_SCORE).asDouble()))
                .toList();
    }

    /**
     * Returns the ordered children of a composite procedure via {@code CONTAINS} relationships.
     */
    public List<Procedure> findChildrenOrdered(UUID parentId) {
        requireNonNull(parentId, "parentId");
        return queryProcedures(FIND_CHILDREN_ORDERED, Map.of(PARAM_PARENT_ID, parentId.toString()));
    }

    /**
     * Returns the parent procedures of the given procedure via {@code CONTAINS} relationships.
     */
    public List<Procedure> findParents(UUID childId) {
        requireNonNull(childId, "childId");
        return queryProcedures(FIND_PARENTS, Map.of(PARAM_CHILD_ID, childId.toString()));
    }

    /**
     * Returns all top-level procedures — those not contained by any parent procedure.
     * These are the root nodes of the knowledge graph hierarchy.
     */
    public List<Procedure> findAllTopLevel() {
        return queryProcedures(FIND_ALL_TOP_LEVEL, Map.of());
    }

    /**
     * Returns all procedures from the knowledge graph.
     */
    public List<Procedure> findAll() {
        return queryProcedures(FIND_ALL, Map.of());
    }

    /**
     * Returns all procedures that have at least one prerequisite phrase defined.
     * Used to avoid loading procedures that can never satisfy a SATISFIES edge.
     */
    public List<Procedure> findAllWithPrerequisites() {
        return findAll().stream().filter(p -> !p.prerequisites().isEmpty()).toList();
    }

    /**
     * Returns procedures that have non-empty {@code effects} or {@code prerequisites} node properties
     * but are missing the corresponding {@code HAS_EFFECT} or {@code HAS_PREREQUISITE} phrase nodes.
     * Used by the startup phrase-node migration to backfill legacy procedures ingested without phrase edges.
     */
    public List<Procedure> findWithMissingPhraseNodes() {
        return queryProcedures(FIND_WITH_MISSING_PHRASE_NODES, Map.of());
    }

    /**
     * Returns procedures whose {@code prerequisites}/{@code effects} node properties differ from the ordered
     * phrase strings of their linked {@code PhraseEmbedding} nodes.
     * The phrase nodes are the authoritative source; the returned {@link ProcedureWithPhraseMismatch} carries
     * the correct lists so the caller can repair the node properties via {@link #updatePhraseProperties}.
     */
    public List<ProcedureWithPhraseMismatch> findWithPhrasePropertyMismatches() {
        return repositorySupport.executeSingleReadQuery(FIND_WITH_PHRASE_PROPERTY_MISMATCHES).stream()
                .map(r -> new ProcedureWithPhraseMismatch(
                        fromDbRecord(r),
                        r.get(ALIAS_PHRASE_PREREQUISITES).asList(Value::asString),
                        r.get(ALIAS_PHRASE_EFFECTS).asList(Value::asString)))
                .toList();
    }

    public record ProcedureWithPhraseMismatch(Procedure procedure, List<String> phrasePrerequisites,
                                              List<String> phraseEffects) {}

    /**
     * Updates only the {@code prerequisites} and {@code effects} node properties on an existing procedure,
     * leaving all other properties unchanged. Used during startup repair when phrase nodes are authoritative.
     */
    public void updatePhraseProperties(UUID procedureId, List<String> prerequisites, List<String> effects) {
        requireNonNull(procedureId, "procedureId");
        requireNonNull(prerequisites, "prerequisites");
        requireNonNull(effects, "effects");
        repositorySupport.executeSingleWriteQuery(UPDATE_PHRASE_PROPERTIES, Map.of(
                PROP_ID, procedureId.toString(),
                PROP_PREREQUISITES, prerequisites,
                PROP_EFFECTS, effects
        ));
        LOG.debug("Repaired phrase properties for procedure id={}", procedureId);
    }

    /**
     * Returns element ID, element stability, and shared-parent count for all given candidate procedure IDs
     * in a single DB round-trip — replaces N×3 individual queries in re-ranking.
     */
    public List<CandidateContext> findCandidateContextBatch(List<UUID> candidateIds, Set<UUID> recentParentIds) {
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        var ids = candidateIds.stream().map(UUID::toString).toList();
        var parentIds = recentParentIds.stream().map(UUID::toString).toList();
        return repositorySupport.executeSingleReadQuery(FIND_CANDIDATE_CONTEXT_BATCH, Map.of(
                PARAM_CANDIDATE_IDS, ids,
                PARAM_RECENT_PARENT_IDS, parentIds
        )).stream()
                .map(r -> {
                    var elementIdVal = r.get(ALIAS_ELEMENT_ID);
                    Optional<UUID> elementId = elementIdVal.isNull()
                            ? Optional.empty()
                            : Optional.of(UUID.fromString(elementIdVal.asString()));
                    Optional<ElementLocationHistory> stability = Optional.empty();
                    if (!elementIdVal.isNull() && !r.get(PROP_STABILITY_SCORE).isNull()) {
                        stability = Optional.of(new ElementLocationHistory(
                                r.get(PROP_STABILITY_SCORE).asDouble(),
                                r.get(PROP_AVG_LOCATION_TIME_MS).asLong(),
                                r.get(PROP_FAILED_LOCATION_COUNT).asDouble(),
                                Instant.parse(r.get(PROP_LAST_LOCATED_AT).asString())
                        ));
                    }
                    return new CandidateContext(
                            UUID.fromString(r.get(ALIAS_CANDIDATE_ID).asString()),
                            elementId,
                            stability,
                            r.get(ALIAS_SHARED_COUNT).asInt()
                    );
                }).toList();
    }

    public record CandidateContext(UUID candidateId, Optional<UUID> elementId, Optional<ElementLocationHistory> stability,
                                   int sharedParentCount) {
    }

    /**
     * Creates a {@code TARGETS} relationship from an atomic procedure to a UI element.
     */
    public void linkToUiElement(UUID procedureId, UUID uiElementId) {
        requireNonNull(procedureId, "procedureId");
        requireNonNull(uiElementId, "uiElementId");
        repositorySupport.executeSingleWriteQuery(LINK_TO_UI_ELEMENT, Map.of(
                PARAM_SP_ID, procedureId.toString(),
                PARAM_EL_ID, uiElementId.toString()
        ));
        LOG.debug("Created TARGETS relationship: Procedure {} -> UiElement {}", procedureId, uiElementId);
    }

    public void linkToUiElement(UUID procedureId, UUID uiElementId, TransactionContext tx) {
        requireNonNull(procedureId, "procedureId");
        requireNonNull(uiElementId, "uiElementId");
        tx.run(LINK_TO_UI_ELEMENT, Map.of(
                PARAM_SP_ID, procedureId.toString(),
                PARAM_EL_ID, uiElementId.toString()
        )).consume();
        LOG.debug("Created TARGETS relationship: Procedure {} -> UiElement {}", procedureId, uiElementId);
    }

    /**
     * Returns the UUID of the UI element targeted by an atomic procedure, if any.
     */
    public Optional<UUID> findTargetedUiElementId(UUID atomicProcedureId) {
        requireNonNull(atomicProcedureId, "atomicProcedureId");
        var records = repositorySupport.executeSingleReadQuery(FIND_TARGETED_UI_ELEMENT_ID, Map.of(PARAM_SP_ID, atomicProcedureId.toString()));
        if (!records.isEmpty()) {
            return Optional.of(UUID.fromString(records.getFirst().get(ALIAS_ELEMENT_ID).asString()));
        }
        return Optional.empty();
    }

    /**
     * Updates all properties of an existing procedure node.
     */
    public void update(Procedure procedure, float[] embedding) {
        requireNonNull(procedure, "procedure");
        requireNonNull(embedding, "embedding");
        repositorySupport.executeSingleWriteQuery(UPDATE_PROCEDURE, buildParams(procedure, embedding));
        LOG.info("Updated Procedure '{}' (id={})", procedure.description(), procedure.id());
    }

    /** Transaction-aware variant of {@link #update(Procedure, float[])} — runs inside the caller's tx. */
    public void update(Procedure procedure, float[] embedding, TransactionContext tx) {
        requireNonNull(procedure, "procedure");
        requireNonNull(embedding, "embedding");
        tx.run(UPDATE_PROCEDURE, buildParams(procedure, embedding)).consume();
        LOG.info("Updated Procedure '{}' (id={})", procedure.description(), procedure.id());
    }

    /**
     * Removes any existing {@code CONTAINS} edges from the root procedure to its children.
     * Child procedures are preserved per the requirement that they may be reused.
     */
    public void removeContainsRelationships(UUID procedureId, TransactionContext tx) {
        requireNonNull(procedureId, "procedureId");
        tx.run(REMOVE_REMAINING_CONTAINS, Map.of(PARAM_ROOT_ID, procedureId.toString())).consume();
    }

    public void deleteTargets(UUID procedureId) {
        repositorySupport.executeSingleWriteQuery(DELETE_TARGETS, Map.of(PARAM_SP_ID, procedureId.toString()));
    }

    /** Transaction-aware variant of {@link #deleteTargets(UUID)} — runs inside the caller's tx. */
    public void deleteTargets(UUID procedureId, TransactionContext tx) {
        tx.run(DELETE_TARGETS, Map.of(PARAM_SP_ID, procedureId.toString())).consume();
    }

    public void deleteProcedure(UUID id) {
        requireNonNull(id, "id");
        repositorySupport.executeSingleWriteQuery(DELETE_PROCEDURE, Map.of(PROP_ID, id.toString()));
        LOG.info("Deleted Procedure with id={}", id);
    }

    /** Transaction-aware variant of {@link #deleteProcedure(UUID)} — runs inside the caller's tx. */
    public void deleteProcedure(UUID id, TransactionContext tx) {
        requireNonNull(id, "id");
        tx.run(DELETE_PROCEDURE, Map.of(PROP_ID, id.toString())).consume();
        LOG.info("Deleted Procedure with id={}", id);
    }

    public int findSharedParentCount(UUID candidateId, Set<UUID> recentParentIds) {
        if (recentParentIds.isEmpty()) {
            return 0;
        }
        var records = repositorySupport.executeSingleReadQuery(FIND_SHARED_PARENT_COUNT, Map.of(
                PARAM_CANDIDATE_ID, candidateId.toString(),
                PARAM_RECENT_PARENT_IDS, recentParentIds.stream().map(UUID::toString).toList()
        ));
        return records.isEmpty() ? 0 : records.getFirst().get(ALIAS_SHARED_COUNT).asInt();
    }

    public Optional<ElementLocationHistory> getElementLocationHistory(UUID elementId) {
        var records = repositorySupport.executeSingleReadQuery(GET_ELEMENT_STABILITY, Map.of(PARAM_ELEMENT_ID, elementId.toString()));
        if (records.isEmpty()) {
            return Optional.empty();
        }
        var node = records.getFirst().get(ALIAS_EL).asNode();
        if (!node.containsKey(PROP_STABILITY_SCORE)) {
            return Optional.empty();
        }
        return Optional.of(new ElementLocationHistory(
                node.get(PROP_STABILITY_SCORE).asDouble(),
                node.get(PROP_AVG_LOCATION_TIME_MS).asLong(),
                node.get(PROP_FAILED_LOCATION_COUNT).asDouble(),
                Instant.parse(node.get(PROP_LAST_LOCATED_AT).asString())
        ));
    }

    private Optional<ElementLocationHistory> getElementLocationHistory(UUID elementId, TransactionContext tx) {
        var result = tx.run(GET_ELEMENT_STABILITY, Map.of(PARAM_ELEMENT_ID, elementId.toString()));
        if (result.hasNext()) {
            var node = result.next().get(ALIAS_EL).asNode();
            if (node.containsKey(PROP_STABILITY_SCORE)) {
                return Optional.of(new ElementLocationHistory(
                        node.get(PROP_STABILITY_SCORE).asDouble(),
                        node.get(PROP_AVG_LOCATION_TIME_MS).asLong(),
                        node.get(PROP_FAILED_LOCATION_COUNT).asDouble(),
                        Instant.parse(node.get(PROP_LAST_LOCATED_AT).asString())
                ));
            }
        }
        return Optional.empty();
    }

    public void updateElementStability(UUID elementId, boolean located, long locationTimeMs) {
        repositorySupport.executeComplexWriteQuery(tx -> {
            var elOpt = getElementLocationHistory(elementId, tx);
            double alpha = config.getStabilityEwmaAlpha();
            double newScore;
            long newAvgTime;
            double failedCount;
            if (elOpt.isPresent()) {
                var el = elOpt.get();
                newScore = el.stabilityScore() * (1 - alpha) + (located ? 1.0 : 0.0) * alpha;
                newAvgTime = (long) (el.avgLocationTimeMs() * (1 - alpha) + locationTimeMs * alpha);
                double rawFailedCount = located ? 0.0 : el.locationRetriesCount() + 1.0;
                failedCount = el.locationRetriesCount() * (1 - alpha) + rawFailedCount * alpha;
            } else {
                newScore = located ? 1.0 : 0.70;
                newAvgTime = locationTimeMs;
                failedCount = located ? 0.0 : 1.0;
            }
            tx.run(UPDATE_ELEMENT_STABILITY, Map.of(
                    PARAM_ELEMENT_ID, elementId.toString(),
                    PARAM_SCORE, newScore,
                    PARAM_AVG_TIME, newAvgTime,
                    PARAM_FAILED_COUNT, failedCount,
                    PARAM_LAST_LOCATED_AT, Instant.now().toString()
            ));
        });
    }

    private Optional<TimingProfile> getTimingProfile(UUID id, TransactionContext tx) {
        var result = tx.run(GET_TIMING_PROFILE, Map.of(PROP_ID, id.toString()));
        if (result.hasNext()) {
            var record = result.next();
            if (!record.get(ALIAS_AVG_EXEC_MS).isNull()) {
                return Optional.of(new TimingProfile(
                        record.get(ALIAS_AVG_EXEC_MS).asLong(),
                        record.get(ALIAS_AVG_DELAY_MS).asLong(),
                        record.get(ALIAS_MAX_DELAY_MS).asLong(),
                        Instant.parse(record.get(ALIAS_LAST_UPDATE).asString())
                ));
            }
        }
        return Optional.empty();
    }

    public void updateTimingProfile(UUID id, long actualExecutionMs, long actualVerificationDelayMs) {
        repositorySupport.executeComplexWriteQuery(tx -> {
            var profileOpt = getTimingProfile(id, tx);
            double alpha = config.getTimingEwmaAlpha();
            long newAvgExec;
            long newAvgDelay;
            long newMaxDelay;

            if (profileOpt.isPresent()) {
                var profile = profileOpt.get();
                newAvgExec = (long) (profile.avgExecutionMs() * (1 - alpha) + actualExecutionMs * alpha);
                newAvgDelay = (long) (profile.avgVerificationDelayMs() * (1 - alpha) + actualVerificationDelayMs * alpha);
                if (actualVerificationDelayMs > profile.maxVerificationDelayMs()) {
                    newMaxDelay = actualVerificationDelayMs;
                } else {
                    newMaxDelay = (long) (profile.maxVerificationDelayMs() * 0.95);
                }
            } else {
                newAvgExec = actualExecutionMs;
                newAvgDelay = actualVerificationDelayMs;
                newMaxDelay = actualVerificationDelayMs;
            }
            tx.run(UPDATE_TIMING_PROFILE, Map.of(
                    PROP_ID, id.toString(),
                    PROP_AVG_EXECUTION_MS, newAvgExec,
                    PROP_AVG_VERIFICATION_DELAY_MS, newAvgDelay,
                    PROP_MAX_VERIFICATION_DELAY_MS, newMaxDelay,
                    PROP_LAST_TIMING_UPDATE, Instant.now().toString()
            ));
        });
    }

    private static List<PhraseEmbedding> parsePhraseNodes(List<Value> rawList, PhraseType fallbackType) {
        return rawList.stream()
                .filter(v -> !v.isNull())
                .sorted(Comparator.comparingInt(v -> v.get(ALIAS_SEQ).asInt(0)))
                .map(v -> {
                    var nodeId = UUID.fromString(v.get(PROP_ID).asString());
                    var phrase = v.get(PROP_PHRASE).asString();
                    var typeVal = v.get(PROP_TYPE);
                    var type = typeVal.isNull() ? fallbackType : PhraseType.valueOf(typeVal.asString());
                    var embeddingVal = v.get(PROP_EMBEDDING);
                    float[] embedding = null;
                    if (!embeddingVal.isNull()) {
                        var floatList = embeddingVal.asList(Value::asFloat);
                        embedding = new float[floatList.size()];
                        for (int i = 0; i < floatList.size(); i++) {
                            embedding[i] = floatList.get(i);
                        }
                    }
                    return new PhraseEmbedding(nodeId, phrase, embedding, type);
                })
                .toList();
    }

    private String buildFindByIdWithPhrases() {
        return repositorySupport.cypher("""
                MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $id})
                OPTIONAL MATCH (p)-[rp:${REL_HAS_PREREQUISITE}]->(prereq:${LABEL_PHRASE_EMBEDDING})
                OPTIONAL MATCH (p)-[re:${REL_HAS_EFFECT}]->(eff:${LABEL_PHRASE_EMBEDDING})
                RETURN p AS n,
                       collect(DISTINCT CASE WHEN prereq IS NOT NULL THEN
                           {${PROP_ID}: prereq.${PROP_ID}, ${PROP_PHRASE}: prereq.${PROP_PHRASE},
                            ${PROP_EMBEDDING}: prereq.${PROP_EMBEDDING}, ${PROP_TYPE}: prereq.${PROP_TYPE},
                            seq: rp.${PROP_SEQUENCE}}
                       END) AS prerequisites,
                       collect(DISTINCT CASE WHEN eff IS NOT NULL THEN
                           {${PROP_ID}: eff.${PROP_ID}, ${PROP_PHRASE}: eff.${PROP_PHRASE},
                            ${PROP_EMBEDDING}: eff.${PROP_EMBEDDING}, ${PROP_TYPE}: eff.${PROP_TYPE},
                            seq: re.${PROP_SEQUENCE}}
                       END) AS effects
                """);
    }

    private String buildFindCandidateContextBatch() {
        return repositorySupport.cypher("""
                UNWIND $candidateIds AS cId
                MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: cId})
                OPTIONAL MATCH (p)-[:${REL_TARGETS}]->(el:${LABEL_UI_ELEMENT})
                OPTIONAL MATCH (parent:${LABEL_PROCEDURE})-[:${REL_CONTAINS}]->(p) WHERE parent.${PROP_ID} IN $recentParentIds
                RETURN cId AS candidateId,
                       el.${PROP_ID} AS elementId,
                       el.${PROP_STABILITY_SCORE} AS ${PROP_STABILITY_SCORE},
                       el.${PROP_AVG_LOCATION_TIME_MS} AS ${PROP_AVG_LOCATION_TIME_MS},
                       el.${PROP_FAILED_LOCATION_COUNT} AS ${PROP_FAILED_LOCATION_COUNT},
                       el.${PROP_LAST_LOCATED_AT} AS ${PROP_LAST_LOCATED_AT},
                       count(DISTINCT parent) AS sharedCount
                """);
    }

    private List<Procedure> queryProcedures(String cypher, Map<String, Object> params) {
        return repositorySupport.executeSingleReadQuery(cypher, params).stream()
                .map(this::fromDbRecord)
                .toList();
    }

    private Procedure fromDbRecord(Record record) {
        var node = record.get(ALIAS_N).asNode();
        var id = UUID.fromString(node.get(PROP_ID).asString());
        var description = node.get(PROP_DESCRIPTION).asString();
        var testData = node.containsKey(PROP_TEST_DATA) ? node.get(PROP_TEST_DATA).asList(Value::asString) : List.<String>of();
        var expectedResults = node.containsKey(PROP_EXPECTED_RESULTS) ? node.get(PROP_EXPECTED_RESULTS).asString() : "";
        var isAtomic = node.get(PROP_IS_ATOMIC).asBoolean();
        var isPrecondition = node.containsKey(PROP_IS_PRECONDITION) && node.get(PROP_IS_PRECONDITION).asBoolean();
        var optional = node.containsKey(PROP_OPTIONAL) && node.get(PROP_OPTIONAL).asBoolean();
        var prerequisites = node.containsKey(PROP_PREREQUISITES) ? node.get(PROP_PREREQUISITES).asList(Value::asString) : List.<String>of();
        var effects = node.containsKey(PROP_EFFECTS) ? node.get(PROP_EFFECTS).asList(Value::asString) : List.<String>of();
        var additionalInfoValue = node.get(PROP_ADDITIONAL_INFO);
        var additionalInfo = (!additionalInfoValue.isNull() && !additionalInfoValue.asString().isBlank())
                ? additionalInfoValue.asString() : null;
        var createdAt = node.containsKey(PROP_CREATED_AT) ? Instant.parse(node.get(PROP_CREATED_AT).asString()) : Instant.now();
        var updatedAt = node.containsKey(PROP_UPDATED_AT) ? Instant.parse(node.get(PROP_UPDATED_AT).asString()) : Instant.now();

        float[] embedding = null;
        var embeddingValue = node.get(PROP_EMBEDDING);
        if (!embeddingValue.isNull()) {
            var floatList = embeddingValue.asList(Value::asFloat);
            embedding = new float[floatList.size()];
            for (int i = 0; i < floatList.size(); i++) {
                embedding[i] = floatList.get(i);
            }
        }

        TimingProfile timing = null;
        if (node.containsKey(PROP_AVG_EXECUTION_MS) && !node.get(PROP_AVG_EXECUTION_MS).isNull()) {
            timing = new TimingProfile(
                    node.get(PROP_AVG_EXECUTION_MS).asLong(),
                    node.get(PROP_AVG_VERIFICATION_DELAY_MS).asLong(),
                    node.get(PROP_MAX_VERIFICATION_DELAY_MS).asLong(),
                    Instant.parse(node.get(PROP_LAST_TIMING_UPDATE).asString())
            );
        }

        return new Procedure(id, description, testData, expectedResults, isAtomic, isPrecondition, optional,
                prerequisites, effects, additionalInfo, createdAt, updatedAt, embedding, timing);
    }

    private static Map<String, Object> buildParams(Procedure procedure, float[] embedding) {
        var params = new HashMap<String, Object>();
        params.put(PROP_ID, procedure.id().toString());
        params.put(PROP_DESCRIPTION, procedure.description());
        params.put(PROP_EMBEDDING, embedding);
        params.put(PROP_TEST_DATA, procedure.testData());
        params.put(PROP_EXPECTED_RESULTS, procedure.expectedResults());
        params.put(PROP_IS_ATOMIC, procedure.isAtomic());
        params.put(PROP_IS_PRECONDITION, procedure.isPrecondition());
        params.put(PROP_OPTIONAL, procedure.optional());
        params.put(PROP_PREREQUISITES, procedure.prerequisites());
        params.put(PROP_EFFECTS, procedure.effects());
        params.put(PROP_ADDITIONAL_INFO, procedure.additionalInfo());
        params.put(PROP_CREATED_AT, procedure.createdAt().toString());
        params.put(PROP_UPDATED_AT, procedure.updatedAt().toString());
        return params;
    }
}
