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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

class EmbeddingModelAssetManager {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingModelAssetManager.class);

    private static final String MODEL_URL =
            "https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/model.onnx";
    private static final String TOKENIZER_URL =
            "https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/tokenizer.json";

    private final Path targetDirectory;

    EmbeddingModelAssetManager(@NotNull Path targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    /**
     * Ensures that {@code model.onnx} and {@code tokenizer.json} exist in the target directory,
     * downloading them from Hugging Face if absent.
     *
     * @throws IllegalStateException if a file cannot be downloaded or written
     */
    void ensureAssets() {
        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to create model cache directory: %s".formatted(targetDirectory), e);
        }
        downloadIfAbsent("model.onnx", MODEL_URL);
        downloadIfAbsent("tokenizer.json", TOKENIZER_URL);
    }

    private void downloadIfAbsent(@NotNull String filename, @NotNull String url) {
        var destination = targetDirectory.resolve(filename);
        if (Files.exists(destination)) {
            LOG.info("Embedding model asset already cached: {}", destination);
            return;
        }
        LOG.info("Downloading embedding model asset '{}' from {} ...", filename, url);
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(destination);
                throw new IllegalStateException(
                        "Failed to download '%s' from %s — HTTP %d".formatted(filename, url, response.statusCode()));
            }
            LOG.info("Downloaded embedding model asset '{}' to {}", filename, destination);
        } catch (IOException | InterruptedException e) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) {}
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(
                    "Failed to download embedding model asset '%s' from %s".formatted(filename, url), e);
        }
    }
}
