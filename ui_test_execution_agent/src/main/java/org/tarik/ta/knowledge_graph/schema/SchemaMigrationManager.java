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
package org.tarik.ta.knowledge_graph.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.tarik.ta.knowledge_graph.model.GraphRelationships.*;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.SchemaVersion;
import org.tarik.ta.knowledge_graph.model.node.TestCase;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

/**
 * Idempotent, versioned schema migration system for Neo4j schema elements.
 *
 * <p>Versions 1–5 are Cypher DDL (index/constraint creation). Version 6 is a Java data migration
 * that creates {@code PhraseEmbedding} nodes from legacy inline embeddings in {@code Procedure.data}.
 * Versions 7–8 add vector indexes for Procedure and UiElement.
 * Version 9 migrates Procedure nodes from JSON {@code data} blob to flat properties, and
 * renames legacy uppercase UiElement metadata properties to camelCase.</p>
 */
public class SchemaMigrationManager {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaMigrationManager.class);
    private static final int DATA_MIGRATION_VERSION = 9;

    private static final List<Migration> DDL_MIGRATIONS = List.of(
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
                    "CREATE VECTOR INDEX phrase_embedding_vector_index IF NOT EXISTS FOR (n:%s) ON n.embedding OPTIONS {indexConfig: {`vector.dimensions`: 384, `vector.similarity_function`: 'cosine'}}"
                            .formatted(PhraseEmbedding.LABEL)),
            new Migration(5, "Create index on PhraseEmbedding.phrase for exact lookups",
                    "CREATE INDEX phrase_embedding_phrase_index IF NOT EXISTS FOR (n:%s) ON (n.phrase)"
                            .formatted(PhraseEmbedding.LABEL)),
            new Migration(7, "Create vector index on Procedure.embedding",
                    "CREATE VECTOR INDEX procedure_embedding_index IF NOT EXISTS FOR (n:%s) ON n.embedding OPTIONS {indexConfig: {`vector.dimensions`: 384, `vector.similarity_function`: 'cosine'}}"
                            .formatted(Procedure.LABEL)),
            new Migration(8, "Create vector index on UiElement.embedding",
                    "CREATE VECTOR INDEX ui_element_embedding_index IF NOT EXISTS FOR (n:%s) ON n.embedding OPTIONS {indexConfig: {`vector.dimensions`: 384, `vector.similarity_function`: 'cosine'}}"
                            .formatted(UiElement.LABEL))
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

        var pendingDdl = DDL_MIGRATIONS.stream()
                .filter(m -> m.version() > currentVersion)
                .toList();

        for (var migration : pendingDdl) {
            LOG.info("Applying DDL migration v{}: {}", migration.version(), migration.description());
            executableQuery(driver, databaseName, migration.cypherStatement()).execute();
            updateVersion(driver, databaseName, migration.version());
            LOG.info("DDL migration v{} applied successfully", migration.version());
        }

        if (currentVersion < 6) {
            LOG.info("Applying data migration v6: Extract PhraseEmbedding nodes from legacy Procedure JSON");
            migratePhraseEmbeddingNodes(driver, databaseName);
            updateVersion(driver, databaseName, 6);
            LOG.info("Data migration v6 applied successfully");
        }

        if (currentVersion < DATA_MIGRATION_VERSION) {
            LOG.info("Applying data migration v{}: Flatten Procedure properties and normalise UiElement property names",
                    DATA_MIGRATION_VERSION);
            migrateProcedureToFlatProperties(driver, databaseName);
            migrateUiElementPropertyNames(driver, databaseName);
            updateVersion(driver, databaseName, DATA_MIGRATION_VERSION);
            LOG.info("Data migration v{} applied successfully", DATA_MIGRATION_VERSION);
        }

        int highestApplied = Math.max(
                pendingDdl.isEmpty() ? currentVersion : pendingDdl.getLast().version(),
                currentVersion
        );
        highestApplied = Math.max(highestApplied, DATA_MIGRATION_VERSION <= currentVersion ? currentVersion : DATA_MIGRATION_VERSION);
        LOG.info("Schema is now at version {}", highestApplied);
    }

    private static org.neo4j.driver.ExecutableQuery executableQuery(org.neo4j.driver.Driver driver, String databaseName, String cypher) {
        return driver.executableQuery(cypher).withConfig(org.neo4j.driver.QueryConfig.builder().withDatabase(databaseName).build());
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

    private record Migration(int version, String description, String cypherStatement) {}

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
