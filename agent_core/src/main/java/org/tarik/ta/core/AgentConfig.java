/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.utils.CommonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

public class AgentConfig {
    private static final Logger LOG = LoggerFactory.getLogger(AgentConfig.class);
    private static final Properties properties = loadConfigPropertiesFromFile();

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
        CHROMA, QDRANT
    }

    // -----------------------------------------------------
    // Constants
    private static final String CONFIG_FILE = "config.properties";

    // Main Config
    private static final ConfigProperty<Integer> START_PORT = loadPropertyAsInteger("port", "PORT", "8005", false);
    private static final ConfigProperty<String> HOST = getRequiredProperty("host", "AGENT_HOST", false);
    private static final ConfigProperty<String> EXTERNAL_URL = loadProperty("external.url", "EXTERNAL_URL",
            "http://localhost:%s".formatted(START_PORT.value()), s -> s, false);
    private static final ConfigProperty<Boolean> DEBUG_MODE = loadProperty("debug.mode", "DEBUG_MODE", "false",
            Boolean::parseBoolean, false);

    // RAG Config
    private static final ConfigProperty<RagDbProvider> VECTOR_DB_PROVIDER = getProperty("vector.db.provider",
            "VECTOR_DB_PROVIDER", "qdrant", s -> stream(RagDbProvider.values())
                    .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(s))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(
                            ("%s is not a supported RAG DB provider. Supported ones: %s".formatted(s,
                                    Arrays.toString(RagDbProvider.values()))))),
            false);
    private static final ConfigProperty<String> VECTOR_DB_URL = getRequiredProperty("vector.db.url", "VECTOR_DB_URL",
            false);
    private static final ConfigProperty<String> VECTOR_DB_KEY = loadProperty("vector.db.key", "VECTOR_DB_KEY", "", s -> s, true);

    private static final ConfigProperty<Integer> RETRIEVER_TOP_N = loadPropertyAsInteger("retriever.top.n",
            "RETRIEVER_TOP_N", "5", false);
    private static final ConfigProperty<Integer> MAX_OUTPUT_TOKENS = loadPropertyAsInteger("model.max.output.tokens",
            "MAX_OUTPUT_TOKENS", "5000", false);
    private static final ConfigProperty<Double> TEMPERATURE = loadPropertyAsDouble("model.temperature", "TEMPERATURE",
            "0.0", false);
    private static final ConfigProperty<Double> TOP_P = loadPropertyAsDouble("model.top.p", "TOP_P", "1.0", false);
    private static final ConfigProperty<Boolean> MODEL_LOGGING_ENABLED = loadProperty("model.logging.enabled",
            "LOG_MODEL_OUTPUT", "false", Boolean::parseBoolean, false);
    private static final ConfigProperty<Boolean> THINKING_OUTPUT_ENABLED = loadProperty("thinking.output.enabled",
            "OUTPUT_THINKING", "false", Boolean::parseBoolean, false);
    private static final ConfigProperty<Integer> GEMINI_THINKING_BUDGET = loadPropertyAsInteger(
            "gemini.thinking.budget", "GEMINI_THINKING_BUDGET", "5000", false);
    private static final ConfigProperty<Integer> MAX_RETRIES = loadPropertyAsInteger("model.max.retries", "MAX_RETRIES",
            "10", false);
    private static final ConfigProperty<Integer> VERIFICATION_MODEL_MAX_RETRIES = loadPropertyAsInteger(
            "verification.model.max.retries", "VERIFICATION_MODEL_MAX_RETRIES", "0", false);
    private static final ConfigProperty<String> GEMINI_THINKING_LEVEL = loadProperty(
            "gemini.thinking.level", "GEMINI_THINKING_LEVEL", "MINIMAL", s -> s, false);

    // Google API Config (Only relevant if model.provider is Google)
    private static final ConfigProperty<GoogleApiProvider> GOOGLE_API_PROVIDER = getProperty("google.api.provider",
            "GOOGLE_API_PROVIDER", "studio_ai", s -> stream(GoogleApiProvider.values())
                    .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(s))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(
                            ("%s is not a supported Google API provider. Supported ones: %s".formatted(s,
                                    Arrays.toString(GoogleApiProvider.values()))))),
            false);
    private static final ConfigProperty<String> GOOGLE_API_TOKEN = getRequiredProperty("google.api.token",
            "GOOGLE_API_KEY", true);
    private static final ConfigProperty<String> GOOGLE_PROJECT = getRequiredProperty("google.project", "GOOGLE_PROJECT",
            false);
    private static final ConfigProperty<String> GOOGLE_LOCATION = getRequiredProperty("google.location",
            "GOOGLE_LOCATION", false);

    // OpenAI API Config
    private static final ConfigProperty<String> OPENAI_API_KEY = getRequiredProperty("azure.openai.api.key",
            "OPENAI_API_KEY", true);
    private static final ConfigProperty<String> OPENAI_API_ENDPOINT = getRequiredProperty("azure.openai.endpoint",
            "OPENAI_API_ENDPOINT", false);

    // Groq API Config
    private static final ConfigProperty<String> GROQ_API_KEY = getRequiredProperty("groq.api.key", "GROQ_API_KEY",
            true);
    private static final ConfigProperty<String> GROQ_API_ENDPOINT = getRequiredProperty("groq.endpoint",
            "GROQ_ENDPOINT", false);

    // Anthropic API Config
    private static final ConfigProperty<AnthropicApiProvider> ANTHROPIC_API_PROVIDER = getProperty(
            "anthropic.api.provider",
            "ANTHROPIC_API_PROVIDER", "anthropic_api", s -> stream(AnthropicApiProvider.values())
                    .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(s))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(
                            ("%s is not a supported Anthropic API provider. Supported ones: %s".formatted(s,
                                    Arrays.toString(AnthropicApiProvider.values()))))),
            false);
    private static final ConfigProperty<String> ANTHROPIC_API_KEY = loadProperty("anthropic.api.key",
            "ANTHROPIC_API_KEY", "", s -> s, true);
    private static final ConfigProperty<String> ANTHROPIC_API_ENDPOINT = loadProperty("anthropic.endpoint",
            "ANTHROPIC_ENDPOINT", "https://api.anthropic.com/v1/", s -> s, false);

    // Timeout and Retry Config
    private static final ConfigProperty<Integer> TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS = loadPropertyAsInteger(
            "test.step.execution.retry.timeout.millis", "TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS", "10000", false);
    private static final ConfigProperty<Integer> TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS = loadPropertyAsInteger(
            "test.step.execution.retry.interval.millis", "TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS", "1000", false);
    private static final ConfigProperty<Integer> VERIFICATION_RETRY_TIMEOUT_MILLIS = loadPropertyAsInteger(
            "verification.retry.timeout.millis", "VERIFICATION_RETRY_TIMEOUT_MILLIS", "10000", false);
    private static final ConfigProperty<Integer> ACTION_VERIFICATION_DELAY_MILLIS = loadPropertyAsInteger(
            "action.verification.delay.millis", "ACTION_VERIFICATION_DELAY_MILLIS", "1000", false);

    private static final ConfigProperty<Integer> MAX_ACTION_EXECUTION_DURATION_MILLIS = loadPropertyAsInteger(
            "max.action.execution.duration.millis", "MAX_ACTION_EXECUTION_DURATION_MILLIS", "15000", false);

    // -----------------------------------------------------
    // Main Config
    public static int getStartPort() {
        return START_PORT.value();
    }

    public static String getHost() {
        return HOST.value();
    }

    public static String getExternalUrl() {
        return EXTERNAL_URL.value();
    }

    public static boolean isDebugMode() {
        return DEBUG_MODE.value();
    }

    // -----------------------------------------------------
    // RAG Config
    public static RagDbProvider getVectorDbProvider() {
        return VECTOR_DB_PROVIDER.value();
    }

    public static String getVectorDbUrl() {
        return VECTOR_DB_URL.value();
    }

    public static String getVectorDbToken() {
        return VECTOR_DB_KEY.value();
    }

    public static int getRetrieverTopN() {
        return RETRIEVER_TOP_N.value();
    }

    // -----------------------------------------------------
    // Model Config
    protected static ModelProvider getModelProvider(String s) {
        return stream(ModelProvider.values())
                .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(s))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(
                        ("%s is not a supported model provider. Supported ones: %s".formatted(s,
                                Arrays.toString(ModelProvider.values())))));
    }

    public static int getMaxOutputTokens() {
        return MAX_OUTPUT_TOKENS.value();
    }

    public static double getTemperature() {
        return TEMPERATURE.value();
    }

    public static double getTopP() {
        return TOP_P.value();
    }

    public static boolean isModelLoggingEnabled() {
        return MODEL_LOGGING_ENABLED.value();
    }

    public static boolean isThinkingOutputEnabled() {
        return THINKING_OUTPUT_ENABLED.value();
    }

    public static int getGeminiThinkingBudget() {
        return GEMINI_THINKING_BUDGET.value();
    }

    public static int getMaxRetries() {
        return MAX_RETRIES.value();
    }

    public static int getVerificationModelMaxRetries() {
        return VERIFICATION_MODEL_MAX_RETRIES.value();
    }

    public static String getGeminiThinkingLevel() {
        return GEMINI_THINKING_LEVEL.value();
    }

    // -----------------------------------------------------
    // Google API Config (Only relevant if model.provider is Google)
    public static GoogleApiProvider getGoogleApiProvider() {
        return GOOGLE_API_PROVIDER.value();
    }

    public static String getGoogleApiToken() {
        return GOOGLE_API_TOKEN.value();
    }

    public static String getGoogleProject() {
        return GOOGLE_PROJECT.value();
    }

    public static String getGoogleLocation() {
        return GOOGLE_LOCATION.value();
    }

    // -----------------------------------------------------
    // OpenAI API Config
    public static String getOpenAiApiKey() {
        return OPENAI_API_KEY.value();
    }

    public static String getOpenAiEndpoint() {
        return OPENAI_API_ENDPOINT.value();
    }

    // -----------------------------------------------------
    // Groq API Config
    public static String getGroqApiKey() {
        return GROQ_API_KEY.value();
    }

    public static String getGroqEndpoint() {
        return GROQ_API_ENDPOINT.value();
    }

    // -----------------------------------------------------
    // Anthropic API Config
    public static AnthropicApiProvider getAnthropicApiProvider() {
        return ANTHROPIC_API_PROVIDER.value();
    }

    public static String getAnthropicApiKey() {
        return ANTHROPIC_API_KEY.value();
    }

    public static String getAnthropicEndpoint() {
        return ANTHROPIC_API_ENDPOINT.value();
    }

    // -----------------------------------------------------
    // Timeout and Retry Config
    public static int getMaxActionExecutionDurationMillis() {
        return MAX_ACTION_EXECUTION_DURATION_MILLIS.value();
    }

    public static RetryPolicy getActionRetryPolicy() {
        return new RetryPolicy(
                MAX_RETRIES.value(),
                TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS.value(),
                TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS.value());
    }

    public static RetryPolicy getVerificationRetryPolicy() {
        return new RetryPolicy(
                MAX_RETRIES.value(),
                TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS.value(),
                VERIFICATION_RETRY_TIMEOUT_MILLIS.value());
    }

    public static int getActionVerificationDelayMillis() {
        return ACTION_VERIFICATION_DELAY_MILLIS.value();
    }

    // -----------------------------------------------------
    // Agent Specific Configs

    // Budgets
    private static final ConfigProperty<Integer> AGENT_TOKEN_BUDGET = loadPropertyAsInteger("agent.token.budget",
            "AGENT_TOKEN_BUDGET", "1000000", false);

    public static int getAgentTokenBudget() {
        return AGENT_TOKEN_BUDGET.value();
    }

    private static final ConfigProperty<Integer> AGENT_TOOL_CALLS_BUDGET = loadPropertyAsInteger(
            "agent.tool.calls.budget.unattended", "AGENT_TOOL_CALLS_BUDGET_UNATTENDED", "5", false);

    public static int getAgentToolCallsBudget() {
        return AGENT_TOOL_CALLS_BUDGET.value();
    }

    private static final ConfigProperty<Integer> AGENT_EXECUTION_TIME_BUDGET_SECONDS = loadPropertyAsInteger(
            "agent.execution.time.budget.seconds", "AGENT_EXECUTION_TIME_BUDGET_SECONDS", "3000", false);

    public static int getAgentExecutionTimeBudgetSeconds() {
        return AGENT_EXECUTION_TIME_BUDGET_SECONDS.value();
    }

    // Precondition Agent
    private static final ConfigProperty<String> PRECONDITION_AGENT_MODEL_NAME = loadProperty(
            "precondition.agent.model.name", "PRECONDITION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);

    public static String getPreconditionActionAgentModelName() {
        return PRECONDITION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> PRECONDITION_AGENT_MODEL_PROVIDER = getProperty(
            "precondition.agent.model.provider", "PRECONDITION_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getPreconditionActionAgentModelProvider() {
        return PRECONDITION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> PRECONDITION_AGENT_PROMPT_VERSION = loadProperty(
            "precondition.agent.prompt.version", "PRECONDITION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);

    public static String getPreconditionAgentPromptVersion() {
        return PRECONDITION_AGENT_PROMPT_VERSION.value();
    }


    // Test Step Action Agent
    private static final ConfigProperty<String> TEST_STEP_ACTION_AGENT_MODEL_NAME = loadProperty(
            "test.step.action.agent.model.name", "TEST_STEP_ACTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s,
            false);

    public static String getTestStepActionAgentModelName() {
        return TEST_STEP_ACTION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> TEST_STEP_ACTION_AGENT_MODEL_PROVIDER = getProperty(
            "test.step.action.agent.model.provider", "TEST_STEP_ACTION_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getTestStepActionAgentModelProvider() {
        return TEST_STEP_ACTION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> TEST_STEP_ACTION_AGENT_PROMPT_VERSION = loadProperty(
            "test.step.action.agent.prompt.version", "TEST_STEP_ACTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);

    public static String getTestStepActionAgentPromptVersion() {
        return TEST_STEP_ACTION_AGENT_PROMPT_VERSION.value();
    }


    // Test Case Extraction Agent
    private static final ConfigProperty<String> TEST_CASE_EXTRACTION_AGENT_MODEL_NAME = loadProperty(
            "test.case.extraction.agent.model.name", "TEST_CASE_EXTRACTION_AGENT_MODEL_NAME", "gemini-3-flash-preview",
            s -> s, false);

    public static String getTestCaseExtractionAgentModelName() {
        return TEST_CASE_EXTRACTION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER = getProperty(
            "test.case.extraction.agent.model.provider", "TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getTestCaseExtractionAgentModelProvider() {
        return TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION = loadProperty(
            "test.case.extraction.agent.prompt.version", "TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s,
            false);

    public static String getTestCaseExtractionAgentPromptVersion() {
        return TEST_CASE_EXTRACTION_AGENT_PROMPT_VERSION.value();
    }

    // -----------------------------------------------------
    // Private methods
    private static Properties loadConfigPropertiesFromFile() {
        var properties = new Properties();
        try (InputStream inputStream = AgentConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                LOG.error("Cannot find resource file '{}' in classpath.", CONFIG_FILE);
                throw new IOException("Cannot find resource: " + CONFIG_FILE);
            }
            properties.load(new java.io.InputStreamReader(inputStream, UTF_8));
            LOG.info("Loaded properties from " + CONFIG_FILE);
            return properties;
        } catch (IOException e) {
            LOG.error("Error loading properties file " + CONFIG_FILE, e);
            throw new UncheckedIOException(e);
        }
    }

    protected static <T> ConfigProperty<T> loadProperty(String key, String envVar, String defaultValue,
                                                        Function<String, T> converter,
                                                        boolean isSecret) {
        var value = getProperty(key, envVar, defaultValue, isSecret);
        return new ConfigProperty<>(converter.apply(value), isSecret);
    }

    protected static Optional<String> getProperty(String key, String envVar, boolean isSecret) {
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

    protected static String getProperty(String key, String envVar, String defaultValue, boolean isSecret) {
        return getProperty(key, envVar, isSecret).orElseGet(() -> {
            LOG.info("Using default value for key '{}' : '{}'", key, defaultValue);
            return defaultValue;
        });
    }

    protected static <T> ConfigProperty<T> getProperty(String key, String envVar, String defaultValue,
                                                       Function<String, T> converter,
                                                       boolean isSecret) {
        String value = getProperty(key, envVar, defaultValue, isSecret);
        return new ConfigProperty<>(converter.apply(value), isSecret);
    }

    protected static ConfigProperty<String> getRequiredProperty(String key, String envVar, boolean isSecret) {
        String value = getProperty(key, envVar, isSecret).orElseThrow(
                () -> new IllegalStateException(("The value of required property '%s' must be either " +
                        "present in the properties file, or in the environment variable '%s'").formatted(key, envVar)));
        return new ConfigProperty<>(value, isSecret);
    }

    protected static ConfigProperty<Integer> loadPropertyAsInteger(String propertyKey, String envVar,
                                                                   String defaultValue, boolean isSecret) {
        var configProperty = getProperty(propertyKey, envVar, defaultValue, s -> s, isSecret);
        Integer value = CommonUtils.parseStringAsInteger(configProperty.value())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The value of property '%s' is not a correct integer value:%s".formatted(propertyKey,
                                configProperty.value())));
        return new ConfigProperty<>(value, configProperty.isSecret());
    }

    protected static ConfigProperty<Double> loadPropertyAsDouble(String propertyKey, String envVar, String defaultValue,
                                                                 boolean isSecret) {
        var configProperty = getProperty(propertyKey, envVar, defaultValue, s -> s, isSecret);
        Double value = CommonUtils.parseStringAsDouble(configProperty.value())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The value of property '%s' is not a correct double value:%s".formatted(propertyKey,
                                configProperty.value())));
        return new ConfigProperty<>(value, configProperty.isSecret());
    }
}