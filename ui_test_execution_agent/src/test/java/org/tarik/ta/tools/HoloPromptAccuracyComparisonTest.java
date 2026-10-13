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
package org.tarik.ta.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.SchemaConstrainedUiElementGrounder;
import org.tarik.ta.core.model.CloudRunCredentials;
import org.tarik.ta.tools.GroundingBaselines.GroundingCase;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.HIGH;
import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparingDouble;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tarik.ta.agents.GroundingProtocol.HOLO_CLICK_POSITION;
import static org.tarik.ta.core.AgentConfig.ModelProvider.HCOMPANY;
import static org.tarik.ta.core.model.OpenAiCompatibleHttpClients.httpClientBuilderFor;
import static org.tarik.ta.core.model.OpenAiCompatibleHttpClients.httpVersionFor;
import static org.tarik.ta.tools.GroundingBaselines.CASES;
import static org.tarik.ta.tools.GroundingBaselines.centerOf;
import static org.tarik.ta.tools.GroundingBaselines.imagePathOf;
import static org.tarik.ta.tools.GroundingBaselines.productionTargetText;
import static org.tarik.ta.tools.GroundingBaselines.stripJsonFence;
import static org.tarik.ta.utils.ImageUtils.convertImageToBase64;

/**
 * Measures Holo3.1 grounding accuracy at native 4K resolution across different prompt templates, output schema titles,
 * and target formulations to isolate the cause of accuracy degradation between the baseline prompt and the official website guide.
 */
@Disabled("Manual comparison against the live Holo API endpoint")
class HoloPromptAccuracyComparisonTest {
    private static final Logger LOG = LoggerFactory.getLogger(HoloPromptAccuracyComparisonTest.class);
    private static final String HOSTED_BASE_URL = "https://api.hcompany.ai/v1/";
    private static final UiTestAgentConfig DEPLOYMENT = new UiTestAgentConfig();
    private static final String BASE_URL =
            System.getenv().getOrDefault("HCOMPANY_BASE_URL", DEPLOYMENT.getHCompanyEndpoint());
    private static final String API_KEY = CloudRunCredentials.resolve(BASE_URL, configuredCredentialOrPlaceholder());
    private static final String MODEL_NAME = System.getenv().getOrDefault("HCOMPANY_MODEL_NAME", "Hcompany/Holo-3.1-35B-A3B-NVFP4");
    private static final boolean USES_HOSTED_API = HOSTED_BASE_URL.equals(BASE_URL);
    private static final Duration TIMEOUT =
            Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("HCOMPANY_TIMEOUT_SECONDS", "60")));
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().version(httpVersionFor(BASE_URL)).connectTimeout(TIMEOUT).build();
    private static final List<Deviation> DEVIATIONS = new CopyOnWriteArrayList<>();

    enum PromptVariant {
        /**
         * Verified baseline: ClickAction schema, compact bullet format without empty lines, and production target text.
         */
        CLICK_ACTION_COMPACT_PRODUCTION_TARGET(
                "ClickAction, compact bullet, production target",
                """
                Localize an element on the GUI image according to the provided target and output a click position.
                 * You must output a valid JSON following the format: %s
                 Your target is:
                %s""",
                """
                {"type":"object","title":"ClickAction",\
                "properties":{"x":{"type":"integer","minimum":0,"maximum":1000},"y":{"type":"integer","minimum":0,"maximum":1000}},\
                "required":["x","y"]}""",
                "ClickAction",
                false
        ),

        VISUAL_LOCALIZER_LOOSE_PRODUCTION_TARGET(
                "VisualLocalizerOutput, loose newlines, production target",
                """
                Localize an element on the GUI image according to the provided target and output a click position.
                
                You must output a valid JSON following the format: %s
                
                Your target is:
                %s
                """,
                """
                {"type":"object","title":"VisualLocalizerOutput",\
                "properties":{"x":{"type":"integer","minimum":0,"maximum":1000,"description":"X coordinate as integer in [0, 1000]"},\
                "y":{"type":"integer","minimum":0,"maximum":1000,"description":"Y coordinate as integer in [0, 1000]"}},\
                "required":["x","y"]}""",
                "VisualLocalizerOutput",
                false
        ),

        /** The VisualLocalizerOutput schema with the compact bullet template, isolating the schema name from spacing. */
        VISUAL_LOCALIZER_COMPACT_PRODUCTION_TARGET(
                "VisualLocalizerOutput, compact bullet, production target",
                """
                Localize an element on the GUI image according to the provided target and output a click position.
                 * You must output a valid JSON following the format: %s
                 Your target is:
                %s""",
                """
                {"type":"object","title":"VisualLocalizerOutput",\
                "properties":{"x":{"type":"integer","minimum":0,"maximum":1000,"description":"X coordinate as integer in [0, 1000]"},\
                "y":{"type":"integer","minimum":0,"maximum":1000,"description":"Y coordinate as integer in [0, 1000]"}},\
                "required":["x","y"]}""",
                "VisualLocalizerOutput",
                false
        ),

        /** The ClickAction baseline schema with loose double newlines, isolating the penalty of whitespace drift alone. */
        CLICK_ACTION_LOOSE_PRODUCTION_TARGET(
                "ClickAction, loose newlines, production target",
                """
                Localize an element on the GUI image according to the provided target and output a click position.
                
                You must output a valid JSON following the format: %s
                
                Your target is:
                %s
                """,
                """
                {"type":"object","title":"ClickAction",\
                "properties":{"x":{"type":"integer","minimum":0,"maximum":1000},"y":{"type":"integer","minimum":0,"maximum":1000}},\
                "required":["x","y"]}""",
                "ClickAction",
                false
        ),

        /**
         * ClickAction with a markdown code fence surrounding the target text, testing whether target isolation improves accuracy.
         */
        CLICK_ACTION_FENCED_PRODUCTION_TARGET(
                "ClickAction, fenced target, production target",
                """
                Localize an element on the GUI image according to the provided target and output a click position.
                 * You must output a valid JSON following the format: %s
                 Your target is:
                ```
                %s
                ```""",
                """
                {"type":"object","title":"ClickAction",\
                "properties":{"x":{"type":"integer","minimum":0,"maximum":1000},"y":{"type":"integer","minimum":0,"maximum":1000}},\
                "required":["x","y"]}""",
                "ClickAction",
                false
        ),

        /**
         * VisualLocalizerOutput schema with compact bullet format and clean pre-resolved natural language target.
         */
        VISUAL_LOCALIZER_COMPACT_RESOLVED_TARGET(
                "VisualLocalizerOutput, compact bullet, resolved target",
                """
                Localize an element on the GUI image according to the provided target and output a click position.
                 * You must output a valid JSON following the format: %s
                 Your target is:
                %s""",
                """
                {"type":"object","title":"VisualLocalizerOutput",\
                "properties":{"x":{"type":"integer","minimum":0,"maximum":1000,"description":"X coordinate as integer in [0, 1000]"},\
                "y":{"type":"integer","minimum":0,"maximum":1000,"description":"Y coordinate as integer in [0, 1000]"}},\
                "required":["x","y"]}""",
                "VisualLocalizerOutput",
                true
        );

        private final String label;
        private final String template;
        private final String schemaJson;
        private final String schemaName;
        private final boolean usesResolvedTarget;

        PromptVariant(String label, String template, String schemaJson, String schemaName, boolean usesResolvedTarget) {
            this.label = label;
            this.template = template;
            this.schemaJson = schemaJson;
            this.schemaName = schemaName;
            this.usesResolvedTarget = usesResolvedTarget;
        }

        public String label() {
            return label;
        }

        public String formatPrompt(@NotNull GroundingCase testCase) {
            var target = usesResolvedTarget ? testCase.resolvedTarget() : productionTargetText(testCase);
            return template.formatted(schemaJson, target);
        }

        public String schemaJson() {
            return schemaJson;
        }

        public String schemaName() {
            return schemaName;
        }
    }

    @TestFactory
    Stream<DynamicTest> promptVariantAccuracyTests() {
        return Stream.of(PromptVariant.values()).flatMap(variant -> CASES.stream()
                .map(testCase -> dynamicTest("%s | %s".formatted(variant.label(), testCase.name()),
                        () -> assertPromptVariant(variant, testCase))));
    }

    @TestFactory
    Stream<DynamicTest> productionSchemaConstrainedGrounderTests() {
        return CASES.stream()
                .map(testCase -> dynamicTest("Production Grounder | %s".formatted(testCase.name()),
                        () -> assertProductionGrounder(testCase)));
    }

    private static void assertPromptVariant(@NotNull PromptVariant variant, @NotNull GroundingCase testCase)
            throws IOException, InterruptedException {
        var rawJson = callPromptVariant(testCase, variant);
        var location = MAPPER.treeToValue(rawJson, NormalizedPoint.class);
        var imageSize = imageSizeOf(testCase);
        var point = location.toAbsolutePoint(imageSize);
        assertLocatedInsideElement(testCase, List.of(point), variant.label());
    }

    private static void assertProductionGrounder(@NotNull GroundingCase testCase) throws IOException {
        var chatModel = OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .httpClientBuilder(httpClientBuilderFor(BASE_URL))
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .maxRetries(1)
                .temperature(0.0)
                .timeout(TIMEOUT)
                .defaultRequestParameters(OpenAiChatRequestParameters.builder()
                        .customParameters(Map.of("chat_template_kwargs", Map.of("enable_thinking", false)))
                        .build())
                .build();
        var grounder = new SchemaConstrainedUiElementGrounder(chatModel, groundingConfig(), HOLO_CLICK_POSITION);
        var originalImage = ImageIO.read(imagePathOf(testCase).toFile());
        var regions = grounder.locate(productionTargetText(testCase), originalImage);
        var locations = regions.stream().map(GroundingBaselines::centerOf).toList();
        assertLocatedInsideElement(testCase, locations, "Production Grounder");
    }

    private static @NotNull JsonNode callPromptVariant(@NotNull GroundingCase testCase, @NotNull PromptVariant variant)
            throws IOException, InterruptedException {
        var original = ImageIO.read(imagePathOf(testCase).toFile());
        var dataUri = "data:image/png;base64,%s".formatted(convertImageToBase64(original, "png"));

        var image = MAPPER.createObjectNode().put("type", "image_url");
        image.putObject("image_url").put("url", dataUri);
        var text = MAPPER.createObjectNode().put("type", "text").put("text", variant.formatPrompt(testCase));
        var content = MAPPER.createArrayNode();
        content.add(image).add(text);
        var message = MAPPER.createObjectNode().put("role", "user");
        message.set("content", content);

        var messages = MAPPER.createArrayNode();
        messages.add(message);

        var body = MAPPER.createObjectNode().put("model", MODEL_NAME).put("temperature", 0.0);
        body.set("messages", messages);
        applyOutputSchema(body, variant.schemaJson(), variant.schemaName());
        body.set("chat_template_kwargs", MAPPER.createObjectNode().put("enable_thinking", false));

        var request = HttpRequest.newBuilder(chatCompletionsEndpoint())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer %s".formatted(API_KEY))
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), UTF_8))
                .build();
        var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertThat(response.statusCode()).as("H Company API responded with %s", response.body()).isEqualTo(200);

        var rawContent = MAPPER.readTree(response.body()).at("/choices/0/message/content").asText();
        return MAPPER.readTree(stripJsonFence(rawContent));
    }

    private static void applyOutputSchema(@NotNull ObjectNode body, @NotNull String schema, @NotNull String schemaName)
            throws IOException {
        if (USES_HOSTED_API) {
            var structuredOutputs = MAPPER.createObjectNode();
            structuredOutputs.set("json", MAPPER.readTree(schema));
            body.set("structured_outputs", structuredOutputs);
            return;
        }
        var jsonSchema = MAPPER.createObjectNode().put("name", schemaName);
        jsonSchema.set("schema", MAPPER.readTree(schema));
        var responseFormat = MAPPER.createObjectNode().put("type", "json_schema");
        responseFormat.set("json_schema", jsonSchema);
        body.set("response_format", responseFormat);
    }

    private static void assertLocatedInsideElement(@NotNull GroundingCase testCase, @NotNull List<Point> locations,
                                                   @NotNull String variantLabel) {
        var expected = testCase.expectedRegion();
        var deviation = recordDeviation(testCase, variantLabel, locations);
        LOG.info("[{}] {} -> located at {}, element occupies {}, {}", variantLabel, testCase.name(), locations, expected,
                describe(deviation));
        assertThat(locations).as("%s returned no location", variantLabel).isNotEmpty();
        assertThat(locations)
                .as("%s: no location falls inside the element bounds %s", variantLabel, expected)
                .anyMatch(expected::contains);
    }

    private static @NotNull Optional<Deviation> recordDeviation(@NotNull GroundingCase testCase,
                                                                @NotNull String variantLabel,
                                                                @NotNull List<Point> locations) {
        var expected = testCase.expectedRegion();
        var target = centerOf(expected);
        var deviation = locations.stream()
                .mapToDouble(location -> location.distance(target))
                .min()
                .stream()
                .mapToObj(distance -> new Deviation(variantLabel, testCase.name(), true,
                        expected.contains(closestTo(target, locations)), distance, distance / halfDiagonalOf(expected)))
                .findFirst();
        DEVIATIONS.add(deviation.orElseGet(
                () -> new Deviation(variantLabel, testCase.name(), false, false, 0, 0)));
        return deviation;
    }

    private static @NotNull Point closestTo(@NotNull Point target, @NotNull List<Point> locations) {
        return locations.stream().min(comparingDouble(location -> location.distance(target))).orElseThrow();
    }

    private static double halfDiagonalOf(@NotNull Rectangle element) {
        return Math.max(Math.hypot(element.width, element.height) / 2, 1);
    }

    private static @NotNull String describe(@NotNull Optional<Deviation> deviation) {
        return deviation
                .map(value -> "off centre by %s px (%s of the element's half-diagonal), %s".formatted(
                        format(value.distancePixels()), format(value.relativeToHalfDiagonal()),
                        value.hit() ? "inside" : "OUTSIDE"))
                .orElse("no location returned");
    }

    private static @NotNull UiTestAgentConfig groundingConfig() {
        var config = mock(UiTestAgentConfig.class);
        when(config.getElementBoundingBoxAgentImageDetailLevel()).thenReturn(HIGH);
        when(config.getElementBoundingBoxAgentModelProvider()).thenReturn(HCOMPANY);
        when(config.getMaxModelImagePixels()).thenReturn(MAX_VALUE);
        when(config.isBoundingBoxAlreadyNormalized()).thenReturn(true);
        return config;
    }

    private static @NotNull Dimension imageSizeOf(@NotNull GroundingCase testCase) throws IOException {
        var image = ImageIO.read(imagePathOf(testCase).toFile());
        return new Dimension(image.getWidth(), image.getHeight());
    }

    private static @NotNull String configuredCredentialOrPlaceholder() {
        var configured = DEPLOYMENT.getHCompanyApiKey();
        return configured.isBlank() ? "no-key-required-locally" : configured;
    }

    private static @NotNull URI chatCompletionsEndpoint() {
        var separator = BASE_URL.endsWith("/") ? "" : "/";
        return URI.create("%s%schat/completions".formatted(BASE_URL, separator));
    }

    @AfterAll
    static void reportDeviationSummary() {
        if (DEVIATIONS.isEmpty()) {
            return;
        }
        reportByVariant();
        reportByCase();
        reportVariantByCaseGrid();
    }

    private static void reportByVariant() {
        LOG.info("=== accuracy by prompt variant, best first ===");
        groupedBy(Deviation::variant).entrySet().stream()
                .sorted(byHitRateDescending().thenComparing(byMedianDistanceAscending()))
                .forEach(entry -> LOG.info("  {} | {}", padded(entry.getKey(), 60), statisticsOf(entry.getValue())));
    }

    private static void reportByCase() {
        LOG.info("=== accuracy by case, hardest first ===");
        groupedBy(Deviation::caseName).entrySet().stream()
                .sorted(byHitRateAscending())
                .forEach(entry -> LOG.info("  {} | {}", padded(entry.getKey(), 66), statisticsOf(entry.getValue())));
    }

    private static void reportVariantByCaseGrid() {
        var cases = CASES.stream().map(GroundingCase::name).toList();
        LOG.info("=== hit status, prompt variant x case ===");
        groupedBy(Deviation::variant).entrySet().stream()
                .sorted(byHitRateDescending().thenComparing(byMedianDistanceAscending()))
                .forEach(entry -> {
                    var variantDeviations = entry.getValue().stream().collect(groupingBy(Deviation::caseName));
                    var row = cases.stream()
                            .map(caseName -> {
                                var devList = variantDeviations.getOrDefault(caseName, List.of());
                                if (devList.isEmpty()) {
                                    return "[ - ]";
                                }
                                var dev = devList.getFirst();
                                return dev.hit() ? "[HIT %3.0fpx]".formatted(dev.distancePixels()) : "[MISS %3.0fpx]".formatted(dev.distancePixels());
                            })
                            .collect(joining(" "));
                    LOG.info("  {} | {}", padded(entry.getKey(), 60), row);
                });
    }

    private static <K> @NotNull Map<K, List<Deviation>> groupedBy(@NotNull Function<Deviation, K> key) {
        return DEVIATIONS.stream().collect(groupingBy(key));
    }

    private static @NotNull Comparator<Map.Entry<String, List<Deviation>>> byHitRateAscending() {
        return comparingDouble(entry -> hitRateOf(entry.getValue()));
    }

    private static @NotNull Comparator<Map.Entry<String, List<Deviation>>> byHitRateDescending() {
        return comparingDouble((Map.Entry<String, List<Deviation>> entry) -> hitRateOf(entry.getValue())).reversed();
    }

    private static @NotNull Comparator<Map.Entry<String, List<Deviation>>> byMedianDistanceAscending() {
        return comparingDouble(entry -> {
            var distances = entry.getValue().stream().filter(Deviation::answered).mapToDouble(Deviation::distancePixels).sorted().toArray();
            return percentile(distances, 0.5);
        });
    }

    private static double hitRateOf(@NotNull List<Deviation> deviations) {
        return deviations.isEmpty() ? 0 : (double) hitsOf(deviations) / deviations.size();
    }

    private static long hitsOf(@NotNull List<Deviation> deviations) {
        return deviations.stream().filter(Deviation::hit).count();
    }

    private static @NotNull String statisticsOf(@NotNull List<Deviation> deviations) {
        var answered = deviations.stream().filter(Deviation::answered).toList();
        var distances = answered.stream().mapToDouble(Deviation::distancePixels).sorted().toArray();
        var relative = answered.stream().mapToDouble(Deviation::relativeToHalfDiagonal).sorted().toArray();
        var accuracy = String.format(ROOT, "%3d/%3d inside (%5.1f%%), %d unanswered", hitsOf(deviations),
                deviations.size(), hitRateOf(deviations) * 100, deviations.size() - answered.size());
        var pixels = String.format(ROOT, "px: median %6s, mean %6s, p90 %6s, worst %6s", format(percentile(distances, 0.5)),
                format(mean(distances)), format(percentile(distances, 0.9)), format(percentile(distances, 1.0)));
        return "%s | %s | half-diagonals: median %s".formatted(accuracy, pixels, format(percentile(relative, 0.5)));
    }

    private static @NotNull String padded(@NotNull String value, int width) {
        return String.format(ROOT, "%-" + width + "s", value);
    }

    private static double mean(double[] values) {
        return values.length == 0 ? Double.NaN : Arrays.stream(values).average().orElse(Double.NaN);
    }

    private static double percentile(double[] sorted, double quantile) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        var rank = (int) Math.ceil(quantile * sorted.length);
        return sorted[Math.min(Math.max(rank, 1), sorted.length) - 1];
    }

    private static @NotNull String format(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(ROOT, "%.1f", value);
    }

    private record NormalizedPoint(int x, int y) {
        private @NotNull Point toAbsolutePoint(@NotNull Dimension imageSize) {
            return new Point(x * imageSize.width / 1000, y * imageSize.height / 1000);
        }
    }

    private record Deviation(String variant, String caseName, boolean answered, boolean hit,
                             double distancePixels, double relativeToHalfDiagonal) {
    }
}
