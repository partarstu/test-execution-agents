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

    @Test
    void testMainConfig() {
        assertThat(AgentConfig.getStartPort()).isEqualTo(7070);
        assertThat(AgentConfig.getHost()).isEqualTo("localhost");
        assertThat(AgentConfig.getExternalUrl()).isEqualTo("http://localhost:7070");
        assertThat(AgentConfig.isDebugMode()).isFalse();
    }

    @Test
    void testRagConfig() {
        assertThat(AgentConfig.getVectorDbProvider()).isEqualTo(AgentConfig.RagDbProvider.QDRANT);
        assertThat(AgentConfig.getVectorDbUrl()).isEqualTo("http://localhost:6334");
        assertThat(AgentConfig.getVectorDbToken()).isEmpty();
        assertThat(AgentConfig.getRetrieverTopN()).isEqualTo(5);
    }

    @Test
    void testModelConfig() {
        assertThat(AgentConfig.getMaxOutputTokens()).isEqualTo(5000);
        assertThat(AgentConfig.getTemperature()).isEqualTo(0.0);
        assertThat(AgentConfig.getTopP()).isEqualTo(1.0);
        assertThat(AgentConfig.isModelLoggingEnabled()).isFalse();
        assertThat(AgentConfig.isThinkingOutputEnabled()).isFalse();
        assertThat(AgentConfig.getGeminiThinkingBudget()).isEqualTo(5000);
        assertThat(AgentConfig.getMaxRetries()).isEqualTo(10);
        assertThat(AgentConfig.getGeminiThinkingLevel()).isEqualTo("MINIMAL");
    }

    @Test
    void testGoogleConfig() {
        assertThat(AgentConfig.getGoogleApiProvider()).isEqualTo(AgentConfig.GoogleApiProvider.STUDIO_AI);
        assertThat(AgentConfig.getGoogleApiToken()).isEqualTo("dummy_token");
        assertThat(AgentConfig.getGoogleProject()).isEqualTo("dummy_project");
        assertThat(AgentConfig.getGoogleLocation()).isEqualTo("dummy_location");
    }

    @Test
    void testOpenAiConfig() {
        assertThat(AgentConfig.getOpenAiApiKey()).isEqualTo("dummy_openai_key");
        assertThat(AgentConfig.getOpenAiEndpoint()).isEqualTo("http://dummy-openai-endpoint");
    }

    @Test
    void testGroqConfig() {
        assertThat(AgentConfig.getGroqApiKey()).isEqualTo("dummy_groq_key");
        assertThat(AgentConfig.getGroqEndpoint()).isEqualTo("http://dummy-groq-endpoint");
    }

    @Test
    void testAnthropicConfig() {
        assertThat(AgentConfig.getAnthropicApiProvider()).isEqualTo(AgentConfig.AnthropicApiProvider.ANTHROPIC_API);
        assertThat(AgentConfig.getAnthropicApiKey()).isEqualTo("dummy_anthropic_key");
        assertThat(AgentConfig.getAnthropicEndpoint()).isEqualTo("https://api.anthropic.com/v1/");
    }

    @Test
    void testRetryConfig() {
        assertThat(AgentConfig.getMaxActionExecutionDurationMillis()).isEqualTo(15000);
        assertThat(AgentConfig.getActionVerificationDelayMillis()).isEqualTo(1000);

        RetryPolicy actionPolicy = AgentConfig.getActionRetryPolicy();
        assertThat(actionPolicy.maxRetries()).isEqualTo(10);
        assertThat(actionPolicy.delayMillis()).isEqualTo(1000);
        assertThat(actionPolicy.timeoutMillis()).isEqualTo(10000);

        RetryPolicy verificationPolicy = AgentConfig.getVerificationRetryPolicy();
        assertThat(verificationPolicy.maxRetries()).isEqualTo(10);
        assertThat(verificationPolicy.delayMillis()).isEqualTo(1000);
        assertThat(verificationPolicy.timeoutMillis()).isEqualTo(10000);
    }

    @Test
    void testBudgets() {
        assertThat(AgentConfig.getAgentTokenBudget()).isEqualTo(1000000);
        assertThat(AgentConfig.getAgentToolCallsBudget()).isEqualTo(5);
        assertThat(AgentConfig.getAgentExecutionTimeBudgetSeconds()).isEqualTo(3000);
    }

    @Test
    void testAgentSpecificConfigs() {
        assertThat(AgentConfig.getPreconditionActionAgentModelName()).isEqualTo("gemini-3-flash-preview");
        assertThat(AgentConfig.getPreconditionActionAgentModelProvider()).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(AgentConfig.getPreconditionAgentPromptVersion()).isEqualTo("v1.0.0");

        assertThat(AgentConfig.getTestStepActionAgentModelName()).isEqualTo("gemini-3-flash-preview");
        assertThat(AgentConfig.getTestStepActionAgentModelProvider()).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(AgentConfig.getTestStepActionAgentPromptVersion()).isEqualTo("v1.0.0");

        assertThat(AgentConfig.getTestCaseExtractionAgentModelName()).isEqualTo("gemini-3-flash-preview");
        assertThat(AgentConfig.getTestCaseExtractionAgentModelProvider()).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(AgentConfig.getTestCaseExtractionAgentPromptVersion()).isEqualTo("v1.0.0");
    }

    @Test
    void testGetModelProvider() {
        assertThat(AgentConfig.getModelProvider("google")).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(AgentConfig.getModelProvider("GOOGLE")).isEqualTo(AgentConfig.ModelProvider.GOOGLE);
        assertThat(AgentConfig.getModelProvider("openai")).isEqualTo(AgentConfig.ModelProvider.OPENAI);
        
        assertThatThrownBy(() -> AgentConfig.getModelProvider("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown is not a supported model provider");
    }
}
