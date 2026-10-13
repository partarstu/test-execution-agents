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

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.tarik.ta.knowledge_graph.health.HealthFinding;
import org.tarik.ta.knowledge_graph.health.HealthFinding.NodeRef;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.Kind;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;

import static org.tarik.ta.knowledge_graph.repository.GraphHealthRepository.QueryAliases.*;

/**
 * Read-only health-check queries for the knowledge graph.
 *
 * <p>Runs independent queries with no cross-repository dependencies.
 * All methods return findings as human-readable text together with the ids of the nodes they name.</p>
 */
@Singleton
public class GraphHealthRepository {
    public GraphHealthRepository(Neo4jRepositorySupport repositorySupport) {
        this.repositorySupport = repositorySupport;
        this.FIND_ORPHANED_UI_ELEMENTS = repositorySupport.cypher("""
            MATCH (el:${LABEL_UI_ELEMENT})
            WHERE NOT ()-[:${REL_TARGETS}]->(el)
            RETURN el.${PROP_ID} AS id, coalesce(el.${PROP_NAME}, el.${PROP_ID}) AS label
            ORDER BY label ASC
            """);
        this.FIND_LEAF_PROCEDURES_WITHOUT_ELEMENT = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_IS_ATOMIC}: true})
            WHERE NOT (p)-[:${REL_TARGETS}]->()
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description
            ORDER BY description ASC
            """);
        this.FIND_DEEP_HIERARCHIES = repositorySupport.cypher("""
            MATCH path = (root:${LABEL_PROCEDURE})-[:${REL_CONTAINS}*1..]->(leaf:${LABEL_PROCEDURE})
            WHERE NOT ()-[:${REL_CONTAINS}]->(root)
            WITH root, max(length(path)) AS depth
            WHERE depth > $maxDepth
            RETURN root.${PROP_ID} AS id, root.${PROP_DESCRIPTION} AS description, depth
            ORDER BY depth DESC
            """);
        this.FIND_UNUSED_PROCEDURES = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE})
            WHERE NOT EXISTS {
                MATCH (:${LABEL_TEST_CASE})-[:${REL_USES_PROCEDURE}]->(:${LABEL_PROCEDURE})-[:${REL_CONTAINS}*0..]->(p)
            }
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description
            ORDER BY description ASC
            """);
        this.FIND_PROCEDURES_WITH_MISSING_EFFECTS = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_IS_ATOMIC}: true})
            WHERE NOT (p)-[:${REL_HAS_EFFECT}]->()
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description
            ORDER BY description ASC
            """);
        this.FIND_ORPHANED_PHRASE_EMBEDDINGS = repositorySupport.cypher("""
            MATCH (pe:${LABEL_PHRASE_EMBEDDING})
            WHERE NOT ()-[:${REL_HAS_PREREQUISITE}|${REL_HAS_EFFECT}|${REL_HAS_NEGATED_EFFECT}]->(pe)
            RETURN pe.${PROP_ID} AS id, coalesce(pe.${PROP_PHRASE}, pe.${PROP_ID}) AS label
            ORDER BY label ASC
            """);
        this.FIND_STALE_SATISFIES_EDGES = repositorySupport.cypher("""
            MATCH (producer:${LABEL_PROCEDURE})-[r:${REL_SATISFIES}]->(consumer:${LABEL_PROCEDURE})
            WHERE r.${PROP_LAST_VERIFIED_AT} < timestamp() - $staleMs
            RETURN producer.${PROP_ID} AS producerId, producer.${PROP_DESCRIPTION} AS producerDesc,
                   consumer.${PROP_ID} AS consumerId, consumer.${PROP_DESCRIPTION} AS consumerDesc,
                   r.${PROP_SCORE} AS score
            """);
        this.FIND_ORPHANED_FAILURE_CONTEXTS = repositorySupport.cypher("""
            MATCH (fc:${LABEL_FAILURE_CONTEXT})
            WHERE NOT ()-[:${REL_HAS_FAILURE_CONTEXT}]->(fc)
            RETURN fc.${PROP_ID} AS id, coalesce(fc.${PROP_SYMPTOM}, fc.${PROP_ID}) AS label
            ORDER BY label ASC
            """);
        this.FIND_VOLATILE_UI_ELEMENTS = repositorySupport.cypher("""
            MATCH (el:${LABEL_UI_ELEMENT})-[:${REL_HAD_APPEARANCE}]->(ap:${LABEL_UI_ELEMENT_APPEARANCE})
            WHERE datetime(ap.${PROP_VALID_TO}) >= datetime() - duration({days: $windowDays})
            WITH el, count(ap) AS changes
            WHERE changes >= $minChanges
            RETURN el.${PROP_ID} AS id, coalesce(el.${PROP_NAME}, el.${PROP_ID}) AS label, changes
            ORDER BY changes DESC, label ASC
            """);
        this.FIND_UNRESOLVED_FAILURE_HINTS = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE})-[:${REL_HAS_FAILURE_CONTEXT}]->(fc:${LABEL_FAILURE_CONTEXT})
            WHERE fc.${PROP_EXPIRED_AT} IS NULL AND trim(coalesce(fc.${PROP_RESOLUTION}, '')) = ''
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description, fc.${PROP_CATEGORY} AS category,
                   fc.${PROP_SYMPTOM} AS symptom, fc.${PROP_OCCURRENCES} AS occurrences
            ORDER BY occurrences DESC, description ASC
            """);
        this.FIND_RECENT_EXECUTION_ANOMALIES = repositorySupport.cypher("""
            MATCH (a:${LABEL_EXECUTION_ANOMALY})-[:${REL_OBSERVED_ON}]->(s)
            WHERE datetime(a.${PROP_OCCURRED_AT}) >= datetime() - duration({days: $windowDays})
            WITH s, a ORDER BY datetime(a.${PROP_OCCURRED_AT}) DESC
            WITH s, collect(a) AS anomalies
            RETURN labels(s)[0] AS subjectLabel, s.${PROP_ID} AS id, coalesce(s.${PROP_NAME}, s.${PROP_DESCRIPTION}) AS label,
                   [kind IN $kinds | size([x IN anomalies WHERE x.${PROP_KIND} = kind])] AS countsByKind,
                   anomalies[0].${PROP_TEST_CASE_NAME} AS lastTestCase
            ORDER BY size(anomalies) DESC, label ASC
            """);
        // A test case may use any ancestor of an atomic procedure, so the test cases are counted over CONTAINS*0.. ancestors
        this.FIND_LOW_STABILITY_ELEMENTS_BY_BLAST_RADIUS = repositorySupport.cypher("""
            MATCH (atomic:${LABEL_PROCEDURE})-[:${REL_TARGETS}]->(el:${LABEL_UI_ELEMENT})
            WHERE el.${PROP_STABILITY_SCORE} < $threshold
            MATCH (ancestor:${LABEL_PROCEDURE})-[:${REL_CONTAINS}*0..]->(atomic)
            OPTIONAL MATCH (tc:${LABEL_TEST_CASE})-[:${REL_USES_PROCEDURE}]->(ancestor)
            WITH el, count(DISTINCT atomic) AS procedures, count(DISTINCT tc) AS testCases
            RETURN el.${PROP_ID} AS id, coalesce(el.${PROP_NAME}, el.${PROP_ID}) AS label, el.${PROP_STABILITY_SCORE} AS stability,
                   procedures, testCases
            ORDER BY testCases DESC, procedures DESC, label ASC
            """);
        this.FIND_FAILING_PROCEDURES_BY_BLAST_RADIUS = repositorySupport.cypher("""
            MATCH (a:${LABEL_EXECUTION_ANOMALY} {${PROP_KIND}: $kind})-[:${REL_OBSERVED_ON}]->(p:${LABEL_PROCEDURE})
            WHERE datetime(a.${PROP_OCCURRED_AT}) >= datetime() - duration({days: $windowDays})
            WITH p, count(a) AS failures
            MATCH (ancestor:${LABEL_PROCEDURE})-[:${REL_CONTAINS}*0..]->(p)
            OPTIONAL MATCH (tc:${LABEL_TEST_CASE})-[:${REL_USES_PROCEDURE}]->(ancestor)
            WITH p, failures, count(DISTINCT tc) AS testCases
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description, failures, testCases
            ORDER BY testCases DESC, failures DESC, description ASC
            """);
        // Timestamps are ISO-8601 strings with a variable number of fraction digits, so they are compared as datetime()
        this.FIND_PROCEDURES_WITHOUT_RECENT_SUCCESS = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_IS_ATOMIC}: true})
            WITH p, datetime() - duration({days: $windowDays}) AS windowStart
            WHERE datetime(p.${PROP_CREATED_AT}) < windowStart
              AND (p.${PROP_LAST_TIMING_UPDATE} IS NULL OR datetime(p.${PROP_LAST_TIMING_UPDATE}) < windowStart)
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description, coalesce(p.${PROP_SUCCESS_COUNT}, 0) AS successes,
                   p.${PROP_LAST_TIMING_UPDATE} AS lastSuccess
            ORDER BY successes ASC, description ASC
            """);
        // Scores every effect directly instead of via the vector index for the crowd-out reason of IS_PREREQUISITE_MET_BATCH
        this.FIND_UNSATISFIABLE_PREREQUISITES = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE})-[:${REL_HAS_PREREQUISITE}]->(pre:${LABEL_PHRASE_EMBEDDING})
            WHERE pre.${PROP_EMBEDDING} IS NOT NULL
            OPTIONAL CALL (p, pre) {
                MATCH (other:${LABEL_PROCEDURE})-[:${REL_HAS_EFFECT}]->(eff:${LABEL_PHRASE_EMBEDDING})
                WHERE other <> p AND eff.${PROP_EMBEDDING} IS NOT NULL
                RETURN eff.${PROP_PHRASE} AS bestEffect, vector.similarity.cosine(pre.${PROP_EMBEDDING}, eff.${PROP_EMBEDDING}) AS score
                ORDER BY score DESC
                LIMIT 1
            }
            WITH p, pre, bestEffect, score
            WHERE score IS NULL OR score < $threshold
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description, pre.${PROP_PHRASE} AS prerequisite, bestEffect, score
            ORDER BY description ASC, prerequisite ASC
            """);
        this.FIND_DUPLICATE_ATOMIC_PROCEDURES = repositorySupport.cypher("""
            MATCH (p:${LABEL_PROCEDURE} {${PROP_IS_ATOMIC}: true})-[:${REL_TARGETS}]->(el:${LABEL_UI_ELEMENT})
                  <-[:${REL_TARGETS}]-(other:${LABEL_PROCEDURE} {${PROP_IS_ATOMIC}: true})
            WHERE p.${PROP_ID} < other.${PROP_ID} AND p.${PROP_EMBEDDING} IS NOT NULL AND other.${PROP_EMBEDDING} IS NOT NULL
            WITH p, other, el, vector.similarity.cosine(p.${PROP_EMBEDDING}, other.${PROP_EMBEDDING}) AS score
            WHERE score >= $threshold
            RETURN p.${PROP_ID} AS id, p.${PROP_DESCRIPTION} AS description, other.${PROP_ID} AS otherId,
                   other.${PROP_DESCRIPTION} AS otherDescription, coalesce(el.${PROP_NAME}, el.${PROP_ID}) AS label, score
            ORDER BY score DESC, label ASC
            """);
        this.DELETE_STALE_SATISFIES_EDGES = repositorySupport.cypher("""
            MATCH ()-[r:${REL_SATISFIES}]->()
            WHERE r.${PROP_LAST_VERIFIED_AT} < timestamp() - $staleMs
            DELETE r
            RETURN count(r) AS deletedCount
            """);
        this.DELETE_ORPHANED_EXECUTION_ANOMALIES = repositorySupport.cypher("""
            MATCH (a:${LABEL_EXECUTION_ANOMALY})
            WHERE NOT (a)-[:${REL_OBSERVED_ON}]->()
            DELETE a
            RETURN count(a) AS deletedCount
            """);
    }

    private final Neo4jRepositorySupport repositorySupport;

    private static final Logger LOG = LoggerFactory.getLogger(GraphHealthRepository.class);

    private static final long MS_PER_DAY = 86_400_000L;

    static final class QueryAliases {
        static final String ALIAS_ID = "id";
        static final String ALIAS_LABEL = "label";
        static final String ALIAS_DESCRIPTION = "description";
        static final String ALIAS_DEPTH = "depth";
        static final String ALIAS_PRODUCER_ID = "producerId";
        static final String ALIAS_CONSUMER_ID = "consumerId";
        static final String ALIAS_PRODUCER_DESC = "producerDesc";
        static final String ALIAS_CONSUMER_DESC = "consumerDesc";
        static final String ALIAS_SCORE = "score";
        static final String ALIAS_DELETED_COUNT = "deletedCount";
        static final String ALIAS_CHANGES = "changes";
        static final String ALIAS_SUBJECT_LABEL = "subjectLabel";
        static final String ALIAS_COUNTS_BY_KIND = "countsByKind";
        static final String ALIAS_LAST_TEST_CASE = "lastTestCase";
        static final String ALIAS_CATEGORY = "category";
        static final String ALIAS_SYMPTOM = "symptom";
        static final String ALIAS_OCCURRENCES = "occurrences";
        static final String ALIAS_STABILITY = "stability";
        static final String ALIAS_PROCEDURES = "procedures";
        static final String ALIAS_TEST_CASES = "testCases";
        static final String ALIAS_FAILURES = "failures";
        static final String ALIAS_SUCCESSES = "successes";
        static final String ALIAS_LAST_SUCCESS = "lastSuccess";
        static final String ALIAS_PREREQUISITE = "prerequisite";
        static final String ALIAS_BEST_EFFECT = "bestEffect";
        static final String ALIAS_OTHER_ID = "otherId";
        static final String ALIAS_OTHER_DESCRIPTION = "otherDescription";

        private QueryAliases() {}
    }

    private final String FIND_ORPHANED_UI_ELEMENTS;

    private final String FIND_LEAF_PROCEDURES_WITHOUT_ELEMENT;

    private final String FIND_DEEP_HIERARCHIES;

    private final String FIND_UNUSED_PROCEDURES;

    private final String FIND_PROCEDURES_WITH_MISSING_EFFECTS;

    private final String FIND_ORPHANED_PHRASE_EMBEDDINGS;

    private final String FIND_STALE_SATISFIES_EDGES;

    private final String FIND_ORPHANED_FAILURE_CONTEXTS;

    private final String DELETE_STALE_SATISFIES_EDGES;

    private final String DELETE_ORPHANED_EXECUTION_ANOMALIES;

    private final String FIND_VOLATILE_UI_ELEMENTS;

    private final String FIND_UNRESOLVED_FAILURE_HINTS;

    private final String FIND_RECENT_EXECUTION_ANOMALIES;

    private final String FIND_LOW_STABILITY_ELEMENTS_BY_BLAST_RADIUS;

    private final String FIND_FAILING_PROCEDURES_BY_BLAST_RADIUS;

    private final String FIND_PROCEDURES_WITHOUT_RECENT_SUCCESS;

    private final String FIND_UNSATISFIABLE_PREREQUISITES;

    private final String FIND_DUPLICATE_ATOMIC_PROCEDURES;

    private static final int VOLATILE_ELEMENT_MIN_CHANGES = 2;

    public List<HealthFinding> findOrphanedUiElements() {
        return repositorySupport.executeSingleReadQuery(FIND_ORPHANED_UI_ELEMENTS).stream()
                .map(r -> finding("UiElement[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_LABEL).asString()),
                        ref(UiElement.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findLeafProceduresWithoutElement() {
        return repositorySupport.executeSingleReadQuery(FIND_LEAF_PROCEDURES_WITHOUT_ELEMENT).stream()
                .map(r -> finding("Procedure[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString()),
                        ref(Procedure.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findDeepHierarchies(int maxDepth) {
        return repositorySupport.executeSingleReadQuery(FIND_DEEP_HIERARCHIES, Map.of("maxDepth", maxDepth))
                .stream()
                .map(r -> finding("Procedure[%s] '%s': depth=%d".formatted(
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString(), r.get(ALIAS_DEPTH).asInt()),
                        ref(Procedure.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findUnusedProcedures() {
        return repositorySupport.executeSingleReadQuery(FIND_UNUSED_PROCEDURES).stream()
                .map(r -> finding("Procedure[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString()),
                        ref(Procedure.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findProceduresWithMissingEffects() {
        return repositorySupport.executeSingleReadQuery(FIND_PROCEDURES_WITH_MISSING_EFFECTS).stream()
                .map(r -> finding("Procedure[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString()),
                        ref(Procedure.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findOrphanedPhraseEmbeddings() {
        return repositorySupport.executeSingleReadQuery(FIND_ORPHANED_PHRASE_EMBEDDINGS).stream()
                .map(r -> finding("PhraseEmbedding[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_LABEL).asString()),
                        ref(PhraseEmbedding.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findStaleSatisfiesEdges(int staleDays) {
        return repositorySupport.executeSingleReadQuery(FIND_STALE_SATISFIES_EDGES, Map.of("staleMs", (long) staleDays * MS_PER_DAY))
                .stream()
                .map(r -> finding("SATISFIES: '%s' -> '%s' (score=%.3f, stale)".formatted(
                        r.get(ALIAS_PRODUCER_DESC).asString(), r.get(ALIAS_CONSUMER_DESC).asString(), r.get(ALIAS_SCORE).asDouble()),
                        ref(Procedure.LABEL, r, ALIAS_PRODUCER_ID), ref(Procedure.LABEL, r, ALIAS_CONSUMER_ID)))
                .toList();
    }

    public List<HealthFinding> findOrphanedFailureContexts() {
        return repositorySupport.executeSingleReadQuery(FIND_ORPHANED_FAILURE_CONTEXTS).stream()
                .map(r -> finding("FailureContext[%s]: %s".formatted(r.get(ALIAS_ID).asString(), r.get(ALIAS_LABEL).asString()),
                        ref(FailureContext.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findVolatileUiElements(int windowDays) {
        return repositorySupport.executeSingleReadQuery(FIND_VOLATILE_UI_ELEMENTS,
                        Map.of("windowDays", windowDays, "minChanges", VOLATILE_ELEMENT_MIN_CHANGES))
                .stream()
                .map(r -> finding("UiElement[%s] '%s': %d appearance changes in %d days".formatted(
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_LABEL).asString(), r.get(ALIAS_CHANGES).asInt(), windowDays),
                        ref(UiElement.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findUnresolvedFailureHints() {
        return repositorySupport.executeSingleReadQuery(FIND_UNRESOLVED_FAILURE_HINTS).stream()
                .map(r -> finding("Procedure[%s] '%s': [%s] %s (seen %d times, no known resolution)".formatted(
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString(), r.get(ALIAS_CATEGORY).asString(),
                        r.get(ALIAS_SYMPTOM).asString(), r.get(ALIAS_OCCURRENCES).asInt()),
                        ref(Procedure.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findRecentExecutionAnomalies(int windowDays) {
        var kinds = Arrays.stream(Kind.values()).map(Kind::name).toList();
        return repositorySupport.executeSingleReadQuery(FIND_RECENT_EXECUTION_ANOMALIES, Map.of("windowDays", windowDays, "kinds", kinds))
                .stream()
                .map(r -> finding("%s[%s] '%s': %s, last test case '%s'".formatted(r.get(ALIAS_SUBJECT_LABEL).asString(),
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_LABEL).asString(""),
                        describeCounts(kinds, r.get(ALIAS_COUNTS_BY_KIND).asList(Value::asInt)),
                        r.get(ALIAS_LAST_TEST_CASE).asString("unknown")),
                        ref(r.get(ALIAS_SUBJECT_LABEL).asString(), r, ALIAS_ID)))
                .toList();
    }

    private static HealthFinding finding(String text, NodeRef... refs) {
        return new HealthFinding(text, List.of(refs));
    }

    private static NodeRef ref(String label, Record record, String idAlias) {
        return new NodeRef(label, UUID.fromString(record.get(idAlias).asString()));
    }

    private static String describeCounts(List<String> kinds, List<Integer> counts) {
        return IntStream.range(0, kinds.size())
                .filter(i -> counts.get(i) > 0)
                .mapToObj(i -> "%s=%d".formatted(kinds.get(i), counts.get(i)))
                .collect(joining(", "));
    }

    public List<HealthFinding> findLowStabilityElementsByBlastRadius(double threshold) {
        return repositorySupport.executeSingleReadQuery(FIND_LOW_STABILITY_ELEMENTS_BY_BLAST_RADIUS, Map.of("threshold", threshold))
                .stream()
                .map(r -> finding(format(ROOT, "UiElement[%s] '%s': stability=%.2f, atomic procedures=%d, test cases=%d",
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_LABEL).asString(), r.get(ALIAS_STABILITY).asDouble(),
                        r.get(ALIAS_PROCEDURES).asInt(), r.get(ALIAS_TEST_CASES).asInt()),
                        ref(UiElement.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findFailingProceduresByBlastRadius(int windowDays) {
        return repositorySupport.executeSingleReadQuery(FIND_FAILING_PROCEDURES_BY_BLAST_RADIUS,
                        Map.of("kind", Kind.STEP_FAILED.name(), "windowDays", windowDays))
                .stream()
                .map(r -> finding("Procedure[%s] '%s': %d failures in %d days, test cases=%d".formatted(
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString(), r.get(ALIAS_FAILURES).asInt(), windowDays,
                        r.get(ALIAS_TEST_CASES).asInt()),
                        ref(Procedure.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findProceduresWithoutRecentSuccess(int windowDays) {
        return repositorySupport.executeSingleReadQuery(FIND_PROCEDURES_WITHOUT_RECENT_SUCCESS, Map.of("windowDays", windowDays))
                .stream()
                .map(r -> finding("Procedure[%s] '%s': successes=%d, last success=%s".formatted(
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString(), r.get(ALIAS_SUCCESSES).asInt(),
                        r.get(ALIAS_LAST_SUCCESS).asString("never")),
                        ref(Procedure.LABEL, r, ALIAS_ID)))
                .toList();
    }

    public List<HealthFinding> findUnsatisfiablePrerequisites(double threshold) {
        return repositorySupport.executeSingleReadQuery(FIND_UNSATISFIABLE_PREREQUISITES, Map.of("threshold", threshold))
                .stream()
                .map(r -> finding("Procedure[%s] '%s': prerequisite '%s' (%s)".formatted(
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString(), r.get(ALIAS_PREREQUISITE).asString(),
                        describeBestEffect(r)),
                        ref(Procedure.LABEL, r, ALIAS_ID)))
                .toList();
    }

    private static String describeBestEffect(Record record) {
        var score = record.get(ALIAS_SCORE);
        return score.isNull()
                ? "no effect in the graph"
                : format(ROOT, "best effect %.2f '%s'", score.asDouble(), record.get(ALIAS_BEST_EFFECT).asString());
    }

    public List<HealthFinding> findDuplicateAtomicProcedures(double threshold) {
        return repositorySupport.executeSingleReadQuery(FIND_DUPLICATE_ATOMIC_PROCEDURES, Map.of("threshold", threshold))
                .stream()
                .map(r -> finding(format(ROOT, "Procedure[%s] '%s' ~ Procedure[%s] '%s' on UiElement '%s' (similarity=%.2f)",
                        r.get(ALIAS_ID).asString(), r.get(ALIAS_DESCRIPTION).asString(), r.get(ALIAS_OTHER_ID).asString(),
                        r.get(ALIAS_OTHER_DESCRIPTION).asString(), r.get(ALIAS_LABEL).asString(), r.get(ALIAS_SCORE).asDouble()),
                        ref(Procedure.LABEL, r, ALIAS_ID), ref(Procedure.LABEL, r, ALIAS_OTHER_ID)))
                .toList();
    }

    public int deleteStaleSatisfiesEdges(int staleDays) {
        var records = repositorySupport.executeSingleWriteQuery(DELETE_STALE_SATISFIES_EDGES, Map.of("staleMs", (long) staleDays * MS_PER_DAY));
        int deleted = records.isEmpty() ? 0 : records.getFirst().get(ALIAS_DELETED_COUNT).asInt();
        LOG.debug("Deleted {} stale SATISFIES edges older than {} days", deleted, staleDays);
        return deleted;
    }

    /** Deletes the execution anomalies whose UI element or procedure no longer exists and returns their count. */
    public int deleteOrphanedExecutionAnomalies() {
        var records = repositorySupport.executeSingleWriteQuery(DELETE_ORPHANED_EXECUTION_ANOMALIES);
        return records.isEmpty() ? 0 : records.getFirst().get(ALIAS_DELETED_COUNT).asInt();
    }

}
