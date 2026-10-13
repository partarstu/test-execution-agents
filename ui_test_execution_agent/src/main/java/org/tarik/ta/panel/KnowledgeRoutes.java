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

import io.javalin.http.Context;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.dto.panel.knowledge.ElementPage.Sort;
import org.tarik.ta.dto.panel.knowledge.GraphSlice;
import org.tarik.ta.dto.panel.knowledge.SearchResults;
import org.tarik.ta.knowledge_graph.model.node.UiElement.Screenshot;
import org.tarik.ta.knowledge_graph.service.KnowledgeViewService;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static io.javalin.http.HttpStatus.BAD_REQUEST;
import static io.javalin.http.HttpStatus.NOT_FOUND;
import static io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE;
import static java.util.Locale.ROOT;
import static org.tarik.ta.panel.PromptInput.require;
import static org.tarik.ta.panel.PromptInput.text;

/** The read-only routes of the *Knowledge* tab; each validates its input before any query and answers every text redacted. */
@Singleton
final class KnowledgeRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeRoutes.class);
    static final String SUMMARY_PATH = "/api/knowledge/summary";
    static final String SEARCH_PATH = "/api/knowledge/search";
    static final String NEIGHBOURHOOD_PATH = "/api/knowledge/procedures/{id}/neighbourhood";
    static final String TEST_CASE_LENS_PATH = "/api/knowledge/test-cases/procedures";
    static final String PROCEDURE_PATH = "/api/knowledge/procedures/{id}";
    static final String ELEMENTS_PATH = "/api/knowledge/elements";
    static final String ELEMENT_PATH = "/api/knowledge/elements/{id}";
    static final String ELEMENT_SCREENSHOT_PATH = "/api/knowledge/elements/{id}/screenshot";
    static final String APPEARANCE_SCREENSHOT_PATH = "/api/knowledge/elements/{id}/appearances/{appearanceId}/screenshot";
    static final String OVERVIEW_PATH = "/api/knowledge/overview";
    static final String TRENDS_PATH = "/api/knowledge/trends";
    static final String FAILURES_PATH = "/api/knowledge/failures";
    static final int MAX_SEARCH_LENGTH = 100;
    static final int MAX_TEST_CASE_NAME_LENGTH = 500;
    static final int MAX_WEEKS = 52;
    private static final int DEFAULT_WEEKS = 12;
    private static final Set<String> SCREENSHOT_TYPES = Set.of("image/png", "image/jpeg");
    private static final Map<String, String> NOT_FOUND_BODY = Map.of("error", "Not found");
    private static final Map<String, String> UNAVAILABLE_BODY = Map.of("error", "Knowledge graph unavailable");

    private final KnowledgeViewService service;

    KnowledgeRoutes(KnowledgeViewService service) {
        this.service = service;
    }

    void summary(@NotNull Context context) {
        serve(context, SUMMARY_PATH, () -> Optional.of(service.summary()));
    }

    void search(@NotNull Context context) {
        serve(context, SEARCH_PATH, () -> Optional.of(service.search(requiredText(context.queryParam("q"), MAX_SEARCH_LENGTH, "q"))));
    }

    void neighbourhood(@NotNull Context context) {
        serve(context, NEIGHBOURHOOD_PATH, () -> service.neighbourhood(uuid(context.pathParam("id"))));
    }

    void testCaseLens(@NotNull Context context) {
        serve(context, TEST_CASE_LENS_PATH,
                () -> service.testCaseLens(requiredText(context.queryParam("name"), MAX_TEST_CASE_NAME_LENGTH, "name")));
    }

    void procedure(@NotNull Context context) {
        serve(context, PROCEDURE_PATH, () -> service.procedureDetails(uuid(context.pathParam("id"))));
    }

    void elements(@NotNull Context context) {
        serve(context, ELEMENTS_PATH,
                () -> Optional.of(service.elements(sort(context.queryParam("sort")), offset(context.queryParam("offset")))));
    }

    void element(@NotNull Context context) {
        serve(context, ELEMENT_PATH, () -> service.elementDetails(uuid(context.pathParam("id"))));
    }

    void elementScreenshot(@NotNull Context context) {
        screenshot(context, ELEMENT_SCREENSHOT_PATH, () -> service.elementScreenshot(uuid(context.pathParam("id"))));
    }

    void appearanceScreenshot(@NotNull Context context) {
        screenshot(context, APPEARANCE_SCREENSHOT_PATH,
                () -> service.appearanceScreenshot(uuid(context.pathParam("id")), uuid(context.pathParam("appearanceId"))));
    }

    void overview(@NotNull Context context) {
        serve(context, OVERVIEW_PATH, () -> Optional.of(service.overview()));
    }

    void trends(@NotNull Context context) {
        serve(context, TRENDS_PATH, () -> Optional.of(service.trends(weeks(context.queryParam("weeks")))));
    }

    void failures(@NotNull Context context) {
        serve(context, FAILURES_PATH, () -> service.failures(uuid(context.queryParam("procedureId"))));
    }

    private static void serve(Context context, String endpoint, Supplier<Optional<?>> query) {
        long startNanos = System.nanoTime();
        answer(context, endpoint, () -> query.get().ifPresentOrElse(result -> {
            context.json(PanelJson.redactedTree(result));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Knowledge view {} served{} in {} ms", endpoint, sizeOf(result),
                        Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
            }
        }, () -> context.status(NOT_FOUND).json(NOT_FOUND_BODY)));
    }

    // Only the image types a browser renders safely are served, so a stored value cannot make the panel serve active content
    private static void screenshot(Context context, String endpoint, Supplier<Optional<Screenshot>> query) {
        answer(context, endpoint, () -> query.get()
                .filter(screenshot -> SCREENSHOT_TYPES.contains(screenshot.mimeType()))
                .ifPresentOrElse(screenshot -> serveImage(context, endpoint, screenshot),
                        () -> context.status(NOT_FOUND).json(NOT_FOUND_BODY)));
    }

    private static void serveImage(Context context, String endpoint, Screenshot screenshot) {
        byte[] image;
        try {
            image = Base64.getDecoder().decode(screenshot.base64EncodedImage());
        } catch (IllegalArgumentException e) {
            LOG.warn("The knowledge view {} found a screenshot that is not valid Base64", endpoint, e);
            context.status(NOT_FOUND).json(NOT_FOUND_BODY);
            return;
        }
        context.contentType(screenshot.mimeType()).result(image);
    }

    private static void answer(Context context, String endpoint, Runnable handler) {
        try {
            handler.run();
        } catch (PromptInputException e) {
            context.status(BAD_REQUEST).json(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            LOG.error("The knowledge view {} failed", endpoint, e);
            context.status(SERVICE_UNAVAILABLE).json(UNAVAILABLE_BODY);
        }
    }

    private static String sizeOf(Object result) {
        return switch (result) {
            case GraphSlice slice ->
                    " (%d nodes, %d edges, capped %b)".formatted(slice.nodes().size(), slice.edges().size(), slice.capped());
            case SearchResults results -> " (%d hits, capped %b)".formatted(results.hits().size(), results.capped());
            default -> "";
        };
    }

    private static String requiredText(@Nullable String value, int maxLength, String name) {
        var present = text(value, maxLength, name).strip();
        require(!present.isEmpty(), "%s is empty".formatted(name));
        return present;
    }

    private static UUID uuid(@Nullable String value) {
        try {
            var parsed = UUID.fromString(text(value, MAX_SEARCH_LENGTH, "The id"));
            // UUID.fromString also accepts shortened groups such as 1-1-1-1-1, which no node id has
            require(parsed.toString().equalsIgnoreCase(value), "The id is not a UUID");
            return parsed;
        } catch (IllegalArgumentException _) {
            throw new PromptInputException("The id is not a UUID");
        }
    }

    private static Sort sort(@Nullable String value) {
        if (value == null) {
            return Sort.STABILITY;
        }
        try {
            return Sort.valueOf(value.toUpperCase(ROOT));
        } catch (IllegalArgumentException _) {
            throw new PromptInputException("Unknown sort");
        }
    }

    private static int offset(@Nullable String value) {
        int offset = integer(value, 0, "offset");
        require(offset >= 0, "offset must not be negative");
        return offset;
    }

    private static int weeks(@Nullable String value) {
        int weeks = integer(value, DEFAULT_WEEKS, "weeks");
        require(weeks >= 1 && weeks <= MAX_WEEKS, "weeks must be between 1 and %d".formatted(MAX_WEEKS));
        return weeks;
    }

    private static int integer(@Nullable String value, int defaultValue, String name) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            throw new PromptInputException("%s is not a number".formatted(name));
        }
    }
}
