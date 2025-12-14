package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import java.io.File;
import java.util.Optional;

import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;

public class ApiAssertionTools extends org.tarik.ta.core.tools.AbstractTools {
    private final ApiContext context;

    public ApiAssertionTools(ApiContext context) {
        this.context = context;
    }

    @Tool("Asserts that the last response status code matches the expected code.")
    public String assertStatusCode(@P("Expected HTTP status code") int expectedCode) {
        Optional<Response> responseOpt = context.getLastResponse();
        if (responseOpt.isEmpty())
            throw new ToolExecutionException("No response available to assert.", TRANSIENT_TOOL_ERROR);

        try {
            responseOpt.get().then().statusCode(expectedCode);
            return "Assertion passed: Status code is " + expectedCode;
        } catch (AssertionError e) {
            return "Assertion failed: " + e.getMessage();
        } catch (Exception e) {
            throw rethrowAsToolException(e, "asserting status code");
        }
    }

    @Tool("Asserts that the JSON path in the last response matches the expected value.")
    public String assertJsonPath(@P("JSON path expression") String jsonPath,
            @P("Expected value") String expectedValue) {
        if (jsonPath == null || jsonPath.isBlank()) {
            throw new ToolExecutionException("JSON path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        Optional<Response> responseOpt = context.getLastResponse();
        if (responseOpt.isEmpty())
            throw new ToolExecutionException("No response available to assert.", TRANSIENT_TOOL_ERROR);

        try {
            // Using string comparison for flexibility
            String actual = responseOpt.get().jsonPath().getString(jsonPath);
            if (expectedValue.equals(actual)) {
                return "Assertion passed: " + jsonPath + " == " + expectedValue;
            } else {
                return "Assertion failed: " + jsonPath + " expected '" + expectedValue + "' but was '" + actual + "'";
            }
        } catch (Exception e) {
            throw rethrowAsToolException(e, "asserting JSON path " + jsonPath);
        }
    }

    @Tool("Extracts a value from the last response using JSON path and stores it in a context variable.")
    public String extractValue(@P("JSON path expression") String jsonPath,
            @P("Variable name to store value") String variableName) {
        if (jsonPath == null || jsonPath.isBlank()) {
            throw new ToolExecutionException("JSON path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (variableName == null || variableName.isBlank()) {
            throw new ToolExecutionException("Variable name cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        Optional<Response> responseOpt = context.getLastResponse();
        if (responseOpt.isEmpty())
            throw new ToolExecutionException("No response available.", TRANSIENT_TOOL_ERROR);

        try {
            Object value = responseOpt.get().jsonPath().get(jsonPath);
            context.setVariable(variableName, value);
            return "Extracted value '" + value + "' to variable '" + variableName + "'";
        } catch (Exception e) {
            throw rethrowAsToolException(e, "extracting value from JSON path " + jsonPath);
        }
    }

    @Tool("Validates the last response body against a JSON Schema file.")
    public String validateSchema(@P("Path to the JSON schema file") String schemaPath) {
        if (schemaPath == null || schemaPath.isBlank()) {
            throw new ToolExecutionException("Schema path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        Optional<Response> responseOpt = context.getLastResponse();
        if (responseOpt.isEmpty())
            throw new ToolExecutionException("No response available.", TRANSIENT_TOOL_ERROR);

        try {
            responseOpt.get().then().body(JsonSchemaValidator.matchesJsonSchema(new File(schemaPath)));
            return "Schema validation passed.";
        } catch (AssertionError e) {
            return "Schema validation failed: " + e.getMessage();
        } catch (Exception e) {
            throw rethrowAsToolException(e, "validating schema against " + schemaPath);
        }
    }

    @Tool("Validates the last response against an OpenAPI specification file.")
    public String validateOpenApi(@P("Path to the OpenAPI spec file") String specPath) {
        if (specPath == null || specPath.isBlank()) {
            throw new ToolExecutionException("OpenAPI spec path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        Optional<Response> responseOpt = context.getLastResponse();
        if (responseOpt.isEmpty())
            throw new ToolExecutionException("No response available.", TRANSIENT_TOOL_ERROR);

        String method = (String) context.getVariable("_last_request_method");
        String path = (String) context.getVariable("_last_request_path");

        if (method == null || path == null) {
            throw new ToolExecutionException(
                    "Request method or path not found in context. Validation requires a preceding request.",
                    TRANSIENT_TOOL_ERROR);
        }

        try {
            OpenApiInteractionValidator validator = OpenApiInteractionValidator
                    .createFor(specPath)
                    .build();

            Response raResponse = responseOpt.get();
            SimpleResponse.Builder builder = SimpleResponse.Builder.status(raResponse.statusCode());
            raResponse.headers().forEach(h -> builder.withHeader(h.getName(), h.getValue()));

            // Check if body is empty or not
            String body = raResponse.getBody().asString();
            if (body != null && !body.isEmpty()) {
                builder.withBody(body);
            }
            if (raResponse.getContentType() != null) {
                builder.withContentType(raResponse.getContentType());
            }

            com.atlassian.oai.validator.model.Response oaiResponse = builder.build();
            Request.Method reqMethod = Request.Method.valueOf(method.toUpperCase());

            ValidationReport report = validator.validateResponse(path, reqMethod, oaiResponse);

            if (report.hasErrors()) {
                StringBuilder sb = new StringBuilder("OpenAPI Validation Failed:\n");
                report.getMessages().forEach(m -> sb.append("- ").append(m.getMessage()).append("\n"));
                return sb.toString();
            }

            return "OpenAPI validation passed.";

        } catch (Exception e) {
            throw rethrowAsToolException(e, "validating against OpenAPI spec " + specPath);
        }
    }
}
