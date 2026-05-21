/*
 * api-test-execution-agent - ${project.description}
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
    void validateSchema_shouldPass_whenSchemaMatches(@TempDir Path tempDir)
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenReturn(validatableResponse);

        // Create a simple schema file
        File schemaFile = tempDir.resolve("schema.json").toFile();
        Files.writeString(schemaFile.toPath(), "{\"type\":\"object\"}");

        String result = tools.validateSchema(schemaFile.getAbsolutePath());

        assertThat(result).contains("Schema validation passed");
        verify(validatableResponse).body(any(Matcher.class));
    }

    @Test
    void validateOpenApi_shouldPass_whenSpecMatches(@TempDir Path tempDir)
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.statusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(ResponseBody.class));
        when(response.getBody().asString()).thenReturn("");
        // Mock headers to be empty
        when(response.headers()).thenReturn(new Headers());

        // Create a simple OpenAPI spec file
        File specFile = tempDir.resolve("openapi.yaml").toFile();
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

        String result = tools.validateOpenApi(specFile.getAbsolutePath(), "GET", "/test");

        assertThat(result).contains("OpenAPI validation passed");
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
    void validateOpenApi_shouldFail_whenSpecHasErrors(@TempDir Path tempDir)
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.statusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(ResponseBody.class));
        when(response.getBody().asString()).thenReturn("");
        when(response.headers()).thenReturn(new Headers());

        // Create a simple OpenAPI spec file
        File specFile = tempDir.resolve("openapi.yaml").toFile();
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

        String result = tools.validateOpenApi(specFile.getAbsolutePath(), "GET", "/test");

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
    void validateSchema_shouldFail_whenSchemaDoesNotMatch(@TempDir Path tempDir)
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenReturn(validatableResponse);
        doThrow(new AssertionError("Schema mismatch")).when(validatableResponse).body(any(Matcher.class));

        // Create a simple schema file
        File schemaFile = tempDir.resolve("schema.json").toFile();
        Files.writeString(schemaFile.toPath(), "{\"type\":\"object\"}");

        String result = tools.validateSchema(schemaFile.getAbsolutePath());

        assertThat(result).contains("Schema validation failed: Schema mismatch");
    }

    @Test
    void validateSchema_shouldRethrowAsToolException_whenUnexpectedExceptionOccurs(@TempDir Path tempDir)
            throws IOException {
        when(apiContext.getLastResponse()).thenReturn(Optional.of(response));
        when(response.then()).thenThrow(new RuntimeException("Unexpected"));

        // Create a simple schema file
        File schemaFile = tempDir.resolve("schema.json").toFile();
        Files.writeString(schemaFile.toPath(), "{\"type\":\"object\"}");

        assertThatThrownBy(() -> tools.validateSchema(schemaFile.getAbsolutePath()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Unexpected");
    }
}
