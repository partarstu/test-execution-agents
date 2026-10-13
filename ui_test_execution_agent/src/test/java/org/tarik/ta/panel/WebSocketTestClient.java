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
package org.tarik.ta.panel;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** The live view's browser side: a WebSocket to the relay that collects what it receives, optionally without reading on. */
public final class WebSocketTestClient implements AutoCloseable {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ByteArrayOutputStream received = new ByteArrayOutputStream();
    private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
    private final boolean reading;
    private final WebSocket webSocket;

    private WebSocketTestClient(String baseUrl, String cookie, boolean reading) throws ExecutionException, InterruptedException,
            TimeoutException {
        this.reading = reading;
        this.webSocket = httpClient.newWebSocketBuilder()
                .header("Cookie", cookie)
                .header("Origin", baseUrl)
                .buildAsync(URI.create(baseUrl.replaceFirst("^http", "ws") + "/api/screen/live"), new Listener())
                .get(10, TimeUnit.SECONDS);
    }

    public static WebSocketTestClient connect(@NotNull String baseUrl, @NotNull String cookie) throws Exception {
        return new WebSocketTestClient(baseUrl, cookie, true);
    }

    /** Connects a browser that reads the first frame and then stops reading, as a stalled tab does. */
    public static WebSocketTestClient connectWithoutReading(@NotNull String baseUrl, @NotNull String cookie) throws Exception {
        return new WebSocketTestClient(baseUrl, cookie, false);
    }

    public void send(byte @NotNull [] bytes) throws Exception {
        webSocket.sendBinary(ByteBuffer.wrap(bytes), true).get(10, TimeUnit.SECONDS);
    }

    public byte @NotNull [] awaitReceived(int length, @NotNull Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (received) {
            while (received.size() < length) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMillis <= 0) {
                    throw new AssertionError("Received %d of %d bytes in time".formatted(received.size(), length));
                }
                received.wait(remainingMillis);
            }
            return received.toByteArray();
        }
    }

    public int awaitClose(@NotNull Duration timeout) throws Exception {
        return closeCode.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean isClosed() {
        return closeCode.isDone();
    }

    @Override
    public void close() {
        webSocket.abort();
        httpClient.shutdownNow();
    }

    private final class Listener implements WebSocket.Listener {
        @Override
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            var bytes = new byte[data.remaining()];
            data.get(bytes);
            synchronized (received) {
                received.writeBytes(bytes);
                received.notifyAll();
            }
            if (reading) {
                socket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closeCode.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            closeCode.complete(-1);
        }
    }
}
