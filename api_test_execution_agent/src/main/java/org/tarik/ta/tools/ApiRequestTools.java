package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.model.AuthType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.tarik.ta.core.utils.CommonUtils;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;

public class ApiRequestTools extends org.tarik.ta.core.tools.AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ApiRequestTools.class);
    private final ApiContext context;
    private final TestExecutionContext executionContext;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    public ApiRequestTools(ApiContext context, TestExecutionContext executionContext) {
        this.context = context;
        this.executionContext = executionContext;
    }

    @Tool("Sends an HTTP request. 'headers' is a map of header names to values. 'authValue' depends on authType" +
            " (e.g., 'user:pass' for BASIC, 'token' for BEARER, 'key=value' for API_KEY).")
    public String sendRequest(
            @P("HTTP method (GET, POST, PUT, DELETE, etc.)") String method,
            @P("The target URL") String url,
            @P("Optional headers map") Map<String, String> headers,
            @P("Request body (optional)") String body,
            @P("Authentication type (NONE, BASIC, BEARER, API_KEY)") AuthType authType,
            @P("Authentication value") String authValue,
            @P("Authentication location for API_KEY (HEADER, QUERY)") String authLocation) {
        if (isBlank(method)) {
            throw new ToolExecutionException("Method cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(url)) {
            throw new ToolExecutionException("URL cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        try {
            String resolvedUrl = resolveVariables(url);
            String resolvedBody = resolveVariables(body);
            RequestSpecification request = given()                    .filter(context.getCookieFilter());
            if (context.isRelaxedHttpsValidation()) {
                request.relaxedHTTPSValidation();
            }

            context.getBaseUri().ifPresent(request::baseUri);
            if (context.getProxyHost().isPresent()) {
                request.proxy(context.getProxyHost().get(), context.getProxyPort().orElse(8080));
            }

            if (headers != null) {
                headers.forEach((k, v) -> request.header(resolveVariables(k), resolveVariables(v)));
            }

            if (resolvedBody != null && !resolvedBody.isEmpty()) {
                request.body(resolvedBody);
            }

            applyAuth(request, authType, authValue, authLocation);

            LOG.info("Sending {} request to {}", method, resolvedUrl);
            Response response = request.request(method, resolvedUrl);
            context.setLastResponse(response);
            executionContext.setSharedData("_last_request_method", method);
            executionContext.setSharedData("_last_request_path", resolvedUrl);
            return "Request sent. Status: " + response.getStatusCode();
        } catch (Exception e) {
            throw rethrowAsToolException(e, "sending request to " + url);
        }
    }

    @Tool("Uploads a file using multipart/form-data.")
    public String uploadFile(
            @P("The target URL") String url,
            @P("Path to the file to upload") String filePath,
            @P("Multipart name for the file") String multipartName,
            @P("Optional headers map") Map<String, String> headers) {
        if (isBlank(url)) {
            throw new ToolExecutionException("URL cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(filePath)) {
            throw new ToolExecutionException("File path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(multipartName)) {
            throw new ToolExecutionException("Multipart name cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        try {
            String resolvedUrl = resolveVariables(url);
            String resolvedFilePath = resolveVariables(filePath);
            File file = new File(resolvedFilePath);

            if (!file.exists()) {
                throw new ToolExecutionException("File not found at " + resolvedFilePath, TRANSIENT_TOOL_ERROR);
            }

            RequestSpecification request = given()
                    .filter(context.getCookieFilter())
                    .multiPart(multipartName, file);

            if (context.isRelaxedHttpsValidation()) {
                request.relaxedHTTPSValidation();
            }

            context.getBaseUri().ifPresent(request::baseUri);

            if (headers != null) {
                headers.forEach((k, v) -> request.header(resolveVariables(k), resolveVariables(v)));
            }

            LOG.info("Uploading file {} to {}", resolvedFilePath, resolvedUrl);
            Response response = request.post(resolvedUrl);
            context.setLastResponse(response);

            return "File uploaded. Status: " + response.getStatusCode();
        } catch (Exception e) {
            throw rethrowAsToolException(e, "uploading file to " + url);
        }
    }

    private void applyAuth(RequestSpecification request, AuthType authType, String authValue, String authLocation) {
        if (isBlank(authValue)) {
            throw new ToolExecutionException("Auth value cannot be null or empty for auth type: " + authType, TRANSIENT_TOOL_ERROR);
        }

        String resolvedValue = resolveVariables(authValue);
        switch (authType) {
            case BASIC -> {
                String[] parts = resolvedValue.split(":", 2);
                if (parts.length == 2) {
                    request.auth().preemptive().basic(parts[0], parts[1]);
                } else {
                    LOG.warn("Invalid Basic Auth value format. Expected 'user:pass'.");
                }
            }
            case BEARER -> request.auth().oauth2(resolvedValue);
            case API_KEY -> {
                if (isBlank(authLocation)) {
                    throw new ToolExecutionException("Auth location cannot be null or empty for API_KEY auth type",
                            TRANSIENT_TOOL_ERROR);
                }
                // authValue expected as 'key=value'
                String[] parts = resolvedValue.split("=", 2);
                if (parts.length != 2) {
                    LOG.warn("Invalid API Key value format. Expected 'keyName=value'.");
                    return;
                }
                String key = parts[0];
                String value = parts[1];

                if ("QUERY".equalsIgnoreCase(authLocation)) {
                    request.queryParam(key, value);
                } else {
                    request.header(key, value);
                }
            }
        }
    }

    private String resolveVariables(String input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = null;
            if (executionContext != null) {
                value = executionContext.getSharedData().get(varName);
            }
            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value.toString()));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
