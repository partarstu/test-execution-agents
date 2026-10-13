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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.dto.panel.knowledge.ElementPage.Sort;
import org.tarik.ta.dto.panel.knowledge.ElementTile;
import org.tarik.ta.dto.panel.knowledge.FailureHistory.Hint;
import org.tarik.ta.dto.panel.knowledge.GraphEdge;
import org.tarik.ta.dto.panel.knowledge.GraphNode;
import org.tarik.ta.dto.panel.knowledge.GraphNode.Flag;
import org.tarik.ta.dto.panel.knowledge.KnowledgeSummary;
import org.tarik.ta.dto.panel.knowledge.KnowledgeSummary.Count;
import org.tarik.ta.dto.panel.knowledge.ProcedureDetails.Anomaly;
import org.tarik.ta.dto.panel.knowledge.ProcedureDetails.ElementRef;
import org.tarik.ta.dto.panel.knowledge.ProcedureRef;
import org.tarik.ta.dto.panel.knowledge.ResolutionView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Locale.ROOT;
import static org.tarik.ta.dto.panel.knowledge.GraphNode.TEST_CASE_ID_PREFIX;
import static org.tarik.ta.knowledge_graph.model.node.IEntity.PROP_ID;

/** Read-only queries behind the panel's *Knowledge* tab; every query binds its parameters and bounds its rows. */
@Singleton
public class KnowledgeViewRepository {
    private static final String NODE_COLUMNS = """
            CASE WHEN n:${LABEL_TEST_CASE} THEN $testCasePrefix + n.${PROP_NAME} ELSE n.${PROP_ID} END AS id,
            CASE WHEN n:${LABEL_TEST_CASE} THEN 'TEST_CASE' WHEN n:${LABEL_UI_ELEMENT} THEN 'ELEMENT'
                 WHEN n.${PROP_IS_ATOMIC} THEN 'ATOMIC' ELSE 'COMPOSITE' END AS kind,
            coalesce(n.${PROP_DESCRIPTION}, n.${PROP_NAME}, n.${PROP_ID}) AS label,
            n:${LABEL_PROCEDURE} AND n.${PROP_IS_ATOMIC} AND NOT EXISTS { (n)-[:${REL_TARGETS}]->() } AS noTarget,
            n:${LABEL_PROCEDURE} AND n.${PROP_IS_ATOMIC} AND NOT EXISTS { (n)-[:${REL_HAS_EFFECT}]->() } AS noEffects,
            n:${LABEL_UI_ELEMENT} AND coalesce(n.${PROP_STABILITY_SCORE}, 1.0) < $stabilityThreshold AS lowStability,
            EXISTS { MATCH (a:${LABEL_EXECUTION_ANOMALY})-[:${REL_OBSERVED_ON}]->(n)
                     WHERE datetime(a.${PROP_OCCURRED_AT}) >= datetime() - duration({days: $windowDays}) } AS recentAnomaly,
            CASE WHEN n:${LABEL_PROCEDURE} THEN COUNT { MATCH (n)<-[:${REL_CONTAINS}*0..]-(:${LABEL_PROCEDURE})
                                                              <-[:${REL_USES_PROCEDURE}]-(tc:${LABEL_TEST_CASE}) RETURN DISTINCT tc }
                 WHEN n:${LABEL_UI_ELEMENT} THEN COUNT { MATCH (n)<-[:${REL_TARGETS}]-(:${LABEL_PROCEDURE})
                                                              <-[:${REL_CONTAINS}*0..]-(:${LABEL_PROCEDURE})
                                                              <-[:${REL_USES_PROCEDURE}]-(tc:${LABEL_TEST_CASE}) RETURN DISTINCT tc }
                 ELSE 0 END AS testCases
            """;
    private static final String ELEMENT_COLUMNS = """
            el.${PROP_ID} AS id, el.${PROP_NAME} AS name, el.${PROP_STABILITY_SCORE} AS stability,
            el.${PROP_AVG_LOCATION_TIME_MS} AS avgLocationTimeMs, el.${PROP_FAILED_LOCATION_COUNT} AS locationRetries,
            coalesce(el.${PROP_IS_DATA_DEPENDENT}, false) AS dataDependent, el.${PROP_UPDATED_AT} AS updatedAt,
            el.${PROP_LAST_LOCATED_AT} AS lastLocatedAt, el.${PROP_SCREENSHOT_IMAGE} IS NOT NULL AS hasScreenshot
            """;
    private static final String ELEMENT_PAGE_TEMPLATE = """
            MATCH (el:${LABEL_UI_ELEMENT})
            WITH el ORDER BY %s, el.${PROP_NAME} ASC
            SKIP $offset LIMIT $limit
            RETURN
            """;
    static final String PARAM_QUERY = "query";
    static final String PARAM_LIMIT = "limit";
    static final String PARAM_OFFSET = "offset";
    static final String PARAM_IDS = "ids";
    static final String PARAM_TEST_CASE_NAMES = "testCaseNames";
    static final String PARAM_NAME = "name";
    static final String PARAM_MAX_DEPTH = "maxDepth";
    static final String PARAM_SINCE = "since";
    static final String PARAM_SINCE_MILLIS = "sinceMillis";
    static final String PARAM_ANOMALY_LIMIT = "anomalyLimit";
    static final String PARAM_TEST_CASE_PREFIX = "testCasePrefix";
    private static final int MAX_ANOMALIES = 10;
    private static final int MAX_TARGETING_PROCEDURES = 50;
    private static final int MAX_TREND_ROWS = 2000;

    private final Neo4jRepositorySupport repositorySupport;
    private final UiTestAgentConfig config;
    private final String summary;
    private final String search;
    private final String neighbourhood;
    private final String testCaseLens;
    private final String edges;
    private final String procedureExtras;
    private final Map<Sort, String> elementPages;
    private final String elementExtras;
    private final String overview;
    private final String trends;
    private final String failureHints;
    private final String resolutionsChoosing;
    private final String latestRunPath;

    public KnowledgeViewRepository(Neo4jRepositorySupport repositorySupport, UiTestAgentConfig config) {
        this.repositorySupport = repositorySupport;
        this.config = config;
        this.summary = repositorySupport.cypher("""
                CALL () { MATCH (n) UNWIND labels(n) AS name WITH name, count(*) AS count ORDER BY name
                          RETURN collect({name: name, count: count}) AS nodes }
                CALL () { MATCH ()-[r]->() WITH type(r) AS name, count(*) AS count ORDER BY name
                          RETURN collect({name: name, count: count}) AS relationships }
                CALL () { OPTIONAL MATCH (version:${LABEL_SCHEMA_VERSION}) RETURN max(version.version) AS schemaVersion }
                CALL () { MATCH (p:${LABEL_PROCEDURE} {${PROP_IS_ATOMIC}: true})
                          RETURN count(p) AS atomic,
                                 count(CASE WHEN EXISTS { (p)-[:${REL_TARGETS}]->() } THEN 1 END) AS atomicWithTarget,
                                 count(CASE WHEN EXISTS { (p)-[:${REL_HAS_EFFECT}]->() } THEN 1 END) AS atomicWithEffects }
                RETURN nodes, relationships, coalesce(schemaVersion, 0) AS schemaVersion, atomic, atomicWithTarget, atomicWithEffects
                LIMIT 1
                """);
        this.search = repositorySupport.cypher("""
                CALL () {
                    MATCH (n:${LABEL_PROCEDURE}) WHERE toLower(n.${PROP_DESCRIPTION}) CONTAINS $query RETURN n
                    UNION
                    MATCH (n:${LABEL_UI_ELEMENT}) WHERE toLower(n.${PROP_NAME}) CONTAINS $query RETURN n
                    UNION
                    MATCH (n:${LABEL_TEST_CASE}) WHERE toLower(n.${PROP_NAME}) CONTAINS $query RETURN n
                }
                WITH n ORDER BY size(coalesce(n.${PROP_DESCRIPTION}, n.${PROP_NAME})) ASC
                LIMIT $limit
                RETURN
                """ + NODE_COLUMNS);
        // The ancestors come first, so a capped slice keeps the chain to the roots
        this.neighbourhood = repositorySupport.cypher("""
                MATCH (focus:${LABEL_PROCEDURE} {${PROP_ID}: $id})
                CALL (focus) {
                    MATCH (ancestor:${LABEL_PROCEDURE})-[:${REL_CONTAINS}*0..]->(focus)
                    RETURN collect(DISTINCT ancestor) AS ancestors
                }
                CALL (focus) {
                    MATCH path = (focus)-[:${REL_CONTAINS}*0..]->(descendant:${LABEL_PROCEDURE})
                    WHERE length(path) <= $maxDepth
                    RETURN collect(DISTINCT descendant) AS subtree
                }
                WITH ancestors + [d IN subtree WHERE NOT d IN ancestors] AS core
                OPTIONAL CALL (core) {
                    UNWIND core AS p
                    MATCH (p)-[:${REL_SATISFIES}]-(n:${LABEL_PROCEDURE}) RETURN n
                    UNION
                    UNWIND core AS p
                    MATCH (p)-[:${REL_TARGETS}]->(n:${LABEL_UI_ELEMENT}) RETURN n
                    UNION
                    UNWIND core AS p
                    MATCH (p)<-[:${REL_USES_PROCEDURE}]-(n:${LABEL_TEST_CASE}) RETURN n
                }
                WITH core, collect(DISTINCT n) AS around
                UNWIND core + [x IN around WHERE NOT x IN core] AS member
                WITH DISTINCT member AS n
                LIMIT $limit
                RETURN
                """ + NODE_COLUMNS);
        this.testCaseLens = repositorySupport.cypher("""
                MATCH (tc:${LABEL_TEST_CASE} {${PROP_NAME}: $name})
                OPTIONAL CALL (tc) {
                    MATCH path = (tc)-[:${REL_USES_PROCEDURE}]->(:${LABEL_PROCEDURE})-[:${REL_CONTAINS}*0..]->(n:${LABEL_PROCEDURE})
                    WHERE length(path) <= $maxDepth + 1
                    RETURN DISTINCT n
                }
                WITH tc, collect(n) AS procedures
                UNWIND [tc] + procedures AS member
                WITH DISTINCT member AS n
                LIMIT $limit
                RETURN
                """ + NODE_COLUMNS);
        this.edges = repositorySupport.cypher("""
                CALL () {
                    MATCH (source:${LABEL_PROCEDURE})-[r:${REL_CONTAINS}|${REL_SATISFIES}]->(target:${LABEL_PROCEDURE})
                    WHERE source.${PROP_ID} IN $ids AND target.${PROP_ID} IN $ids
                    RETURN source.${PROP_ID} AS source, target.${PROP_ID} AS target, type(r) AS kind, r.${PROP_SEQUENCE} AS sequence,
                           r.${PROP_SCORE} AS score, r.${PROP_EFFECT_PHRASE} AS effectPhrase,
                           r.${PROP_PREREQUISITE_PHRASE} AS prerequisitePhrase
                    UNION ALL
                    MATCH (source:${LABEL_PROCEDURE})-[r:${REL_TARGETS}]->(target:${LABEL_UI_ELEMENT})
                    WHERE source.${PROP_ID} IN $ids AND target.${PROP_ID} IN $ids
                    RETURN source.${PROP_ID} AS source, target.${PROP_ID} AS target, type(r) AS kind, null AS sequence, null AS score,
                           null AS effectPhrase, null AS prerequisitePhrase
                    UNION ALL
                    MATCH (testCase:${LABEL_TEST_CASE})-[r:${REL_USES_PROCEDURE}]->(target:${LABEL_PROCEDURE})
                    WHERE testCase.${PROP_NAME} IN $testCaseNames AND target.${PROP_ID} IN $ids
                    RETURN $testCasePrefix + testCase.${PROP_NAME} AS source, target.${PROP_ID} AS target, type(r) AS kind,
                           null AS sequence, null AS score, null AS effectPhrase, null AS prerequisitePhrase
                }
                RETURN source, target, kind, sequence, score, effectPhrase, prerequisitePhrase
                LIMIT $limit
                """);
        this.procedureExtras = repositorySupport.cypher("""
                MATCH (p:${LABEL_PROCEDURE} {${PROP_ID}: $id})
                OPTIONAL MATCH (p)-[:${REL_TARGETS}]->(element:${LABEL_UI_ELEMENT})
                RETURN coalesce(p.${PROP_SUCCESS_COUNT}, 0) AS successCount, element.${PROP_ID} AS elementId,
                       element.${PROP_NAME} AS elementName,
                       COLLECT { MATCH (a:${LABEL_EXECUTION_ANOMALY})-[:${REL_OBSERVED_ON}]->(p)
                                 WITH a ORDER BY datetime(a.${PROP_OCCURRED_AT}) DESC LIMIT $anomalyLimit
                                 RETURN a { .${PROP_KIND}, .${PROP_OCCURRED_AT}, .${PROP_TEST_CASE_NAME}, .${PROP_DETAILS} } } AS anomalies
                LIMIT 1
                """);
        // A sort needs its own ORDER BY, which Cypher cannot take as a parameter; the variants come from the enum, never from input
        this.elementPages = Map.of(
                Sort.STABILITY, elementPage("coalesce(el.${PROP_STABILITY_SCORE}, 1.0) ASC"),
                Sort.UPDATED, elementPage("el.${PROP_UPDATED_AT} IS NULL ASC, datetime(el.${PROP_UPDATED_AT}) DESC"),
                Sort.RETRIES, elementPage("coalesce(el.${PROP_FAILED_LOCATION_COUNT}, 0) DESC"));
        this.elementExtras = repositorySupport.cypher("""
                MATCH (el:${LABEL_UI_ELEMENT} {${PROP_ID}: $id})
                RETURN %s,
                       COLLECT { MATCH (p:${LABEL_PROCEDURE})-[:${REL_TARGETS}]->(el)
                                 WITH p ORDER BY p.${PROP_DESCRIPTION} ASC LIMIT $limit
                                 RETURN p { .${PROP_ID}, .${PROP_DESCRIPTION} } } AS targetedBy
                LIMIT 1
                """.formatted(ELEMENT_COLUMNS));
        this.overview = repositorySupport.cypher("""
                CALL () {
                    MATCH (n:${LABEL_PROCEDURE}) RETURN n
                    UNION
                    MATCH (n:${LABEL_UI_ELEMENT}) RETURN n
                }
                WITH n
                LIMIT $limit
                RETURN
                """ + NODE_COLUMNS);
        // Procedures and anomalies store ISO-8601 strings, runs epoch milliseconds; both are bucketed in UTC
        this.trends = repositorySupport.cypher("""
                CALL () {
                    MATCH (p:${LABEL_PROCEDURE}) WHERE p.${PROP_CREATED_AT} IS NOT NULL
                    WITH date(datetime(p.${PROP_CREATED_AT})) AS day WHERE day >= $since
                    RETURN 'PROCEDURES' AS series, date.truncate('week', day) AS week, null AS key, count(*) AS count
                    UNION ALL
                    MATCH (a:${LABEL_EXECUTION_ANOMALY})
                    WITH a, date(datetime(a.${PROP_OCCURRED_AT})) AS day WHERE day >= $since
                    RETURN 'ANOMALIES' AS series, date.truncate('week', day) AS week, a.${PROP_KIND} AS key, count(*) AS count
                    UNION ALL
                    MATCH (run:${LABEL_EXECUTION_RUN})-[:${REL_HAS_RESOLUTION}]->(s:${LABEL_STEP_RESOLUTION})
                    WHERE run.${PROP_STARTED_AT} >= $sinceMillis
                    RETURN 'RESOLUTIONS' AS series, date.truncate('week', date(datetime({epochMillis: run.${PROP_STARTED_AT}}))) AS week,
                           s.${PROP_SOURCE} AS key, count(*) AS count
                    UNION ALL
                    MATCH (run:${LABEL_EXECUTION_RUN})-[:${REL_HAS_RESOLUTION}]->(s:${LABEL_STEP_RESOLUTION})
                    WHERE run.${PROP_STARTED_AT} >= $sinceMillis
                    RETURN 'OUTCOMES' AS series, date.truncate('week', date(datetime({epochMillis: run.${PROP_STARTED_AT}}))) AS week,
                           s.${PROP_OUTCOME} AS key, count(*) AS count
                }
                RETURN series, week, key, count
                LIMIT $limit
                """);
        this.failureHints = repositorySupport.cypher("""
                MATCH (:${LABEL_PROCEDURE} {${PROP_ID}: $id})-[:${REL_HAS_FAILURE_CONTEXT}]->(fc:${LABEL_FAILURE_CONTEXT})
                RETURN fc.${PROP_ID} AS id, fc.${PROP_CATEGORY} AS category, fc.${PROP_SYMPTOM} AS symptom,
                       coalesce(fc.${PROP_RESOLUTION}, '') AS resolution, coalesce(fc.${PROP_OCCURRENCES}, 0) AS occurrences,
                       fc.${PROP_LAST_OCCURRED} AS lastOccurred, fc.${PROP_EXPIRED_AT} AS expiredAt, fc.${PROP_MODE} AS mode
                ORDER BY fc.${PROP_EXPIRED_AT} IS NOT NULL ASC, coalesce(fc.${PROP_EXPIRED_AT}, fc.${PROP_LAST_OCCURRED}) DESC
                LIMIT $limit
                """);
        this.resolutionsChoosing = repositorySupport.cypher("""
                MATCH (run:${LABEL_EXECUTION_RUN})-[:${REL_HAS_RESOLUTION}]->(resolution:${LABEL_STEP_RESOLUTION})
                      -[:${REL_CHOSE}]->(:${LABEL_PROCEDURE} {${PROP_ID}: $id})
                RETURN run.${PROP_ID} AS runId, run.${PROP_TEST_CASE_NAME} AS testCaseName, run.${PROP_STARTED_AT} AS startedAt, resolution
                ORDER BY run.${PROP_STARTED_AT} DESC, resolution.${PROP_SEQUENCE} ASC
                LIMIT $limit
                """);
        this.latestRunPath = repositorySupport.cypher("""
                MATCH (:${LABEL_TEST_CASE} {${PROP_NAME}: $name})-[:${REL_HAS_RUN}]->(run:${LABEL_EXECUTION_RUN})
                WITH run ORDER BY run.${PROP_STARTED_AT} DESC LIMIT 1
                MATCH (run)-[:${REL_HAS_RESOLUTION}]->(resolution:${LABEL_STEP_RESOLUTION})-[:${REL_CHOSE}]->(chosen:${LABEL_PROCEDURE})
                RETURN run.${PROP_ID} AS runId, chosen.${PROP_ID} AS procedureId
                ORDER BY resolution.${PROP_SEQUENCE} ASC
                LIMIT $limit
                """);
    }

    public KnowledgeSummary summary() {
        var row = repositorySupport.executeSingleReadQuery(summary).getFirst();
        return new KnowledgeSummary(counts(row.get("nodes")), counts(row.get("relationships")), row.get("schemaVersion").asInt(),
                row.get("atomic").asLong(), row.get("atomicWithTarget").asLong(), row.get("atomicWithEffects").asLong());
    }

    /** Returns the procedures, UI elements and test cases whose text contains the query, ignoring case, the shortest first. */
    public List<GraphNode> search(@NotNull String query, int limit) {
        return nodes(search, Map.of(PARAM_QUERY, query.toLowerCase(ROOT), PARAM_LIMIT, limit));
    }

    /** Returns the procedure's ancestors, its subtree and what they link to; empty when the procedure does not exist. */
    public List<GraphNode> neighbourhood(@NotNull UUID procedureId, int limit) {
        return nodes(neighbourhood, Map.of(PROP_ID, procedureId.toString(), PARAM_MAX_DEPTH, config.getKnowledgeMaxDepth(),
                PARAM_LIMIT, limit));
    }

    /** Returns the test case and the procedures it uses with their subtrees; empty when the test case does not exist. */
    public List<GraphNode> testCaseLens(@NotNull String testCaseName, int limit) {
        return nodes(testCaseLens, Map.of(PARAM_NAME, testCaseName, PARAM_MAX_DEPTH, config.getKnowledgeMaxDepth(), PARAM_LIMIT, limit));
    }

    public List<GraphNode> overview(int limit) {
        return nodes(overview, Map.of(PARAM_LIMIT, limit));
    }

    /** Returns the {@code CONTAINS}, {@code SATISFIES}, {@code TARGETS} and {@code USES_PROCEDURE} edges between the nodes. */
    public List<GraphEdge> edgesBetween(@NotNull Collection<GraphNode> nodes, int limit) {
        var ids = nodes.stream().filter(node -> node.kind() != GraphNode.Kind.TEST_CASE).map(GraphNode::id).toList();
        var testCaseNames = nodes.stream()
                .filter(node -> node.kind() == GraphNode.Kind.TEST_CASE)
                .map(node -> node.id().substring(TEST_CASE_ID_PREFIX.length()))
                .toList();
        var params = Map.<String, Object>of(PARAM_IDS, ids, PARAM_TEST_CASE_NAMES, testCaseNames,
                PARAM_TEST_CASE_PREFIX, TEST_CASE_ID_PREFIX, PARAM_LIMIT, limit);
        return repositorySupport.executeSingleReadQuery(edges, params).stream().map(KnowledgeViewRepository::edgeOf).toList();
    }

    public Optional<ProcedureExtras> procedureExtras(@NotNull UUID procedureId) {
        var rows = repositorySupport.executeSingleReadQuery(procedureExtras,
                Map.of(PROP_ID, procedureId.toString(), PARAM_ANOMALY_LIMIT, MAX_ANOMALIES));
        return rows.stream().findFirst().map(row -> {
            var elementId = row.get("elementId");
            var target = elementId.isNull() ? null
                    : new ElementRef(UUID.fromString(elementId.asString()), row.get("elementName").asString(""));
            var anomalies = row.get("anomalies").asList(KnowledgeViewRepository::anomalyOf);
            return new ProcedureExtras(row.get("successCount").asLong(), target, anomalies);
        });
    }

    public List<ElementTile> elements(@NotNull Sort sort, int offset, int limit) {
        return repositorySupport.executeSingleReadQuery(elementPages.get(sort), Map.of(PARAM_OFFSET, offset, PARAM_LIMIT, limit)).stream()
                .map(KnowledgeViewRepository::elementTileOf)
                .toList();
    }

    public Optional<ElementExtras> elementExtras(@NotNull UUID elementId) {
        var rows = repositorySupport.executeSingleReadQuery(elementExtras,
                Map.of(PROP_ID, elementId.toString(), PARAM_LIMIT, MAX_TARGETING_PROCEDURES));
        return rows.stream().findFirst().map(row -> new ElementExtras(elementTileOf(row), row.get("targetedBy").asList(
                value -> new ProcedureRef(UUID.fromString(value.get(PROP_ID).asString()), value.get("description").asString("")))));
    }

    /** Returns the weekly counts since the given UTC day: procedures and SATISFIES edges created, anomalies, resolutions, outcomes. */
    public List<TrendRow> trendRows(@NotNull LocalDate since, @NotNull Instant sinceInstant) {
        var params = Map.<String, Object>of(PARAM_SINCE, since, PARAM_SINCE_MILLIS, sinceInstant.toEpochMilli(),
                PARAM_LIMIT, MAX_TREND_ROWS);
        return repositorySupport.executeSingleReadQuery(trends, params).stream()
                .map(row -> new TrendRow(row.get("series").asString(), row.get("week").asLocalDate(), row.get("key").asString(null),
                        row.get("count").asLong()))
                .toList();
    }

    /** Returns the procedure's failure hints, the current ones first, then the most recently expired. */
    public List<Hint> failureHints(@NotNull UUID procedureId, int limit) {
        return repositorySupport.executeSingleReadQuery(failureHints, Map.of(PROP_ID, procedureId.toString(), PARAM_LIMIT, limit)).stream()
                .map(row -> new Hint(UUID.fromString(row.get(PROP_ID).asString()), row.get("category").asString(""),
                        row.get("symptom").asString(""), row.get("resolution").asString(), row.get("occurrences").asInt(),
                        epochMillis(row.get("lastOccurred")), nullableEpochMillis(row.get("expiredAt")), row.get("mode").asString("")))
                .toList();
    }

    /** Returns the newest step resolutions that chose the procedure. */
    public List<ResolutionView> resolutionsChoosing(@NotNull UUID procedureId, int limit) {
        var chosenId = procedureId.toString();
        return repositorySupport.executeSingleReadQuery(resolutionsChoosing, Map.of(PROP_ID, chosenId, PARAM_LIMIT, limit)).stream()
                .map(row -> new ResolutionView(UUID.fromString(row.get("runId").asString()), row.get("testCaseName").asString(""),
                        epochMillis(row.get("startedAt")),
                        ExecutionRunRepository.resolutionFromNode(row.get("resolution").asNode(), chosenId)))
                .toList();
    }

    /** Returns the procedures the latest run of the test case chose, in the order of its items; empty without such a run. */
    public Optional<LatestRun> latestRunPath(@NotNull String testCaseName, int limit) {
        var rows = repositorySupport.executeSingleReadQuery(latestRunPath, Map.of(PARAM_NAME, testCaseName, PARAM_LIMIT, limit));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var path = rows.stream().map(row -> UUID.fromString(row.get("procedureId").asString())).toList();
        return Optional.of(new LatestRun(UUID.fromString(rows.getFirst().get("runId").asString()), path));
    }

    private String elementPage(String order) {
        return repositorySupport.cypher(ELEMENT_PAGE_TEMPLATE.formatted(order) + ELEMENT_COLUMNS);
    }

    private List<GraphNode> nodes(String query, Map<String, Object> params) {
        var withFlagParameters = new HashMap<String, Object>(params);
        withFlagParameters.put(PARAM_TEST_CASE_PREFIX, TEST_CASE_ID_PREFIX);
        withFlagParameters.put("stabilityThreshold", config.getStabilityPenaltyThreshold());
        withFlagParameters.put("windowDays", config.getHealthRecentWindowDays());
        return repositorySupport.executeSingleReadQuery(query, withFlagParameters).stream().map(KnowledgeViewRepository::nodeOf).toList();
    }

    private static GraphNode nodeOf(Record row) {
        var flags = EnumSet.noneOf(Flag.class);
        addIf(flags, Flag.NO_TARGET, row.get("noTarget"));
        addIf(flags, Flag.NO_EFFECTS, row.get("noEffects"));
        addIf(flags, Flag.LOW_STABILITY, row.get("lowStability"));
        addIf(flags, Flag.RECENT_ANOMALY, row.get("recentAnomaly"));
        return new GraphNode(row.get(PROP_ID).asString(), GraphNode.Kind.valueOf(row.get("kind").asString()), row.get("label").asString(""),
                flags, row.get("testCases").asInt(0));
    }

    private static void addIf(EnumSet<Flag> flags, Flag flag, Value value) {
        if (value.asBoolean(false)) {
            flags.add(flag);
        }
    }

    private static GraphEdge edgeOf(Record row) {
        var sequence = row.get("sequence");
        var score = row.get("score");
        return new GraphEdge(row.get("source").asString(), row.get("target").asString(), GraphEdge.Kind.valueOf(row.get("kind").asString()),
                sequence.isNull() ? null : sequence.asInt(), score.isNull() ? null : score.asDouble(),
                row.get("effectPhrase").asString(null), row.get("prerequisitePhrase").asString(null));
    }

    private static ElementTile elementTileOf(Record row) {
        var stability = row.get("stability");
        var avgLocationTime = row.get("avgLocationTimeMs");
        var retries = row.get("locationRetries");
        return new ElementTile(UUID.fromString(row.get(PROP_ID).asString()), row.get("name").asString(""),
                stability.isNull() ? null : stability.asDouble(), avgLocationTime.isNull() ? null : avgLocationTime.asLong(),
                retries.isNull() ? null : retries.asDouble(), row.get("dataDependent").asBoolean(), nullableInstant(row.get("updatedAt")),
                nullableInstant(row.get("lastLocatedAt")), row.get("hasScreenshot").asBoolean());
    }

    private static Anomaly anomalyOf(Value value) {
        return new Anomaly(value.get("kind").asString(""), Instant.parse(value.get("occurredAt").asString()),
                value.get("testCaseName").asString(null), value.get("details").asString(""));
    }

    private static List<Count> counts(Value value) {
        return value.asList(entry -> new Count(entry.get("name").asString(), entry.get("count").asLong()));
    }

    private static @Nullable Instant nullableInstant(Value value) {
        return value.isNull() ? null : Instant.parse(value.asString());
    }

    private static Instant epochMillis(Value value) {
        return Instant.ofEpochMilli(value.asLong());
    }

    private static @Nullable Instant nullableEpochMillis(Value value) {
        return value.isNull() ? null : epochMillis(value);
    }

    public record ProcedureExtras(long successCount, @Nullable ElementRef target, List<Anomaly> recentAnomalies) {
    }

    public record ElementExtras(ElementTile tile, List<ProcedureRef> targetedBy) {
    }

    /** One weekly count; {@code key} names the anomaly kind, the resolution source or the outcome, and is null otherwise. */
    public record TrendRow(String series, LocalDate week, @Nullable String key, long count) {
    }

    public record LatestRun(UUID runId, List<UUID> procedureIds) {
    }
}
