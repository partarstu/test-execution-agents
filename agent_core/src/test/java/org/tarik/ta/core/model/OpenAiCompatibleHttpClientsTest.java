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
package org.tarik.ta.core.model;

import org.junit.jupiter.api.Test;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.core.model.OpenAiCompatibleHttpClients.httpClientBuilderFor;
import static org.tarik.ta.core.model.OpenAiCompatibleHttpClients.httpVersionFor;

class OpenAiCompatibleHttpClientsTest {

    @Test
    void httpVersionFor_shouldBeHttp11_whenEndpointIsCleartext() {
        assertThat(httpVersionFor("http://localhost:18000/v1/")).isEqualTo(HTTP_1_1);
        assertThat(httpVersionFor("HTTP://10.0.0.5:8000/v1")).isEqualTo(HTTP_1_1);
        assertThat(httpVersionFor(" http://vllm:8000/v1/ ")).isEqualTo(HTTP_1_1);
    }

    @Test
    void httpVersionFor_shouldKeepJdkDefault_whenEndpointIsNotCleartext() {
        assertThat(httpVersionFor("https://model-service-abc123-ez.a.run.app/v1/")).isEqualTo(HTTP_2);
        assertThat(httpVersionFor("YOUR_QWEN_ENDPOINT_HERE")).isEqualTo(HTTP_2);
        assertThat(httpVersionFor("")).isEqualTo(HTTP_2);
    }

    @Test
    void httpClientBuilderFor_shouldBuildClientSpeakingHttp11_whenEndpointIsCleartext() {
        var httpClient = httpClientBuilderFor("http://localhost:18000/v1/").httpClientBuilder().build();

        assertThat(httpClient.version()).isEqualTo(HTTP_1_1);
    }

    @Test
    void httpClientBuilderFor_shouldBuildClientSpeakingHttp2_whenEndpointIsTls() {
        var httpClient = httpClientBuilderFor("https://api.openai.com/v1/").httpClientBuilder().build();

        assertThat(httpClient.version()).isEqualTo(HTTP_2);
    }
}
