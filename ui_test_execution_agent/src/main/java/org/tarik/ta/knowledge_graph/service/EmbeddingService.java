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
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import org.tarik.ta.UiTestAgentConfig;

/**
 * Centralized embedding service for batch embedding during knowledge ingestion
 * and for Procedure semantic search queries.
 *
 * <p>Reuses the shared {@link BgeSmallEnV15EmbeddingModel} instance (384-dimension, ONNX-based,
 * thread-safe). Configurable batch size via {@code knowledge.embedding.batch.size} property.</p>
 */
@Factory
public class EmbeddingService {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel MODEL = new BgeSmallEnV15EmbeddingModel();
    private final UiTestAgentConfig config;

    public EmbeddingService(UiTestAgentConfig config) {
        this.config = config;
    }

    /**
     * Embeds a single text string and returns the resulting {@link Embedding}.
     */
    public Embedding embed(String text) {
        requireNonNull(text, "text");
        return MODEL.embed(text).content();
    }

    /**
     * Embeds multiple texts in configurable batch chunks.
     * Each chunk is processed via {@code embedAll()} for efficiency.
     *
     * @param texts the list of texts to embed
     * @return embeddings in the same order as the input texts
     */
    public List<Embedding> embedBatch(List<String> texts) {
        requireNonNull(texts, "texts");
        if (texts.isEmpty()) {
            return List.of();
        }

        var batchSize = config.getKnowledgeEmbeddingBatchSize();
        var segments = texts.stream().map(TextSegment::from).toList();
        var allEmbeddings = new ArrayList<Embedding>(segments.size());

        for (int start = 0; start < segments.size(); start += batchSize) {
            int end = Math.min(start + batchSize, segments.size());
            var chunk = segments.subList(start, end);
            var chunkEmbeddings = MODEL.embedAll(chunk).content();
            allEmbeddings.addAll(chunkEmbeddings);
            LOG.debug("Embedded batch chunk [{}-{}) of {} total texts", start, end, segments.size());
        }

        LOG.info("Embedded {} texts in {} batch(es) of size {}",
                texts.size(), (int) Math.ceil((double) texts.size() / batchSize), batchSize);
        return List.copyOf(allEmbeddings);
    }

    /**
     * Exposes the shared embedding model as an injectable bean so that avaje can inject it into
     * repositories that need it (e.g. {@link org.tarik.ta.knowledge_graph.repository.UiElementRepository}).
     */
    @Bean
    @Singleton
    public EmbeddingModel getModel() {
        return MODEL;
    }
}
