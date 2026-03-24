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

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.utils.CommonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.core.utils.CommonUtils.parseStringAsInteger;

@Singleton
public class AgentConfig {
    private static final Logger LOG = LoggerFactory.getLogger(AgentConfig.class);
    private static final String CONFIG_FILE = "config.properties";

    private final Properties properties;

    // Main Config
    private final ConfigProperty<Integer> startPort;
    private final ConfigProperty<String> host;
    private final ConfigProperty<String> externalUrl;
    private final ConfigProperty<Boolean> debugMode;

    // RAG Config
    private final ConfigProperty<RagDbProvider> VECTOR_DB_PROVIDER;
    private final ConfigProperty<String> VECTOR_DB_URL;
    private final ConfigProperty<String> VECTOR_DB_KEY;
    private final ConfigProperty<Integer> RETRIEVER_TOP_N;
    private final ConfigProperty<Integer> MAX_OUTPUT_TOKENS;
    private final ConfigProperty<Double> TEMPERATURE;
    private final ConfigProperty<Double> TOP_P;
    private final ConfigProperty<Boolean> MODEL_LOGGING_ENABLED;
    private final ConfigProperty<Boolean> THINKING_OUTPUT_ENABLED;
    private final ConfigProperty<Integer> GEMINI_THINKING_BUDGET;
    private final ConfigProperty<Integer> MAX_RETRIES;
    private final ConfigProperty<String> GEMINI_THINKING_LEVEL;

    // Google API Config
    private final ConfigProperty<GoogleApiProvider> GOOGLE_API_PROVIDER;
    private final ConfigProperty<String> GOOGLE_API_TOKEN;
    private final ConfigProperty<String> GOOGLE_PROJECT;
    private final ConfigProperty<String> GOOGLE_LOCATION;

    // OpenAI API Config
    private final ConfigProperty<String> OPENAI_API_KEY;
    private final ConfigProperty<String> OPENAI_API_ENDPOINT;

    // Groq API Config
    private final ConfigProperty<String> GROQ_API_KEY;
    private final ConfigProperty<String> GROQ_API_ENDPOINT;

    // Anthropic API Config
    private final ConfigProperty<AnthropicApiProvider> ANTHROPIC_API_PROVIDER;
    private final ConfigProperty<String> ANTHROPIC_API_KEY;
    private final ConfigProperty<String> ANTHROPIC_API_ENDPOINT;

    // Timeout and Retry Config
    private final ConfigProperty<Integer> TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS;
    private final ConfigProperty<Integer> TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS;
    private final ConfigProperty<Integer> VERIFICATION_RETRY_TIMEOUT_MILLIS;
    private final ConfigProperty<Integer> ACTION_VERIFICATION_DELAY_MILLIS;
    private final ConfigProperty<Integer> MAX_ACTION_EXECUTION_DURATION_MILLIS;

    // Agent Specific Configs - Budgets
    private final ConfigProperty<Integer> AGENT_TOKEN_BUDGET;
    private final ConfigProperty<Integer> AGENT_TOOL_CALLS_BUDGET;
    private final ConfigProperty<Integer> AGENT_EXECUTION_TIME_BUDGET_SECONDS;

    // Precondition Agent
    private final ConfigProperty<String> PRECONDITION_AGENT_MODEL_NAME;
    private final ConfigProperty<ModelProvider> PRECONDITION_AGENT_MODEL_PROVIDER;
    private final ConfigProperty<String> PRECONDITION_AGENT_PROMPT_VERSION;

    // Test Step Action Agent
    private final ConfigProperty<String> TEST_STEP_ACTION_AGENT_MODEL_NAME;
    private final ConfigProperty<ModelProvider> TEST_STEP_ACTION_AGENT_MODEL_PROVIDER;
    private final ConfigProperty<String> TEST_STEP_ACTION_AGENT_PROMPT_VERSION;

    // Test Case Extraction Agent
    private final ConfigProperty<String> TEST_CASE_EXTRACTION_AGENT_MODEL_NAME;
    private final ConfigProperty<ModelProvider> TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER;
    private final ConfigProperty<String> TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION;

    public record ConfigProperty<T>(T value, boolean isSecret) {
    }

    public enum ModelProvider {
        GOOGLE, OPENAI, GROQ, ANTHROPIC
    }

    public enum GoogleApiProvider {
        STUDIO_AI, VERTEX_AI
    }

    public enum AnthropicApiProvider {
        ANTHROPIC_API, VERTEX_AI
    }

    public enum RagDbProvider {
        CHROMA, QDRANT, NEO4J
    }

    public AgentConfig() {
        // properties must be loaded first — all other fields depend on it
        this.properties = loadConfigPropertiesFromFile();

        // START_PORT must be assigned before EXTERNAL_URL (used in its default value)
        this.startPort = loadPropertyAsInteger("port", "PORT", "8005", false);
        this.host = getRequiredProperty("host", "AGENT_HOST", false);
        this.externalUrl = loadProperty("external.url", "EXTERNAL_URL",
                "http://localhost:%s".formatted(startPort.value()), s -> s, false);
        this.debugMode = loadProperty("debug.mode", "DEBUG_MODE", "false", Boolean::parseBoolean, false);

        // RAG Config
        this.VECTOR_DB_PROVIDER = getProperty("vector.db.provider", "VECTOR_DB_PROVIDER", "qdrant",
                s -> stream(RagDbProvider.values())
                        .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(s))
                        .findAny()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "%s is not a supported RAG DB provider. Supported ones: %s".formatted(s,
                                        Arrays.toString(RagDbProvider.values())))),
                false);
        this.VECTOR_DB_URL = getRequiredProperty("vector.db.url", "VECTOR_DB_URL", false);
        this.VECTOR_DB_KEY = loadProperty("vector.db.key", "VECTOR_DB_KEY", "", s -> s, true);
        this.RETRIEVER_TOP_N = loadPropertyAsInteger("retriever.top.n", "RETRIEVER_TOP_N", "5", false);
        this.MAX_OUTPUT_TOKENS = loadPropertyAsInteger("model.max.output.tokens", "MAX_OUTPUT_TOKENS", "5000", false);
        this.TEMPERATURE = loadPropertyAsDouble("model.temperature", "TEMPERATURE", "0.0", false);
        this.TOP_P = loadPropertyAsDouble("model.top.p", "TOP_P", "1.0", false);
        this.MODEL_LOGGING_ENABLED = loadProperty("model.logging.enabled", "LOG_MODEL_OUTPUT", "false", Boolean::parseBoolean, false);
        this.THINKING_OUTPUT_ENABLED = loadProperty("thinking.output.enabled", "OUTPUT_THINKING", "false", Boolean::parseBoolean, false);
        this.GEMINI_THINKING_BUDGET = loadPropertyAsInteger("gemini.thinking.budget", "GEMINI_THINKING_BUDGET", "5000", false);
        this.MAX_RETRIES = loadPropertyAsInteger("model.max.retries", "MAX_RETRIES", "10", false);
        this.GEMINI_THINKING_LEVEL = loadProperty("gemini.thinking.level", "GEMINI_THINKING_LEVEL", "MINIMAL", s -> s, false);

        // Google API Config
        this.GOOGLE_API_PROVIDER = getProperty("google.api.provider", "GOOGLE_API_PROVIDER", "studio_ai",
                s -> stream(GoogleApiProvider.values())
                        .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(s))
                        .findAny()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "%s is not a supported Google API provider. Supported ones: %s".formatted(s,
                                        Arrays.toString(GoogleApiProvider.values())))),
                false);
        this.GOOGLE_API_TOKEN = getRequiredProperty("google.api.token", "GOOGLE_API_KEY", true);
        this.GOOGLE_PROJECT = getRequiredProperty("google.project", "GOOGLE_PROJECT", false);
        this.GOOGLE_LOCATION = getRequiredProperty("google.location", "GOOGLE_LOCATION", false);

        // OpenAI API Config
        this.OPENAI_API_KEY = getRequiredProperty("azure.openai.api.key", "OPENAI_API_KEY", true);
        this.OPENAI_API_ENDPOINT = getRequiredProperty("azure.openai.endpoint", "OPENAI_API_ENDPOINT", false);

        // Groq API Config
        this.GROQ_API_KEY = getRequiredProperty("groq.api.key", "GROQ_API_KEY", true);
        this.GROQ_API_ENDPOINT = getRequiredProperty("groq.endpoint", "GROQ_ENDPOINT", false);

        // Anthropic API Config
        this.ANTHROPIC_API_PROVIDER = getProperty("anthropic.api.provider", "ANTHROPIC_API_PROVIDER", "anthropic_api",
                s -> stream(AnthropicApiProvider.values())
                        .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(s))
                        .findAny()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "%s is not a supported Anthropic API provider. Supported ones: %s".formatted(s,
                                        Arrays.toString(AnthropicApiProvider.values())))),
                false);
        this.ANTHROPIC_API_KEY = loadProperty("anthropic.api.key", "ANTHROPIC_API_KEY", "", s -> s, true);
        this.ANTHROPIC_API_ENDPOINT = loadProperty("anthropic.endpoint", "ANTHROPIC_ENDPOINT",
                "https://api.anthropic.com/v1/", s -> s, false);

        // Timeout and Retry Config
        this.TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS = loadPropertyAsInteger(
                "test.step.execution.retry.timeout.millis", "TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS", "10000", false);
        this.TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS = loadPropertyAsInteger(
                "test.step.execution.retry.interval.millis", "TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS", "1000", false);
        this.VERIFICATION_RETRY_TIMEOUT_MILLIS = loadPropertyAsInteger(
                "verification.retry.timeout.millis", "VERIFICATION_RETRY_TIMEOUT_MILLIS", "10000", false);
        this.ACTION_VERIFICATION_DELAY_MILLIS = loadPropertyAsInteger(
                "action.verification.delay.millis", "ACTION_VERIFICATION_DELAY_MILLIS", "1000", false);
        this.MAX_ACTION_EXECUTION_DURATION_MILLIS = loadPropertyAsInteger(
                "max.action.execution.duration.millis", "MAX_ACTION_EXECUTION_DURATION_MILLIS", "15000", false);

        // Agent Specific Configs - Budgets
        this.AGENT_TOKEN_BUDGET = loadPropertyAsInteger("agent.token.budget", "AGENT_TOKEN_BUDGET", "1000000", false);
        this.AGENT_TOOL_CALLS_BUDGET = loadPropertyAsInteger(
                "agent.tool.calls.budget.unattended", "AGENT_TOOL_CALLS_BUDGET_UNATTENDED", "5", false);
        this.AGENT_EXECUTION_TIME_BUDGET_SECONDS = loadPropertyAsInteger(
                "agent.execution.time.budget.seconds", "AGENT_EXECUTION_TIME_BUDGET_SECONDS", "3000", false);

        // Precondition Agent
        this.PRECONDITION_AGENT_MODEL_NAME = loadProperty(
                "precondition.agent.model.name", "PRECONDITION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.PRECONDITION_AGENT_MODEL_PROVIDER = getProperty(
                "precondition.agent.model.provider", "PRECONDITION_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.PRECONDITION_AGENT_PROMPT_VERSION = loadProperty(
                "precondition.agent.prompt.version", "PRECONDITION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);

        // Test Step Action Agent
        this.TEST_STEP_ACTION_AGENT_MODEL_NAME = loadProperty(
                "test.step.action.agent.model.name", "TEST_STEP_ACTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.TEST_STEP_ACTION_AGENT_MODEL_PROVIDER = getProperty(
                "test.step.action.agent.model.provider", "TEST_STEP_ACTION_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.TEST_STEP_ACTION_AGENT_PROMPT_VERSION = loadProperty(
                "test.step.action.agent.prompt.version", "TEST_STEP_ACTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);

        // Test Case Extraction Agent
        this.TEST_CASE_EXTRACTION_AGENT_MODEL_NAME = loadProperty(
                "test.case.extraction.agent.model.name", "TEST_CASE_EXTRACTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER = getProperty(
                "test.case.extraction.agent.model.provider", "TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION = loadProperty(
                "test.case.extraction.agent.prompt.version", "TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);
    }

    // -----------------------------------------------------
    // Main Config
    public int getStartPort() {
        return startPort.value();
    }

    public String getHost() {
        return host.value();
    }

    public String getExternalUrl() {
        return externalUrl.value();
    }

    public boolean isDebugMode() {
        return debugMode.value();
    }

    // -----------------------------------------------------
    // RAG Config
    public RagDbProvider getVectorDbProvider() {
        return VECTOR_DB_PROVIDER.value();
    }

    public String getVectorDbUrl() {
        return VECTOR_DB_URL.value();
    }

    public String getVectorDbToken() {
        return VECTOR_DB_KEY.value();
    }

    public int getRetrieverTopN() {
        return RETRIEVER_TOP_N.value();
    }

    // -----------------------------------------------------
    // Model Config
    protected ModelProvider getModelProvider(String s) {
        return stream(ModelProvider.values())
                .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(s))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(
                        "%s is not a supported model provider. Supported ones: %s".formatted(s,
                                Arrays.toString(ModelProvider.values()))));
    }

    public int getMaxOutputTokens() {
        return MAX_OUTPUT_TOKENS.value();
    }

    public double getTemperature() {
        return TEMPERATURE.value();
    }

    public double getTopP() {
        return TOP_P.value();
    }

    public boolean isModelLoggingEnabled() {
        return MODEL_LOGGING_ENABLED.value();
    }

    public boolean isThinkingOutputEnabled() {
        return THINKING_OUTPUT_ENABLED.value();
    }

    public int getGeminiThinkingBudget() {
        return GEMINI_THINKING_BUDGET.value();
    }

    public int getMaxRetries() {
        return MAX_RETRIES.value();
    }

    public String getGeminiThinkingLevel() {
        return GEMINI_THINKING_LEVEL.value();
    }

    // -----------------------------------------------------
    // Google API Config (Only relevant if model.provider is Google)
    public GoogleApiProvider getGoogleApiProvider() {
        return GOOGLE_API_PROVIDER.value();
    }

    public String getGoogleApiToken() {
        return GOOGLE_API_TOKEN.value();
    }

    public String getGoogleProject() {
        return GOOGLE_PROJECT.value();
    }

    public String getGoogleLocation() {
        return GOOGLE_LOCATION.value();
    }

    // -----------------------------------------------------
    // OpenAI API Config
    public String getOpenAiApiKey() {
        return OPENAI_API_KEY.value();
    }

    public String getOpenAiEndpoint() {
        return OPENAI_API_ENDPOINT.value();
    }

    // -----------------------------------------------------
    // Groq API Config
    public String getGroqApiKey() {
        return GROQ_API_KEY.value();
    }

    public String getGroqEndpoint() {
        return GROQ_API_ENDPOINT.value();
    }

    // -----------------------------------------------------
    // Anthropic API Config
    public AnthropicApiProvider getAnthropicApiProvider() {
        return ANTHROPIC_API_PROVIDER.value();
    }

    public String getAnthropicApiKey() {
        return ANTHROPIC_API_KEY.value();
    }

    public String getAnthropicEndpoint() {
        return ANTHROPIC_API_ENDPOINT.value();
    }

    // -----------------------------------------------------
    // Timeout and Retry Config
    public int getMaxActionExecutionDurationMillis() {
        return MAX_ACTION_EXECUTION_DURATION_MILLIS.value();
    }

    public RetryPolicy getActionRetryPolicy() {
        return new RetryPolicy(
                MAX_RETRIES.value(),
                TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS.value(),
                TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS.value());
    }

    public RetryPolicy getVerificationRetryPolicy() {
        return new RetryPolicy(
                MAX_RETRIES.value(),
                TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS.value(),
                VERIFICATION_RETRY_TIMEOUT_MILLIS.value());
    }

    public int getActionVerificationDelayMillis() {
        return ACTION_VERIFICATION_DELAY_MILLIS.value();
    }

    // -----------------------------------------------------
    // Agent Specific Configs - Budgets
    public int getAgentTokenBudget() {
        return AGENT_TOKEN_BUDGET.value();
    }

    public int getAgentToolCallsBudget() {
        return AGENT_TOOL_CALLS_BUDGET.value();
    }

    public int getAgentExecutionTimeBudgetSeconds() {
        return AGENT_EXECUTION_TIME_BUDGET_SECONDS.value();
    }

    // Precondition Agent
    public String getPreconditionActionAgentModelName() {
        return PRECONDITION_AGENT_MODEL_NAME.value();
    }

    public ModelProvider getPreconditionActionAgentModelProvider() {
        return PRECONDITION_AGENT_MODEL_PROVIDER.value();
    }

    public String getPreconditionAgentPromptVersion() {
        return PRECONDITION_AGENT_PROMPT_VERSION.value();
    }

    // Test Step Action Agent
    public String getTestStepActionAgentModelName() {
        return TEST_STEP_ACTION_AGENT_MODEL_NAME.value();
    }

    public ModelProvider getTestStepActionAgentModelProvider() {
        return TEST_STEP_ACTION_AGENT_MODEL_PROVIDER.value();
    }

    public String getTestStepActionAgentPromptVersion() {
        return TEST_STEP_ACTION_AGENT_PROMPT_VERSION.value();
    }

    // Test Case Extraction Agent
    public String getTestCaseExtractionAgentModelName() {
        return TEST_CASE_EXTRACTION_AGENT_MODEL_NAME.value();
    }

    public ModelProvider getTestCaseExtractionAgentModelProvider() {
        return TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER.value();
    }

    public String getTestCaseExtractionAgentPromptVersion() {
        return TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION.value();
    }

    // -----------------------------------------------------
    // Protected helper methods for subclasses to initialize their own ConfigProperty fields
    private Properties loadConfigPropertiesFromFile() {
        var props = new Properties();
        try (InputStream inputStream = AgentConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                LOG.error("Cannot find resource file '{}' in classpath.", CONFIG_FILE);
                throw new IOException("Cannot find resource: " + CONFIG_FILE);
            }
            props.load(new InputStreamReader(inputStream, UTF_8));
            LOG.info("Loaded properties from " + CONFIG_FILE);
            return props;
        } catch (IOException e) {
            LOG.error("Error loading properties file " + CONFIG_FILE, e);
            throw new UncheckedIOException(e);
        }
    }

    protected <T> ConfigProperty<T> loadProperty(String key, String envVar, String defaultValue,
                                                 Function<String, T> converter,
                                                 boolean isSecret) {
        var value = getProperty(key, envVar, defaultValue, isSecret);
        return new ConfigProperty<>(converter.apply(value), isSecret);
    }

    protected Optional<String> getProperty(String key, String envVar, boolean isSecret) {
        var envVariableOptional = ofNullable(envVar)
                .map(System::getenv)
                .map(String::trim)
                .filter(CommonUtils::isNotBlank);
        if (envVariableOptional.isPresent()) {
            var message = "Using environment variable '%s' for key '%s'".formatted(envVar, key);
            if (!isSecret) {
                message = "%s with value '%s'".formatted(message, envVariableOptional.get());
            }
            LOG.info(message);
            return envVariableOptional;
        } else {
            var propertyFileValueOptional = ofNullable(properties.getProperty(key))
                    .map(String::trim)
                    .filter(CommonUtils::isNotBlank);
            if (propertyFileValueOptional.isPresent()) {
                var message = "Using property file value for key '%s'".formatted(key);
                if (!isSecret) {
                    message = "%s with value '%s'".formatted(message, propertyFileValueOptional.get());
                }
                LOG.info(message);
                return propertyFileValueOptional;
            } else {
                return empty();
            }
        }
    }

    protected String getProperty(String key, String envVar, String defaultValue, boolean isSecret) {
        return getProperty(key, envVar, isSecret).orElseGet(() -> {
            LOG.info("Using default value for key '{}' : '{}'", key, defaultValue);
            return defaultValue;
        });
    }

    protected <T> ConfigProperty<T> getProperty(String key, String envVar, String defaultValue,
                                                Function<String, T> converter,
                                                boolean isSecret) {
        String value = getProperty(key, envVar, defaultValue, isSecret);
        return new ConfigProperty<>(converter.apply(value), isSecret);
    }

    protected ConfigProperty<String> getRequiredProperty(String key, String envVar, boolean isSecret) {
        String value = getProperty(key, envVar, isSecret).orElseThrow(
                () -> new IllegalStateException(("The value of required property '%s' must be either " +
                        "present in the properties file, or in the environment variable '%s'").formatted(key, envVar)));
        return new ConfigProperty<>(value, isSecret);
    }

    protected ConfigProperty<Integer> loadPropertyAsInteger(String propertyKey, String envVar, String defaultValue, boolean isSecret) {
        var configProperty = getProperty(propertyKey, envVar, defaultValue, s -> s, isSecret);
        Integer value = parseStringAsInteger(configProperty.value())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The value of property '%s' is not a correct integer value:%s".formatted(propertyKey,
                                configProperty.value())));
        return new ConfigProperty<>(value, configProperty.isSecret());
    }

    protected ConfigProperty<Double> loadPropertyAsDouble(String propertyKey, String envVar, String defaultValue, boolean isSecret) {
        var configProperty = getProperty(propertyKey, envVar, defaultValue, s -> s, isSecret);
        Double value = CommonUtils.parseStringAsDouble(configProperty.value())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The value of property '%s' is not a correct double value:%s".formatted(propertyKey,
                                configProperty.value())));
        return new ConfigProperty<>(value, configProperty.isSecret());
    }
}
