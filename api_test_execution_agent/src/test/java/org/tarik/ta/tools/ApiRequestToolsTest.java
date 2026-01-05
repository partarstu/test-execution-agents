package org.tarik.ta.tools;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.model.AuthType;
import org.tarik.ta.core.model.TestExecutionContext;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiRequestToolsTest {

    private WireMockServer wireMockServer;
    private ApiContext apiContext;
    private ApiRequestTools apiRequestTools;
    private TestExecutionContext testExecutionContext;
    private Map<String, Object> sharedData;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        apiContext = Mockito.spy(new ApiContext());
        apiContext.setBaseUri(wireMockServer.baseUrl());

        sharedData = new HashMap<>();
        testExecutionContext = Mockito.mock(TestExecutionContext.class);
        when(testExecutionContext.getSharedData()).thenReturn(sharedData);

        apiRequestTools = new ApiRequestTools(apiContext, testExecutionContext);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
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
}
