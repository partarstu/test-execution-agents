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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.service.AiServices;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.SchemaConstrainedUiElementGrounder;
import org.tarik.ta.agents.UiElementGroundingAgent;
import org.tarik.ta.core.model.CloudRunCredentials;
import org.tarik.ta.dto.BoundingBoxes;
import org.tarik.ta.dto.ElementRegion;
import org.tarik.ta.tools.GroundingBaselines.GroundingCase;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.AUTO;
import static dev.langchain4j.data.message.ImageContent.from;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparingDouble;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tarik.ta.agents.GroundingProtocol.HOLO_CLICK_POSITION;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;
import static org.tarik.ta.tools.GroundingBaselines.CASES;
import static org.tarik.ta.tools.GroundingBaselines.format;
import static org.tarik.ta.tools.GroundingBaselines.groundingConfig;
import static org.tarik.ta.tools.GroundingBaselines.imagePathOf;
import static org.tarik.ta.tools.GroundingBaselines.intersectionOverUnion;
import static org.tarik.ta.tools.GroundingBaselines.mean;
import static org.tarik.ta.tools.GroundingBaselines.percentile;
import static org.tarik.ta.tools.GroundingBaselines.productionTargetText;
import static org.tarik.ta.tools.GroundingBaselines.screenshotFor;
import static org.tarik.ta.tools.GroundingBaselines.stripJsonFence;
import static org.tarik.ta.tools.Holo31GroundingComparisonTest.ToolCallVariant.SUITE_DEFAULT;
import static org.tarik.ta.tools.Holo31GroundingComparisonTest.ToolCallVariant.THINKING_ENABLED;
import static org.tarik.ta.tools.Holo31GroundingComparisonTest.ToolCallVariant.TOOL_CHOICE_LEFT_TO_MODEL;
import static org.tarik.ta.core.AgentConfig.ModelProvider.HCOMPANY;
import static org.tarik.ta.core.model.OpenAiCompatibleHttpClients.httpClientBuilderFor;
import static org.tarik.ta.core.model.OpenAiCompatibleHttpClients.httpVersionFor;
import static org.tarik.ta.utils.ImageUtils.*;

/**
 * Measures how accurately Holo3.1 grounds the baseline elements under ten request protocols (documented, tool-calling, thinking,
 * tool-choice and phrasing variants, and the production {@code SchemaConstrainedUiElementGrounder}) at each of the {@link
 * #MAX_SCREENSHOT_MEGAPIXELS} budgets, to attribute its precision loss on data-driven elements. A location passes when it falls
 * inside the element's bounds, and every answer is also recorded as a {@link Deviation} that {@link #reportDeviationSummary()}
 * breaks down by protocol, budget and case.
 */
@Disabled("Manual comparison against the live Holo API endpoint")
class Holo31GroundingComparisonTest {
    private static final Logger LOG = LoggerFactory.getLogger(Holo31GroundingComparisonTest.class);
    private static final String HOSTED_BASE_URL = "https://api.hcompany.ai/v1/";
    /**
     * The deployment the rest of the project resolves from {@code hcompany.endpoint}, because hand-built requests to the hosted
     * API came back 404.
     */
    private static final UiTestAgentConfig DEPLOYMENT = new UiTestAgentConfig();
    private static final String BASE_URL =
            System.getenv().getOrDefault("HCOMPANY_BASE_URL", DEPLOYMENT.getHCompanyEndpoint());
    /**
     * The configured credential or a placeholder, which locally served models ignore, replaced by an identity token for Cloud Run
     * exactly as {@code ModelFactory} does in production.
     */
    private static final String API_KEY = CloudRunCredentials.resolve(BASE_URL, configuredCredentialOrPlaceholder());
    private static final String MODEL_NAME = System.getenv().getOrDefault("HCOMPANY_MODEL_NAME", "Hcompany/Holo-3.1-35B-A3B-NVFP4");
    /** A locally served model reaches the same accuracy far slower, so the timeout is overridable. */
    private static final boolean USES_HOSTED_API = HOSTED_BASE_URL.equals(BASE_URL);
    private static final Duration TIMEOUT =
            Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("HCOMPANY_TIMEOUT_SECONDS", "60")));
    /**
     * The swept screenshot budgets, 3072 to 8192 of Holo3.1's 32x32-pixel visual tokens, the largest leaving the 4K fixtures at
     * native resolution; smaller budgets would measure the downscaler more than the model.
     */
    private static final List<Double> MAX_SCREENSHOT_MEGAPIXELS = List.of(3.15, 3.67, 4.19, 5.24, 6.29, 7.34, 8.39);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().version(httpVersionFor(BASE_URL)).connectTimeout(TIMEOUT).build();
    /** Every answer this run produced, grouped along protocol, budget and case by {@link #reportDeviationSummary()}. */
    private static final List<Deviation> DEVIATIONS = new CopyOnWriteArrayList<>();

    private static final String BOUNDING_BOX_SYSTEM_PROMPT =
            loadSystemPrompt("element_locator/bounding_box", "v1.0.0", "element_bounding_box_prompt.md");
    private static final String CENTER_AND_SIZE_SYSTEM_PROMPT =
            loadSystemPrompt("element_locator/element_region", "v1.0.0", "element_region_prompt.md");

    /**
     * The {@code VisualLocalizerOutput} schema from the official element localization guide, sent both inline in the prompt
     * and via {@code structured_outputs} - the model was trained with it visible in both places.
     */
    private static final String LOCALIZER_SCHEMA = """
            {"type":"object","title":"VisualLocalizerOutput",\
            "properties":{"x":{"type":"integer","minimum":0,"maximum":1000,"description":"X coordinate as integer in [0, 1000]"},\
            "y":{"type":"integer","minimum":0,"maximum":1000,"description":"Y coordinate as integer in [0, 1000]"}},\
            "required":["x","y"]}""";

    /** The same schema extended by the element size, to check what asking for width and height costs the click position. */
    private static final String LOCALIZER_WITH_SIZE_SCHEMA = """
            {"type":"object","title":"VisualRegionOutput",\
            "properties":{"x":{"type":"integer","minimum":0,"maximum":1000,"description":"X coordinate of the click position as integer in [0, 1000]"},\
            "y":{"type":"integer","minimum":0,"maximum":1000,"description":"Y coordinate of the click position as integer in [0, 1000]"},\
            "width":{"type":"integer","minimum":0,"maximum":1000,"description":"Width of the element as integer in [0, 1000]"},\
            "height":{"type":"integer","minimum":0,"maximum":1000,"description":"Height of the element as integer in [0, 1000]"}},\
            "required":["x","y","width","height"]}""";

    private static final String OFFICIAL_PROMPT_TEMPLATE = """
            Localize an element on the GUI image according to the provided target and output a click position.
             * You must output a valid JSON following the format: %s
             Your target is:
            %s""";

    private static final String OFFICIAL_WITH_SIZE_PROMPT_TEMPLATE = """
            Localize an element on the GUI image according to the provided target and output a click position together with the size of that element.
             * You must output a valid JSON following the format: %s
             Your target is:
            %s""";

    @TestFactory
    Stream<DynamicTest> officialProtocolWithProductionTargetText() {
        return groundingTests((testCase, maxMegapixels) -> {
            var location = MAPPER.treeToValue(callOfficialProtocol(testCase, productionTargetText(testCase),
                    OFFICIAL_PROMPT_TEMPLATE, LOCALIZER_SCHEMA, maxMegapixels), NormalizedPoint.class);
            assertLocatedInsideElement(testCase, List.of(location.toAbsolutePoint(imageSizeOf(testCase))),
                    "official protocol, production target text", maxMegapixels);
        });
    }

    @TestFactory
    Stream<DynamicTest> officialProtocolWithResolvedTarget() {
        return groundingTests((testCase, maxMegapixels) -> {
            var location = MAPPER.treeToValue(callOfficialProtocol(testCase, testCase.resolvedTarget(),
                    OFFICIAL_PROMPT_TEMPLATE, LOCALIZER_SCHEMA, maxMegapixels), NormalizedPoint.class);
            assertLocatedInsideElement(testCase, List.of(location.toAbsolutePoint(imageSizeOf(testCase))),
                    "official protocol, resolved target", maxMegapixels);
        });
    }

    @TestFactory
    Stream<DynamicTest> officialProtocolAskingForClickCenterAndSize() {
        return groundingTests((testCase, maxMegapixels) -> {
            var region = MAPPER.treeToValue(callOfficialProtocol(testCase, productionTargetText(testCase),
                    OFFICIAL_WITH_SIZE_PROMPT_TEMPLATE, LOCALIZER_WITH_SIZE_SCHEMA, maxMegapixels), ElementRegion.class);
            var imageSize = imageSizeOf(testCase);
            assertRegionMatchesElement(testCase, region.toRectangles(imageSize.width, imageSize.height, true),
                    "official protocol, click center plus size", maxMegapixels);
        });
    }

    @TestFactory
    Stream<DynamicTest> productionBoundingBoxToolPath() {
        return groundingTests((testCase, maxMegapixels) -> {
            var boxes = callGroundingAgent(testCase, BOUNDING_BOX_SYSTEM_PROMPT, new BoundingBoxes(List.of()), maxMegapixels);
            assertRegionMatchesElement(testCase, boxes, "production bounding box tool path", maxMegapixels);
        });
    }

    @TestFactory
    Stream<DynamicTest> agentToolPathAskingForClickCenterAndSize() {
        return groundingTests((testCase, maxMegapixels) -> {
            var regions = callGroundingAgent(testCase, CENTER_AND_SIZE_SYSTEM_PROMPT, new ElementRegion(0, 0, 0, 0), maxMegapixels);
            assertRegionMatchesElement(testCase, regions, "agent tool path, click center plus size", maxMegapixels);
        });
    }

    /**
     * {@link #agentToolPathAskingForClickCenterAndSize} with thinking on, as H Company's own agent runs the model, telling
     * "thinking, not transport" apart from "transport regardless of thinking".
     */
    @TestFactory
    Stream<DynamicTest> agentToolPathWithThinkingEnabled() {
        return groundingTests((testCase, maxMegapixels) -> {
            var regions = callGroundingAgent(testCase, CENTER_AND_SIZE_SYSTEM_PROMPT, new ElementRegion(0, 0, 0, 0), maxMegapixels,
                    THINKING_ENABLED);
            assertRegionMatchesElement(testCase, regions, "agent tool path, thinking enabled", maxMegapixels);
        });
    }

    /**
     * {@link #agentToolPathAskingForClickCenterAndSize} with the tool choice left to the model, which is what production sends
     * while every other tool-calling arm forces it.
     */
    @TestFactory
    Stream<DynamicTest> agentToolPathWithoutForcedToolChoice() {
        return groundingTests((testCase, maxMegapixels) -> {
            var regions = callGroundingAgent(testCase, CENTER_AND_SIZE_SYSTEM_PROMPT, new ElementRegion(0, 0, 0, 0), maxMegapixels,
                    TOOL_CHOICE_LEFT_TO_MODEL);
            assertRegionMatchesElement(testCase, regions,
                    "agent tool path, tool choice left to model", maxMegapixels);
        });
    }

    /**
     * The documented protocol with the project's system prompt prepended, isolating what a system message alone costs.
     */
    @TestFactory
    Stream<DynamicTest> officialProtocolWithSystemPromptAdded() {
        return groundingTests((testCase, maxMegapixels) -> {
            var region = MAPPER.treeToValue(callOfficialProtocol(testCase, productionTargetText(testCase),
                    OFFICIAL_WITH_SIZE_PROMPT_TEMPLATE, LOCALIZER_WITH_SIZE_SCHEMA, maxMegapixels, CENTER_AND_SIZE_SYSTEM_PROMPT,
                    true), ElementRegion.class);
            var imageSize = imageSizeOf(testCase);
            assertLocatedInsideElement(testCase, region.toRectangles(imageSize.width, imageSize.height, true).stream()
                            .map(GroundingBaselines::centerOf).toList(),
                    "official protocol, system prompt added", maxMegapixels);
        });
    }

    /**
     * The documented protocol with the image after the text, which is the order the langchain4j agent sends, isolating what
     * the content order alone costs.
     */
    @TestFactory
    Stream<DynamicTest> officialProtocolWithTextBeforeImage() {
        return groundingTests((testCase, maxMegapixels) -> {
            var location = MAPPER.treeToValue(callOfficialProtocol(testCase, productionTargetText(testCase),
                    OFFICIAL_PROMPT_TEMPLATE, LOCALIZER_SCHEMA, maxMegapixels, null, false), NormalizedPoint.class);
            assertLocatedInsideElement(testCase, List.of(location.toAbsolutePoint(imageSizeOf(testCase))),
                    "official protocol, text before image", maxMegapixels);
        });
    }

    /**
     * The production grounder class itself, judged on the click point alone because the rectangle around it is a fixed-size
     * placeholder.
     */
    @TestFactory
    Stream<DynamicTest> productionSchemaConstrainedGrounder() {
        return schemaConstrainedGrounderTests(false, "production schema-constrained grounder");
    }

    /**
     * Pins the guard naming the parse failure of a thinking-on schema-constrained request, whose answer stays inside the think
     * block the chat template opened and leaves the content empty.
     */
    @Test
    void schemaConstrainedGrounderRejectsAnEmptyAnswerWithThinkingEnabled() {
        // The message is mocked rather than built: AiMessage.from rejects a null text outright, while the server really does
        // hand langchain4j one, which is the whole reason the guard exists.
        var aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(null);
        var chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(aiMessage).build());
        var grounder = new SchemaConstrainedUiElementGrounder(chatModel, groundingConfig(HCOMPANY), HOLO_CLICK_POSITION);

        assertThatThrownBy(() -> grounder.locate("any target", new BufferedImage(8, 8, TYPE_INT_RGB)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thinking disabled");
    }

    private static @NotNull Stream<DynamicTest> schemaConstrainedGrounderTests(boolean thinkingEnabled,
                                                                              @NotNull String protocolName) {
        var grounder = new SchemaConstrainedUiElementGrounder(groundingChatModel(false, thinkingEnabled), groundingConfig(HCOMPANY),
                HOLO_CLICK_POSITION);
        return groundingTests((testCase, maxMegapixels) -> {
            var screenshot = screenshotFor(testCase, maxMegapixels);
            var regions = grounder.locate(productionTargetText(testCase), screenshot.image());
            assertLocatedInsideElement(testCase, regions.stream().map(screenshot::inOriginalPixelSpace)
                    .map(GroundingBaselines::centerOf).toList(), protocolName, maxMegapixels);
        });
    }

    /**
     * Reproduces the request of H Company's element localization guide, built by hand because langchain4j cannot express its
     * {@code structured_outputs} and {@code chat_template_kwargs} body fields.
     */
    private static @NotNull JsonNode callOfficialProtocol(@NotNull GroundingCase testCase, @NotNull String target,
                                                          @NotNull String promptTemplate, @NotNull String schema,
                                                          double maxMegapixels)
            throws IOException, InterruptedException {
        return callOfficialProtocol(testCase, target, promptTemplate, schema, maxMegapixels, null, true);
    }

    /**
     * Sends the documented request with the screenshot downscaled to {@code maxMegapixels}, an optional {@code systemPrompt} the
     * documented protocol omits, and the image before the text when {@code imageFirst} is set.
     */
    private static @NotNull JsonNode callOfficialProtocol(@NotNull GroundingCase testCase, @NotNull String target,
                                                          @NotNull String promptTemplate, @NotNull String schema,
                                                          double maxMegapixels, @Nullable String systemPrompt,
                                                          boolean imageFirst)
            throws IOException, InterruptedException {
        var dataUri = "data:image/png;base64,%s".formatted(
                convertImageToBase64(screenshotFor(testCase, maxMegapixels).image(), "png"));

        var image = MAPPER.createObjectNode().put("type", "image_url");
        image.putObject("image_url").put("url", dataUri);
        var text = MAPPER.createObjectNode().put("type", "text").put("text", promptTemplate.formatted(schema, target));
        var content = MAPPER.createArrayNode();
        content.add(imageFirst ? image : text).add(imageFirst ? text : image);
        var message = MAPPER.createObjectNode().put("role", "user");
        message.set("content", content);

        var messages = MAPPER.createArrayNode();
        if (systemPrompt != null) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.add(message);

        var body = MAPPER.createObjectNode().put("model", MODEL_NAME).put("temperature", 0.0);
        body.set("messages", messages);
        applyOutputSchema(body, schema);
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

    /**
     * Joins the path onto the configured endpoint, which langchain4j normalises for the arms that go through it and this
     * one has to do for itself - a base URL without its trailing slash otherwise builds a path no server routes.
     */
    private static @NotNull URI chatCompletionsEndpoint() {
        var separator = BASE_URL.endsWith("/") ? "" : "/";
        return URI.create("%s%schat/completions".formatted(BASE_URL, separator));
    }

    /** The credential {@code hcompany.api.key} carries, or the placeholder an endpoint that authenticates by IAM ignores. */
    private static @NotNull String configuredCredentialOrPlaceholder() {
        var configured = DEPLOYMENT.getHCompanyApiKey();
        return configured.isBlank() ? "no-key-required-locally" : configured;
    }

    /**
     * Constrains the answer to the schema through H Company's {@code structured_outputs} or an OpenAI-compatible server's {@code
     * response_format}, which keeps the model on the JSON and must never be dropped when thinking is disabled.
     */
    private static void applyOutputSchema(@NotNull ObjectNode body, @NotNull String schema) throws IOException {
        if (USES_HOSTED_API) {
            var structuredOutputs = MAPPER.createObjectNode();
            structuredOutputs.set("json", MAPPER.readTree(schema));
            body.set("structured_outputs", structuredOutputs);
            return;
        }
        var jsonSchema = MAPPER.createObjectNode().put("name", "VisualLocalizerOutput");
        jsonSchema.set("schema", MAPPER.readTree(schema));
        var responseFormat = MAPPER.createObjectNode().put("type", "json_schema");
        responseFormat.set("json_schema", jsonSchema);
        body.set("response_format", responseFormat);
    }

    /**
     * Runs the very path {@code ElementLocatorTools#identifyBoundingBoxesUsingVision} uses, with the system prompt and the
     * final-result tool that {@code UiAgentsBeanFactory} would wire in for the given grounding output format.
     */
    private static @NotNull List<Rectangle> callGroundingAgent(@NotNull GroundingCase testCase, @NotNull String systemPrompt,
                                                               @NotNull Object finalResultTool, double maxMegapixels)
            throws IOException {
        return callGroundingAgent(testCase, systemPrompt, finalResultTool, maxMegapixels, SUITE_DEFAULT);
    }

    /**
     * Asks the grounding agent with the screenshot downscaled to {@code maxMegapixels}, moving only the property {@code variant}
     * names away from {@link ToolCallVariant#SUITE_DEFAULT}.
     */
    private static @NotNull List<Rectangle> callGroundingAgent(@NotNull GroundingCase testCase, @NotNull String systemPrompt,
                                                               @NotNull Object finalResultTool, double maxMegapixels,
                                                               @NotNull ToolCallVariant variant)
            throws IOException {
        var agent = AiServices.builder(UiElementGroundingAgent.class)
                .chatModel(groundingChatModel(variant.forcesToolChoice(), variant.thinkingEnabled()))
                .systemMessageProvider(_ -> systemPrompt)
                .tools(finalResultTool)
                .build();

        var prompt = productionTargetText(testCase);
        var screenshot = screenshotFor(testCase, maxMegapixels);
        var imageContent = from(getImage(screenshot.image(), "png"), AUTO);
        var userMessage = List.<Content>of(TextContent.from(prompt));
        var executionResult = agent.executeAndGetResult(() -> agent.identifyElementRegions(userMessage, imageContent));
        assertThat(executionResult.isSuccess()).as("grounding agent failed: %s", executionResult.getMessage()).isTrue();
        var payload = executionResult.getResultPayload();
        assertThat(payload).as("grounding agent returned no payload").isNotNull();
        var originalSize = screenshot.originalSize();
        return payload.toRectangles(originalSize.width, originalSize.height, true);
    }

    /**
     * Built as {@code ModelFactory} builds it for the H Company provider with thinking off, where only {@link
     * ToolCallVariant#TOOL_CHOICE_LEFT_TO_MODEL} and schema-constrained requests, which carry no tools, leave {@code
     * forceToolChoice} off as production does.
     */
    private static @NotNull ChatModel groundingChatModel(boolean forceToolChoice) {
        return groundingChatModel(forceToolChoice, false);
    }

    /**
     * {@link #groundingChatModel(boolean)} with the thinking mode as a parameter, so an arm flipping it differs from its
     * counterpart in nothing else.
     */
    private static @NotNull ChatModel groundingChatModel(boolean forceToolChoice, boolean thinkingEnabled) {
        var parameters = OpenAiChatRequestParameters.builder()
                .customParameters(Map.of("chat_template_kwargs", Map.of("enable_thinking", thinkingEnabled)));
        if (forceToolChoice) {
            parameters.toolChoice(REQUIRED);
        }
        return OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .httpClientBuilder(httpClientBuilderFor(BASE_URL))
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .maxRetries(1)
                .temperature(0.0)
                .timeout(TIMEOUT)
                .defaultRequestParameters(parameters.build())
                .build();
    }

    private static void assertLocatedInsideElement(@NotNull GroundingCase testCase, @NotNull List<Point> locations,
                                                   @NotNull String protocolName, double maxMegapixels) {
        var expected = testCase.expectedRegion();
        var label = protocolLabel(protocolName, maxMegapixels);
        // Recorded before the assertions, because a miss is the measurement worth having and an assertion would skip it.
        var deviation = recordDeviation(testCase, protocolName, maxMegapixels, locations);
        LOG.info("[{}] {} -> located at {}, element occupies {}, {}", label, testCase.name(), locations, expected,
                describe(deviation));
        assertThat(locations).as("%s returned no location", label).isNotEmpty();
        assertThat(locations)
                .as("%s: no location falls inside the element bounds %s", label, expected)
                .anyMatch(expected::contains);
    }

    /**
     * Measures the answer closest to the element's centre, which is the one the {@code anyMatch} assertion above would
     * accept, and files it under its protocol, budget and case so that {@link #reportDeviationSummary()} can group it
     * along any of the three.
     */
    private static @NotNull Optional<Deviation> recordDeviation(@NotNull GroundingCase testCase,
                                                                @NotNull String protocolName, double maxMegapixels,
                                                                @NotNull List<Point> locations) {
        var expected = testCase.expectedRegion();
        var target = centerOf(expected);
        var deviation = locations.stream()
                .mapToDouble(location -> location.distance(target))
                .min()
                .stream()
                .mapToObj(distance -> new Deviation(protocolName, maxMegapixels, testCase.name(), true,
                        expected.contains(closestTo(target, locations)), distance, distance / halfDiagonalOf(expected)))
                .findFirst();
        DEVIATIONS.add(deviation.orElseGet(
                () -> new Deviation(protocolName, maxMegapixels, testCase.name(), false, false, 0, 0)));
        return deviation;
    }

    private static @NotNull Point closestTo(@NotNull Point target, @NotNull List<Point> locations) {
        return locations.stream().min(comparingDouble(location -> location.distance(target))).orElseThrow();
    }

    /** Half the element's diagonal, the radius that turns a pixel offset into a figure comparable across element sizes. */
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

    /**
     * Judges a click center exactly as {@link #assertLocatedInsideElement} does, so that all protocols stay comparable, and
     * additionally requires the reported size to describe the same element rather than an arbitrary area.
     */
    private static void assertRegionMatchesElement(@NotNull GroundingCase testCase, @NotNull List<Rectangle> regions,
                                                   @NotNull String protocolName, double maxMegapixels) {
        var expected = testCase.expectedRegion();
        var label = protocolLabel(protocolName, maxMegapixels);
        regions.forEach(region -> LOG.info("[{}] {} -> region {} (center {}), element occupies {}, IoU {}",
                label, testCase.name(), region, centerOf(region), expected,
                String.format(ROOT, "%.2f", intersectionOverUnion(region, expected))));
        assertLocatedInsideElement(testCase, regions.stream().map(GroundingBaselines::centerOf).toList(),
                protocolName, maxMegapixels);
        assertThat(regions)
                .as("%s: no reported size overlaps the element bounds %s", label, expected)
                .anyMatch(region -> !region.intersection(expected).isEmpty());
    }


    /**
     * Runs the given protocol over every case at every screenshot size budget, naming each test after the pair it measures.
     */
    private static @NotNull Stream<DynamicTest> groundingTests(@NotNull GroundingAssertion assertion) {
        return MAX_SCREENSHOT_MEGAPIXELS.stream().flatMap(maxMegapixels -> CASES.stream()
                .map(testCase -> dynamicTest(protocolLabel(testCase.name(), maxMegapixels),
                        () -> assertion.assertGrounding(testCase, maxMegapixels))));
    }

    /**
     * Prints the deviations of every arm that ran by protocol, budget and case, plus the protocol-by-budget grid, after the whole
     * class and regardless of how many cases failed.
     */
    @AfterAll
    static void reportDeviationSummary() {
        if (DEVIATIONS.isEmpty()) {
            return;
        }
        reportByProtocol();
        reportByBudget();
        reportByCase();
        reportProtocolByBudgetGrid();
    }

    /** Which request protocol grounds best once every budget and every case it ran against is pooled. */
    private static void reportByProtocol() {
        LOG.info("=== accuracy by protocol, best first ===");
        groupedBy(Deviation::protocol).entrySet().stream()
                .sorted(byHitRateDescending())
                .forEach(entry -> LOG.info("  {} | {}", padded(entry.getKey(), 44), statisticsOf(entry.getValue())));
    }

    /** How accuracy moves with the resolution the screenshot reaches the model at, pooled over every protocol. */
    private static void reportByBudget() {
        LOG.info("=== accuracy by screenshot budget, smallest first ===");
        groupedBy(Deviation::maxMegapixels).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> LOG.info("  {} MP | {}", String.format(ROOT, "%5.2f", entry.getKey()),
                        statisticsOf(entry.getValue())));
    }

    /** Which screenshots the model actually fails on, which is the axis a per-protocol average hides completely. */
    private static void reportByCase() {
        LOG.info("=== accuracy by case, hardest first ===");
        groupedBy(Deviation::caseName).entrySet().stream()
                .sorted(byHitRateAscending())
                .forEach(entry -> LOG.info("  {} | {}", padded(entry.getKey(), 66), statisticsOf(entry.getValue())));
    }

    /**
     * The grid the two marginals above are taken from, so that a protocol holding up at one resolution only - which both
     * averages would hide - stands out as a row that moves.
     */
    private static void reportProtocolByBudgetGrid() {
        var budgets = DEVIATIONS.stream().map(Deviation::maxMegapixels).distinct().sorted().toList();
        LOG.info("=== hit rate, protocol x budget in MP ===");
        LOG.info("  {}{}", padded("protocol", 44),
                budgets.stream().map(budget -> String.format(ROOT, "%8.2f", budget)).collect(joining()));
        groupedBy(Deviation::protocol).entrySet().stream()
                .sorted(byHitRateDescending())
                .forEach(entry -> LOG.info("  {}{}", padded(entry.getKey(), 44), gridRowOf(entry.getValue(), budgets)));
    }

    private static @NotNull String gridRowOf(@NotNull List<Deviation> deviations, @NotNull List<Double> budgets) {
        var byBudget = deviations.stream().collect(groupingBy(Deviation::maxMegapixels));
        return budgets.stream()
                .map(budget -> byBudget.getOrDefault(budget, List.of()))
                .map(cell -> cell.isEmpty() ? "       -" : String.format(ROOT, "%7.0f%%", hitRateOf(cell) * 100))
                .collect(joining());
    }

    private static <K> @NotNull Map<K, List<Deviation>> groupedBy(@NotNull Function<Deviation, K> key) {
        return DEVIATIONS.stream().collect(groupingBy(key));
    }

    private static <K> @NotNull Comparator<Map.Entry<K, List<Deviation>>> byHitRateAscending() {
        return comparingDouble(entry -> hitRateOf(entry.getValue()));
    }

    private static <K> @NotNull Comparator<Map.Entry<K, List<Deviation>>> byHitRateDescending() {
        return comparingDouble((Map.Entry<K, List<Deviation>> entry) -> hitRateOf(entry.getValue())).reversed();
    }

    private static double hitRateOf(@NotNull List<Deviation> deviations) {
        return deviations.isEmpty() ? 0 : (double) hitsOf(deviations) / deviations.size();
    }

    private static long hitsOf(@NotNull List<Deviation> deviations) {
        return deviations.stream().filter(Deviation::hit).count();
    }

    /** The one line every table shares, so that a number means the same thing whichever axis it was grouped along. */
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

    private static @NotNull String protocolLabel(@NotNull String protocolName, double maxMegapixels) {
        return String.format(ROOT, "%s @ %.2f MP", protocolName, maxMegapixels);
    }

    private static @NotNull Dimension imageSizeOf(@NotNull GroundingCase testCase) throws IOException {
        var image = ImageIO.read(imagePathOf(testCase).toFile());
        return new Dimension(image.getWidth(), image.getHeight());
    }

    private static @NotNull Point centerOf(@NotNull Rectangle rectangle) {
        return new Point(rectangle.x + rectangle.width / 2, rectangle.y + rectangle.height / 2);
    }

    private record NormalizedPoint(int x, int y) {
        private @NotNull Point toAbsolutePoint(@NotNull Dimension imageSize) {
            return new Point(x * imageSize.width / 1000, y * imageSize.height / 1000);
        }
    }

    @FunctionalInterface
    private interface GroundingAssertion {
        void assertGrounding(@NotNull GroundingCase testCase, double maxMegapixels) throws Exception;
    }

    /**
     * How far one answer landed from its element, separating a near miss from a click across the screen where a pass/fail count
     * on nine cases cannot. Distances run centre to centre in the original screenshot's pixels, and {@code
     * relativeToHalfDiagonal} weighs a miss on a small target more than the same offset on a large one.
     */
    private record Deviation(String protocol, double maxMegapixels, String caseName, boolean answered, boolean hit,
                             double distancePixels, double relativeToHalfDiagonal) {
    }

    /**
     * How a tool-calling arm's request differs from {@link #SUITE_DEFAULT}. Each constant moves exactly one property, so
     * that the score difference between an arm and the default arm is attributable to that property alone.
     */
    enum ToolCallVariant {
        /** Thinking off and the tool choice forced, as every other tool-calling arm of this suite asks. */
        SUITE_DEFAULT(false, true),
        /** Thinking on, telling "thinking, not transport" apart from "transport regardless of thinking". */
        THINKING_ENABLED(true, true),
        /** The tool choice left to the model, which is the request production actually sends to a self-hosted provider. */
        TOOL_CHOICE_LEFT_TO_MODEL(false, false);

        private final boolean thinkingEnabled;
        private final boolean forcesToolChoice;

        ToolCallVariant(boolean thinkingEnabled, boolean forcesToolChoice) {
            this.thinkingEnabled = thinkingEnabled;
            this.forcesToolChoice = forcesToolChoice;
        }

        private boolean thinkingEnabled() {
            return thinkingEnabled;
        }

        private boolean forcesToolChoice() {
            return forcesToolChoice;
        }
    }
}
