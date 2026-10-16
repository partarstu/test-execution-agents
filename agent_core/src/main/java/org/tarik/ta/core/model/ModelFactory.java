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
package org.tarik.ta.core.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.googleai.GeminiMode;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicChatModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.AgentConfig.AnthropicApiProvider;
import org.tarik.ta.core.AgentConfig.GoogleApiProvider;
import org.tarik.ta.core.AgentConfig.ModelProvider;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.telemetry.GenAiMetrics;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static org.tarik.ta.core.model.OpenAiCompatibleHttpClients.httpClientBuilderFor;
import static org.tarik.ta.core.telemetry.GenAiAttributes.providerName;

@Singleton
public class ModelFactory {
    private static final String CHAT_TEMPLATE_KWARGS = "chat_template_kwargs";
    private static final String ENABLE_THINKING = "enable_thinking";
    private static final String OUTPUT_CONFIG = "output_config";
    private static final String EFFORT = "effort";
    private static final Logger LOG = LoggerFactory.getLogger(ModelFactory.class);
    private static final Logger SELF_HOSTED_RESPONSE_LOG = LoggerFactory.getLogger("model.http.responses");

    private final AgentConfig agentConfig;
    private final BudgetManager budgetManager;
    private final GenAiMetrics genAiMetrics;

    @Inject
    public ModelFactory(AgentConfig agentConfig, BudgetManager budgetManager, GenAiMetrics genAiMetrics) {
        this.agentConfig = agentConfig;
        this.budgetManager = budgetManager;
        this.genAiMetrics = genAiMetrics;
    }

    public GenAiModel getModel(@NotNull String operationName, String modelName, ModelProvider modelProvider) {
        return getModel(operationName, modelName, modelProvider, agentConfig.getMaxRetries());
    }

    /**
     * Creates the model serving the given sub-agent operation (e.g. {@code test_step_action}), under which every token and call
     * duration of the model is accounted.
     */
    public GenAiModel getModel(@NotNull String operationName, String modelName, ModelProvider modelProvider, int maxRetries) {
        var listener = buildListener(operationName, modelName, modelProvider);
        return switch (modelProvider) {
            case GOOGLE -> new GenAiModel(getGeminiModel(modelName, maxRetries, listener));
            case OPENAI -> new GenAiModel(getOpenAiModel(modelName, maxRetries, listener));
            case GROQ -> new GenAiModel(getGroqModel(modelName, maxRetries, listener));
            case ANTHROPIC -> new GenAiModel(getAnthropicModel(modelName, maxRetries, listener));
            case HCOMPANY -> new GenAiModel(getHCompanyAgentModel(modelName, maxRetries, listener));
            case QWEN -> new GenAiModel(getQwenModel(modelName, maxRetries, listener));
        };
    }

    /**
     * A GUI grounding model which answers under a JSON schema instead of through a tool call, and therefore needs neither
     * thinking nor the sampling headroom the agentic sub-agents run with.
     */
    public GenAiModel getSchemaConstrainedGroundingModel(@NotNull String operationName, String modelName, ModelProvider modelProvider) {
        var maxRetries = agentConfig.getMaxRetries();
        var listener = buildListener(operationName, modelName, modelProvider);
        return switch (modelProvider) {
            case HCOMPANY -> new GenAiModel(getHCompanyGroundingModel(modelName, maxRetries, listener));
            case QWEN -> new GenAiModel(getQwenGroundingModel(modelName, maxRetries, listener));
            default -> throw new IllegalArgumentException(
                    "Schema-constrained grounding is only served by a self-hosted provider, not by %s".formatted(modelProvider));
        };
    }

    /**
     * One listener per sub-agent model: it carries the operation, the provider and the endpoint of that model, so that the
     * usage it records needs nothing from the call site to be attributed.
     */
    private ChatModelEventListener buildListener(String operationName, String modelName, ModelProvider modelProvider) {
        return new ChatModelEventListener(budgetManager, genAiMetrics, operationName,
                providerName(modelProvider, servedByVertexAi(modelProvider)), modelName, serverAddress(modelProvider));
    }

    private boolean servedByVertexAi(ModelProvider modelProvider) {
        return switch (modelProvider) {
            case GOOGLE -> agentConfig.getGoogleApiProvider() == GoogleApiProvider.VERTEX_AI;
            case ANTHROPIC -> agentConfig.getAnthropicApiProvider() == AnthropicApiProvider.VERTEX_AI;
            default -> false;
        };
    }

    /** The request host reported as {@code server.address}, or {@code null} for a provider addressed through its vendor SDK. */
    private @Nullable String serverAddress(ModelProvider modelProvider) {
        return switch (modelProvider) {
            case OPENAI -> hostOf(agentConfig.getOpenAiEndpoint());
            case GROQ -> hostOf(agentConfig.getGroqEndpoint());
            case HCOMPANY -> hostOf(agentConfig.getHCompanyEndpoint());
            case QWEN -> hostOf(agentConfig.getQwenEndpoint());
            case ANTHROPIC -> servedByVertexAi(modelProvider) ? null : hostOf(agentConfig.getAnthropicEndpoint());
            case GOOGLE -> null;
        };
    }

    private static @Nullable String hostOf(@Nullable String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        try {
            return URI.create(endpoint.trim()).getHost();
        } catch (IllegalArgumentException e) {
            LOG.debug("Endpoint '{}' is not a valid URI, reporting no server address for it.", endpoint);
            return null;
        }
    }

    private ChatModel getGeminiModel(String modelName, int maxRetries, ChatModelEventListener listener) {
        var provider = agentConfig.getGoogleApiProvider();
        return switch (provider) {
            case STUDIO_AI -> GoogleAiGeminiChatModel.builder()
                    .apiKey(agentConfig.getGoogleApiToken())
                    .modelName(modelName)
                    .maxRetries(maxRetries)
                    .maxOutputTokens(agentConfig.getMaxOutputTokens())
                    .temperature(agentConfig.getTemperature())
                    .topP(agentConfig.getTopP())
                    .toolConfig(GeminiMode.ANY)
                    .logRequestsAndResponses(agentConfig.isModelLoggingEnabled())
                    .thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(agentConfig.isThinkingOutputEnabled())
                            .thinkingLevel(
                                    GeminiThinkingConfig.GeminiThinkingLevel.valueOf(agentConfig.getGeminiThinkingLevel().toUpperCase()))
                            .build())
                    .returnThinking(true)
                    .sendThinking(true)
                    .mediaResolutionPerPartEnabled(true)
                    .listeners(List.of(listener))
                    .build();

            case VERTEX_AI -> VertexAiGeminiChatModel.builder()
                    .project(agentConfig.getGoogleProject())
                    .location(agentConfig.getGoogleLocation())
                    .modelName(modelName)
                    .maxRetries(maxRetries)
                    .maxOutputTokens(agentConfig.getMaxOutputTokens())
                    .temperature((float) agentConfig.getTemperature())
                    .topP((float) agentConfig.getTopP())
                    .logResponses(agentConfig.isModelLoggingEnabled())
                    .listeners(List.of(listener))
                    .build();
        };
    }

    private ChatModel getOpenAiModel(String modelName, int maxRetries, ChatModelEventListener listener) {
        return OpenAiChatModel.builder()
                .baseUrl(agentConfig.getOpenAiEndpoint())
                .httpClientBuilder(httpClientBuilderFor(agentConfig.getOpenAiEndpoint()))
                .modelName(modelName)
                .maxRetries(maxRetries)
                .apiKey(agentConfig.getOpenAiApiKey())
                .maxCompletionTokens(agentConfig.getMaxOutputTokens())
                .temperature(agentConfig.getTemperature())
                .topP(agentConfig.getTopP())
                .defaultRequestParameters(ChatRequestParameters.builder().toolChoice(REQUIRED).build())
                .listeners(List.of(listener))
                .build();
    }

    private ChatModel getGroqModel(String modelName, int maxRetries, ChatModelEventListener listener) {
        return OpenAiChatModel.builder()
                .baseUrl(agentConfig.getGroqEndpoint())
                .httpClientBuilder(httpClientBuilderFor(agentConfig.getGroqEndpoint()))
                .modelName(modelName)
                .maxRetries(maxRetries)
                .apiKey(agentConfig.getGroqApiKey())
                .maxCompletionTokens(agentConfig.getMaxOutputTokens())
                .temperature(agentConfig.getTemperature())
                .topP(agentConfig.getTopP())
                .defaultRequestParameters(ChatRequestParameters.builder().toolChoice(REQUIRED).build())
                .listeners(List.of(listener))
                .build();
    }

    /** The H Company model as a sub-agent drives it, with thinking on and its own temperature instead of the global one. */
    private ChatModel getHCompanyAgentModel(String modelName, int maxRetries, ChatModelEventListener listener) {
        return getSelfHostedModel(modelName, maxRetries, listener, agentConfig.getHCompanyEndpoint(), agentConfig.getHCompanyApiKey(),
                null, agentConfig.isHCompanyAgentThinkingEnabled(), agentConfig.getHCompanyAgentTemperature());
    }

    /**
     * The same endpoint asked the way localization is documented and benchmarked: thinking off, because a schema-constrained
     * answer emitted inside an open think block arrives as reasoning with the content empty, and the global temperature,
     * which is what {@code Holo31GroundingComparisonTest} measured.
     */
    private ChatModel getHCompanyGroundingModel(String modelName, int maxRetries, ChatModelEventListener listener) {
        return getSelfHostedModel(modelName, maxRetries, listener, agentConfig.getHCompanyEndpoint(), agentConfig.getHCompanyApiKey(),
                null, false, agentConfig.getTemperature());
    }

    private ChatModel getQwenModel(String modelName, int maxRetries, ChatModelEventListener listener) {
        return getSelfHostedModel(modelName, maxRetries, listener, agentConfig.getQwenEndpoint(), agentConfig.getQwenApiKey(), REQUIRED,
                agentConfig.isQwenAgentThinkingEnabled(), agentConfig.getTemperature());
    }

    /** No tool choice, because vLLM rejects one on a schema-constrained request, which carries no tools. */
    private ChatModel getQwenGroundingModel(String modelName, int maxRetries, ChatModelEventListener listener) {
        return getSelfHostedModel(modelName, maxRetries, listener, agentConfig.getQwenEndpoint(), agentConfig.getQwenApiKey(), null,
                false, agentConfig.getTemperature());
    }

    /**
     * An OpenAI-compatible model the project hosts itself, with its thinking mode stated explicitly and a Cloud Run identity
     * token instead of a static key where the endpoint needs one.
     */
    private ChatModel getSelfHostedModel(String modelName, int maxRetries, ChatModelEventListener listener, @NotNull String endpoint,
                                         @NotNull String configuredApiKey, @Nullable ToolChoice toolChoice, boolean thinkingEnabled,
                                         double temperature) {
        // The H Company service ignores any tool choice, answering in prose with an empty tool call array.
        return OpenAiChatModel.builder()
                .baseUrl(endpoint)
                .httpClientBuilder(httpClientBuilderFor(endpoint))
                .modelName(modelName)
                .returnThinking(true)
                .sendThinking(false)
                .maxRetries(maxRetries)
                .logResponses(agentConfig.isModelLoggingEnabled())
                //.logger(SELF_HOSTED_RESPONSE_LOG)
                .apiKey(CloudRunCredentials.resolve(endpoint, configuredApiKey))
                .maxCompletionTokens(agentConfig.getMaxOutputTokens())
                .temperature(temperature)
                .topP(agentConfig.getTopP())
                .defaultRequestParameters(OpenAiChatRequestParameters.builder()
                        .customParameters(Map.of(CHAT_TEMPLATE_KWARGS, Map.of(ENABLE_THINKING, thinkingEnabled)))
                        .toolChoice(toolChoice)
                        .build())
                .listeners(List.of(listener))
                .build();
    }

    /** Sends no temperature, because newer Claude models such as Haiku 5.5 reject any non-default value with a 400 error. */
    private ChatModel getAnthropicModel(String modelName, int maxRetries, ChatModelEventListener listener) {
        var provider = agentConfig.getAnthropicApiProvider();
        return switch (provider) {
            case ANTHROPIC_API -> {
                String apiKey = agentConfig.getAnthropicApiKey();
                if (apiKey.isBlank()) {
                    throw new IllegalArgumentException("Anthropic API Key is missing for ANTHROPIC_API provider");
                }
                var builder = AnthropicChatModel.builder()
                        .baseUrl(agentConfig.getAnthropicEndpoint())
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .maxRetries(maxRetries)
                        .maxTokens(agentConfig.getMaxOutputTokens())
                        .listeners(List.of(listener));
                // A forced tool choice makes Claude answer with the tool call and skip thinking, so a thinking model chooses its
                // tools itself. langchain4j has no effort setting, so the effort goes out as Anthropic's own output_config.
                yield agentConfig.isAnthropicThinkingEnabled()
                        ? builder.thinkingType("adaptive")
                                .returnThinking(true)
                                .sendThinking(true)
                                .customParameters(Map.of(OUTPUT_CONFIG, Map.of(EFFORT,
                                        agentConfig.getAnthropicThinkingEffort().name().toLowerCase(Locale.ROOT))))
                                .build()
                        : builder.thinkingType("disabled")
                                .returnThinking(false)
                                .sendThinking(false)
                                .toolChoice(REQUIRED)
                                .build();
            }
            case VERTEX_AI -> VertexAiAnthropicChatModel.builder()
                    .project(agentConfig.getGoogleProject())
                    .location(agentConfig.getGoogleLocation())
                    .modelName(modelName)
                    .maxTokens(agentConfig.getMaxOutputTokens())
                    .topP(agentConfig.getTopP())
                    .logResponses(agentConfig.isModelLoggingEnabled())
                    .listeners(List.of(listener))
                    .build();
        };
    }
}
