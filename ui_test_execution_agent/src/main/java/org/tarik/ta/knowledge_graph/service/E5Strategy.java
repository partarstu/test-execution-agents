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
import dev.langchain4j.model.output.Response;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;

class E5Strategy implements EmbeddingStrategy {
    private final MultilingualE5SmallEmbeddingModel e5Model;
    private final EmbeddingModel documentModel;

    E5Strategy(@NotNull Path cacheDir) {
        new EmbeddingModelAssetManager(cacheDir).ensureAssets();
        this.e5Model = new MultilingualE5SmallEmbeddingModel(
                cacheDir.resolve("model.onnx"), cacheDir.resolve("tokenizer.json"));
        this.documentModel = new E5DocumentModeAdapter(e5Model);
    }

    E5Strategy(@NotNull MultilingualE5SmallEmbeddingModel e5Model) {
        this.e5Model = e5Model;
        this.documentModel = new E5DocumentModeAdapter(e5Model);
    }

    @Override
    public @NotNull List<Embedding> embedDocuments(@NotNull List<String> texts) {
        return e5Model.embedDocumentBatch(texts);
    }

    @Override
    public @NotNull List<Embedding> embedQueries(@NotNull List<String> texts) {
        return e5Model.embedQueryBatch(texts);
    }

    @Override
    public @NotNull EmbeddingModel documentModel() {
        return documentModel;
    }

    private static final class E5DocumentModeAdapter implements EmbeddingModel {
        private final MultilingualE5SmallEmbeddingModel e5;

        private E5DocumentModeAdapter(@NotNull MultilingualE5SmallEmbeddingModel e5) {
            this.e5 = e5;
        }

        @Override
        public Response<Embedding> embed(String text) {
            return Response.from(e5.embedDocument(text));
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<String> texts = segments.stream().map(TextSegment::text).toList();
            return Response.from(e5.embedDocumentBatch(texts));
        }
    }
}
