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
    void assertStatusCode_shouldPass_whenCodeMatches() {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenReturn(validatableResponse);
        when(validatableResponse.statusCode(200)).thenReturn(validatableResponse);

        String result = tools.assertStatusCode(200);

        assertThat(result).contains("Assertion passed");
        verify(validatableResponse).statusCode(200);
    }

    @Test
    void assertStatusCode_shouldFail_whenCodeMismatch() {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenReturn(validatableResponse);
        // Simulate assertion error from REST Assured
        doThrow(new AssertionError("Status code 200 mismatch")).when(validatableResponse).statusCode(200);

        String result = tools.assertStatusCode(200);

        assertThat(result).contains("Assertion failed");
    }

    @Test
    void assertStatusCode_shouldThrowException_whenNoResponse() {
        when(apiContext.getLastResponse()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.assertStatusCode(200))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No response available");
    }
    
    @Test
    void assertJsonPath_shouldPass_whenValueMatches() {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        JsonPath jsonPath = mock(JsonPath.class);
        when(response.jsonPath()).thenReturn(jsonPath);
        when(jsonPath.getString("data.id")).thenReturn("123");

        String result = tools.assertJsonPath("data.id", "123");
        
        assertThat(result).contains("Assertion passed");
    }

    @Test
    void assertJsonPath_shouldFail_whenValueMismatch() {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        JsonPath jsonPath = mock(JsonPath.class);
        when(response.jsonPath()).thenReturn(jsonPath);
        when(jsonPath.getString("data.id")).thenReturn("456");

        String result = tools.assertJsonPath("data.id", "123");
        
        assertThat(result).contains("Assertion failed");
        assertThat(result).contains("expected '123' but was '456'");
    }

    @Test
    void extractValue_shouldStoreValue() {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        JsonPath jsonPath = mock(JsonPath.class);
        when(response.jsonPath()).thenReturn(jsonPath);
        when(jsonPath.get("data.token")).thenReturn("secret-token");

        String result = tools.extractValue("data.token", "TOKEN");

        assertThat(result).contains("Extracted value 'secret-token' to variable 'TOKEN'");
        verify(testExecutionContext).addSharedData("TOKEN", "secret-token");
    }

    @Test
    void extractValue_shouldThrowException_whenInvalidInput() {
        assertThatThrownBy(() -> tools.extractValue("", "var"))
                .isInstanceOf(ToolExecutionException.class);
        assertThatThrownBy(() -> tools.extractValue("path", ""))
                .isInstanceOf(ToolExecutionException.class);
        
        when(apiContext.getLastResponse()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tools.extractValue("path", "var"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No response");
    }

    @Test
    void assertJsonPath_shouldThrowException_whenInvalidInput() {
        assertThatThrownBy(() -> tools.assertJsonPath("", "val"))
                .isInstanceOf(ToolExecutionException.class);
        assertThatThrownBy(() -> tools.assertJsonPath("path", ""))
                .isInstanceOf(ToolExecutionException.class);
        
        when(apiContext.getLastResponse()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tools.assertJsonPath("path", "val"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No response");
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
    void validateSchema_shouldPass_whenSchemaMatches(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
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
    void validateOpenApi_shouldPass_whenSpecMatches(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
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
