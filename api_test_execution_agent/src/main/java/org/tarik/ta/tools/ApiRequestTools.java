package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;
import io.restassured.specification.RequestSpecification;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.model.AuthType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.tarik.ta.ApiTestAgentConfig;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.ApiTestAgentConfig.*;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.getEnvironmentVariable;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;

public class ApiRequestTools extends org.tarik.ta.core.tools.AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ApiRequestTools.class);
    public static final String LAST_REQUEST_METHOD = "_last_request_method";
    public static final String LAST_REQUEST_URL = "_last_request_path";
    private final ApiContext apiContext;
    private final TestExecutionContext testExecutionContext;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    public ApiRequestTools(ApiContext context, TestExecutionContext executionContext) {
        this.apiContext = context;
        this.testExecutionContext = executionContext;
    }

    @Tool("Sends an HTTP request.")
    public String sendRequest(
            @P("HTTP method (GET, POST, PUT, DELETE, etc.)") String method,
            @P("The fully qualified absolute target URL") String url,
            @P(value = "Optional headers map", required = false) Map<String, String> headers,
            @P(value = "Request body", required = false) String body,
            @P(value = "Authentication type. If not specified, the configured default will be used.", required = false) AuthType authType) {
        if (isBlank(method)) {
            throw new ToolExecutionException("Method cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(url)) {
            throw new ToolExecutionException("URL cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        AuthType effectiveAuthType = authType != null ? authType : ApiTestAgentConfig.getDefaultAuthType();

        try {
            String resolvedUrl = resolveVariables(url);
            String resolvedBody = resolveVariables(body);
            RequestSpecification request = given().filter(apiContext.getCookieFilter());
            if (apiContext.isRelaxedHttpsValidation()) {
                request.relaxedHTTPSValidation();
            }

            apiContext.getBaseUri().ifPresent(request::baseUri);
            if (apiContext.getProxyHost().isPresent()) {
                request.proxy(apiContext.getProxyHost().get(), apiContext.getProxyPort().orElse(8080));
            }

            Map<String, String> requestHeaders = headers != null ? new HashMap<>(headers) : new HashMap<>();

            if (!requestHeaders.containsKey("Content-Type") && !requestHeaders.containsKey("content-type")) {
                requestHeaders.put("Content-Type", ApiTestAgentConfig.getDefaultContentType());
            }

            requestHeaders.forEach((k, v) -> request.header(resolveVariables(k), resolveVariables(v)));

            if (resolvedBody != null && !resolvedBody.isEmpty()) {
                request.body(resolvedBody);
            }

            applyAuth(request, effectiveAuthType);

            LOG.info("Sending {} request to {} with auth type {}", method, resolvedUrl, effectiveAuthType);
            Response response = request.request(method, resolvedUrl);
            apiContext.setLastResponse(response);
            testExecutionContext.addSharedData(LAST_REQUEST_METHOD, method);
            testExecutionContext.addSharedData(LAST_REQUEST_URL, resolvedUrl);
            return "Request sent. Status: %s. Response body: '%s'".formatted(response.getStatusCode(),
                    ofNullable(response.getBody()).map(ResponseBody::prettyPrint).orElse(""));
        } catch (Exception e) {
            throw rethrowAsToolException(e, "sending request to " + url);
        }
    }

    @Tool("Retrieves the last API response information including status code, body, and headers. Use this tool when the test step requires access to the previous API response data.")
    public String getLastApiResponse() {
        var responseOpt = apiContext.getLastResponse();
        if (responseOpt.isEmpty()) {
            return "No API response available. No request has been made yet.";
        }

        var response = responseOpt.get();
        var statusCode = String.valueOf(response.getStatusCode());
        var body = response.getBody() != null ? response.getBody().asString() : "";
        var headersBuilder = new StringBuilder();
        response.getHeaders().forEach(h -> headersBuilder.append(h.getName()).append(": ").append(h.getValue()).append("\n"));

        return """
                Last API Response:
                - Status Code: %s
                - Response Body: %s
                - Response Headers: %s"""
                .formatted(statusCode, body, headersBuilder.toString().trim());
    }

    @Tool("Uploads a file using multipart/form-data.")

    public String uploadFile(
            @P("The fully qualified absolute target URL") String url,
            @P("Absolute path to the file to upload") String filePath,
            @P("Multipart name for the file") String multipartName,
            @P(value = "Headers map", required = false) Map<String, String> headers,
            @P(value = "Authentication type. If not specified, the configured default will be used.", required = false) AuthType authType) {
        if (isBlank(url)) {
            throw new ToolExecutionException("URL cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(filePath)) {
            throw new ToolExecutionException("File path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        if (isBlank(multipartName)) {
            throw new ToolExecutionException("Multipart name cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }

        AuthType effectiveAuthType = authType != null ? authType : ApiTestAgentConfig.getDefaultAuthType();

        try {
            String resolvedUrl = resolveVariables(url);
            String resolvedFilePath = resolveVariables(filePath);
            File file = new File(resolvedFilePath);

            if (!file.exists()) {
                throw new ToolExecutionException("File not found at " + resolvedFilePath, TRANSIENT_TOOL_ERROR);
            }

            RequestSpecification request = given()
                    .filter(apiContext.getCookieFilter())
                    .multiPart(multipartName, file);

            if (apiContext.isRelaxedHttpsValidation()) {
                request.relaxedHTTPSValidation();
            }

            apiContext.getBaseUri().ifPresent(request::baseUri);

            if (headers != null) {
                headers.forEach((k, v) -> request.header(resolveVariables(k), resolveVariables(v)));
            }

            applyAuth(request, effectiveAuthType);

            LOG.info("Uploading file {} to {} with auth type {}", resolvedFilePath, resolvedUrl, effectiveAuthType);
            Response response = request.post(resolvedUrl);
            apiContext.setLastResponse(response);

            return "File uploaded. Status: " + response.getStatusCode();
        } catch (Exception e) {
            throw rethrowAsToolException(e, "uploading file to " + url);
        }
    }

    private void applyAuth(RequestSpecification request, AuthType authType) {
        switch (authType) {
            case BASIC -> addBasicAuth(request);
            case BEARER -> addBearerToken(request);
            case API_TOKEN -> addApiTokenToHeader(request);
            case NONE -> {
            }
            default -> throw new IllegalArgumentException("Unsupported authentication type: " + authType);
        }
    }

    private static void addApiTokenToHeader(RequestSpecification request) {
        String keyNameEnv = getApiKeyNameEnv();
        String keyValueEnv = getApiKeyValueEnv();
        String key = getEnvironmentVariable(keyNameEnv);
        String value = getEnvironmentVariable(keyValueEnv);
        if (isBlank(key) || isBlank(value)) {
            throw new ToolExecutionException("API Key name or value environment variables not set or empty", TRANSIENT_TOOL_ERROR);
        }
        request.header(key, value);
    }

    private static void addBearerToken(RequestSpecification request) {
        String tokenEnv = getBearerTokenEnv();
        String token = getEnvironmentVariable(tokenEnv);
        if (isBlank(token)) {
            throw new ToolExecutionException("Bearer token environment variable not set or empty", TRANSIENT_TOOL_ERROR);
        }
        request.auth().oauth2(token);
    }

    private static void addBasicAuth(RequestSpecification request) {
        String usernameEnv = getBasicAuthUsernameEnv();
        String passwordEnv = getBasicAuthPasswordEnv();
        String username = getEnvironmentVariable(usernameEnv);
        String password = getEnvironmentVariable(passwordEnv);
        if (isBlank(username) || isBlank(password)) {
            throw new ToolExecutionException("Username or password environment variables not set or empty for BASIC auth",
                    TRANSIENT_TOOL_ERROR);
        }
        request.auth().preemptive().basic(username, password);
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
            if (testExecutionContext != null) {
                value = testExecutionContext.getSharedData().get(varName);
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