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
package org.tarik.ta.core.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Minimal A2A test client over the JDK {@link HttpClient}. It sends JSON-RPC requests to the agent's main endpoint and
 * parses the Server-Sent Events stream that streaming methods answer with — the SSE-parsing client the smoke suite
 * needs and which the production code does not provide. Non-streaming calls return the raw JSON-RPC response; streaming
 * calls return a live {@link SseStream} whose {@code result} payloads can be awaited as they arrive.
 */
class A2aTestClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String baseUrl;

    A2aTestClient(@NotNull String baseUrl) {
        this.baseUrl = baseUrl;
    }

    record Response(int status, @NotNull String body) {
    }

    Response get(@NotNull String path, @Nullable String bearerToken) {
        return sendForString(withAuth(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), bearerToken).build());
    }

    Response postRpc(@NotNull String jsonRpcBody, @Nullable String bearerToken) {
        return sendForString(rpcRequest(jsonRpcBody, bearerToken));
    }

    SseStream openSse(@NotNull String jsonRpcBody, @Nullable String bearerToken) {
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(rpcRequest(jsonRpcBody, bearerToken), HttpResponse.BodyHandlers.ofInputStream());
            return new SseStream(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while opening the SSE stream", e);
        }
    }

    private HttpRequest rpcRequest(@NotNull String body, @Nullable String bearerToken) {
        return withAuth(HttpRequest.newBuilder(URI.create(baseUrl + "/"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, UTF_8)), bearerToken).build();
    }

    private static HttpRequest.Builder withAuth(@NotNull HttpRequest.Builder builder, @Nullable String bearerToken) {
        return bearerToken == null ? builder : builder.header("Authorization", "Bearer " + bearerToken);
    }

    private Response sendForString(@NotNull HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending the request", e);
        }
    }

    /**
     * A live Server-Sent Events stream. A daemon thread reads {@code data:} frames as they arrive, parses the JSON-RPC
     * {@code result} payload of each, and makes them available for the test to await. Closing it stops the reader and
     * releases the underlying connection.
     */
    static final class SseStream implements AutoCloseable {
        private static final String DATA_PREFIX = "data:";

        private final InputStream source;
        private final Thread reader;
        private final LinkedBlockingQueue<JsonNode> pending = new LinkedBlockingQueue<>();
        // Only the test thread touches this (via await/received), so a plain list is enough.
        private final List<JsonNode> received = new ArrayList<>();

        private SseStream(@NotNull InputStream source) {
            this.source = source;
            this.reader = new Thread(this::readLoop, "a2a-sse-reader");
            this.reader.setDaemon(true);
            this.reader.start();
        }

        private void readLoop() {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(source, UTF_8))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (line.startsWith(DATA_PREFIX)) {
                        String json = line.substring(DATA_PREFIX.length()).trim();
                        if (!json.isEmpty()) {
                            JsonNode root = MAPPER.readTree(json);
                            pending.put(root.has("result") ? root.get("result") : root);
                        }
                    }
                }
            } catch (IOException e) {
                // The stream was closed by the server or by close(); nothing left to read.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Returns the first event (already seen, or arriving within {@code timeout}) whose {@code result} payload matches
         * {@code predicate}. Events polled while waiting are retained, so a later await can still observe them.
         */
        Optional<JsonNode> await(@NotNull Predicate<JsonNode> predicate, @NotNull Duration timeout) {
            for (JsonNode event : received) {
                if (predicate.test(event)) {
                    return Optional.of(event);
                }
            }
            long deadlineNanos = System.nanoTime() + timeout.toNanos();
            try {
                while (true) {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        return Optional.empty();
                    }
                    JsonNode event = pending.poll(remainingNanos, TimeUnit.NANOSECONDS);
                    if (event == null) {
                        return Optional.empty();
                    }
                    received.add(event);
                    if (predicate.test(event)) {
                        return Optional.of(event);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        @NotNull List<JsonNode> received() {
            return List.copyOf(received);
        }

        @Override
        public void close() {
            reader.interrupt();
            try {
                source.close();
            } catch (IOException e) {
                // Best-effort cleanup; the stream is being discarded.
            }
        }
    }
}
