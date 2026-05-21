/*
 * agent-core - ${project.description}
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
import dev.langchain4j.model.googleai.GeminiMode;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicChatModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.AgentConfig.ModelProvider;

import java.util.List;

import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;

@Singleton
public class ModelFactory {
    private final AgentConfig agentConfig;
    private final ChatModelEventListener chatModelEventListener;

    @Inject
    public ModelFactory(AgentConfig agentConfig, ChatModelEventListener chatModelEventListener) {
        this.agentConfig = agentConfig;
        this.chatModelEventListener = chatModelEventListener;
    }

    public GenAiModel getModel(String modelName, ModelProvider modelProvider) {
        return getModel(modelName, modelProvider, agentConfig.getMaxRetries());
    }

    public GenAiModel getModel(String modelName, ModelProvider modelProvider, int maxRetries) {
        return switch (modelProvider) {
            case GOOGLE -> new GenAiModel(getGeminiModel(modelName, maxRetries));
            case OPENAI -> new GenAiModel(getOpenAiModel(modelName, maxRetries));
            case GROQ -> new GenAiModel(getGroqModel(modelName, maxRetries));
            case ANTHROPIC -> new GenAiModel(getAnthropicModel(modelName, maxRetries));
        };
    }

    private ChatModel getGeminiModel(String modelName, int maxRetries) {
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
                    .listeners(List.of(chatModelEventListener))
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
                    .listeners(List.of(chatModelEventListener))
                    .build();
        };
    }

    private ChatModel getOpenAiModel(String modelName, int maxRetries) {
        return OpenAiChatModel.builder()
                .baseUrl(agentConfig.getOpenAiEndpoint())
                .modelName(modelName)
                .maxRetries(maxRetries)
                .apiKey(agentConfig.getOpenAiApiKey())
                .maxCompletionTokens(agentConfig.getMaxOutputTokens())
                .temperature(agentConfig.getTemperature())
                .topP(agentConfig.getTopP())
                .defaultRequestParameters(ChatRequestParameters.builder().toolChoice(REQUIRED).build())
                .listeners(List.of(chatModelEventListener))
                .build();
    }

    private ChatModel getGroqModel(String modelName, int maxRetries) {
        return OpenAiChatModel.builder()
                .baseUrl(agentConfig.getGroqEndpoint())
                .modelName(modelName)
                .maxRetries(maxRetries)
                .apiKey(agentConfig.getGroqApiKey())
                .maxCompletionTokens(agentConfig.getMaxOutputTokens())
                .temperature(agentConfig.getTemperature())
                .topP(agentConfig.getTopP())
                .defaultRequestParameters(ChatRequestParameters.builder().toolChoice(REQUIRED).build())
                .listeners(List.of(chatModelEventListener))
                .build();
    }

    private ChatModel getAnthropicModel(String modelName, int maxRetries) {
        var provider = agentConfig.getAnthropicApiProvider();
        return switch (provider) {
            case ANTHROPIC_API -> {
                String apiKey = agentConfig.getAnthropicApiKey();
                if (apiKey.isBlank()) {
                    throw new IllegalArgumentException("Anthropic API Key is missing for ANTHROPIC_API provider");
                }
                yield AnthropicChatModel.builder()
                        .baseUrl(agentConfig.getAnthropicEndpoint())
                        .thinkingType("disabled")
                        .returnThinking(false)
                        .sendThinking(false)
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .maxRetries(maxRetries)
                        .maxTokens(agentConfig.getMaxOutputTokens())
                        .temperature(agentConfig.getTemperature())
                        .toolChoice(REQUIRED)
                        .listeners(List.of(chatModelEventListener))
                        .build();
            }
            case VERTEX_AI -> VertexAiAnthropicChatModel.builder()
                    .project(agentConfig.getGoogleProject())
                    .location(agentConfig.getGoogleLocation())
                    .modelName(modelName)
                    .maxTokens(agentConfig.getMaxOutputTokens())
                    .temperature(agentConfig.getTemperature())
                    .topP(agentConfig.getTopP())
                    .logResponses(agentConfig.isModelLoggingEnabled())
                    .listeners(List.of(chatModelEventListener))
                    .build();
        };
    }
}
