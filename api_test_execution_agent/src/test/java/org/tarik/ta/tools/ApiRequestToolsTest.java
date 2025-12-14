package org.tarik.ta.tools;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.model.AuthType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ApiRequestToolsTest {

    private WireMockServer wireMockServer;
    private ApiContext apiContext;
    private ApiRequestTools apiRequestTools;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        apiContext = Mockito.spy(new ApiContext());
        apiContext.setBaseUri(wireMockServer.baseUrl());

        apiRequestTools = new ApiRequestTools(apiContext,
                Mockito.mock(org.tarik.ta.core.model.TestExecutionContext.class));
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
                AuthType.NONE, null, null);

        assertThat(result).contains("Status: 200");
        assertThat(apiContext.getLastResponse()).isPresent();
        assertThat(apiContext.getLastResponse().get().getBody().asString()).isEqualTo("Success");
    }

    @Test
    void testSendGetRequestWithVariableSubstitution() {
        wireMockServer.stubFor(get(urlEqualTo("/resource/123"))
                .willReturn(aResponse()
                        .withStatus(200)));

        apiContext.setVariable("id", "123");

        String result = apiRequestTools.sendRequest("GET", wireMockServer.baseUrl() + "/resource/${id}", null, null,
                AuthType.NONE, null, null);

        assertThat(result).contains("Status: 200");
    }

    @Test
    void testSendPostRequestWithAuth() {
        wireMockServer.stubFor(post(urlEqualTo("/login"))
                .withHeader("Authorization", containing("Basic")) // simplified check
                .willReturn(aResponse()
                        .withStatus(201)));

        String result = apiRequestTools.sendRequest("POST", wireMockServer.baseUrl() + "/login", null, "{}",
                AuthType.BASIC, "user:pass", null);

        assertThat(result).contains("Status: 201");
    }
}
