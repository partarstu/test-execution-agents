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
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ApiTestAgentConfig;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.model.TestExecutionContext;

import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;
import io.restassured.http.Headers;
import io.restassured.response.ResponseBody;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(MockitoExtension.class)
class ApiAssertionToolsTest {

    @Mock
    private ApiContext apiContext;
    @Mock
    private TestExecutionContext testExecutionContext;
    @Mock
    private Response response;
    @Mock
    private ValidatableResponse validatableResponse;
    @Mock
    private ApiTestAgentConfig config;
    @TempDir
    private Path schemaFolder;

    private ApiAssertionTools tools;

    @BeforeEach
    void setUp() {
        when(config.getApiSchemaFolder()).thenReturn(schemaFolder.toString());
        when(config.getOutboundHostAllowlist()).thenReturn(Set.of("api.example.com"));
        when(config.getUploadBaseDir()).thenReturn(Optional.empty());
        tools = new ApiAssertionTools(apiContext, testExecutionContext, config);
    }

    @Test
    void validateSchema_shouldThrowException_whenInvalidInput() {
        assertThatThrownBy(() -> tools.validateSchema(""))
                .isInstanceOf(ToolExecutionException.class);

        when(apiContext.getLastResponse()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tools.validateSchema("schema.json"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No response");
    }

    @Test
    void validateSchema_shouldPass_whenSchemaMatches()
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenReturn(validatableResponse);

        // Create a simple schema file
        File schemaFile = schemaFolder.resolve("schema.json").toFile();
        Files.writeString(schemaFile.toPath(), "{\"type\":\"object\"}");

        String result = tools.validateSchema("schema.json");

        assertThat(result).contains("Schema validation passed");
        verify(validatableResponse).body(any(Matcher.class));
    }

    @Test
    void validateOpenApi_shouldPass_whenSpecMatches()
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.statusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(ResponseBody.class));
        when(response.getBody().asString()).thenReturn("");
        // Mock headers to be empty
        when(response.headers()).thenReturn(new Headers());

        // Create a simple OpenAPI spec file
        File specFile = schemaFolder.resolve("openapi.yaml").toFile();
        String openApiSpec = "openapi: 3.0.0\n" +
                "info:\n" +
                "  title: Sample API\n" +
                "  version: 0.1.0\n" +
                "paths:\n" +
                "  /test:\n" +
                "    get:\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: OK";
        Files.writeString(specFile.toPath(), openApiSpec);

        String result = tools.validateOpenApi("openapi.yaml", "GET", "/test");

        assertThat(result).contains("OpenAPI validation passed");
    }

    @Test
    void validateOpenApi_shouldResolveTheLocalRefs_whenTheSpecIsSelfContained() throws IOException {
        givenLastResponse(200, "{\"id\": \"not-a-number\"}");
        Files.writeString(schemaFolder.resolve("openapi.yaml"), specWithResponseSchemaRef("#/components/schemas/Item") + """
                components:
                  schemas:
                    Item:
                      type: object
                      properties:
                        id:
                          type: integer
                """);

        String result = tools.validateOpenApi("openapi.yaml", "GET", "/test");

        assertThat(result).startsWith("OpenAPI Validation Failed").contains("integer");
    }

    @Test
    void validateOpenApi_shouldNotFetchARemoteRef() throws IOException {
        var remoteRefServer = new WireMockServer(wireMockConfig().dynamicPort());
        remoteRefServer.start();
        try {
            remoteRefServer.stubFor(get(urlEqualTo("/item.yaml")).willReturn(aResponse().withStatus(200).withBody("type: object")));
            when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
            Files.writeString(schemaFolder.resolve("openapi.yaml"), specWithResponseSchemaRef(remoteRefServer.baseUrl() + "/item.yaml"));

            var thrown = catchThrowable(() -> tools.validateOpenApi("openapi.yaml", "GET", "/test"));

            assertThat(thrown).isInstanceOf(ToolExecutionException.class).hasMessageContaining("denylist");
            assertThat(remoteRefServer.getAllServeEvents()).isEmpty();
        } finally {
            remoteRefServer.stop();
        }
    }

    @Test
    void validateOpenApi_shouldRefuseAnotherFile_whenTheLocalSpecReferencesIt() throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        Files.writeString(schemaFolder.resolve("item.yaml"), "type: object");
        Files.writeString(schemaFolder.resolve("openapi.yaml"), specWithResponseSchemaRef("item.yaml"));

        assertThatThrownBy(() -> tools.validateOpenApi("openapi.yaml", "GET", "/test"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("item.yaml");
    }

    @Test
    void validateOpenApi_shouldValidateAgainstASpecUrl_whenItsHostIsAllowed() {
        var specServer = new WireMockServer(wireMockConfig().dynamicPort());
        specServer.start();
        try {
            specServer.stubFor(get(urlEqualTo("/openapi.yaml")).willReturn(aResponse().withStatus(200)
                    .withBody(specWithResponseSchemaRef("#/components/schemas/Item") + """
                            components:
                              schemas:
                                Item:
                                  type: object
                                  properties:
                                    id:
                                      type: integer
                            """)));
            when(config.getOutboundHostAllowlist()).thenReturn(Set.of("localhost"));
            var toolsAllowingLocalhost = new ApiAssertionTools(apiContext, testExecutionContext, config);
            givenLastResponse(200, "{\"id\": \"not-a-number\"}");

            String result = toolsAllowingLocalhost.validateOpenApi(specServer.baseUrl() + "/openapi.yaml", "GET", "/test");

            assertThat(result).startsWith("OpenAPI Validation Failed").contains("integer");
        } finally {
            specServer.stop();
        }
    }

    private void givenLastResponse(int statusCode, String body) {
        var responseBody = mock(ResponseBody.class);
        when(responseBody.asString()).thenReturn(body);
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.statusCode()).thenReturn(statusCode);
        when(response.getBody()).thenReturn(responseBody);
        when(response.headers()).thenReturn(new Headers());
        when(response.getContentType()).thenReturn("application/json");
    }

    private static String specWithResponseSchemaRef(String ref) {
        return """
                openapi: 3.0.0
                info:
                  title: Sample API
                  version: 0.1.0
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                $ref: '%s'
                """.formatted(ref);
    }

    @Test
    void validateOpenApi_shouldThrowException_whenInvalidInput() {
        assertThatThrownBy(() -> tools.validateOpenApi("", "GET", "/path"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("OpenAPI spec path cannot be null or empty");
        assertThatThrownBy(() -> tools.validateOpenApi("spec.yaml", "", "/path"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Method cannot be null or empty");
        assertThatThrownBy(() -> tools.validateOpenApi("spec.yaml", "GET", ""))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Path cannot be null or empty");
    }

    @Test
    void validateOpenApi_shouldThrowException_whenNoResponse() {
        when(apiContext.getLastResponse()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tools.validateOpenApi("spec.yaml", "GET", "/path"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No response available.");
    }

    @Test
    void validateOpenApi_shouldFail_whenSpecHasErrors()
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.statusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(ResponseBody.class));
        when(response.getBody().asString()).thenReturn("");
        when(response.headers()).thenReturn(new Headers());

        // Create a simple OpenAPI spec file
        File specFile = schemaFolder.resolve("openapi.yaml").toFile();
        String openApiSpec = "openapi: 3.0.0\n" +
                "info:\n" +
                "  title: Sample API\n" +
                "  version: 0.1.0\n" +
                "paths:\n" +
                "  /test:\n" +
                "    get:\n" +
                "      responses:\n" +
                "        '404':\n" + // DIFFERENT STATUS
                "          description: Not Found";
        Files.writeString(specFile.toPath(), openApiSpec);

        String result = tools.validateOpenApi("openapi.yaml", "GET", "/test");

        assertThat(result).contains("OpenAPI Validation Failed");
    }

    @Test
    void validateOpenApi_shouldThrowException_whenMethodIsInvalid() {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        assertThatThrownBy(() -> tools.validateOpenApi("spec.yaml", "INVALID_METHOD", "/path"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Invalid HTTP method");
    }

    @Test
    void validateSchema_shouldFail_whenSchemaDoesNotMatch()
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenReturn(validatableResponse);
        doThrow(new AssertionError("Schema mismatch")).when(validatableResponse).body(any(Matcher.class));

        // Create a simple schema file
        File schemaFile = schemaFolder.resolve("schema.json").toFile();
        Files.writeString(schemaFile.toPath(), "{\"type\":\"object\"}");

        String result = tools.validateSchema("schema.json");

        assertThat(result).contains("Schema validation failed: Schema mismatch");
    }

    @Test
    void validateSchema_shouldRethrowAsToolException_whenUnexpectedExceptionOccurs()
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenThrow(new RuntimeException("Unexpected"));

        // Create a simple schema file
        File schemaFile = schemaFolder.resolve("schema.json").toFile();
        Files.writeString(schemaFile.toPath(), "{\"type\":\"object\"}");

        assertThatThrownBy(() -> tools.validateSchema("schema.json"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Unexpected");
    }

    @Test
    void validateSchema_shouldReadTheSchemaFromTheSchemaFolder_whenThePathIsRelative() throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenReturn(validatableResponse);
        Files.createDirectories(schemaFolder.resolve("products"));
        Files.writeString(schemaFolder.resolve("products/product.schema.json"), "{\"type\":\"object\"}");

        String result = tools.validateSchema("products/product.schema.json");

        assertThat(result).contains("Schema validation passed");
    }

    @Test
    void validateSchema_shouldRejectThePath_whenItLeavesTheSchemaFolder() {
        assertThatThrownBy(() -> tools.validateSchema("../outside.json"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("is outside of the schema folder");
        verifyNoInteractions(apiContext);
    }

    @Test
    void validateOpenApi_shouldRejectTheSpecUrl_whenItsHostIsNotAllowed() {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));

        assertThatThrownBy(() -> tools.validateOpenApi("http://10.0.0.5/openapi.yaml", "GET", "/test"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("not in the configured outbound allow-list");
    }
}
