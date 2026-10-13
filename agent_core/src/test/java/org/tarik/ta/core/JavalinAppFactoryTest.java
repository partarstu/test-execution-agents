/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class JavalinAppFactoryTest {
    private static final String PATH = "/probe";

    private Javalin runningApp;

    @AfterEach
    void tearDown() {
        if (runningApp != null) {
            runningApp.stop();
            runningApp = null;
        }
    }

    record Payload(String name, Instant at) {
    }

    @Test
    void create_shouldSerializeRecordsWithJavaTimeThroughJackson() throws Exception {
        start(config -> config.routes.get(PATH, context ->
                context.json(new Payload("probe", Instant.parse("2026-10-02T10:15:30Z")))));

        HttpResponse<String> response = get();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"probe\"").doesNotContain("\"at\":null");
    }

    @Test
    void create_shouldSuppressIllegalStateExceptionInsteadOfAnsweringWithServerError() throws Exception {
        start(config -> config.routes.get(PATH, _ -> {
            throw new IllegalStateException("recycled response");
        }));

        HttpResponse<String> response = get();

        assertThat(response.statusCode()).isNotEqualTo(500);
        assertThat(response.body()).doesNotContain("recycled response");
    }

    @Test
    void create_shouldApplyCallerSettingsAfterSharedOnes() throws Exception {
        start(config -> {
            config.routes.get(PATH, _ -> {
                throw new IllegalStateException("caller handles it");
            });
            config.routes.exception(IllegalStateException.class, (exception, context) ->
                    context.status(409).result(exception.getMessage()));
        });

        HttpResponse<String> response = get();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).isEqualTo("caller handles it");
    }

    private void start(Consumer<JavalinConfig> configure) {
        runningApp = JavalinAppFactory.create(configure).start("localhost", 0);
    }

    private HttpResponse<String> get() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + runningApp.port() + PATH)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
