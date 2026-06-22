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
import java.time.Duration;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

class EmbeddingModelAssetManager {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingModelAssetManager.class);

    private static final String MODEL_URL =
            "https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/model.onnx";
    private static final String TOKENIZER_URL =
            "https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/tokenizer.json";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    private final Path targetDirectory;
    private final String modelUrl;
    private final String tokenizerUrl;

    EmbeddingModelAssetManager(@NotNull Path targetDirectory) {
        this(targetDirectory, MODEL_URL, TOKENIZER_URL);
    }

    EmbeddingModelAssetManager(@NotNull Path targetDirectory, @NotNull String modelUrl, @NotNull String tokenizerUrl) {
        this.targetDirectory = targetDirectory;
        this.modelUrl = modelUrl;
        this.tokenizerUrl = tokenizerUrl;
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
        downloadIfAbsent("model.onnx", modelUrl);
        downloadIfAbsent("tokenizer.json", tokenizerUrl);
    }

    private void downloadIfAbsent(@NotNull String filename, @NotNull String url) {
        var destination = targetDirectory.resolve(filename);
        if (Files.exists(destination)) {
            LOG.info("Embedding model asset already cached: {}", destination);
            return;
        }
        LOG.info("Downloading embedding model asset '{}' from {} ...", filename, url);
        // Download into a temp file and atomically move it into place so that a crash mid-download cannot leave a
        // truncated file that later looks like a valid cache hit.
        var tempFile = targetDirectory.resolve(filename + ".tmp");
        try (var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build()) {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Failed to download '%s' from %s — HTTP %d".formatted(filename, url, response.statusCode()));
            }
            Files.move(tempFile, destination, ATOMIC_MOVE, REPLACE_EXISTING);
            LOG.info("Downloaded embedding model asset '{}' to {}", filename, destination);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(
                    "Failed to download embedding model asset '%s' from %s".formatted(filename, url), e);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                LOG.debug("Failed to delete temp file {}", tempFile, e);
            }
        }
    }
}
