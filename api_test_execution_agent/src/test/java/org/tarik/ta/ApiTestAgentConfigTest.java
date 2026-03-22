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
        assertThat(ApiTestAgentConfig.getTargetBaseUri()).isNotNull();
        assertThat(ApiTestAgentConfig.getProxyPort()).isNotNull();
        assertThat(ApiTestAgentConfig.getProxyHost()).isNotNull();
        assertThat(ApiTestAgentConfig.getRelaxedHttpsValidation()).isTrue();
        assertThat(ApiTestAgentConfig.getRequestTimeoutMillis()).isGreaterThan(0);
        assertThat(ApiTestAgentConfig.getResponseTimeoutMillis()).isGreaterThan(0);
        assertThat(ApiTestAgentConfig.getConnectionTimeoutMillis()).isGreaterThan(0);
        assertThat(ApiTestAgentConfig.getRequestLoggingEnabled()).isFalse();
        assertThat(ApiTestAgentConfig.getResponseLoggingEnabled()).isFalse();
        assertThat(ApiTestAgentConfig.getTestDataFolder()).isEqualTo("test-data");
        assertThat(ApiTestAgentConfig.getApiSchemaFolder()).isEqualTo("schemas");
        assertThat(ApiTestAgentConfig.getApiOpenApiSpecPath()).isEmpty();
        assertThat(ApiTestAgentConfig.getMaxRetryAttempts()).isGreaterThanOrEqualTo(0);
        assertThat(ApiTestAgentConfig.getRetryDelayMillis()).isGreaterThanOrEqualTo(0);
        assertThat(ApiTestAgentConfig.getDefaultContentType()).isEqualTo("application/json");
        assertThat(ApiTestAgentConfig.getDefaultAccept()).isEqualTo("application/json");
        assertThat(ApiTestAgentConfig.getCookiesEnabled()).isTrue();
        assertThat(ApiTestAgentConfig.getMaxResponseBodySizeKb()).isGreaterThan(0);
        assertThat(ApiTestAgentConfig.getDefaultAuthType()).isEqualTo(AuthType.NONE);
        assertThat(ApiTestAgentConfig.getBasicAuthUsernameEnv()).isEqualTo("API_USERNAME");
        assertThat(ApiTestAgentConfig.getBasicAuthPasswordEnv()).isEqualTo("API_PASSWORD");
        assertThat(ApiTestAgentConfig.getBearerTokenEnv()).isEqualTo("API_TOKEN");
        assertThat(ApiTestAgentConfig.getApiKeyNameEnv()).isEqualTo("API_KEY_NAME");
        assertThat(ApiTestAgentConfig.getApiKeyValueEnv()).isEqualTo("API_KEY_VALUE");
    }
}
