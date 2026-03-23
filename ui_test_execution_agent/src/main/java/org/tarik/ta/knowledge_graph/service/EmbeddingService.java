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
package org.tarik.ta.knowledge_graph.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import io.avaje.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.UiTestAgentConfig.getKnowledgeEmbeddingBatchSize;

/**
 * Centralized embedding service for batch embedding during knowledge ingestion
 * and for Procedure semantic search queries.
 *
 * <p>Reuses the shared {@link BgeSmallEnV15EmbeddingModel} instance (384-dimension, ONNX-based,
 * thread-safe). Configurable batch size via {@code knowledge.embedding.batch.size} property.</p>
 */
@Singleton
@Factory
public class EmbeddingService {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel MODEL = new BgeSmallEnV15EmbeddingModel();

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

        var batchSize = getKnowledgeEmbeddingBatchSize();
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
