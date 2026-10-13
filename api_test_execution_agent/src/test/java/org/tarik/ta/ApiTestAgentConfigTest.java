/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

class ApiTestAgentConfigTest {

    @Test
    void testConfigAccessors() {
        var config = new ApiTestAgentConfig();

        assertThat(config.getTargetBaseUri()).isNotNull();
        assertThat(config.getProxyPort()).isNotNull();
        assertThat(config.getProxyHost()).isNotNull();
        assertThat(config.getRelaxedHttpsValidation()).isFalse();
        assertThat(config.getOutboundHostAllowlist()).isEmpty();
        assertThat(config.getUploadBaseDir()).isEmpty();
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

    @Test
    void configuredActionPrompts_shouldExplainTheStepAttributionOfTheContextData() {
        var config = new ApiTestAgentConfig();

        var prompts = List.of(
                loadSystemPrompt("test_step/executor", config.getTestStepActionAgentPromptVersion(), "test_step_action_prompt.md"),
                loadSystemPrompt("precondition/executor", config.getPreconditionAgentPromptVersion(), "precondition_execution_prompt.md"));

        assertThat(config.getTestStepActionAgentPromptVersion()).isEqualTo("v1.1.0");
        assertThat(config.getPreconditionAgentPromptVersion()).isEqualTo("v1.1.0");
        assertThat(prompts).allSatisfy(prompt -> assertThat(prompt)
                .contains("## Test execution context data")
                .contains("tells whether that is the one being executed right now")
                .contains("Never resolve an indirect reference of the current precondition or test step")
                .contains("not even when exactly one earlier entry would fit")
                .contains("report a failure instead and name the missing data")
                // LangChain4j renders a system prompt as a template, so any double curly brace would demand a template variable.
                .doesNotContain("{{"));
    }
}
