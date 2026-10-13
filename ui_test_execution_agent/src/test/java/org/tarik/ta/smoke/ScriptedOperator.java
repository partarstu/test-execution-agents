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
package org.tarik.ta.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.panel.PanelTestFixture;
import org.tarik.ta.panel.SseTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

/**
 * An operator in a browser tab: it logs in to the panel, follows the event stream and answers every prompt that opens over the
 * panel's HTTP API, as the script tells it. It records the prompts it saw.
 */
final class ScriptedOperator implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration POLL = Duration.ofMillis(200);

    /** A prompt as the operator saw it: its type and its payload. */
    record SeenPrompt(String id, String type, JsonNode payload) {
    }

    private final String baseUrl;
    private final String cookie;
    private final Function<SeenPrompt, Optional<String>> script;
    private final List<SeenPrompt> seen = new CopyOnWriteArrayList<>();
    private final Set<String> handled = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = newVirtualThreadPerTaskExecutor();
    private final SseTestClient stream;
    private volatile boolean running = true;

    private ScriptedOperator(String baseUrl, Function<SeenPrompt, Optional<String>> script) throws Exception {
        this.baseUrl = baseUrl;
        this.script = script;
        var login = given().baseUri(baseUrl).header("Sec-Fetch-Site", "same-origin").contentType(ContentType.JSON)
                .body("{\"password\":\"%s\"}".formatted(PanelTestFixture.PASSWORD)).post("/api/login");
        this.cookie = "panel_session=" + login.getCookie("panel_session");
        this.stream = SseTestClient.open(baseUrl, cookie, "scripted operator");
        executor.submit(this::follow);
    }

    /** Starts the operator; the script returns the JSON answer for a prompt, or empty to leave it open. */
    static ScriptedOperator start(@NotNull String baseUrl, @NotNull Function<SeenPrompt, Optional<String>> script) throws Exception {
        return new ScriptedOperator(baseUrl, script);
    }

    List<SeenPrompt> seen() {
        return List.copyOf(seen);
    }

    /** Pauses, resumes or holds the countdown of a prompt as the browser does, and returns the status code of the answer. */
    int changeCountdown(@NotNull String promptId, @NotNull String change) {
        return given().baseUri(baseUrl).header("Cookie", cookie).header("Sec-Fetch-Site", "same-origin")
                .post("/api/prompts/%s/%s".formatted(promptId, change)).statusCode();
    }

    /** The last state of the run the panel sent, as a fresh snapshot shows it. */
    JsonNode snapshot() throws Exception {
        try (var fresh = SseTestClient.open(baseUrl, cookie, "scripted operator")) {
            return MAPPER.readTree(fresh.nextEvent(Duration.ofSeconds(5)).data()).get("state");
        }
    }

    @Override
    public void close() {
        running = false;
        stream.close();
        executor.shutdownNow();
    }

    private void follow() {
        while (running) {
            try {
                var event = stream.nextEvent(POLL);
                var data = MAPPER.readTree(event.data());
                switch (event.name()) {
                    case "prompt-opened" -> answer(data.get("prompt"));
                    case "snapshot" -> answer(data.get("state").get("prompt"));
                    default -> {
                    }
                }
            } catch (AssertionError _) {
                // No event within the poll interval; keep following until closed
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                throw new IllegalStateException("The scripted operator could not read the event stream", e);
            }
        }
    }

    private void answer(JsonNode prompt) {
        if (prompt == null || prompt.isNull() || !handled.add(prompt.get("id").asText())) {
            return;
        }
        var seenPrompt = new SeenPrompt(prompt.get("id").asText(), prompt.get("type").asText(), prompt.get("payload"));
        seen.add(seenPrompt);
        script.apply(seenPrompt).ifPresent(answer -> given().baseUri(baseUrl).header("Cookie", cookie)
                .header("Sec-Fetch-Site", "same-origin").contentType(ContentType.JSON).body(answer)
                .post("/api/prompts/%s/answer".formatted(seenPrompt.id())));
    }
}
