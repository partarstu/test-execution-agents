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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/** Reads the panel's event stream like a browser tab would, collecting events and comments until the server ends it. */
public final class SseTestClient implements AutoCloseable {
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ExecutorService reader = newVirtualThreadPerTaskExecutor();
    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    private final HttpResponse<Stream<String>> response;

    public sealed interface Received {
    }

    public record Event(String name, String data) implements Received {
    }

    public record Comment(String text) implements Received {
    }

    private record End() implements Received {
    }

    private SseTestClient(String baseUrl, String cookie, String userAgent) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/events"))
                .header("Cookie", cookie)
                .header("User-Agent", userAgent)
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        this.response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() == 200) {
            reader.submit(this::readEvents);
        }
    }

    public static SseTestClient open(@NotNull String baseUrl, @NotNull String cookie, @NotNull String userAgent)
            throws IOException, InterruptedException {
        return new SseTestClient(baseUrl, cookie, userAgent);
    }

    public int status() {
        return response.statusCode();
    }

    /** Returns the next event, skipping comments, or fails when none arrives in time or the stream ended. */
    public @NotNull Event nextEvent(@NotNull Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            var next = poll(deadline);
            if (next instanceof Event event) {
                return event;
            }
            if (next instanceof End) {
                throw new AssertionError("The event stream ended before the next event");
            }
        }
    }

    public @NotNull Comment nextComment(@NotNull Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            var next = poll(deadline);
            if (next instanceof Comment comment) {
                return comment;
            }
            if (next instanceof End) {
                throw new AssertionError("The event stream ended before the next comment");
            }
        }
    }

    /** Waits until the server ends the stream, dropping whatever arrives before. */
    public boolean awaitEnd(@NotNull Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!(poll(deadline) instanceof End)) {
                // skip events and comments until the stream ends
            }
            return true;
        } catch (AssertionError _) {
            return false;
        }
    }

    @Override
    public void close() {
        response.body().close();
        reader.shutdownNow();
        httpClient.shutdownNow();
    }

    private Received poll(long deadlineNanos) throws InterruptedException {
        var next = received.poll(Math.max(0, deadlineNanos - System.nanoTime()), NANOSECONDS);
        if (next == null) {
            throw new AssertionError("Nothing arrived on the event stream in time");
        }
        return next;
    }

    private void readEvents() {
        var name = new StringBuilder();
        var data = new StringBuilder();
        try (var lines = response.body()) {
            lines.forEach(line -> {
                if (line.startsWith(":")) {
                    received.add(new Comment(line.substring(1).trim()));
                } else if (line.startsWith("event:")) {
                    name.append(line.substring("event:".length()).trim());
                } else if (line.startsWith("data:")) {
                    data.append(line.substring("data:".length()).trim());
                } else if (line.isEmpty() && !name.isEmpty()) {
                    received.add(new Event(name.toString(), data.toString()));
                    name.setLength(0);
                    data.setLength(0);
                }
            });
        } catch (RuntimeException _) {
            // The stream failed, which the tests observe as its end
        } finally {
            received.add(new End());
        }
    }
}
