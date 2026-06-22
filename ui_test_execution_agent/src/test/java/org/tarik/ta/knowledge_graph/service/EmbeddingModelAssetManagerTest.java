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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingModelAssetManagerTest {

    private static final byte[] MODEL_BYTES = "onnx-model-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TOKENIZER_BYTES = "tokenizer-json-bytes".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String baseUrl;

    @TempDir
    private Path targetDirectory;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        baseUrl = "http://localhost:%d".formatted(server.getAddress().getPort());

        // Hugging Face LFS URLs answer with a 302 redirect to their CDN; the manager must follow it.
        server.createContext("/model.onnx", exchange -> redirect(exchange, baseUrl + "/cdn/model.onnx"));
        server.createContext("/tokenizer.json", exchange -> redirect(exchange, baseUrl + "/cdn/tokenizer.json"));
        server.createContext("/cdn/model.onnx", exchange -> respondOk(exchange, MODEL_BYTES));
        server.createContext("/cdn/tokenizer.json", exchange -> respondOk(exchange, TOKENIZER_BYTES));
        server.createContext("/fail", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void ensureAssets_followsRedirectAndWritesFinalFilesOnly() throws IOException {
        var manager = new EmbeddingModelAssetManager(targetDirectory, baseUrl + "/model.onnx", baseUrl + "/tokenizer.json");

        manager.ensureAssets();

        assertThat(Files.readAllBytes(targetDirectory.resolve("model.onnx"))).isEqualTo(MODEL_BYTES);
        assertThat(Files.readAllBytes(targetDirectory.resolve("tokenizer.json"))).isEqualTo(TOKENIZER_BYTES);
        assertThat(Files.exists(targetDirectory.resolve("model.onnx.tmp"))).isFalse();
        assertThat(Files.exists(targetDirectory.resolve("tokenizer.json.tmp"))).isFalse();
    }

    @Test
    void ensureAssets_leavesNoFileWhenDownloadFails() {
        var manager = new EmbeddingModelAssetManager(targetDirectory, baseUrl + "/fail", baseUrl + "/tokenizer.json");

        assertThatThrownBy(manager::ensureAssets).isInstanceOf(IllegalStateException.class);

        assertThat(Files.exists(targetDirectory.resolve("model.onnx"))).isFalse();
        assertThat(Files.exists(targetDirectory.resolve("model.onnx.tmp"))).isFalse();
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respondOk(HttpExchange exchange, byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        }
    }
}
