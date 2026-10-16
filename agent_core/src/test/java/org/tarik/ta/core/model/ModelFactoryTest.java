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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.AgentConfig;
import io.opentelemetry.api.OpenTelemetry;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.telemetry.GenAiMetrics;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ModelFactoryTest {
    private static final String MINIMAL_CHAT_COMPLETION = """
            {"id": "1", "object": "chat.completion", "created": 0, "model": "Qwen/Qwen3.8-27B",
             "choices": [{"index": 0, "message": {"role": "assistant", "content": "pong"}, "finish_reason": "stop"}]}""";
    private static final String MINIMAL_ANTHROPIC_MESSAGE = """
            {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-haiku-5-5",
             "content": [{"type": "text", "text": "pong"}], "stop_reason": "end_turn",
             "usage": {"input_tokens": 1, "output_tokens": 1}}""";

    private final AgentConfig agentConfig = new AgentConfig();
    private final BudgetManager budgetManager = new BudgetManager(agentConfig);
    private final ModelFactory modelFactory = new ModelFactory(agentConfig, budgetManager, new GenAiMetrics(OpenTelemetry.noop()));

    @Test
    void shouldGetGoogleModel() {
        GenAiModel model = modelFactory.getModel("test_operation", "gemini-pro", AgentConfig.ModelProvider.GOOGLE);
        assertThat(model).isNotNull();
        assertThat(model.chatModel()).isNotNull();
    }

    @Test
    void shouldGetOpenAiModel() {
        GenAiModel model = modelFactory.getModel("test_operation", "gpt-5.4", AgentConfig.ModelProvider.OPENAI);
        assertThat(model).isNotNull();
        assertThat(model.chatModel()).isNotNull();
    }

    @Test
    void shouldGetGroqModel() {
        GenAiModel model = modelFactory.getModel("test_operation", "mixtral-8x7b", AgentConfig.ModelProvider.GROQ);
        assertThat(model).isNotNull();
    }

    @Test
    void shouldGetAnthropicModel() {
        GenAiModel model = modelFactory.getModel("test_operation", "claude-3", AgentConfig.ModelProvider.ANTHROPIC);
        assertThat(model).isNotNull();
    }

    @Test
    void shouldSendNoTemperatureToAnthropic() {
        var config = spy(new AgentConfig());
        when(config.getTemperature()).thenReturn(0.3);
        when(config.getAnthropicApiKey()).thenReturn("test-key");
        var factory = new ModelFactory(config, new BudgetManager(config), new GenAiMetrics(OpenTelemetry.noop()));

        var model = factory.getModel("test_operation", "claude-haiku-5-5", AgentConfig.ModelProvider.ANTHROPIC);

        assertThat(model.chatModel().defaultRequestParameters().temperature()).isNull();
    }

    @Test
    void shouldAskAThinkingClaudeAdaptivelyAtTheConfiguredEffortWithoutForcingATool() throws IOException {
        var config = spy(new AgentConfig());
        when(config.isAnthropicThinkingEnabled()).thenReturn(true);
        when(config.getAnthropicThinkingEffort()).thenReturn(AgentConfig.AnthropicEffort.HIGH);

        var body = anthropicRequestBodyOf(config);

        assertThat(body.at("/thinking/type").asText()).isEqualTo("adaptive");
        assertThat(body.at("/output_config/effort").asText()).isEqualTo("high");
        assertThat(body.has("tool_choice")).isFalse();
    }

    @Test
    void shouldAskClaudeWithThinkingDisabledAndAForcedToolByDefault() throws IOException {
        var body = anthropicRequestBodyOf(spy(new AgentConfig()));

        assertThat(body.at("/thinking/type").asText()).isEqualTo("disabled");
        assertThat(body.at("/tool_choice/type").asText()).isEqualTo("any");
        assertThat(body.has("output_config")).isFalse();
    }

    private JsonNode anthropicRequestBodyOf(AgentConfig config) throws IOException {
        var receivedBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/messages", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            var response = MINIMAL_ANTHROPIC_MESSAGE.getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var address = server.getAddress();
            when(config.getAnthropicEndpoint()).thenReturn("http://%s:%d/v1/".formatted(address.getHostString(), address.getPort()));
            when(config.getAnthropicApiKey()).thenReturn("test-key");
            var factory = new ModelFactory(config, new BudgetManager(config), new GenAiMetrics(OpenTelemetry.noop()));

            assertThat(factory.getModel("test_operation", "claude-haiku-5-5", AgentConfig.ModelProvider.ANTHROPIC).chatModel()
                    .chat("ping")).isEqualTo("pong");
            return new ObjectMapper().readTree(receivedBody.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldGetHCompanyModel() {
        GenAiModel model = modelFactory.getModel("test_operation", "holo3-1-35b-a3b", AgentConfig.ModelProvider.HCOMPANY);
        assertThat(model).isNotNull();
        assertThat(model.chatModel()).isNotNull();
    }

    @Test
    void shouldGetQwenModel() {
        GenAiModel model = modelFactory.getModel("test_operation", "Qwen/Qwen3.8-27B", AgentConfig.ModelProvider.QWEN);
        assertThat(model).isNotNull();
        assertThat(model.chatModel()).isNotNull();
    }

    @Test
    void shouldGetSchemaConstrainedGroundingModelForEverySelfHostedProvider() {
        assertThat(modelFactory.getSchemaConstrainedGroundingModel("test_operation", "holo3-1-35b-a3b", AgentConfig.ModelProvider.HCOMPANY)
                .chatModel()).isNotNull();
        assertThat(modelFactory.getSchemaConstrainedGroundingModel("test_operation", "Qwen/Qwen3.8-27B", AgentConfig.ModelProvider.QWEN)
                .chatModel()).isNotNull();
    }

    /**
     * Pins that the shared H Company endpoint is asked with thinking on for a sub-agent, which otherwise never reaches the
     * final-result tool, and off for grounding, whose schema-constrained answer would otherwise arrive empty inside the think
     * block.
     */
    @Test
    void shouldAskHCompanyDifferentlyForAnAgentThanForGrounding() {
        var agentParameters = openAiParametersOf(
                modelFactory.getModel("test_operation", "holo3-1-35b-a3b", AgentConfig.ModelProvider.HCOMPANY));
        var groundingParameters = openAiParametersOf(
                modelFactory.getSchemaConstrainedGroundingModel("test_operation", "holo3-1-35b-a3b", AgentConfig.ModelProvider.HCOMPANY));

        assertThat(thinkingEnabledIn(agentParameters)).isEqualTo(agentConfig.isHCompanyAgentThinkingEnabled());
        assertThat(agentParameters.temperature()).isEqualTo(agentConfig.getHCompanyAgentTemperature());
        assertThat(thinkingEnabledIn(groundingParameters)).isFalse();
        assertThat(groundingParameters.temperature()).isEqualTo(agentConfig.getTemperature());
    }

    @Test
    void shouldGroundOnHCompanyAtTheConfiguredGlobalTemperature() {
        var config = spy(new AgentConfig());
        when(config.getTemperature()).thenReturn(0.3);
        var factory = new ModelFactory(config, new BudgetManager(config), new GenAiMetrics(OpenTelemetry.noop()));

        var parameters = openAiParametersOf(
                factory.getSchemaConstrainedGroundingModel("test_operation", "holo3-1-35b-a3b", AgentConfig.ModelProvider.HCOMPANY));

        assertThat(parameters.temperature()).isEqualTo(0.3);
    }

    @Test
    void shouldAskQwenWithTheConfiguredAgentThinkingAtTheGlobalTemperature() {
        var config = spy(new AgentConfig());
        when(config.isQwenAgentThinkingEnabled()).thenReturn(false);
        var factory = new ModelFactory(config, new BudgetManager(config), new GenAiMetrics(OpenTelemetry.noop()));

        var defaultParameters = openAiParametersOf(
                modelFactory.getModel("test_operation", "Qwen/Qwen3.8-27B", AgentConfig.ModelProvider.QWEN));
        var configuredParameters = openAiParametersOf(
                factory.getModel("test_operation", "Qwen/Qwen3.8-27B", AgentConfig.ModelProvider.QWEN));

        assertThat(thinkingEnabledIn(defaultParameters)).isTrue();
        assertThat(thinkingEnabledIn(configuredParameters)).isFalse();
        assertThat(configuredParameters.temperature()).isEqualTo(config.getTemperature());
    }

    /** vLLM rejects a tool choice on a request without tools, which a schema-constrained grounding request always is. */
    @Test
    void shouldSendNoToolChoiceWhenQwenGrounds() {
        var agentParameters = openAiParametersOf(
                modelFactory.getModel("test_operation", "Qwen/Qwen3.8-27B", AgentConfig.ModelProvider.QWEN));
        var groundingParameters = openAiParametersOf(
                modelFactory.getSchemaConstrainedGroundingModel("test_operation", "Qwen/Qwen3.8-27B", AgentConfig.ModelProvider.QWEN));

        assertThat(agentParameters.toolChoice()).isEqualTo(REQUIRED);
        assertThat(groundingParameters.toolChoice()).isNull();
        assertThat(thinkingEnabledIn(groundingParameters)).isFalse();
    }

    /** vLLM's uvicorn drops the body of a request that offers an h2c upgrade, so a cleartext endpoint must never get that offer. */
    @Test
    void getModel_shouldSendBodyWithoutH2cUpgrade_whenSelfHostedEndpointIsCleartext() throws IOException {
        var receivedRequest = new AtomicReference<ReceivedRequest>();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
            receivedRequest.set(new ReceivedRequest(exchange.getRequestHeaders().containsKey("Upgrade"), body));
            var response = MINIMAL_CHAT_COMPLETION.getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var config = mock(AgentConfig.class);
            var address = server.getAddress();
            when(config.getQwenEndpoint()).thenReturn("http://%s:%d/v1/".formatted(address.getHostString(), address.getPort()));
            when(config.getQwenApiKey()).thenReturn("test-key");
            var factory = new ModelFactory(config, budgetManager, new GenAiMetrics(OpenTelemetry.noop()));

            var answer = factory.getModel("test_operation", "Qwen/Qwen3.8-27B", AgentConfig.ModelProvider.QWEN).chatModel()
                    .chat("ping");

            assertThat(answer).isEqualTo("pong");
            assertThat(receivedRequest.get().offeredUpgrade()).isFalse();
            assertThat(receivedRequest.get().body()).contains("Qwen/Qwen3.8-27B", "ping");
        } finally {
            server.stop(0);
        }
    }

    private record ReceivedRequest(boolean offeredUpgrade, String body) {
    }

    private static OpenAiChatRequestParameters openAiParametersOf(GenAiModel model) {
        assertThat(model.chatModel().defaultRequestParameters()).isInstanceOf(OpenAiChatRequestParameters.class);
        return (OpenAiChatRequestParameters) model.chatModel().defaultRequestParameters();
    }

    @SuppressWarnings("unchecked")
    private static boolean thinkingEnabledIn(OpenAiChatRequestParameters parameters) {
        var chatTemplateKwargs = (Map<String, Object>) parameters.customParameters().get("chat_template_kwargs");
        return (boolean) chatTemplateKwargs.get("enable_thinking");
    }

    @Test
    void shouldRejectSchemaConstrainedGroundingForHostedProvider() {
        assertThatThrownBy(
                () -> modelFactory.getSchemaConstrainedGroundingModel("test_operation", "gemini-pro", AgentConfig.ModelProvider.GOOGLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GOOGLE");
    }

    @Test
    void genAiModel_shouldCloseIfCloseable() throws Exception {
        ChatModel chatModel = mock(ChatModel.class, withSettings().extraInterfaces(AutoCloseable.class));
        GenAiModel genAiModel = new GenAiModel(chatModel);

        genAiModel.close();

        verify((AutoCloseable) chatModel).close();
    }

    @Test
    void genAiModel_shouldNotFailIfNonCloseable() {
        ChatModel chatModel = mock(ChatModel.class);
        GenAiModel genAiModel = new GenAiModel(chatModel);

        genAiModel.close();
        // Should not throw anything
    }
}
