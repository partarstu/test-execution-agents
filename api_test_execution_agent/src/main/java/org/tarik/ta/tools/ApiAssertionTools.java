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
import org.tarik.ta.core.model.TestExecutionContext;

import java.io.File;
import java.util.Optional;

import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;

public class ApiAssertionTools extends org.tarik.ta.core.tools.AbstractTools {
    private final ApiContext apiContext;
    private final TestExecutionContext testExecutionContext;

    public ApiAssertionTools(ApiContext apiContext, TestExecutionContext testExecutionContext) {
        this.apiContext = apiContext;
        this.testExecutionContext = testExecutionContext;
    }

    @Tool("Validates the last response body against a JSON Schema file.")
    public String validateSchema(@P("Path to the JSON schema file") String schemaPath) {
        if (isBlank(schemaPath)) {
            throw new ToolExecutionException("Schema path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        Optional<Response> responseOpt = apiContext.getLastResponse();
        if (responseOpt.isEmpty()) {
            throw new ToolExecutionException("No response available.", TRANSIENT_TOOL_ERROR);
        }

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
    public String validateOpenApi(@P("Path to the OpenAPI spec file") String specPath,
            @P("HTTP method of the request") String method,
            @P("Request path") String path) {
        if (isBlank(specPath)) {
            throw new ToolExecutionException("OpenAPI spec path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(method)) {
            throw new ToolExecutionException("Method cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(path)) {
            throw new ToolExecutionException("Path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        Optional<Response> responseOpt = apiContext.getLastResponse();
        if (responseOpt.isEmpty()) {
            throw new ToolExecutionException("No response available.", TRANSIENT_TOOL_ERROR);
        }

        try {
            OpenApiInteractionValidator validator = OpenApiInteractionValidator.createFor(specPath).build();
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
        } catch (IllegalArgumentException e) {
            throw new ToolExecutionException("Invalid HTTP method: " + method, TRANSIENT_TOOL_ERROR);
        } catch (Exception e) {
            throw rethrowAsToolException(e, "validating against OpenAPI spec " + specPath);
        }
    }
}
