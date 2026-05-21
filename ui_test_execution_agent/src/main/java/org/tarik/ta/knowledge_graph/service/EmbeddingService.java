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
package org.tarik.ta.knowledge_graph.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized embedding service for batch embedding during knowledge ingestion
 * and for Procedure semantic search queries.
 *
 * <p>The active model is configured via {@code knowledge.embedding.model} property.
 * Supported values: {@code bge-small-en-v15}, {@code multilingual-e5-small}.
 * Both produce 384-dimensional embeddings. For multilingual-e5-small, document
 * embeddings use the {@code "passage: "} prefix and query embeddings use {@code "query: "}.</p>
 */
@Factory
public class EmbeddingService {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int EXPECTED_DIMENSION = 384;

    private static final Path E5_CACHE_DIR = Path.of(
            System.getProperty("user.home"), ".cache", "test-execution-agents", "models", "multilingual-e5-small");

    private final SupportedEmbeddingModel modelDescriptor;
    private final UiTestAgentConfig config;

    // Non-final: private, each is assigned exactly once in the constructor.
    // `e5Model` is null for BGE; `model` is always set.
    private EmbeddingModel model;
    private @Nullable MultilingualE5SmallEmbeddingModel e5Model;

    public EmbeddingService(@NotNull UiTestAgentConfig config) {
        this.config = config;
        this.modelDescriptor = SupportedEmbeddingModel.fromConfigKey(config.getKnowledgeEmbeddingModel());

        if (modelDescriptor.dimension != EXPECTED_DIMENSION) {
            throw new IllegalStateException(
                    ("Embedding model '%s' produces %d-dimensional vectors, but the knowledge graph vector indexes " +
                            "require exactly %d dimensions. Update SchemaMigrationManager or choose a compatible model.")
                            .formatted(modelDescriptor.configKey, modelDescriptor.dimension, EXPECTED_DIMENSION));
        }

        initModel();
        LOG.info("Embedding model initialized: {}", modelDescriptor.configKey);
    }

    private void initModel() {
        switch (modelDescriptor) {
            case BGE_SMALL_EN_V15 -> {
                model = new BgeSmallEnV15EmbeddingModel();
                e5Model = null;
            }
            case MULTILINGUAL_E5_SMALL -> {
                new EmbeddingModelAssetManager(E5_CACHE_DIR).ensureAssets();
                var e5 = new MultilingualE5SmallEmbeddingModel(
                        E5_CACHE_DIR.resolve("model.onnx"), E5_CACHE_DIR.resolve("tokenizer.json"));
                e5Model = e5;
                model = buildE5DocumentModeBean(e5);
            }
        }
    }

    /**
     * Embeds a single text for document ingestion.
     */
    public @NotNull Embedding embed(@NotNull String text) {
        if (e5Model != null) {
            return e5Model.embedDocument(text);
        }
        return model.embed(text).content();
    }

    /**
     * Embeds multiple texts in configurable batch chunks for document ingestion.
     *
     * @return embeddings in the same order as the input texts
     */
    public @NotNull List<Embedding> embedBatch(@NotNull List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        if (e5Model != null) {
            return embedInChunks(texts, false);
        }
        return embedInChunksWithModel(texts);
    }

    /**
     * Embeds a single text in query context (used for semantic search).
     */
    public @NotNull Embedding embedForQuery(@NotNull String text) {
        if (e5Model != null) {
            return e5Model.embedQuery(text);
        }
        return model.embed(text).content();
    }

    /**
     * Embeds multiple texts in query context (used for semantic search).
     *
     * @return embeddings in the same order as the input texts
     */
    public @NotNull List<Embedding> embedBatchForQuery(@NotNull List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        if (e5Model != null) {
            return embedInChunks(texts, true);
        }
        return embedInChunksWithModel(texts);
    }

    /**
     * Exposes the shared embedding model as an injectable bean so that avaje can inject it into
     * repositories that need it (e.g. {@link org.tarik.ta.knowledge_graph.repository.UiElementRepository}).
     * For multilingual-e5-small this is a document-mode wrapper.
     */
    @Bean
    @Singleton
    public @NotNull EmbeddingModel getModel() {
        return model;
    }

    private @NotNull List<Embedding> embedInChunks(@NotNull List<String> texts, boolean queryMode) {
        var batchSize = config.getKnowledgeEmbeddingBatchSize();
        var allEmbeddings = new ArrayList<Embedding>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            var chunk = texts.subList(start, end);
            var chunkEmbeddings = queryMode ? e5Model.embedQueryBatch(chunk) : e5Model.embedDocumentBatch(chunk);
            allEmbeddings.addAll(chunkEmbeddings);
            LOG.debug("Embedded batch chunk [{}-{}) of {} total texts", start, end, texts.size());
        }
        logBatchSummary(texts.size(), batchSize);
        return List.copyOf(allEmbeddings);
    }

    private @NotNull List<Embedding> embedInChunksWithModel(@NotNull List<String> texts) {
        var batchSize = config.getKnowledgeEmbeddingBatchSize();
        var segments = texts.stream().map(TextSegment::from).toList();
        var allEmbeddings = new ArrayList<Embedding>(segments.size());
        for (int start = 0; start < segments.size(); start += batchSize) {
            int end = Math.min(start + batchSize, segments.size());
            var chunk = segments.subList(start, end);
            allEmbeddings.addAll(model.embedAll(chunk).content());
            LOG.debug("Embedded batch chunk [{}-{}) of {} total texts", start, end, segments.size());
        }
        logBatchSummary(texts.size(), batchSize);
        return List.copyOf(allEmbeddings);
    }

    private void logBatchSummary(int totalTexts, int batchSize) {
        LOG.info("Embedded {} texts in {} batch(es) of size {}",
                totalTexts, (int) Math.ceil((double) totalTexts / batchSize), batchSize);
    }

    private static @NotNull EmbeddingModel buildE5DocumentModeBean(@NotNull MultilingualE5SmallEmbeddingModel e5) {
        return new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                return Response.from(e5.embedDocument(text));
            }

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                var texts = segments.stream().map(TextSegment::text).toList();
                return Response.from(e5.embedDocumentBatch(texts));
            }
        };
    }
}
