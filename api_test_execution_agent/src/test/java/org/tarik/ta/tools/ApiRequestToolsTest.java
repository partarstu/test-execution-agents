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
package org.tarik.ta.tools;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tarik.ta.ApiTestAgentConfig;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.model.AuthType;
import org.tarik.ta.core.model.TestExecutionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ApiRequestToolsTest {

    private static WireMockServer wireMockServer;
    private ApiContext apiContext;
    private ApiRequestTools apiRequestTools;
    private ApiTestAgentConfig config;
    private TestExecutionContext testExecutionContext;
    private Map<String, Object> sharedData;

    @BeforeAll
    static void setUpAll() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void tearDownAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        config = mock(ApiTestAgentConfig.class);
        when(config.getDefaultAuthType()).thenReturn(AuthType.NONE);
        when(config.getDefaultContentType()).thenReturn("application/json");
        when(config.getBasicAuthUsernameEnv()).thenReturn("API_USERNAME");
        when(config.getBasicAuthPasswordEnv()).thenReturn("API_PASSWORD");
        when(config.getBearerTokenEnv()).thenReturn("API_TOKEN");
        when(config.getApiKeyNameEnv()).thenReturn("API_KEY_NAME");
        when(config.getApiKeyValueEnv()).thenReturn("API_KEY_VALUE");
        apiContext = Mockito.spy(new ApiContext(config));
        apiContext.setBaseUri(wireMockServer.baseUrl());

        sharedData = new HashMap<>();
        testExecutionContext = Mockito.mock(TestExecutionContext.class);
        when(testExecutionContext.getSharedData()).thenReturn(sharedData);

        apiRequestTools = new ApiRequestTools(apiContext, testExecutionContext, config);
    }

    @Test
    void testSendGetRequest() {
        wireMockServer.stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Success")));

        String result = apiRequestTools.sendRequest("GET", wireMockServer.baseUrl() + "/test", null, null,
                AuthType.NONE);

        assertThat(result).contains("Status: 200");
        assertThat(apiContext.getLastResponse()).isPresent();
        assertThat(apiContext.getLastResponse().get().getBody().asString()).isEqualTo("Success");
    }

    @Test
    void testSendGetRequestWithVariableSubstitution() {
        wireMockServer.stubFor(get(urlEqualTo("/resource/123"))
                .willReturn(aResponse()
                        .withStatus(200)));

        sharedData.put("id", "123");

        String result = apiRequestTools.sendRequest("GET", wireMockServer.baseUrl() + "/resource/${id}", null,
                null,
                AuthType.NONE);

        assertThat(result).contains("Status: 200");
    }

    @Test
    void testSendPostRequestWithAuth() {
        wireMockServer.stubFor(post(urlEqualTo("/login"))
                .withHeader("Authorization", containing("Basic"))
                .willReturn(aResponse()
                        .withStatus(201)));

        System.setProperty("API_USERNAME", "user");
        System.setProperty("API_PASSWORD", "pass");

        try {
            String result = apiRequestTools.sendRequest("POST", wireMockServer.baseUrl() + "/login", null,
                    "{}",
                    AuthType.BASIC);

            assertThat(result).contains("Status: 201");
        } finally {
            System.clearProperty("API_USERNAME");
            System.clearProperty("API_PASSWORD");
        }
    }

    @Test
    void testSendPutRequest() {
        wireMockServer.stubFor(put(urlEqualTo("/update"))
                .withRequestBody(equalToJson("{\"key\":\"value\"}"))
                .willReturn(aResponse().withStatus(200)));

        String result = apiRequestTools.sendRequest("PUT", wireMockServer.baseUrl() + "/update", null,
                "{\"key\":\"value\"}", AuthType.NONE);

        assertThat(result).contains("Status: 200");
    }

    @Test
    void testSendDeleteRequest() {
        wireMockServer.stubFor(delete(urlEqualTo("/delete"))
                .willReturn(aResponse().withStatus(204)));

        String result = apiRequestTools.sendRequest("DELETE", wireMockServer.baseUrl() + "/delete", null,
                null, AuthType.NONE);

        assertThat(result).contains("Status: 204");
    }

    @Test
    void testSendRequestWithHeadersAndQueryParams() {
        wireMockServer.stubFor(get(urlPathEqualTo("/search"))
                .withHeader("X-Custom", equalTo("customVal"))
                .withQueryParam("q", equalTo("term"))
                .willReturn(aResponse().withStatus(200)));

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom", "customVal");

        String result = apiRequestTools.sendRequest("GET", wireMockServer.baseUrl() + "/search?q=term", headers,
                null, AuthType.NONE);

        assertThat(result).contains("Status: 200");
    }

    @Test
    void testBearerAuth() {
        wireMockServer.stubFor(get(urlEqualTo("/protected"))
                .withHeader("Authorization", equalTo("Bearer mytoken"))
                .willReturn(aResponse().withStatus(200)));

        System.setProperty("API_TOKEN", "mytoken");
        try {
            String result = apiRequestTools.sendRequest("GET", wireMockServer.baseUrl() + "/protected",
                    null,
                    null, AuthType.BEARER);
            assertThat(result).contains("Status: 200");
        } finally {
            System.clearProperty("API_TOKEN");
        }
    }

    @Test
    void testApiKeyAuth() {
        wireMockServer.stubFor(get(urlEqualTo("/protected"))
                .withHeader("X-API-KEY", equalTo("mykey"))
                .willReturn(aResponse().withStatus(200)));

        System.setProperty("API_KEY_NAME", "X-API-KEY");
        System.setProperty("API_KEY_VALUE", "mykey");
        try {
            String result = apiRequestTools.sendRequest("GET", wireMockServer.baseUrl() + "/protected",
                    null,
                    null, AuthType.API_TOKEN);
            assertThat(result).contains("Status: 200");
        } finally {
            System.clearProperty("API_KEY_NAME");
            System.clearProperty("API_KEY_VALUE");
        }
    }

    @Test
    void testUploadFile() {
        wireMockServer.stubFor(post(urlEqualTo("/upload"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .willReturn(aResponse().withStatus(201)));

        // Use a resource file
        String filePath = getClass().getClassLoader().getResource("pet-image.png").getPath();
        // Fix for Windows path if needed (leading slash issue)
        if (System.getProperty("os.name").toLowerCase().contains("win") && filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }

        String result = apiRequestTools.uploadFile(wireMockServer.baseUrl() + "/upload", filePath, "file",
                null, AuthType.NONE);

        assertThat(result).contains("Status: 201");
    }

    @Test
    void testSendRequestWithNullAuthTypeUsesDefault() {
        // Default auth type is NONE (from config), so no auth headers should be added
        wireMockServer.stubFor(get(urlEqualTo("/default-auth"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Success")));

        // Pass null for authType - should use default (NONE)
        String result = apiRequestTools.sendRequest("GET", wireMockServer.baseUrl() + "/default-auth", null, null,
                null);

        assertThat(result).contains("Status: 200");
        assertThat(apiContext.getLastResponse()).isPresent();
        assertThat(apiContext.getLastResponse().get().getBody().asString()).isEqualTo("Success");
    }

    @Test
    void testUploadFileWithNullAuthTypeUsesDefault() {
        wireMockServer.stubFor(post(urlEqualTo("/upload-default-auth"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .willReturn(aResponse().withStatus(201)));

        // Use a resource file
        String filePath = getClass().getClassLoader().getResource("pet-image.png").getPath();
        // Fix for Windows path if needed (leading slash issue)
        if (System.getProperty("os.name").toLowerCase().contains("win") && filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }

        // Pass null for authType - should use default (NONE)
        String result = apiRequestTools.uploadFile(wireMockServer.baseUrl() + "/upload-default-auth", filePath, "file",
                null, null);

        assertThat(result).contains("Status: 201");
    }

    @Test
    void testSendRequest_shouldThrowException_whenMethodIsEmpty() {
        assertThatThrownBy(() -> apiRequestTools.sendRequest("", "http://url", null, null, AuthType.NONE))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Method cannot be null or empty");
    }

    @Test
    void testSendRequest_shouldThrowException_whenUrlIsEmpty() {
        assertThatThrownBy(() -> apiRequestTools.sendRequest("GET", "", null, null, AuthType.NONE))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("URL cannot be null or empty");
    }

    @Test
    void testSendRequest_shouldResolveHeaders_whenVariablesPresent() {
        wireMockServer.stubFor(get(urlEqualTo("/headers"))
                .withHeader("X-Var", equalTo("val"))
                .willReturn(aResponse().withStatus(200)));

        sharedData.put("myvar", "val");
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Var", "${myvar}");

        String result = apiRequestTools.sendRequest("GET", wireMockServer.baseUrl() + "/headers", headers, null, AuthType.NONE);

        assertThat(result).contains("Status: 200");
    }

    @Test
    void testUploadFile_shouldThrowException_whenUrlIsEmpty() {
        assertThatThrownBy(() -> apiRequestTools.uploadFile("", "path", "name", null, AuthType.NONE))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("URL cannot be null or empty");
    }

    @Test
    void testUploadFile_shouldThrowException_whenFilePathIsEmpty() {
        assertThatThrownBy(() -> apiRequestTools.uploadFile("http://url", "", "name", null, AuthType.NONE))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("File path cannot be null or empty");
    }

    @Test
    void testUploadFile_shouldThrowException_whenMultipartNameIsEmpty() {
        assertThatThrownBy(() -> apiRequestTools.uploadFile("http://url", "path", "", null, AuthType.NONE))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Multipart name cannot be null or empty");
    }

    @Test
    void testUploadFile_shouldThrowException_whenFileNotFound() {
        assertThatThrownBy(() -> apiRequestTools.uploadFile("http://url", "non-existent-file", "name", null, AuthType.NONE))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("File not found at non-existent-file");
    }

    @Test
    void testAddBasicAuth_shouldThrowException_whenEnvVarsMissing() {
        assertThatThrownBy(() -> apiRequestTools.sendRequest("GET", "http://url", null, null, AuthType.BASIC))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Username or password environment variables not set or empty");
    }

    @Test
    void testAddBearerToken_shouldThrowException_whenEnvVarMissing() {
        assertThatThrownBy(() -> apiRequestTools.sendRequest("GET", "http://url", null, null, AuthType.BEARER))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Bearer token environment variable not set or empty");
    }

    @Test
    void testAddApiTokenToHeader_shouldThrowException_whenEnvVarsMissing() {
        assertThatThrownBy(() -> apiRequestTools.sendRequest("GET", "http://url", null, null, AuthType.API_TOKEN))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("API Key name or value environment variables not set or empty");
    }

    @Test
    void testGetLastApiResponse_shouldReturnMessage_whenNoResponseAvailable() {
        when(apiContext.getLastResponse()).thenReturn(Optional.empty());
        String result = apiRequestTools.getLastApiResponse();
        assertThat(result).isEqualTo("No API response available. No request has been made yet.");
    }

    @Test
    void testGetLastApiResponse_shouldReturnDetails_whenResponseAvailable() {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatusCode()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(mockResponse.getBody().asString()).thenReturn("Body");
        when(mockResponse.getHeaders()).thenReturn(new io.restassured.http.Headers());

        when(apiContext.getLastResponse()).thenReturn(Optional.of(mockResponse));

        String result = apiRequestTools.getLastApiResponse();
        assertThat(result).contains("Last API Response:");
        assertThat(result).contains("Status Code: 200");
        assertThat(result).contains("Response Body: Body");
    }
}
