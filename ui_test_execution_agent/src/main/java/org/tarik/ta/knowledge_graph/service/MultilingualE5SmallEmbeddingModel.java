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
        return (mode == QUERY ? "query: " : "passage: ") + text;
    }
}
