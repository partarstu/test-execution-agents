package org.tarik.ta.tools;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.model.TestExecutionContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    private ApiAssertionTools tools;

    @BeforeEach
    void setUp() {
        tools = new ApiAssertionTools(apiContext, testExecutionContext);
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
    void validateSchema_shouldPass_whenSchemaMatches(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws java.io.IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenReturn(validatableResponse);

        // Create a simple schema file
        java.io.File schemaFile = tempDir.resolve("schema.json").toFile();
        java.nio.file.Files.writeString(schemaFile.toPath(), "{\"type\":\"object\"}");

        String result = tools.validateSchema(schemaFile.getAbsolutePath());

        assertThat(result).contains("Schema validation passed");
        verify(validatableResponse).body(any(org.hamcrest.Matcher.class));
    }

    @Test
    void validateOpenApi_shouldPass_whenSpecMatches(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws java.io.IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.statusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("");
        // Mock headers to be empty
        when(response.headers()).thenReturn(new io.restassured.http.Headers());

        // Create a simple OpenAPI spec file
        java.io.File specFile = tempDir.resolve("openapi.yaml").toFile();
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
        java.nio.file.Files.writeString(specFile.toPath(), openApiSpec);

        String result = tools.validateOpenApi(specFile.getAbsolutePath(), "GET", "/test");

        assertThat(result).contains("OpenAPI validation passed");
    }
}
