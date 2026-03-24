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
package org.tarik.ta;

import jakarta.inject.Singleton;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.model.AuthType;

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
@Singleton
public class ApiTestAgentConfig extends AgentConfig {

    // -----------------------------------------------------
    // HTTP Client Configuration
    private final ConfigProperty<String> TARGET_BASE_URI;
    private final ConfigProperty<Integer> PROXY_PORT;
    private final ConfigProperty<String> PROXY_HOST;
    private final ConfigProperty<Boolean> RELAXED_HTTPS_VALIDATION;

    // -----------------------------------------------------
    // Request/Response Timeout Configuration
    private final ConfigProperty<Integer> REQUEST_TIMEOUT_MILLIS;
    private final ConfigProperty<Integer> RESPONSE_TIMEOUT_MILLIS;
    private final ConfigProperty<Integer> CONNECTION_TIMEOUT_MILLIS;

    // -----------------------------------------------------
    // Logging and Debugging Configuration
    private final ConfigProperty<Boolean> REQUEST_LOGGING_ENABLED;
    private final ConfigProperty<Boolean> RESPONSE_LOGGING_ENABLED;

    // -----------------------------------------------------
    // Data Loading Configuration
    private final ConfigProperty<String> TEST_DATA_FOLDER;

    // -----------------------------------------------------
    // Schema Validation Configuration
    private final ConfigProperty<String> API_SCHEMA_FOLDER;
    private final ConfigProperty<String> OPENAPI_SPEC_PATH;

    // -----------------------------------------------------
    // Retry and Resilience Configuration
    private final ConfigProperty<Integer> MAX_RETRY_ATTEMPTS;
    private final ConfigProperty<Integer> RETRY_DELAY_MILLIS;

    // -----------------------------------------------------
    // Content Type Configuration
    private final ConfigProperty<String> DEFAULT_CONTENT_TYPE;
    private final ConfigProperty<String> DEFAULT_ACCEPT;

    // -----------------------------------------------------
    // Cookie Management Configuration
    private final ConfigProperty<Boolean> COOKIES_ENABLED;

    // -----------------------------------------------------
    // Response Size Limits
    private final ConfigProperty<Integer> MAX_RESPONSE_BODY_SIZE_KB;

    // -----------------------------------------------------
    // Authentication Configuration (Environment Variables)
    private final ConfigProperty<AuthType> DEFAULT_AUTH_TYPE;
    private final ConfigProperty<String> BASIC_AUTH_USERNAME_ENV;
    private final ConfigProperty<String> BASIC_AUTH_PASSWORD_ENV;
    private final ConfigProperty<String> BEARER_TOKEN_ENV;
    private final ConfigProperty<String> API_KEY_NAME_ENV;
    private final ConfigProperty<String> API_KEY_VALUE_ENV;

    public ApiTestAgentConfig() {
        super();

        // -----------------------------------------------------
        // HTTP Client Configuration
        this.TARGET_BASE_URI = loadProperty("api.base.uri", "API_BASE_URI", "", s -> s, false);
        this.PROXY_PORT = loadPropertyAsInteger("api.proxy.port", "API_PROXY_PORT", "8080", false);
        this.PROXY_HOST = loadProperty("api.proxy.host", "API_PROXY_HOST", "", s -> s, false);
        this.RELAXED_HTTPS_VALIDATION = loadProperty("api.relaxed.https.validation", "API_RELAXED_HTTPS_VALIDATION", "true", Boolean::parseBoolean, false);

        // -----------------------------------------------------
        // Request/Response Timeout Configuration
        this.REQUEST_TIMEOUT_MILLIS = loadPropertyAsInteger("api.request.timeout.millis", "API_REQUEST_TIMEOUT_MILLIS", "30000", false);
        this.RESPONSE_TIMEOUT_MILLIS = loadPropertyAsInteger("api.response.timeout.millis", "API_RESPONSE_TIMEOUT_MILLIS", "30000", false);
        this.CONNECTION_TIMEOUT_MILLIS = loadPropertyAsInteger("api.connection.timeout.millis", "API_CONNECTION_TIMEOUT_MILLIS", "10000", false);

        // -----------------------------------------------------
        // Logging and Debugging Configuration
        this.REQUEST_LOGGING_ENABLED = loadProperty("api.request.logging.enabled", "API_REQUEST_LOGGING_ENABLED", "false", Boolean::parseBoolean, false);
        this.RESPONSE_LOGGING_ENABLED = loadProperty("api.response.logging.enabled", "API_RESPONSE_LOGGING_ENABLED", "false", Boolean::parseBoolean, false);

        // -----------------------------------------------------
        // Data Loading Configuration
        this.TEST_DATA_FOLDER = loadProperty("api.test.data.folder", "API_TEST_DATA_FOLDER", "test-data", s -> s, false);

        // -----------------------------------------------------
        // Schema Validation Configuration
        this.API_SCHEMA_FOLDER = loadProperty("api.schema.folder", "API_SCHEMA_FOLDER", "schemas", s -> s, false);
        this.OPENAPI_SPEC_PATH = loadProperty("api.openapi.spec.path", "API_OPENAPI_SPEC_PATH", "", s -> s, false);

        // -----------------------------------------------------
        // Retry and Resilience Configuration
        this.MAX_RETRY_ATTEMPTS = loadPropertyAsInteger("api.max.retry.attempts", "API_MAX_RETRY_ATTEMPTS", "3", false);
        this.RETRY_DELAY_MILLIS = loadPropertyAsInteger("api.retry.delay.millis", "API_RETRY_DELAY_MILLIS", "1000", false);

        // -----------------------------------------------------
        // Content Type Configuration
        this.DEFAULT_CONTENT_TYPE = loadProperty("api.default.content.type", "API_DEFAULT_CONTENT_TYPE", "application/json", s -> s, false);
        this.DEFAULT_ACCEPT = loadProperty("api.default.accept", "API_DEFAULT_ACCEPT", "application/json", s -> s, false);

        // -----------------------------------------------------
        // Cookie Management Configuration
        this.COOKIES_ENABLED = loadProperty("api.cookies.enabled", "API_COOKIES_ENABLED", "true", Boolean::parseBoolean, false);

        // -----------------------------------------------------
        // Response Size Limits
        this.MAX_RESPONSE_BODY_SIZE_KB = loadPropertyAsInteger("api.max.response.body.size.kb", "API_MAX_RESPONSE_BODY_SIZE_KB", "10240", false);

        // -----------------------------------------------------
        // Authentication Configuration (Environment Variables)
        this.DEFAULT_AUTH_TYPE = loadProperty("api.default.auth.type", "API_DEFAULT_AUTH_TYPE", "NONE", s -> AuthType.valueOf(s.toUpperCase()), false);
        this.BASIC_AUTH_USERNAME_ENV = loadProperty("api.auth.basic.username.env", "API_AUTH_BASIC_USERNAME_ENV", "API_USERNAME", s -> s, false);
        this.BASIC_AUTH_PASSWORD_ENV = loadProperty("api.auth.basic.password.env", "API_AUTH_BASIC_PASSWORD_ENV", "API_PASSWORD", s -> s, false);
        this.BEARER_TOKEN_ENV = loadProperty("api.auth.bearer.token.env", "API_AUTH_BEARER_TOKEN_ENV", "API_TOKEN", s -> s, false);
        this.API_KEY_NAME_ENV = loadProperty("api.auth.apikey.name.env", "API_AUTH_APIKEY_NAME_ENV", "API_KEY_NAME", s -> s, false);
        this.API_KEY_VALUE_ENV = loadProperty("api.auth.apikey.value.env", "API_AUTH_APIKEY_VALUE_ENV", "API_KEY_VALUE", s -> s, false);
    }

    public Optional<String> getTargetBaseUri() {
        String value = TARGET_BASE_URI.value();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    public int getProxyPort() {
        return PROXY_PORT.value();
    }

    public Optional<String> getProxyHost() {
        String value = PROXY_HOST.value();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    public boolean getRelaxedHttpsValidation() {
        return RELAXED_HTTPS_VALIDATION.value();
    }

    public int getRequestTimeoutMillis() {
        return REQUEST_TIMEOUT_MILLIS.value();
    }

    public int getResponseTimeoutMillis() {
        return RESPONSE_TIMEOUT_MILLIS.value();
    }

    public int getConnectionTimeoutMillis() {
        return CONNECTION_TIMEOUT_MILLIS.value();
    }

    public boolean getRequestLoggingEnabled() {
        return REQUEST_LOGGING_ENABLED.value();
    }

    public boolean getResponseLoggingEnabled() {
        return RESPONSE_LOGGING_ENABLED.value();
    }

    public String getTestDataFolder() {
        return TEST_DATA_FOLDER.value();
    }

    public String getApiSchemaFolder() {
        return API_SCHEMA_FOLDER.value();
    }

    public Optional<String> getApiOpenApiSpecPath() {
        String value = OPENAPI_SPEC_PATH.value();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    public int getMaxRetryAttempts() {
        return MAX_RETRY_ATTEMPTS.value();
    }

    public int getRetryDelayMillis() {
        return RETRY_DELAY_MILLIS.value();
    }

    public String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE.value();
    }

    public String getDefaultAccept() {
        return DEFAULT_ACCEPT.value();
    }

    public boolean getCookiesEnabled() {
        return COOKIES_ENABLED.value();
    }

    public int getMaxResponseBodySizeKb() {
        return MAX_RESPONSE_BODY_SIZE_KB.value();
    }

    public AuthType getDefaultAuthType() {
        return DEFAULT_AUTH_TYPE.value();
    }

    public String getBasicAuthUsernameEnv() {
        return BASIC_AUTH_USERNAME_ENV.value();
    }

    public String getBasicAuthPasswordEnv() {
        return BASIC_AUTH_PASSWORD_ENV.value();
    }

    public String getBearerTokenEnv() {
        return BEARER_TOKEN_ENV.value();
    }

    public String getApiKeyNameEnv() {
        return API_KEY_NAME_ENV.value();
    }

    public String getApiKeyValueEnv() {
        return API_KEY_VALUE_ENV.value();
    }
}