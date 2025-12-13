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

package org.tarik.ta;

import org.tarik.ta.core.AgentConfig;

import java.util.Optional;

/**
 * Configuration class for the API Test Execution Agent.
 * <p>
 * This class extends {@link AgentConfig} and provides configuration properties
 * specific to API test execution, including:
 * <ul>
 * <li>HTTP client settings (base URI, proxy, timeouts, SSL validation)</li>
 * <li>API test step action agent model configuration</li>
 * <li>Request/response handling configuration</li>
 * </ul>
 */
public class ApiTestAgentConfig extends AgentConfig {

    // -----------------------------------------------------
    // HTTP Client Configuration

    private static final ConfigProperty<String> BASE_URI = loadProperty("api.base.uri",
            "API_BASE_URI", "", s -> s, false);

    public static Optional<String> getBaseUri() {
        String value = BASE_URI.value();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static final ConfigProperty<Integer> PORT = loadPropertyAsInteger("api.proxy.port",
            "API_PROXY_PORT", "8080", false);

    public static int getPort() {
        return PORT.value();
    }

    private static final ConfigProperty<String> PROXY_HOST = loadProperty("api.proxy.host",
            "API_PROXY_HOST", "", s -> s, false);

    public static Optional<String> getProxyHost() {
        String value = PROXY_HOST.value();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static final ConfigProperty<Boolean> RELAXED_HTTPS_VALIDATION = loadProperty(
            "api.relaxed.https.validation", "API_RELAXED_HTTPS_VALIDATION", "true", Boolean::parseBoolean, false);

    public static boolean getRelaxedHttpsValidation() {
        return RELAXED_HTTPS_VALIDATION.value();
    }

    // -----------------------------------------------------
    // Request/Response Timeout Configuration

    private static final ConfigProperty<Integer> REQUEST_TIMEOUT_MILLIS = loadPropertyAsInteger(
            "api.request.timeout.millis", "API_REQUEST_TIMEOUT_MILLIS", "30000", false);

    public static int getRequestTimeoutMillis() {
        return REQUEST_TIMEOUT_MILLIS.value();
    }

    private static final ConfigProperty<Integer> RESPONSE_TIMEOUT_MILLIS = loadPropertyAsInteger(
            "api.response.timeout.millis", "API_RESPONSE_TIMEOUT_MILLIS", "30000", false);

    public static int getResponseTimeoutMillis() {
        return RESPONSE_TIMEOUT_MILLIS.value();
    }

    private static final ConfigProperty<Integer> CONNECTION_TIMEOUT_MILLIS = loadPropertyAsInteger(
            "api.connection.timeout.millis", "API_CONNECTION_TIMEOUT_MILLIS", "10000", false);

    public static int getConnectionTimeoutMillis() {
        return CONNECTION_TIMEOUT_MILLIS.value();
    }

    // -----------------------------------------------------
    // Logging and Debugging Configuration

    private static final ConfigProperty<Boolean> REQUEST_LOGGING_ENABLED = loadProperty(
            "api.request.logging.enabled", "API_REQUEST_LOGGING_ENABLED", "false", Boolean::parseBoolean, false);

    public static boolean getRequestLoggingEnabled() {
        return REQUEST_LOGGING_ENABLED.value();
    }

    private static final ConfigProperty<Boolean> RESPONSE_LOGGING_ENABLED = loadProperty(
            "api.response.logging.enabled", "API_RESPONSE_LOGGING_ENABLED", "false", Boolean::parseBoolean, false);

    public static boolean getResponseLoggingEnabled() {
        return RESPONSE_LOGGING_ENABLED.value();
    }

    // -----------------------------------------------------
    // Data Loading Configuration

    private static final ConfigProperty<String> TEST_DATA_FOLDER = loadProperty("api.test.data.folder",
            "API_TEST_DATA_FOLDER", "test-data", s -> s, false);

    public static String getTestDataFolder() {
        return TEST_DATA_FOLDER.value();
    }

    // -----------------------------------------------------
    // Schema Validation Configuration

    private static final ConfigProperty<String> API_SCHEMA_FOLDER = loadProperty("api.schema.folder",
            "API_SCHEMA_FOLDER", "schemas", s -> s, false);

    public static String getApiSchemaFolder() {
        return API_SCHEMA_FOLDER.value();
    }

    private static final ConfigProperty<String> OPENAPI_SPEC_PATH = loadProperty("api.openapi.spec.path",
            "API_OPENAPI_SPEC_PATH", "", s -> s, false);

    public static Optional<String> getApiOpenApiSpecPath() {
        String value = OPENAPI_SPEC_PATH.value();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    // -----------------------------------------------------
    // Retry and Resilience Configuration

    private static final ConfigProperty<Integer> MAX_RETRY_ATTEMPTS = loadPropertyAsInteger(
            "api.max.retry.attempts", "API_MAX_RETRY_ATTEMPTS", "3", false);

    public static int getMaxRetryAttempts() {
        return MAX_RETRY_ATTEMPTS.value();
    }

    private static final ConfigProperty<Integer> RETRY_DELAY_MILLIS = loadPropertyAsInteger(
            "api.retry.delay.millis", "API_RETRY_DELAY_MILLIS", "1000", false);

    public static int getRetryDelayMillis() {
        return RETRY_DELAY_MILLIS.value();
    }

    // -----------------------------------------------------
    // API Precondition Action Agent Configuration

    private static final ConfigProperty<String> PRECONDITION_ACTION_AGENT_MODEL_NAME = loadProperty(
            "api.precondition.action.agent.model.name", "API_PRECONDITION_ACTION_AGENT_MODEL_NAME", "gemini-2.5-flash",
            s -> s, false);

    public static String getPreconditionActionAgentModelName() {
        return PRECONDITION_ACTION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> PRECONDITION_ACTION_AGENT_MODEL_PROVIDER = getProperty(
            "api.precondition.action.agent.model.provider", "API_PRECONDITION_ACTION_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getPreconditionActionAgentModelProvider() {
        return PRECONDITION_ACTION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> PRECONDITION_ACTION_AGENT_PROMPT_VERSION = loadProperty(
            "api.precondition.action.agent.prompt.version", "API_PRECONDITION_ACTION_AGENT_PROMPT_VERSION", "v1.0.0",
            s -> s, false);

    public static String getPreconditionActionAgentPromptVersion() {
        return PRECONDITION_ACTION_AGENT_PROMPT_VERSION.value();
    }

    // -----------------------------------------------------
    // API Precondition Verification Agent Configuration

    private static final ConfigProperty<String> PRECONDITION_VERIFICATION_AGENT_MODEL_NAME = loadProperty(
            "api.precondition.verification.agent.model.name", "API_PRECONDITION_VERIFICATION_AGENT_MODEL_NAME",
            "gemini-2.5-flash", s -> s, false);

    public static String getPreconditionVerificationAgentModelName() {
        return PRECONDITION_VERIFICATION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER = getProperty(
            "api.precondition.verification.agent.model.provider", "API_PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER",
            "google", AgentConfig::getModelProvider, false);

    public static ModelProvider getPreconditionVerificationAgentModelProvider() {
        return PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION = loadProperty(
            "api.precondition.verification.agent.prompt.version", "API_PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION",
            "v1.0.0", s -> s, false);

    public static String getPreconditionVerificationAgentPromptVersion() {
        return PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION.value();
    }

    // -----------------------------------------------------
    // API Test Step Action Agent Configuration

    private static final ConfigProperty<String> TEST_STEP_ACTION_AGENT_MODEL_NAME = loadProperty(
            "api.test.step.action.agent.model.name", "API_TEST_STEP_ACTION_AGENT_MODEL_NAME", "gemini-2.5-flash",
            s -> s, false);

    public static String getTestStepActionAgentModelName() {
        return TEST_STEP_ACTION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> TEST_STEP_ACTION_AGENT_MODEL_PROVIDER = getProperty(
            "api.test.step.action.agent.model.provider", "API_TEST_STEP_ACTION_AGENT_MODEL_PROVIDER", "google",
            AgentConfig::getModelProvider, false);

    public static ModelProvider getTestStepActionAgentModelProvider() {
        return TEST_STEP_ACTION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> TEST_STEP_ACTION_AGENT_PROMPT_VERSION = loadProperty(
            "api.test.step.action.agent.prompt.version", "API_TEST_STEP_ACTION_AGENT_PROMPT_VERSION", "v1.0.0",
            s -> s, false);

    public static String getTestStepActionAgentPromptVersion() {
        return TEST_STEP_ACTION_AGENT_PROMPT_VERSION.value();
    }

    // -----------------------------------------------------
    // API Test Step Verification Agent Configuration

    private static final ConfigProperty<String> TEST_STEP_VERIFICATION_AGENT_MODEL_NAME = loadProperty(
            "api.test.step.verification.agent.model.name", "API_TEST_STEP_VERIFICATION_AGENT_MODEL_NAME",
            "gemini-2.5-flash", s -> s, false);

    public static String getTestStepVerificationAgentModelName() {
        return TEST_STEP_VERIFICATION_AGENT_MODEL_NAME.value();
    }

    private static final ConfigProperty<ModelProvider> TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER = getProperty(
            "api.test.step.verification.agent.model.provider", "API_TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER",
            "google", AgentConfig::getModelProvider, false);

    public static ModelProvider getTestStepVerificationAgentModelProvider() {
        return TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER.value();
    }

    private static final ConfigProperty<String> TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION = loadProperty(
            "api.test.step.verification.agent.prompt.version", "API_TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION",
            "v1.0.0", s -> s, false);

    public static String getTestStepVerificationAgentPromptVersion() {
        return TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION.value();
    }

    // -----------------------------------------------------
    // Content Type Configuration

    private static final ConfigProperty<String> DEFAULT_CONTENT_TYPE = loadProperty("api.default.content.type",
            "API_DEFAULT_CONTENT_TYPE", "application/json", s -> s, false);

    public static String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE.value();
    }

    private static final ConfigProperty<String> DEFAULT_ACCEPT = loadProperty("api.default.accept",
            "API_DEFAULT_ACCEPT", "application/json", s -> s, false);

    public static String getDefaultAccept() {
        return DEFAULT_ACCEPT.value();
    }

    // -----------------------------------------------------
    // Cookie Management Configuration

    private static final ConfigProperty<Boolean> COOKIES_ENABLED = loadProperty("api.cookies.enabled",
            "API_COOKIES_ENABLED", "true", Boolean::parseBoolean, false);

    public static boolean getCookiesEnabled() {
        return COOKIES_ENABLED.value();
    }

    // -----------------------------------------------------
    // Response Size Limits

    private static final ConfigProperty<Integer> MAX_RESPONSE_BODY_SIZE_KB = loadPropertyAsInteger(
            "api.max.response.body.size.kb", "API_MAX_RESPONSE_BODY_SIZE_KB", "10240", false);

    public static int getMaxResponseBodySizeKb() {
        return MAX_RESPONSE_BODY_SIZE_KB.value();
    }
}
