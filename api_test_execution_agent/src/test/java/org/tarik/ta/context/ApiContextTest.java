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
