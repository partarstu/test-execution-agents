package org.tarik.ta.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.AgentConfig.ModelProvider;
import org.tarik.ta.core.AgentConfig.GoogleApiProvider;
import org.tarik.ta.core.AgentConfig.AnthropicApiProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelFactoryTest {

    @Test
    void getModel_shouldReturnGoogleStudioAiModel() {
        try (MockedStatic<AgentConfig> config = mockStatic(AgentConfig.class)) {
            config.when(AgentConfig::getMaxRetries).thenReturn(1);
            config.when(AgentConfig::getMaxOutputTokens).thenReturn(100);
            config.when(AgentConfig::getTemperature).thenReturn(0.5);
            config.when(AgentConfig::getTopP).thenReturn(0.9);
            config.when(AgentConfig::isModelLoggingEnabled).thenReturn(false);
            config.when(AgentConfig::isThinkingOutputEnabled).thenReturn(false);
            config.when(AgentConfig::getGeminiThinkingBudget).thenReturn(1024);
            config.when(AgentConfig::getGeminiThinkingLevel).thenReturn("MINIMAL");
            
            config.when(AgentConfig::getGoogleApiProvider).thenReturn(GoogleApiProvider.STUDIO_AI);
            config.when(AgentConfig::getGoogleApiToken).thenReturn("fake-token");
            
            GenAiModel model = ModelFactory.getModel("gemini-pro", ModelProvider.GOOGLE);
            assertThat(model).isNotNull();
            assertThat(model.chatModel()).isInstanceOf(dev.langchain4j.model.googleai.GoogleAiGeminiChatModel.class);
        }
    }

    @Test
    void getModel_shouldReturnGoogleVertexAiModel() {
        try (MockedStatic<AgentConfig> config = mockStatic(AgentConfig.class)) {
            config.when(AgentConfig::getMaxRetries).thenReturn(1);
            config.when(AgentConfig::getMaxOutputTokens).thenReturn(100);
            config.when(AgentConfig::getTemperature).thenReturn(0.5);
            config.when(AgentConfig::getTopP).thenReturn(0.9);
            config.when(AgentConfig::isModelLoggingEnabled).thenReturn(false);
            
            config.when(AgentConfig::getGoogleApiProvider).thenReturn(GoogleApiProvider.VERTEX_AI);
            config.when(AgentConfig::getGoogleProject).thenReturn("fake-project");
            config.when(AgentConfig::getGoogleLocation).thenReturn("us-central1");
            
            GenAiModel model = ModelFactory.getModel("gemini-pro", ModelProvider.GOOGLE);
            assertThat(model).isNotNull();
            assertThat(model.chatModel()).isInstanceOf(dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel.class);
        }
    }

    @Test
    void getModel_shouldReturnOpenAiModel() {
        try (MockedStatic<AgentConfig> config = mockStatic(AgentConfig.class)) {
            config.when(AgentConfig::getMaxRetries).thenReturn(1);
            config.when(AgentConfig::getMaxOutputTokens).thenReturn(100);
            config.when(AgentConfig::getTemperature).thenReturn(0.5);
            config.when(AgentConfig::getTopP).thenReturn(0.9);
            config.when(AgentConfig::isModelLoggingEnabled).thenReturn(false);

            config.when(AgentConfig::getOpenAiApiKey).thenReturn("fake-key");
            config.when(AgentConfig::getOpenAiEndpoint).thenReturn("https://fake.endpoint");
            
            GenAiModel model = ModelFactory.getModel("gpt-4", ModelProvider.OPENAI);
            assertThat(model).isNotNull();
            assertThat(model.chatModel()).isInstanceOf(dev.langchain4j.model.azure.AzureOpenAiChatModel.class);
        }
    }
    
    @Test
    void getModel_shouldReturnGroqModel() {
         try (MockedStatic<AgentConfig> config = mockStatic(AgentConfig.class)) {
            config.when(AgentConfig::getMaxRetries).thenReturn(1);
            config.when(AgentConfig::getMaxOutputTokens).thenReturn(100);
            config.when(AgentConfig::getTemperature).thenReturn(0.5);
            config.when(AgentConfig::getTopP).thenReturn(0.9);
            config.when(AgentConfig::isModelLoggingEnabled).thenReturn(false);

            config.when(AgentConfig::getGroqEndpoint).thenReturn("https://api.groq.com/openai/v1");
            config.when(AgentConfig::getGroqApiKey).thenReturn("fake-groq-key");

            GenAiModel model = ModelFactory.getModel("llama3-70b", ModelProvider.GROQ);
            assertThat(model).isNotNull();
            // Groq uses OpenAiChatModel client
            assertThat(model.chatModel()).isInstanceOf(dev.langchain4j.model.openai.OpenAiChatModel.class);
        }
    }

    @Test
    void getModel_shouldReturnAnthropicModel() {
         try (MockedStatic<AgentConfig> config = mockStatic(AgentConfig.class)) {
            config.when(AgentConfig::getMaxRetries).thenReturn(1);
            config.when(AgentConfig::getMaxOutputTokens).thenReturn(100);
            config.when(AgentConfig::getTemperature).thenReturn(0.5);
            config.when(AgentConfig::getTopP).thenReturn(0.9);
            config.when(AgentConfig::isModelLoggingEnabled).thenReturn(false);

            config.when(AgentConfig::getAnthropicApiProvider).thenReturn(AnthropicApiProvider.ANTHROPIC_API);
            config.when(AgentConfig::getAnthropicApiKey).thenReturn("fake-anthropic-key");
            config.when(AgentConfig::getAnthropicEndpoint).thenReturn("https://api.anthropic.com");

            GenAiModel model = ModelFactory.getModel("claude-3-opus", ModelProvider.ANTHROPIC);
            assertThat(model).isNotNull();
            assertThat(model.chatModel()).isInstanceOf(dev.langchain4j.model.anthropic.AnthropicChatModel.class);
        }
    }

    @Test
    void getModel_shouldThrowException_whenAnthropicKeyIsMissing() {
         try (MockedStatic<AgentConfig> config = mockStatic(AgentConfig.class)) {
            config.when(AgentConfig::getMaxRetries).thenReturn(1);
            config.when(AgentConfig::getMaxOutputTokens).thenReturn(100);
            config.when(AgentConfig::getTemperature).thenReturn(0.5);
            config.when(AgentConfig::getTopP).thenReturn(0.9);
            config.when(AgentConfig::isModelLoggingEnabled).thenReturn(false);

            config.when(AgentConfig::getAnthropicApiProvider).thenReturn(AnthropicApiProvider.ANTHROPIC_API);
            config.when(AgentConfig::getAnthropicApiKey).thenReturn(""); // Empty key

            assertThatThrownBy(() -> ModelFactory.getModel("claude-3-opus", ModelProvider.ANTHROPIC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Anthropic API Key is missing");
        }
    }
}
