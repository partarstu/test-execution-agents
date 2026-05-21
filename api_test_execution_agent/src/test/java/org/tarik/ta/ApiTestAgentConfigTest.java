/*
 * api-test-execution-agent - ${project.description}
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
