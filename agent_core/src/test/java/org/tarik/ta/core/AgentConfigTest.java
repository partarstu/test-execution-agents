/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.core;

import org.junit.jupiter.api.Test;
import org.tarik.ta.core.error.RetryPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentConfigTest {

    private final AgentConfig config = new AgentConfig();

    @Test
    void testMainConfig() {
        assertThat(config.getStartPort()).isEqualTo(7070);
        assertThat(config.getHost()).isEqualTo("localhost");
        assertThat(config.getExternalUrl()).isEqualTo("http://localhost:7070");
        assertThat(config.isDebugMode()).isFalse();
    }

    @Test
    void testRagConfig() {
        assertThat(config.getVectorDbProvider()).isEqualTo(AgentConfig.RagDbProvider.NEO4J);
        assertThat(config.getVectorDbUrl()).isEqualTo("http://localhost:6334");
        assertThat(config.getVectorDbToken()).isEmpty();
        assertThat(config.getRetrieverTopN()).isEqualTo(5);
    }

    @Test
    void testModelConfig() {
        assertThat(config.getMaxOutputTokens()).isEqualTo(5000);
        assertThat(config.getTemperature()).isEqualTo(0.0);
        assertThat(config.getTopP()).isEqualTo(1.0);
        assertThat(config.isModelLoggingEnabled()).isFalse();
        assertThat(config.isThinkingOutputEnabled()).isFalse();
        assertThat(config.getMaxRetries()).isEqualTo(10);
        assertThat(config.getGeminiThinkingLevel()).isEqualTo("MINIMAL");
    }

    @Test
    void testGoogleConfig() {
        assertThat(config.getGoogleApiProvider()).isEqualTo(AgentConfig.GoogleApiProvider.STUDIO_AI);
        assertThat(config.getGoogleApiToken()).isEqualTo("dummy_token");
        assertThat(config.getGoogleProject()).isEqualTo("dummy_project");
        assertThat(config.getGoogleLocation()).isEqualTo("dummy_location");
    }

    @Test
    void testOpenAiConfig() {
        assertThat(config.getOpenAiApiKey()).isEqualTo("dummy_openai_key");
        assertThat(config.getOpenAiEndpoint()).isEqualTo("http://dummy-openai-endpoint");
    }

    @Test
    void testGroqConfig() {
        assertThat(config.getGroqApiKey()).isEqualTo("dummy_groq_key");
        assertThat(config.getGroqEndpoint()).isEqualTo("http://dummy-groq-endpoint");
    }

    @Test
    void testAnthropicConfig() {
        assertThat(config.getAnthropicApiProvider()).isEqualTo(AgentConfig.AnthropicApiProvider.ANTHROPIC_API);
        assertThat(config.getAnthropicApiKey()).isEqualTo("dummy_anthropic_key");
        assertThat(config.getAnthropicEndpoint()).isEqualTo("https://api.anthropic.com/v1/");
    }

    @Test
    void testRetryConfig() {
        assertThat(config.getMaxActionExecutionDurationMillis()).isEqualTo(15000);
        assertThat(config.getActionVerificationDelayMillis()).isEqualTo(1000);

        RetryPolicy actionPolicy = config.getActionRetryPolicy();
        assertThat(actionPolicy.maxRetries()).isEqualTo(10);
        assertThat(actionPolicy.delayMillis()).isEqualTo(1000);
        assertThat(actionPolicy.timeoutMillis()).isEqualTo(10000);

        RetryPolicy verificationPolicy = config.getVerificationRetryPolicy();
        assertThat(verificationPolicy.maxRetries()).isEqualTo(10);
        assertThat(verificationPolicy.delayMillis()).isEqualTo(1000);
        assertThat(verificationPolicy.timeoutMillis()).isEqualTo(10000);
    }

    @Test
    void testBudgets() {
        assertThat(config.getAgentTokenBudget()).isEqualTo(1000000);
        assertThat(config.getAgentToolCallsBudget()).isEqualTo(5);
        assertThat(config.getAgentExecutionTimeBudgetSeconds()).isEqualTo(3000);
    }

    @Test
    void testAgentSpecificConfigs() {
        assertThat(config.getPreconditionActionAgentModelName()).isEqualTo("gemini-3-flash-preview");
        assertThat(config.getPreconditionActionAgentModelProvider()).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(config.getPreconditionAgentPromptVersion()).isEqualTo("v1.0.0");

        assertThat(config.getTestStepActionAgentModelName()).isEqualTo("gemini-3-flash-preview");
        assertThat(config.getTestStepActionAgentModelProvider()).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(config.getTestStepActionAgentPromptVersion()).isEqualTo("v1.0.0");

        assertThat(config.getTestCaseExtractionAgentModelName()).isEqualTo("gemini-3-flash-preview");
        assertThat(config.getTestCaseExtractionAgentModelProvider()).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(config.getTestCaseExtractionAgentPromptVersion()).isEqualTo("v1.0.0");
    }

    @Test
    void testGetModelProvider() {
        assertThat(config.getModelProvider("google")).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(config.getModelProvider("GOOGLE")).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(config.getModelProvider("openai")).isEqualTo(AgentConfig.ModelProvider.OPENAI);

        assertThatThrownBy(() -> config.getModelProvider("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown is not a valid ModelProvider value");
    }
}
