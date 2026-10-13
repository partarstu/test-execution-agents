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
package org.tarik.ta.knowledge_graph.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly;
import org.tarik.ta.knowledge_graph.model.node.ExecutionRun;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.tarik.ta.knowledge_graph.model.GraphRelationships.*;
import static org.tarik.ta.knowledge_graph.repository.Neo4jRepositorySupport.CYPHER_VERSION_PREFIX;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.SchemaVersion;
import org.tarik.ta.knowledge_graph.model.node.TestCase;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

/**
 * Idempotent, versioned schema and data migrations of the Neo4j graph, applied in version order on startup. Every migration and
 * its version is listed in {@link #MIGRATIONS}.
 */
public class SchemaMigrationManager {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaMigrationManager.class);

    // Applied in this order, each followed by its version update, so that the stored version only ever grows
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "Create standard index on Procedure.isAtomic",
                    "CREATE INDEX procedure_is_atomic IF NOT EXISTS FOR (n:%s) ON (n.isAtomic)"
                            .formatted(Procedure.LABEL)),
            new Migration(2, "Create index on SATISFIES.lastVerifiedAt",
                    "CREATE INDEX satisfies_last_verified_at IF NOT EXISTS FOR ()-[r:%s]-() ON (r.lastVerifiedAt)"
                            .formatted(REL_SATISFIES)),
            new Migration(3, "Create constraint on TestCase.name",
                    "CREATE CONSTRAINT test_case_name_unique IF NOT EXISTS FOR (n:%s) REQUIRE n.name IS UNIQUE"
                            .formatted(TestCase.LABEL)),
            new Migration(4, "Create vector index on PhraseEmbedding.embedding",
                    "CREATE VECTOR INDEX %s IF NOT EXISTS FOR (n:%s) ON n.embedding OPTIONS {indexConfig: {`vector.dimensions`: 384, `vector.similarity_function`: 'cosine'}}"
                            .formatted(PhraseEmbedding.VECTOR_INDEX, PhraseEmbedding.LABEL)),
            new Migration(5, "Create index on PhraseEmbedding.phrase for exact lookups",
                    "CREATE INDEX phrase_embedding_phrase_index IF NOT EXISTS FOR (n:%s) ON (n.phrase)"
                            .formatted(PhraseEmbedding.LABEL)),
            new Migration(6, "Extract PhraseEmbedding nodes from legacy Procedure JSON", SchemaMigrationManager::migratePhraseEmbeddingNodes),
            new Migration(7, "Create vector index on Procedure.embedding",
                    "CREATE VECTOR INDEX %s IF NOT EXISTS FOR (n:%s) ON n.embedding OPTIONS {indexConfig: {`vector.dimensions`: 384, `vector.similarity_function`: 'cosine'}}"
                            .formatted(Procedure.VECTOR_INDEX, Procedure.LABEL)),
            new Migration(8, "Create vector index on UiElement.embedding",
                    "CREATE VECTOR INDEX %s IF NOT EXISTS FOR (n:%s) ON n.embedding OPTIONS {indexConfig: {`vector.dimensions`: 384, `vector.similarity_function`: 'cosine'}}"
                            .formatted(UiElement.VECTOR_INDEX, UiElement.LABEL)),
            new Migration(9, "Flatten Procedure properties and normalise UiElement property names", (driver, databaseName) -> {
                migrateProcedureToFlatProperties(driver, databaseName);
                migrateUiElementPropertyNames(driver, databaseName);
            }),
            new Migration(10, "Create index on ExecutionAnomaly.subjectId",
                    "CREATE INDEX execution_anomaly_subject_id IF NOT EXISTS FOR (n:%s) ON (n.%s)"
                            .formatted(ExecutionAnomaly.LABEL, ExecutionAnomaly.PROP_SUBJECT_ID)),
            new Migration(11, "Create index on ExecutionRun.startedAt",
                    "CREATE INDEX execution_run_started_at IF NOT EXISTS FOR (n:%s) ON (n.%s)"
                            .formatted(ExecutionRun.LABEL, ExecutionRun.PROP_STARTED_AT))
    );

    private SchemaMigrationManager() {
        // utility class
    }

    /**
     * Runs all pending migrations in order. Safe to call multiple times (idempotent).
     */
    public static void migrateOnStartup(org.neo4j.driver.Driver driver, String databaseName) {
        LOG.info("Starting Neo4j schema migration check");
        int currentVersion = readCurrentVersion(driver, databaseName);
        LOG.info("Current schema version: {}", currentVersion);

        var pendingMigrations = MIGRATIONS.stream()
                .filter(m -> m.version() > currentVersion)
                .toList();

        for (var migration : pendingMigrations) {
            LOG.info("Applying migration v{}: {}", migration.version(), migration.description());
            migration.apply().accept(driver, databaseName);
            updateVersion(driver, databaseName, migration.version());
            LOG.info("Migration v{} applied successfully", migration.version());
        }

        LOG.info("Schema is now at version {}", pendingMigrations.isEmpty() ? currentVersion : pendingMigrations.getLast().version());
    }

    private static org.neo4j.driver.ExecutableQuery executableQuery(org.neo4j.driver.Driver driver, String databaseName, String cypher) {
        return driver.executableQuery(CYPHER_VERSION_PREFIX + cypher).withConfig(org.neo4j.driver.QueryConfig.builder().withDatabase(databaseName).build());
    }

    /**
     * Reads Procedure nodes that still have the legacy {@code data} JSON property and writes
     * all fields as individual flat Neo4j properties, then removes the {@code data} property.
     */
    private static void migrateProcedureToFlatProperties(org.neo4j.driver.Driver driver, String databaseName) {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var records = executableQuery(driver, databaseName, """
                MATCH (p:Procedure) WHERE p.data IS NOT NULL
                RETURN p.id AS id, p.data AS data
                """).execute().records();

        LOG.info("Data migration v9: found {} Procedure node(s) with legacy JSON data property", records.size());
        int migrated = 0;
        for (var record : records) {
            var idVal = record.get("id");
            var dataVal = record.get("data");
            if (idVal.isNull() || dataVal.isNull()) {
                continue;
            }
            try {
                var data = objectMapper.readValue(dataVal.asString(), LegacyProcedureFullData.class);
                var prerequisites = extractPhraseTexts(data.prerequisites());
                var effects = extractPhraseTexts(data.effects());

                var params = new HashMap<String, Object>();
                params.put("id", idVal.asString());
                params.put("description", data.description() != null ? data.description() : "");
                params.put("testData", data.testData() != null ? data.testData() : List.of());
                params.put("expectedResults", data.expectedResults() != null ? data.expectedResults() : "");
                params.put("isAtomic", data.isAtomic());
                params.put("isPrecondition", data.isPrecondition());
                params.put("prerequisites", prerequisites);
                params.put("effects", effects);
                params.put("createdAt", data.createdAt() != null ? data.createdAt().toString() : Instant.now().toString());
                params.put("updatedAt", data.updatedAt() != null ? data.updatedAt().toString() : Instant.now().toString());

                executableQuery(driver, databaseName, """
                        MATCH (p:Procedure {id: $id})
                        SET p.description = $description,
                            p.testData = $testData,
                            p.expectedResults = $expectedResults,
                            p.isAtomic = $isAtomic,
                            p.isPrecondition = $isPrecondition,
                            p.prerequisites = $prerequisites,
                            p.effects = $effects,
                            p.createdAt = $createdAt,
                            p.updatedAt = $updatedAt
                        REMOVE p.data
                        """).withParameters(params).execute();
                migrated++;
            } catch (Exception e) {
                LOG.warn("Data migration v9: failed to migrate Procedure id={}: {}", idVal.asString(), e.getMessage());
            }
        }
        LOG.info("Data migration v9: migrated {} Procedure node(s) to flat properties", migrated);
    }

    /**
     * Renames legacy uppercase UiElement metadata properties (stored by the old Neo4jEmbeddingStore)
     * to camelCase property names, and removes the redundant {@code text} and uppercase {@code ID} properties.
     */
    private static void migrateUiElementPropertyNames(org.neo4j.driver.Driver driver, String databaseName) {
        executableQuery(driver, databaseName, """
                MATCH (el:UiElement) WHERE el.NAME IS NOT NULL
                SET el.name = el.NAME,
                    el.ownDescription = el.OWN_DESCRIPTION,
                    el.anchorsDescription = el.ANCHORS_DESCRIPTION,
                    el.parentElementSummary = el.PAGE_SUMMARY,
                    el.screenshotFileExtension = el.SCREENSHOT_FILE_EXTENSION,
                    el.screenshotMimeType = el.SCREENSHOT_MIME_TYPE,
                    el.screenshotImage = el.SCREENSHOT_IMAGE,
                    el.isDataDependent = (el.IS_DATA_DEPENDENT = 'true')
                REMOVE el.NAME, el.OWN_DESCRIPTION, el.ANCHORS_DESCRIPTION, el.PAGE_SUMMARY,
                       el.SCREENSHOT_FILE_EXTENSION, el.SCREENSHOT_MIME_TYPE, el.SCREENSHOT_IMAGE,
                       el.IS_DATA_DEPENDENT, el.ID, el.text
                """).execute();
        LOG.info("Data migration v9: UiElement property names normalised to camelCase");
    }

    /**
     * Reads all Procedure nodes that do not yet have HAS_PREREQUISITE or HAS_EFFECT relationships,
     * extracts phrase+embedding pairs from their legacy JSON data, and creates PhraseEmbedding nodes.
     */
    private static void migratePhraseEmbeddingNodes(org.neo4j.driver.Driver driver, String databaseName) {
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        var records = executableQuery(driver, databaseName, """
                MATCH (p:%s)
                WHERE NOT (p)-[:%s|%s]->()
                RETURN p.id AS id, p.data AS data
                """.formatted(Procedure.LABEL, REL_HAS_PREREQUISITE, REL_HAS_EFFECT))
                .execute().records();

        LOG.info("Data migration v6: found {} procedure(s) without phrase embedding nodes", records.size());
        int migrated = 0;
        for (var record : records) {
            var idVal = record.get("id");
            var dataVal = record.get("data");
            if (idVal.isNull() || dataVal.isNull()) {
                continue;
            }
            try {
                UUID procedureId = UUID.fromString(idVal.asString());
                LegacyProcedureData data = objectMapper.readValue(dataVal.asString(), LegacyProcedureData.class);
                var prereqNodes = toLegacyNodes(data.prerequisites(), PhraseType.PREREQUISITE);
                var effectNodes = toLegacyNodes(data.effects(), PhraseType.EFFECT);

                if (prereqNodes.isEmpty() && effectNodes.isEmpty()) {
                    continue;
                }
                
                // Directly create the nodes instead of using PhraseEmbeddingRepository to avoid cyclic dependencies
                for (var phraseNode : prereqNodes) {
                    executableQuery(driver, databaseName, """
                            MATCH (p:Procedure {id: $procedureId})
                            CREATE (pe:PhraseEmbedding {
                                id: $id,
                                phrase: $phrase,
                                type: $type,
                                embedding: $embedding
                            })
                            CREATE (p)-[:HAS_PREREQUISITE]->(pe)
                            """).withParameters(Map.of(
                            "procedureId", procedureId.toString(),
                            "id", phraseNode.id().toString(),
                            "phrase", phraseNode.phrase(),
                            "type", phraseNode.type().name(),
                            "embedding", phraseNode.embedding()
                    )).execute();
                }
                for (var phraseNode : effectNodes) {
                    executableQuery(driver, databaseName, """
                            MATCH (p:Procedure {id: $procedureId})
                            CREATE (pe:PhraseEmbedding {
                                id: $id,
                                phrase: $phrase,
                                type: $type,
                                embedding: $embedding
                            })
                            CREATE (p)-[:HAS_EFFECT]->(pe)
                            """).withParameters(Map.of(
                            "procedureId", procedureId.toString(),
                            "id", phraseNode.id().toString(),
                            "phrase", phraseNode.phrase(),
                            "type", phraseNode.type().name(),
                            "embedding", phraseNode.embedding()
                    )).execute();
                }
                migrated++;
            } catch (Exception e) {
                LOG.warn("Data migration v6: failed to migrate procedure id={}: {}", idVal.asString(), e.getMessage());
            }
        }
        LOG.info("Data migration v6: migrated {} procedure(s)", migrated);
    }

    private static List<String> extractPhraseTexts(List<LegacyPhrase> phrases) {
        if (phrases == null) {
            return List.of();
        }
        return phrases.stream()
                .filter(p -> p.phrase() != null && !p.phrase().isBlank())
                .map(LegacyPhrase::phrase)
                .toList();
    }

    private static List<PhraseEmbedding> toLegacyNodes(List<LegacyPhrase> phrases, PhraseType type) {
        if (phrases == null) {
            return List.of();
        }
        var result = new ArrayList<PhraseEmbedding>(phrases.size());
        for (var p : phrases) {
            if (p.phrase() != null && !p.phrase().isBlank() && p.embedding() != null && p.embedding().length > 0) {
                result.add(new PhraseEmbedding(UUID.randomUUID(), p.phrase(), p.embedding(), type));
            }
        }
        return result;
    }

    private static void updateVersion(org.neo4j.driver.Driver driver, String databaseName, int version) {
        executableQuery(driver, databaseName, "MERGE (sv:%s) SET sv.version = $version".formatted(SchemaVersion.LABEL))
                .withParameters(Map.of("version", version))
                .execute();
    }

    private static int readCurrentVersion(org.neo4j.driver.Driver driver, String databaseName) {
        var records = executableQuery(driver, databaseName,
                "MERGE (sv:%s) ON CREATE SET sv.version = 0 RETURN sv.version AS version".formatted(SchemaVersion.LABEL)
        ).execute().records();
        return records.isEmpty() ? 0 : records.getFirst().get("version").asInt();
    }

    private record Migration(int version, String description, BiConsumer<org.neo4j.driver.Driver, String> apply) {
        Migration(int version, String description, String cypherStatement) {
            this(version, description, (driver, databaseName) -> executableQuery(driver, databaseName, cypherStatement).execute());
        }
    }

    /** Full Procedure JSON shape used for v9 flat-property migration. */
    private record LegacyProcedureFullData(
            @JsonProperty UUID id,
            @JsonProperty String description,
            @JsonProperty List<String> testData,
            @JsonProperty String expectedResults,
            @JsonProperty("isAtomic") boolean isAtomic,
            @JsonProperty("isPrecondition") boolean isPrecondition,
            @JsonProperty List<LegacyPhrase> prerequisites,
            @JsonProperty List<LegacyPhrase> effects,
            @JsonProperty Instant createdAt,
            @JsonProperty Instant updatedAt) {}

    /** Legacy Procedure JSON shape used for v6 phrase-embedding migration. */
    private record LegacyProcedureData(
            @JsonProperty List<LegacyPhrase> prerequisites,
            @JsonProperty List<LegacyPhrase> effects) {}

    private record LegacyPhrase(
            @JsonProperty String phrase,
            @JsonProperty float[] embedding) {}
}
