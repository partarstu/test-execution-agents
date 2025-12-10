package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.Tool;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.tarik.ta.context.ApiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;

public class ApiAssertionTools {
    private static final Logger LOG = LoggerFactory.getLogger(ApiAssertionTools.class);
    private final ApiContext context;

    public ApiAssertionTools(ApiContext context) {
        this.context = context;
    }

    @Tool("Asserts that the last response status code matches the expected code.")
    public String assertStatusCode(int expectedCode) {
        Optional<Response> responseOpt = context.getLastResponse();
        if (responseOpt.isEmpty()) return "Error: No response available to assert.";

        try {
            responseOpt.get().then().statusCode(expectedCode);
            return "Assertion passed: Status code is " + expectedCode;
        } catch (AssertionError e) {
            return "Assertion failed: " + e.getMessage();
        }
    }

    @Tool("Asserts that the JSON path in the last response matches the expected value.")
    public String assertJsonPath(String jsonPath, String expectedValue) {
        Optional<Response> responseOpt = context.getLastResponse();
        if (responseOpt.isEmpty()) return "Error: No response available to assert.";

        try {
            // Using string comparison for flexibility
            String actual = responseOpt.get().jsonPath().getString(jsonPath);
            if (expectedValue.equals(actual)) {
                return "Assertion passed: " + jsonPath + " == " + expectedValue;
            } else {
                return "Assertion failed: " + jsonPath + " expected '" + expectedValue + "' but was '" + actual + "'";
            }
        } catch (Exception e) {
            return "Assertion error: " + e.getMessage();
        }
    }

    @Tool("Extracts a value from the last response using JSON path and stores it in a context variable.")
    public String extractValue(String jsonPath, String variableName) {
        Optional<Response> responseOpt = context.getLastResponse();
        if (responseOpt.isEmpty()) return "Error: No response available.";

        try {
            Object value = responseOpt.get().jsonPath().get(jsonPath);
            context.setVariable(variableName, value);
            return "Extracted value '" + value + "' to variable '" + variableName + "'";
        } catch (Exception e) {
            return "Error extracting value: " + e.getMessage();
        }
    }

    @Tool("Validates the last response body against a JSON Schema file.")
    public String validateSchema(String schemaPath) {
        Optional<Response> responseOpt = context.getLastResponse();
        if (responseOpt.isEmpty()) return "Error: No response available.";

        try {
            responseOpt.get().then().body(JsonSchemaValidator.matchesJsonSchema(new File(schemaPath)));
            return "Schema validation passed.";
        } catch (AssertionError e) {
            return "Schema validation failed: " + e.getMessage();
        } catch (Exception e) {
            return "Error reading schema: " + e.getMessage();
        }
    }
}
