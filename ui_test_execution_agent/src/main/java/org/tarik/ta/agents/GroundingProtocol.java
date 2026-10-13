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
package org.tarik.ta.agents;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import org.jetbrains.annotations.NotNull;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static org.tarik.ta.utils.NormalizedCoordinates.NORMALIZED_RANGE;

/**
 * How a schema-constrained grounding request is phrased and its answer read back per model family, since each was trained on its
 * own localization format. It must never ask through a tool call: over the nine 4K baselines the same model scored 9/9
 * schema-constrained and 5/9 through a function call, missing entirely different parts of the screen.
 */
public enum GroundingProtocol {
    /**
     * H Company's official element localization instruction including its inline schema, which Holo3.1 was trained with and
     * without which the 35B misses the tightest targets, answered as a click position normalized to [0, 1000].
     */
    HOLO_CLICK_POSITION {
        /**
         * The guide's {@code VisualLocalizerOutput} schema, which its example interpolates into the instruction and sends
         * again as the structured output - the model was trained with it visible in both places.
         */
        private static final String SCHEMA = """
                {"type":"object","title":"VisualLocalizerOutput",\
                "properties":{"x":{"type":"integer","minimum":0,"maximum":1000,"description":"X coordinate as integer in [0, 1000]"},\
                "y":{"type":"integer","minimum":0,"maximum":1000,"description":"Y coordinate as integer in [0, 1000]"}},\
                "required":["x","y"]}""";

        private static final String INSTRUCTION_TEMPLATE = """
                Localize an element on the GUI image according to the provided target and output a click position.
                 * You must output a valid JSON following the format: %s
                 Your target is:
                %s
                """;

        private static final ResponseFormat FORMAT = ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("VisualLocalizerOutput")
                        .rootElement(JsonObjectSchema.builder()
                                .addIntegerProperty("x", "X coordinate as integer in [0, 1000]")
                                .addIntegerProperty("y", "Y coordinate as integer in [0, 1000]")
                                .required("x", "y")
                                .build())
                        .build())
                .build();

        @Override
        public @NotNull String instruction(@NotNull String elementDescription) {
            return INSTRUCTION_TEMPLATE.formatted(SCHEMA, elementDescription);
        }

        @Override
        public @NotNull ResponseFormat responseFormat() {
            return FORMAT;
        }

        @Override
        public @NotNull List<Rectangle> toRectangles(@NotNull String rawAnswer, int imageWidth, int imageHeight,
                                                     boolean alreadyNormalized) {
            var position = parse(rawAnswer, VisualLocalizerOutput.class);
            return placeholderAround(toPixels(position.x(), imageWidth, alreadyNormalized),
                    toPixels(position.y(), imageHeight, alreadyNormalized));
        }
    },

    /**
     * Qwen's own {@code 2d_grounding} phrasing and key name, because asked for plain {@code x} and {@code y} keys Qwen answers
     * with the box's top-left corner instead of a click position.
     */
    QWEN_CLICK_POINT {
        private static final String INSTRUCTION_TEMPLATE = """
                Localize an element on the GUI image according to the provided target and output a click position.
                
                You must output a valid JSON following the format: \
                {"point_2d": [x, y]}.
                
                Your target is:
                ```
                %s
                ```""";

        private static final ResponseFormat FORMAT = ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("GroundingOutput")
                        .rootElement(JsonObjectSchema.builder()
                                .addProperty("point_2d", JsonArraySchema.builder()
                                        .description("[x, y] of the click position, integers normalized to [0, 1000]")
                                        .items(new JsonIntegerSchema())
                                        .build())
                                .required("point_2d")
                                .build())
                        .build())
                .build();

        @Override
        public @NotNull String instruction(@NotNull String elementDescription) {
            return INSTRUCTION_TEMPLATE.formatted(elementDescription);
        }

        @Override
        public @NotNull ResponseFormat responseFormat() {
            return FORMAT;
        }

        @Override
        public @NotNull List<Rectangle> toRectangles(@NotNull String rawAnswer, int imageWidth, int imageHeight,
                                                     boolean alreadyNormalized) {
            var point = parse(rawAnswer, ClickPoint.class).point();
            return placeholderAround(toPixels(point.get(0), imageWidth, alreadyNormalized),
                    toPixels(point.get(1), imageHeight, alreadyNormalized));
        }
    };

    static final int CLICK_POINT_PLACEHOLDER_SIZE = 50;
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

    public abstract @NotNull String instruction(@NotNull String elementDescription);

    public abstract @NotNull ResponseFormat responseFormat();

    public abstract @NotNull List<Rectangle> toRectangles(@NotNull String rawAnswer, int imageWidth, int imageHeight,
                                                          boolean alreadyNormalized);

    private static int toPixels(int coordinate, int imageExtent, boolean alreadyNormalized) {
        return alreadyNormalized ? coordinate * imageExtent / NORMALIZED_RANGE : coordinate;
    }

    /**
     * A grounding model predicts where to click, never how large the element is, so the extent is a placeholder the
     * locator's own filters accept rather than a measurement anything downstream should trust.
     */
    private static @NotNull List<Rectangle> placeholderAround(int x, int y) {
        return List.of(new Rectangle(x - CLICK_POINT_PLACEHOLDER_SIZE / 2, y - CLICK_POINT_PLACEHOLDER_SIZE / 2,
                CLICK_POINT_PLACEHOLDER_SIZE, CLICK_POINT_PLACEHOLDER_SIZE));
    }

    private static <T> @NotNull T parse(@NotNull String rawAnswer, @NotNull Class<T> type) {
        try {
            return MAPPER.readValue(rawAnswer, type);
        } catch (IOException e) {
            var message = "Grounding model answered with '%s', which is not a valid %s".formatted(rawAnswer,
                    type.getSimpleName());
            throw new IllegalStateException(message, e);
        }
    }

    private record VisualLocalizerOutput(int x, int y) {
    }

    private record ClickPoint(@JsonProperty("point_2d") List<Integer> point) {
        private ClickPoint {
            if (point == null || point.size() < 2) {
                throw new IllegalArgumentException("point_2d must carry an x and a y, got %s".formatted(point));
            }
        }
    }
}
