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

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.tarik.ta.ApiAgentRequestScope;
import org.tarik.ta.ApiTestAgentConfig;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.tarik.ta.core.model.TestExecutionContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.tools.OutboundRequestGuard.isAbsoluteHttpUrl;
import org.tarik.ta.core.tools.AbstractTools;

@ApiAgentRequestScope
public class ApiAssertionTools extends AbstractTools {
    private static final String SCHEMA_FOLDER_NAME = "schema folder";
    private static final String ANY_HOST = "*";

    private final ApiContext apiContext;
    private final TestExecutionContext testExecutionContext;
    private final Path schemaFolder;
    private final OutboundRequestGuard outboundRequestGuard;

    public ApiAssertionTools(ApiContext apiContext, TestExecutionContext testExecutionContext, ApiTestAgentConfig config) {
        this.apiContext = apiContext;
        this.testExecutionContext = testExecutionContext;
        this.schemaFolder = Path.of(config.getApiSchemaFolder()).toAbsolutePath().normalize();
        this.outboundRequestGuard = OutboundRequestGuard.from(config);
    }

    @Tool("Validates the last response body against a JSON Schema file.")
    public String validateSchema(@P("Path to the JSON schema file, relative to the configured schema folder") String schemaPath) {
        if (isBlank(schemaPath)) {
            throw new ToolExecutionException("Schema path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        var schemaFile = resolveWithinFolder(schemaFolder, schemaPath, SCHEMA_FOLDER_NAME);

        Optional<Response> responseOpt = apiContext.getLastResponse();
        if (responseOpt.isEmpty()) {
            throw new ToolExecutionException("No response available.", TRANSIENT_TOOL_ERROR);
        }

        try {
            responseOpt.get().then().body(JsonSchemaValidator.matchesJsonSchema(schemaFile.toFile()));
            return "Schema validation passed.";
        } catch (AssertionError e) {
            return "Schema validation failed: " + e.getMessage();
        } catch (Exception e) {
            throw rethrowAsToolException(e, "validating schema against " + schemaPath);
        }
    }

    @Tool("Validates the last response against an OpenAPI specification file.")
    public String validateOpenApi(
            @P("URL of the OpenAPI spec, or path to the spec file relative to the configured schema folder") String specPath,
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

        var specLocation = resolveSpecLocation(specPath);
        Request.Method reqMethod = toRequestMethod(method);

        try {
            OpenApiInteractionValidator validator = OpenApiInteractionValidator.createFor(specLocation)
                    .withParseOptions(parseOptionsWithoutExternalRefs())
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

    private static Request.Method toRequestMethod(String method) {
        try {
            return Request.Method.valueOf(method.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ToolExecutionException("Invalid HTTP method: %s".formatted(method), TRANSIENT_TOOL_ERROR, e);
        }
    }

    // A $ref to another file or URL would be fetched by the parser outside the outbound guard, so every one is denied. The other
    // options mirror the validator's defaults, which a custom ParseOptions replaces entirely.
    private static ParseOptions parseOptionsWithoutExternalRefs() {
        var parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        parseOptions.setResolveFully(true);
        parseOptions.setResolveCombinators(false);
        parseOptions.setExplicitObjectSchema(false);
        parseOptions.setSafelyResolveURL(true);
        parseOptions.setRemoteRefBlockList(List.of(ANY_HOST));
        return parseOptions;
    }

    // The validator fetches a URL itself, so a spec URL from the model passes the same outbound guard as any request.
    private String resolveSpecLocation(String specPath) {
        if (isAbsoluteHttpUrl(specPath)) {
            outboundRequestGuard.assertHostAllowed(specPath);
            return specPath;
        }
        return resolveWithinFolder(schemaFolder, specPath, SCHEMA_FOLDER_NAME).toString();
    }
}
