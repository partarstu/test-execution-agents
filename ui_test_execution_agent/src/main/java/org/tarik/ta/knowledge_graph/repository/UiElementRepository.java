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

import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.Screenshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.tarik.ta.knowledge_graph.service.UiElementCache;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.knowledge_graph.model.node.IEntity.PROP_ID;
import static org.tarik.ta.knowledge_graph.model.node.UiElement.*;
import static org.tarik.ta.knowledge_graph.model.node.Embeddable.PROP_EMBEDDING;
import static org.tarik.ta.knowledge_graph.repository.UiElementRepository.QueryAliases.*;

/**
 * Repository for {@link UiElement} persistence using direct Neo4j Cypher queries.
 *
 * <p>All UiElement fields are stored as flat Neo4j node properties.
 * Semantic search uses {@code CALL db.index.vector.queryNodes(...)}, following the same
 * pattern as {@link PhraseEmbeddingRepository}. The vector index is created by
 * {@link org.tarik.ta.knowledge_graph.schema.SchemaMigrationManager}.
 * UUID-based lookup queries Neo4j directly by node property.</p>
 */
@Singleton
public class UiElementRepository {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementRepository.class);
    private static final String VECTOR_INDEX_NAME = "ui_element_embedding_index";

    static final class QueryAliases {
        static final String ALIAS_N = "n";
        static final String ALIAS_SCORE = "score";

        private QueryAliases() {
        }
    }

    private final Neo4jRepositorySupport repositorySupport;
    private final UiElementCache uiElementCache;
    private final String findByIdCypher;
    private final String saveUiElementCypher;
    private final String updateUiElementCypher;
    private final String removeUiElementCypher;
    private final String findBySemanticSearchCypher;
    private final EmbeddingModel embeddingModel;

    public UiElementRepository(Neo4jRepositorySupport repositorySupport, EmbeddingModel embeddingModel, UiElementCache uiElementCache) {
        this.repositorySupport = repositorySupport;
        this.uiElementCache = uiElementCache;
        var sharedSetClause = """
                    n.${PROP_NAME} = $name,
                    n.${PROP_OWN_DESCRIPTION} = $ownDescription,
                    n.${PROP_ANCHORS_DESCRIPTION} = $anchorsDescription,
                    n.${PROP_PARENT_ELEMENT_SUMMARY} = $parentElementSummary,
                    n.${PROP_IS_DATA_DEPENDENT} = $isDataDependent,
                    n.${PROP_SCREENSHOT_FILE_EXTENSION} = $screenshotFileExtension,
                    n.${PROP_SCREENSHOT_MIME_TYPE} = $screenshotMimeType,
                    n.${PROP_SCREENSHOT_IMAGE} = $screenshotImage""";
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
                DETACH DELETE n
                """);
        this.findBySemanticSearchCypher = repositorySupport.cypher("""
                CALL db.index.vector.queryNodes($indexName, $topN, $queryVector)
                YIELD node, score
                WHERE score >= $minScore
                RETURN node AS n, score
                """);

        this.embeddingModel = embeddingModel;
    }

    public void create(@NotNull UiElement element) {
        var embedding = embeddingModel.embed(element.name()).content().vector();
        repositorySupport.executeSingleWriteQuery(saveUiElementCypher, buildSaveParams(element, embedding));
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
                        "indexName", VECTOR_INDEX_NAME,
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

    public void update(@NotNull UiElement element, boolean refreshEmbedding) {
        if (refreshEmbedding) {
            var embedding = embeddingModel.embed(element.name()).content().vector();
            repositorySupport.executeSingleWriteQuery(saveUiElementCypher, buildSaveParams(element, embedding));
        } else {
            repositorySupport.executeSingleWriteQuery(updateUiElementCypher, buildUpdateParams(element));
        }
        uiElementCache.update(element);
        LOG.info("Updated UiElement '{}' (id={})", element.name(), element.id());
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

        Screenshot screenshot = null;
        if (node.containsKey(PROP_SCREENSHOT_FILE_EXTENSION) && !node.get(PROP_SCREENSHOT_FILE_EXTENSION).isNull()) {
            screenshot = new Screenshot(
                    node.get(PROP_SCREENSHOT_FILE_EXTENSION).asString(),
                    node.get(PROP_SCREENSHOT_MIME_TYPE).asString(),
                    node.get(PROP_SCREENSHOT_IMAGE).asString()
            );
        }

        return new UiElement(id, name, ownDescription, anchorsDescription, parentElementSummary, screenshot, isDataDependent);
    }

    private static Map<String, Object> buildSaveParams(UiElement element, float[] embedding) {
        var params = buildUpdateParams(element);
        params.put(PROP_EMBEDDING, embedding);
        return params;
    }

    private static HashMap<String, Object> buildUpdateParams(UiElement element) {
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