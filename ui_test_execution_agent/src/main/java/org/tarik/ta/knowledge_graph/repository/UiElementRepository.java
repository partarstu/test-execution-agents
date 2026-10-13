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

import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.Screenshot;
import org.tarik.ta.knowledge_graph.model.node.UiElementAppearance;
import org.tarik.ta.UiTestAgentConfig;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.tarik.ta.knowledge_graph.service.UiElementCache;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.knowledge_graph.model.node.IEntity.PROP_ID;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.*;
import static org.tarik.ta.knowledge_graph.model.node.Embeddable.PROP_EMBEDDING;
import static org.tarik.ta.knowledge_graph.model.node.UiElementAppearance.PROP_VALID_FROM;
import static org.tarik.ta.knowledge_graph.model.node.UiElementAppearance.PROP_VALID_TO;
import static org.tarik.ta.knowledge_graph.repository.UiElementRepository.QueryAliases.*;

/**
 * Repository for {@link UiElement} persistence using direct Neo4j Cypher queries.
 *
 * <p>All UiElement fields are stored as flat Neo4j node properties.
 * Semantic search uses the Cypher 25 {@code SEARCH} clause, following the same
 * pattern as {@link PhraseEmbeddingRepository}. The vector index is created by
 * {@link org.tarik.ta.knowledge_graph.schema.SchemaMigrationManager}.
 * UUID-based lookup queries Neo4j directly by node property.</p>
 */
@Singleton
public class UiElementRepository {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementRepository.class);

    static final class QueryAliases {
        static final String ALIAS_N = "n";
        static final String ALIAS_SCORE = "score";
        static final String ALIAS_APPEARANCE = "ap";

        private QueryAliases() {
        }
    }

    private final Neo4jRepositorySupport repositorySupport;
    private final UiElementCache uiElementCache;
    private final String findByIdCypher;
    private final String saveUiElementCypher;
    private final String updateUiElementCypher;
    private final String removeUiElementCypher;
    private final String expireAppearanceCypher;
    private final String trimAppearancesCypher;
    private final String findExpiredAppearancesCypher;
    private final UiTestAgentConfig config;
    private final String findBySemanticSearchCypher;
    private final EmbeddingModel embeddingModel;

    public UiElementRepository(Neo4jRepositorySupport repositorySupport, EmbeddingModel embeddingModel, UiElementCache uiElementCache,
                               UiTestAgentConfig config) {
        this.repositorySupport = repositorySupport;
        this.uiElementCache = uiElementCache;
        this.config = config;
        var sharedSetClause = """
                    n.${PROP_NAME} = $name,
                    n.${PROP_OWN_DESCRIPTION} = $ownDescription,
                    n.${PROP_ANCHORS_DESCRIPTION} = $anchorsDescription,
                    n.${PROP_PARENT_ELEMENT_SUMMARY} = $parentElementSummary,
                    n.${PROP_IS_DATA_DEPENDENT} = $isDataDependent,
                    n.${PROP_SCREENSHOT_FILE_EXTENSION} = $screenshotFileExtension,
                    n.${PROP_SCREENSHOT_MIME_TYPE} = $screenshotMimeType,
                    n.${PROP_SCREENSHOT_IMAGE} = $screenshotImage,
                    n.${PROP_UPDATED_AT} = $updatedAt""";
        this.findByIdCypher = repositorySupport.cypher("MATCH (n:${LABEL_UI_ELEMENT} {${PROP_ID}: $id}) RETURN n");
        this.saveUiElementCypher = repositorySupport.cypher("""
                MERGE (n:${LABEL_UI_ELEMENT} {${PROP_ID}: $id})
                SET n.${PROP_EMBEDDING} = $embedding,
                """ + sharedSetClause);
        this.updateUiElementCypher = repositorySupport.cypher("""
                MATCH (n:${LABEL_UI_ELEMENT} {${PROP_ID}: $id})
                SET\s""" + sharedSetClause);
        this.removeUiElementCypher = repositorySupport.cypher("""
                MATCH (n:${LABEL_UI_ELEMENT} {${PROP_ID}: $id})
                OPTIONAL MATCH (n)-[:${REL_HAD_APPEARANCE}]->(ap:${LABEL_UI_ELEMENT_APPEARANCE})
                DETACH DELETE ap, n
                """);
        // Copies the stored appearance inside the database, so the screenshot never travels to the client and back
        this.expireAppearanceCypher = repositorySupport.cypher("""
                MATCH (n:${LABEL_UI_ELEMENT} {${PROP_ID}: $id})
                CREATE (n)-[:${REL_HAD_APPEARANCE}]->(:${LABEL_UI_ELEMENT_APPEARANCE} {
                    ${PROP_ID}: $appearanceId,
                    ${PROP_OWN_DESCRIPTION}: n.${PROP_OWN_DESCRIPTION},
                    ${PROP_ANCHORS_DESCRIPTION}: n.${PROP_ANCHORS_DESCRIPTION},
                    ${PROP_PARENT_ELEMENT_SUMMARY}: n.${PROP_PARENT_ELEMENT_SUMMARY},
                    ${PROP_SCREENSHOT_FILE_EXTENSION}: n.${PROP_SCREENSHOT_FILE_EXTENSION},
                    ${PROP_SCREENSHOT_MIME_TYPE}: n.${PROP_SCREENSHOT_MIME_TYPE},
                    ${PROP_SCREENSHOT_IMAGE}: n.${PROP_SCREENSHOT_IMAGE},
                    ${PROP_VALID_FROM}: n.${PROP_UPDATED_AT},
                    ${PROP_VALID_TO}: $validTo})
                """);
        this.trimAppearancesCypher = repositorySupport.cypher("""
                MATCH (n:${LABEL_UI_ELEMENT} {${PROP_ID}: $id})-[:${REL_HAD_APPEARANCE}]->(ap:${LABEL_UI_ELEMENT_APPEARANCE})
                WITH ap ORDER BY datetime(ap.${PROP_VALID_TO}) DESC
                WITH collect(ap) AS appearances
                UNWIND appearances[$keep..] AS expired
                DETACH DELETE expired
                """);
        this.findExpiredAppearancesCypher = repositorySupport.cypher("""
                MATCH (n:${LABEL_UI_ELEMENT} {${PROP_ID}: $id})-[:${REL_HAD_APPEARANCE}]->(ap:${LABEL_UI_ELEMENT_APPEARANCE})
                RETURN ap
                ORDER BY datetime(ap.${PROP_VALID_TO}) DESC
                """);
        this.findBySemanticSearchCypher = repositorySupport.cypher("""
                MATCH (node:${LABEL_UI_ELEMENT})
                  SEARCH node IN (VECTOR INDEX ${INDEX_UI_ELEMENT_EMBEDDING} FOR $queryVector LIMIT $topN) SCORE AS score
                WHERE score >= $minScore
                RETURN node AS n, score
                ORDER BY score DESC
                """);

        this.embeddingModel = embeddingModel;
    }

    public void create(@NotNull UiElement element) {
        var embedding = embeddingModel.embed(element.name()).content().vector();
        repositorySupport.executeSingleWriteQuery(saveUiElementCypher, buildSaveParams(element, embedding, Instant.now().toString()));
        uiElementCache.put(element);
        LOG.info("Saved UiElement '{}' (id={})", element.name(), element.id());
    }

    /**
     * Performs semantic vector search on UI element names.
     * Returns each match paired with its cosine similarity score.
     */
    public List<UiElementMatch> findBySemanticSearch(@NotNull String query, int topN, double minScore) {
        var queryVector = embeddingModel.embed(query).content().vector();
        return repositorySupport.executeSingleReadQuery(findBySemanticSearchCypher, Map.of(
                        "topN", topN,
                        "queryVector", queryVector,
                        "minScore", minScore))
                .stream()
                .map(r -> {
                    var element = fromRecord(r.get(ALIAS_N).asNode());
                    var score = r.get(ALIAS_SCORE).asDouble();
                    LOG.debug("Retrieved UiElement by similarity: name='{}', score={}", element.name(), score);
                    uiElementCache.update(element);
                    return new UiElementMatch(element, score);
                })
                .toList();
    }

    /**
     * Retrieves a UI element by its UUID, first checking the cache.
     */
    public Optional<UiElement> findById(@NotNull UUID id) {
        return repositorySupport.executeSingleReadQuery(findByIdCypher, Map.of(PROP_ID, id.toString()))
                .stream()
                .map(r -> {
                    var element = fromRecord(r.get(ALIAS_N).asNode());
                    LOG.debug("Retrieved UiElement by UUID: name='{}', id={}", element.name(), id);
                    uiElementCache.update(element);
                    return element;
                })
                .findFirst();
    }

    /** Updates the element, first keeping its stored appearance as an expired {@link UiElementAppearance} when the new one differs. */
    public void update(@NotNull UiElement element, boolean refreshEmbedding) {
        var embedding = refreshEmbedding ? embeddingModel.embed(element.name()).content().vector() : null;
        repositorySupport.executeComplexWriteQuery(tx -> {
            var storedNode = tx.run(findByIdCypher, Map.of(PROP_ID, element.id().toString())).list().stream()
                    .map(r -> r.get(ALIAS_N).asNode())
                    .findFirst();
            var stored = storedNode.map(UiElementRepository::fromRecord);
            boolean appearanceChanged = stored.map(s -> !hasSameAppearance(s, element)).orElse(true);
            if (appearanceChanged && stored.filter(UiElementRepository::hasNonEmptyAppearance).isPresent()) {
                expireStoredAppearance(tx, element);
            }
            // The timestamp becomes the validFrom of the appearance once it expires, so it moves only with the appearance
            var appearanceValidFrom = appearanceChanged
                    ? Instant.now().toString()
                    : storedNode.orElseThrow().get(PROP_UPDATED_AT).asString(null);
            if (embedding != null) {
                tx.run(saveUiElementCypher, buildSaveParams(element, embedding, appearanceValidFrom)).consume();
            } else {
                tx.run(updateUiElementCypher, buildUpdateParams(element, appearanceValidFrom)).consume();
            }
        });
        uiElementCache.update(element);
        LOG.info("Updated UiElement '{}' (id={})", element.name(), element.id());
    }

    private void expireStoredAppearance(TransactionContext tx, UiElement element) {
        var id = element.id().toString();
        tx.run(expireAppearanceCypher, Map.of(PROP_ID, id, "appearanceId", UUID.randomUUID().toString(),
                "validTo", Instant.now().toString())).consume();
        tx.run(trimAppearancesCypher, Map.of(PROP_ID, id, "keep", config.getElementAppearanceHistorySize())).consume();
        LOG.info("Expired the previous appearance of UiElement '{}' (id={})", element.name(), element.id());
    }

    private static boolean hasNonEmptyAppearance(UiElement element) {
        return element.screenshot() != null || !element.description().isBlank() || !element.locationDetails().isBlank()
                || !element.parentElementSummary().isBlank();
    }

    private static boolean hasSameAppearance(UiElement stored, UiElement updated) {
        return stored.description().equals(updated.description())
                && stored.locationDetails().equals(updated.locationDetails())
                && stored.parentElementSummary().equals(updated.parentElementSummary())
                && Objects.equals(stored.screenshot(), updated.screenshot());
    }

    /** Returns the expired appearances of the element, the most recently expired first. */
    public List<UiElementAppearance> findExpiredAppearances(@NotNull UUID elementId) {
        return repositorySupport.executeSingleReadQuery(findExpiredAppearancesCypher, Map.of(PROP_ID, elementId.toString())).stream()
                .map(r -> appearanceFromNode(r.get(ALIAS_APPEARANCE).asNode()))
                .toList();
    }

    private static UiElementAppearance appearanceFromNode(@NotNull Node node) {
        var validFrom = node.get(PROP_VALID_FROM);
        return new UiElementAppearance(
                UUID.fromString(node.get(PROP_ID).asString()),
                node.get(PROP_OWN_DESCRIPTION).asString(""),
                node.get(PROP_ANCHORS_DESCRIPTION).asString(""),
                node.get(PROP_PARENT_ELEMENT_SUMMARY).asString(""),
                screenshotOf(node),
                validFrom.isNull() ? null : Instant.parse(validFrom.asString()),
                Instant.parse(node.get(PROP_VALID_TO).asString()));
    }

    public void update(@NotNull UiElement element) {
        update(element, true);
    }

    public void remove(@NotNull UiElement element) {
        repositorySupport.executeSingleWriteQuery(removeUiElementCypher, Map.of(PROP_ID, element.id().toString()));
        uiElementCache.remove(element.id());
        LOG.info("Removed UiElement '{}' from DB", element.name());
    }

    private static UiElement fromRecord(@NotNull Node node) {
        var id = UUID.fromString(node.get(PROP_ID).asString());
        var name = node.containsKey(PROP_NAME) ? node.get(PROP_NAME).asString() : "";
        var ownDescription = node.containsKey(PROP_OWN_DESCRIPTION) ? node.get(PROP_OWN_DESCRIPTION).asString() : "";
        var anchorsDescription = node.containsKey(PROP_ANCHORS_DESCRIPTION) ? node.get(PROP_ANCHORS_DESCRIPTION).asString() : "";
        var parentElementSummary = node.containsKey(PROP_PARENT_ELEMENT_SUMMARY) ? node.get(PROP_PARENT_ELEMENT_SUMMARY).asString() : "";
        var isDataDependent = node.containsKey(PROP_IS_DATA_DEPENDENT) && node.get(PROP_IS_DATA_DEPENDENT).asBoolean();

        return new UiElement(id, name, ownDescription, anchorsDescription, parentElementSummary, screenshotOf(node), isDataDependent);
    }

    @Nullable
    private static Screenshot screenshotOf(@NotNull Node node) {
        if (!node.containsKey(PROP_SCREENSHOT_FILE_EXTENSION) || node.get(PROP_SCREENSHOT_FILE_EXTENSION).isNull()) {
            return null;
        }
        return new Screenshot(
                node.get(PROP_SCREENSHOT_FILE_EXTENSION).asString(),
                node.get(PROP_SCREENSHOT_MIME_TYPE).asString(),
                node.get(PROP_SCREENSHOT_IMAGE).asString()
        );
    }

    private static Map<String, Object> buildSaveParams(UiElement element, float[] embedding, @Nullable String appearanceValidFrom) {
        var params = buildUpdateParams(element, appearanceValidFrom);
        params.put(PROP_EMBEDDING, embedding);
        return params;
    }

    private static HashMap<String, Object> buildUpdateParams(UiElement element, @Nullable String appearanceValidFrom) {
        var params = new HashMap<String, Object>();
        var screenshot = element.screenshot();
        params.put(PROP_ID, element.id().toString());
        params.put(PROP_NAME, element.name());
        params.put(PROP_OWN_DESCRIPTION, element.description());
        params.put(PROP_ANCHORS_DESCRIPTION, element.locationDetails());
        params.put(PROP_PARENT_ELEMENT_SUMMARY, element.parentElementSummary());
        params.put(PROP_IS_DATA_DEPENDENT, element.isDataDependent());
        params.put(PROP_SCREENSHOT_FILE_EXTENSION, screenshot != null ? screenshot.fileExtension() : null);
        params.put(PROP_SCREENSHOT_MIME_TYPE, screenshot != null ? screenshot.mimeType() : null);
        params.put(PROP_SCREENSHOT_IMAGE, screenshot != null ? screenshot.base64EncodedImage() : null);
        params.put(PROP_UPDATED_AT, appearanceValidFrom);
        return params;
    }

    /**
     * A {@link UiElement} paired with its semantic similarity score from a vector search.
     */
    public record UiElementMatch(UiElement element, double score) {
        public UiElementMatch {
            requireNonNull(element, "element");
        }
    }
}