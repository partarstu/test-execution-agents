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
    private final ConfigProperty<String> agentAuthToken;

    // RAG Config
    private final ConfigProperty<RagDbProvider> vectorDbProvider;
    private final ConfigProperty<String> vectorDbUrl;
    private final ConfigProperty<String> vectorDbKey;
    private final ConfigProperty<Integer> retrieverTopN;
    private final ConfigProperty<Integer> maxOutputTokens;
    private final ConfigProperty<Double> temperature;
    private final ConfigProperty<Double> topP;
    private final ConfigProperty<Boolean> modelLoggingEnabled;
    private final ConfigProperty<Boolean> thinkingOutputEnabled;
    private final ConfigProperty<Integer> maxRetries;
    private final ConfigProperty<String> geminiThinkingLevel;

    // Google API Config
    private final ConfigProperty<GoogleApiProvider> googleApiProvider;
    private final ConfigProperty<String> googleApiToken;
    private final ConfigProperty<String> googleProject;
    private final ConfigProperty<String> googleLocation;

    // OpenAI API Config (Direct)
    private final ConfigProperty<String> openaiApiKey;
    private final ConfigProperty<String> openaiApiEndpoint;

    // Groq API Config
    private final ConfigProperty<String> groqApiKey;
    private final ConfigProperty<String> groqApiEndpoint;

    // Anthropic API Config
    private final ConfigProperty<AnthropicApiProvider> anthropicApiProvider;
    private final ConfigProperty<String> anthropicApiKey;
    private final ConfigProperty<String> anthropicApiEndpoint;

    // Timeout and Retry Config
    private final ConfigProperty<Integer> testStepExecutionRetryTimeoutMillis;
    private final ConfigProperty<Integer> testStepExecutionRetryIntervalMillis;
    private final ConfigProperty<Integer> verificationRetryTimeoutMillis;
    private final ConfigProperty<Integer> actionVerificationDelayMillis;
    private final ConfigProperty<Integer> maxActionExecutionDurationMillis;

    // Agent Specific Configs - Budgets
    private final ConfigProperty<Integer> agentTokenBudget;
    private final ConfigProperty<Integer> agentToolCallsBudget;
    private final ConfigProperty<Integer> agentExecutionTimeBudgetSeconds;

    // Precondition Agent
    private final ConfigProperty<String> preconditionAgentModelName;
    private final ConfigProperty<ModelProvider> preconditionAgentModelProvider;
    private final ConfigProperty<String> preconditionAgentPromptVersion;

    // Test Step Action Agent
    private final ConfigProperty<String> testStepActionAgentModelName;
    private final ConfigProperty<ModelProvider> testStepActionAgentModelProvider;
    private final ConfigProperty<String> testStepActionAgentPromptVersion;

    // Test Case Extraction Agent
    private final ConfigProperty<String> testCaseExtractionAgentModelName;
    private final ConfigProperty<ModelProvider> testCaseExtractionAgentModelProvider;
    private final ConfigProperty<String> testCaseExtractionAgentPromptVersion;

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
        this.agentAuthToken = loadProperty("agent.auth.token", "AGENT_AUTH_TOKEN", "", s -> s, true);

        // RAG Config
        this.vectorDbProvider = getProperty("vector.db.provider", "VECTOR_DB_PROVIDER", "neo4j",
                s -> parseEnum(RagDbProvider.class, s), false);
        this.vectorDbUrl = getRequiredProperty("vector.db.url", "VECTOR_DB_URL", false);
        this.vectorDbKey = loadProperty("vector.db.key", "VECTOR_DB_KEY", "", s -> s, true);
        this.retrieverTopN = loadPropertyAsInteger("retriever.top.n", "RETRIEVER_TOP_N", "5", false);
        this.maxOutputTokens = loadPropertyAsInteger("model.max.output.tokens", "MAX_OUTPUT_TOKENS", "5000", false);
        this.temperature = loadPropertyAsDouble("model.temperature", "TEMPERATURE", "0.0", false);
        this.topP = loadPropertyAsDouble("model.top.p", "TOP_P", "1.0", false);
        this.modelLoggingEnabled = loadProperty("model.logging.enabled", "LOG_MODEL_OUTPUT", "false", Boolean::parseBoolean, false);
        this.thinkingOutputEnabled = loadProperty("thinking.output.enabled", "OUTPUT_THINKING", "false", Boolean::parseBoolean, false);
        this.maxRetries = loadPropertyAsInteger("model.max.retries", "MAX_RETRIES", "10", false);
        this.geminiThinkingLevel = loadProperty("gemini.thinking.level", "GEMINI_THINKING_LEVEL", "MINIMAL", s -> s, false);

        // Google API Config
        this.googleApiProvider = getProperty("google.api.provider", "GOOGLE_API_PROVIDER", "studio_ai",
                s -> parseEnum(GoogleApiProvider.class, s), false);
        this.googleApiToken = getRequiredProperty("google.api.token", "GOOGLE_API_KEY", true);
        this.googleProject = getRequiredProperty("google.project", "GOOGLE_PROJECT", false);
        this.googleLocation = getRequiredProperty("google.location", "GOOGLE_LOCATION", false);

        // OpenAI API Config (Direct)
        this.openaiApiKey = loadProperty("openai.api.key", "OPENAI_API_KEY", "", s -> s, true);
        this.openaiApiEndpoint = loadProperty("openai.endpoint", "OPENAI_ENDPOINT", "https://api.openai.com/v1/", s -> s, false);

        // Groq API Config
        this.groqApiKey = getRequiredProperty("groq.api.key", "GROQ_API_KEY", true);
        this.groqApiEndpoint = getRequiredProperty("groq.endpoint", "GROQ_ENDPOINT", false);

        // Anthropic API Config
        this.anthropicApiProvider = getProperty("anthropic.api.provider", "ANTHROPIC_API_PROVIDER", "anthropic_api",
                s -> parseEnum(AnthropicApiProvider.class, s), false);
        this.anthropicApiKey = loadProperty("anthropic.api.key", "ANTHROPIC_API_KEY", "", s -> s, true);
        this.anthropicApiEndpoint = loadProperty("anthropic.endpoint", "ANTHROPIC_ENDPOINT",
                "https://api.anthropic.com/v1/", s -> s, false);

        // Timeout and Retry Config
        this.testStepExecutionRetryTimeoutMillis = loadPropertyAsInteger(
                "test.step.execution.retry.timeout.millis", "TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS", "10000", false);
        this.testStepExecutionRetryIntervalMillis = loadPropertyAsInteger(
                "test.step.execution.retry.interval.millis", "TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS", "1000", false);
        this.verificationRetryTimeoutMillis = loadPropertyAsInteger(
                "verification.retry.timeout.millis", "VERIFICATION_RETRY_TIMEOUT_MILLIS", "10000", false);
        this.actionVerificationDelayMillis = loadPropertyAsInteger(
                "action.verification.delay.millis", "ACTION_VERIFICATION_DELAY_MILLIS", "1000", false);
        this.maxActionExecutionDurationMillis = loadPropertyAsInteger(
                "max.action.execution.duration.millis", "MAX_ACTION_EXECUTION_DURATION_MILLIS", "15000", false);

        // Agent Specific Configs - Budgets
        this.agentTokenBudget = loadPropertyAsInteger("agent.token.budget", "AGENT_TOKEN_BUDGET", "1000000", false);
        this.agentToolCallsBudget = loadPropertyAsInteger(
                "agent.tool.calls.budget.unattended", "AGENT_TOOL_CALLS_BUDGET_UNATTENDED", "5", false);
        this.agentExecutionTimeBudgetSeconds = loadPropertyAsInteger(
                "agent.execution.time.budget.seconds", "AGENT_EXECUTION_TIME_BUDGET_SECONDS", "3000", false);

        // Precondition Agent
        this.preconditionAgentModelName = loadProperty(
                "precondition.agent.model.name", "PRECONDITION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.preconditionAgentModelProvider = getProperty(
                "precondition.agent.model.provider", "PRECONDITION_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.preconditionAgentPromptVersion = loadProperty(
                "precondition.agent.prompt.version", "PRECONDITION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);

        // Test Step Action Agent
        this.testStepActionAgentModelName = loadProperty(
                "test.step.action.agent.model.name", "TEST_STEP_ACTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.testStepActionAgentModelProvider = getProperty(
                "test.step.action.agent.model.provider", "TEST_STEP_ACTION_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.testStepActionAgentPromptVersion = loadProperty(
                "test.step.action.agent.prompt.version", "TEST_STEP_ACTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);

        // Test Case Extraction Agent
        this.testCaseExtractionAgentModelName = loadProperty(
                "test.case.extraction.agent.model.name", "TEST_CASE_EXTRACTION_AGENT_MODEL_NAME", "gemini-3-flash-preview", s -> s, false);
        this.testCaseExtractionAgentModelProvider = getProperty(
                "test.case.extraction.agent.model.provider", "TEST_CASE_EXTRACTION_AGENT_MODEL_PROVIDER", "google",
                this::getModelProvider, false);
        this.testCaseExtractionAgentPromptVersion = loadProperty(
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

    /**
     * Shared secret required as a {@code Bearer} token on requests to the main endpoint. An empty value means
     * authentication is disabled (intended only for local/dev runs).
     */
    public Optional<String> getAuthToken() {
        String value = agentAuthToken.value();
        return value.isEmpty() ? empty() : Optional.of(value);
    }

    // -----------------------------------------------------
    // RAG Config
    public RagDbProvider getVectorDbProvider() {
        return vectorDbProvider.value();
    }

    public String getVectorDbUrl() {
        return vectorDbUrl.value();
    }

    public String getVectorDbToken() {
        return vectorDbKey.value();
    }

    public int getRetrieverTopN() {
        return retrieverTopN.value();
    }

    protected <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        return stream(enumClass.getEnumConstants())
                .filter(e -> e.name().equalsIgnoreCase(value))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(
                        "%s is not a valid %s value. Supported ones: %s".formatted(value,
                                enumClass.getSimpleName(), Arrays.toString(enumClass.getEnumConstants()))));
    }

    // -----------------------------------------------------
    // Model Config
    protected ModelProvider getModelProvider(String s) {
        return parseEnum(ModelProvider.class, s);
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens.value();
    }

    public double getTemperature() {
        return temperature.value();
    }

    public double getTopP() {
        return topP.value();
    }

    public boolean isModelLoggingEnabled() {
        return modelLoggingEnabled.value();
    }

    public boolean isThinkingOutputEnabled() {
        return thinkingOutputEnabled.value();
    }

    public int getMaxRetries() {
        return maxRetries.value();
    }

    public String getGeminiThinkingLevel() {
        return geminiThinkingLevel.value();
    }

    // -----------------------------------------------------
    // Google API Config (Only relevant if model.provider is Google)
    public GoogleApiProvider getGoogleApiProvider() {
        return googleApiProvider.value();
    }

    public String getGoogleApiToken() {
        return googleApiToken.value();
    }

    public String getGoogleProject() {
        return googleProject.value();
    }

    public String getGoogleLocation() {
        return googleLocation.value();
    }

    // -----------------------------------------------------
    // OpenAI API Config (Direct)
    public String getOpenAiApiKey() {
        return openaiApiKey.value();
    }

    public String getOpenAiEndpoint() {
        return openaiApiEndpoint.value();
    }

    // -----------------------------------------------------
    // Groq API Config
    public String getGroqApiKey() {
        return groqApiKey.value();
    }

    public String getGroqEndpoint() {
        return groqApiEndpoint.value();
    }

    // -----------------------------------------------------
    // Anthropic API Config
    public AnthropicApiProvider getAnthropicApiProvider() {
        return anthropicApiProvider.value();
    }

    public String getAnthropicApiKey() {
        return anthropicApiKey.value();
    }

    public String getAnthropicEndpoint() {
        return anthropicApiEndpoint.value();
    }

    // -----------------------------------------------------
    // Timeout and Retry Config
    public int getMaxActionExecutionDurationMillis() {
        return maxActionExecutionDurationMillis.value();
    }

    public RetryPolicy getActionRetryPolicy() {
        return new RetryPolicy(
                maxRetries.value(),
                testStepExecutionRetryIntervalMillis.value(),
                testStepExecutionRetryTimeoutMillis.value());
    }

    public RetryPolicy getVerificationRetryPolicy() {
        return new RetryPolicy(
                maxRetries.value(),
                testStepExecutionRetryIntervalMillis.value(),
                verificationRetryTimeoutMillis.value());
    }

    public int getActionVerificationDelayMillis() {
        return actionVerificationDelayMillis.value();
    }

    // -----------------------------------------------------
    // Agent Specific Configs - Budgets
    public int getAgentTokenBudget() {
        return agentTokenBudget.value();
    }

    public int getAgentToolCallsBudget() {
        return agentToolCallsBudget.value();
    }

    public int getAgentExecutionTimeBudgetSeconds() {
        return agentExecutionTimeBudgetSeconds.value();
    }

    // Precondition Agent
    public String getPreconditionActionAgentModelName() {
        return preconditionAgentModelName.value();
    }

    public ModelProvider getPreconditionActionAgentModelProvider() {
        return preconditionAgentModelProvider.value();
    }

    public String getPreconditionAgentPromptVersion() {
        return preconditionAgentPromptVersion.value();
    }

    // Test Step Action Agent
    public String getTestStepActionAgentModelName() {
        return testStepActionAgentModelName.value();
    }

    public ModelProvider getTestStepActionAgentModelProvider() {
        return testStepActionAgentModelProvider.value();
    }

    public String getTestStepActionAgentPromptVersion() {
        return testStepActionAgentPromptVersion.value();
    }

    // Test Case Extraction Agent
    public String getTestCaseExtractionAgentModelName() {
        return testCaseExtractionAgentModelName.value();
    }

    public ModelProvider getTestCaseExtractionAgentModelProvider() {
        return testCaseExtractionAgentModelProvider.value();
    }

    public String getTestCaseExtractionAgentPromptVersion() {
        return testCaseExtractionAgentPromptVersion.value();
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