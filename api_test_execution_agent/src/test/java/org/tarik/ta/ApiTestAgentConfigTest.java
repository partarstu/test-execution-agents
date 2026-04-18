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

import org.junit.jupiter.api.Test;
import org.tarik.ta.model.AuthType;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTestAgentConfigTest {

    @Test
    void testConfigAccessors() {
        var config = new ApiTestAgentConfig();

        assertThat(config.getTargetBaseUri()).isNotNull();
        assertThat(config.getProxyPort()).isNotNull();
        assertThat(config.getProxyHost()).isNotNull();
        assertThat(config.getRelaxedHttpsValidation()).isTrue();
        assertThat(config.getRequestTimeoutMillis()).isGreaterThan(0);
        assertThat(config.getResponseTimeoutMillis()).isGreaterThan(0);
        assertThat(config.getConnectionTimeoutMillis()).isGreaterThan(0);
        assertThat(config.getRequestLoggingEnabled()).isFalse();
        assertThat(config.getResponseLoggingEnabled()).isFalse();
        assertThat(config.getTestDataFolder()).isEqualTo("test-data");
        assertThat(config.getApiSchemaFolder()).isEqualTo("schemas");
        assertThat(config.getApiOpenApiSpecPath()).isEmpty();
        assertThat(config.getMaxRetryAttempts()).isGreaterThanOrEqualTo(0);
        assertThat(config.getRetryDelayMillis()).isGreaterThanOrEqualTo(0);
        assertThat(config.getDefaultContentType()).isEqualTo("application/json");
        assertThat(config.getDefaultAccept()).isEqualTo("application/json");
        assertThat(config.getCookiesEnabled()).isTrue();
        assertThat(config.getMaxResponseBodySizeKb()).isGreaterThan(0);
        assertThat(config.getDefaultAuthType()).isEqualTo(AuthType.NONE);
        assertThat(config.getBasicAuthUsernameEnv()).isEqualTo("API_USERNAME");
        assertThat(config.getBasicAuthPasswordEnv()).isEqualTo("API_PASSWORD");
        assertThat(config.getBearerTokenEnv()).isEqualTo("API_TOKEN");
        assertThat(config.getApiKeyNameEnv()).isEqualTo("API_KEY_NAME");
        assertThat(config.getApiKeyValueEnv()).isEqualTo("API_KEY_VALUE");
    }
}
