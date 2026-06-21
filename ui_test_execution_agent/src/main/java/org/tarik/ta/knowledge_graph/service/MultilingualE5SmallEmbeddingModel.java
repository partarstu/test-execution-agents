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
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

import static org.tarik.ta.knowledge_graph.service.E5EmbeddingMode.DOCUMENT;
import static org.tarik.ta.knowledge_graph.service.E5EmbeddingMode.QUERY;

class MultilingualE5SmallEmbeddingModel {

    private final OnnxEmbeddingModel onnxModel;

    MultilingualE5SmallEmbeddingModel(@NotNull Path modelPath, @NotNull Path tokenizerPath) {
        this.onnxModel = new OnnxEmbeddingModel(modelPath.toString(), tokenizerPath.toString(), PoolingMode.MEAN);
    }

    @NotNull Embedding embedDocument(@NotNull String text) {
        return embed(text, DOCUMENT);
    }

    @NotNull Embedding embedQuery(@NotNull String text) {
        return embed(text, QUERY);
    }

    @NotNull List<Embedding> embedDocumentBatch(@NotNull List<String> texts) {
        return embedBatch(texts, DOCUMENT);
    }

    @NotNull List<Embedding> embedQueryBatch(@NotNull List<String> texts) {
        return embedBatch(texts, QUERY);
    }

    private @NotNull Embedding embed(@NotNull String text, @NotNull E5EmbeddingMode mode) {
        return onnxModel.embed(prefixed(text, mode)).content();
    }

    private @NotNull List<Embedding> embedBatch(@NotNull List<String> texts, @NotNull E5EmbeddingMode mode) {
        var segments = texts.stream().map(t -> TextSegment.from(prefixed(t, mode))).toList();
        return onnxModel.embedAll(segments).content();
    }

    private static @NotNull String prefixed(@NotNull String text, @NotNull E5EmbeddingMode mode) {
        return (mode == QUERY ? "query: %s" : "passage: %s").formatted(text);
    }
}
