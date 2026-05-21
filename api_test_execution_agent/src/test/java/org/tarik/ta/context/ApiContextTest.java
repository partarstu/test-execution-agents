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
package org.tarik.ta.context;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.tarik.ta.ApiTestAgentConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiContextTest {

    @Test
    void testApiContextAccessors() {
        ApiTestAgentConfig config = mock(ApiTestAgentConfig.class);
        ApiContext context = new ApiContext(config);
        
        context.setBaseUri("http://localhost");
        assertThat(context.getBaseUri()).contains("http://localhost");
        
        context.setProxyHost("proxy");
        assertThat(context.getProxyHost()).contains("proxy");
        
        context.setProxyPort(8080);
        assertThat(context.getProxyPort()).contains(8080);
        
        context.setRelaxedHttpsValidation(false);
        assertThat(context.isRelaxedHttpsValidation()).isFalse();
        
        Response mockResponse = mock(Response.class);
        context.setLastResponse(mockResponse);
        assertThat(context.getLastResponse()).contains(mockResponse);
        
        context.clear();
        assertThat(context.getLastResponse()).isEmpty();
        
        assertThat(context.getCookieFilter()).isNotNull();
    }

    @Test
    void constructor_shouldInitializeFromConfig() {
        ApiTestAgentConfig config = mock(ApiTestAgentConfig.class);
        when(config.getTargetBaseUri()).thenReturn(Optional.of("http://base"));
        when(config.getProxyHost()).thenReturn(Optional.of("proxy-host"));
        when(config.getProxyPort()).thenReturn(8888);
        when(config.getRelaxedHttpsValidation()).thenReturn(true);

        ApiContext context = new ApiContext(config);

        assertThat(context.getBaseUri()).contains("http://base");
        assertThat(context.getProxyHost()).contains("proxy-host");
        assertThat(context.getProxyPort()).contains(8888);
        assertThat(context.isRelaxedHttpsValidation()).isTrue();
    }
}
