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

import org.tarik.ta.core.utils.CommonUtils;

import org.tarik.ta.ApiTestAgentConfig;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;

public class ApiRequestTools extends org.tarik.ta.core.tools.AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ApiRequestTools.class);
    private final ApiContext apiContext;
    private final TestExecutionContext testExecutionContext;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    public ApiRequestTools(ApiContext context, TestExecutionContext executionContext) {
        this.apiContext = context;
        this.testExecutionContext = executionContext;
    }

    @Tool("Sends an HTTP request. 'headers' is a map of header names to values. 'authValue' is not needed as it is " +
            "configured via properties.")
    public String sendRequest(
            @P("HTTP method (GET, POST, PUT, DELETE, etc.)") String method,
            @P("The target URL") String url,
            @P("Optional headers map") Map<String, String> headers,
            @P("Request body (optional)") String body,
            @P("Authentication type (NONE, BASIC, BEARER, API_KEY)") AuthType authType,
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

            applyAuth(request, authType, authLocation);

            LOG.info("Sending {} request to {}", method, resolvedUrl);
            Response response = request.request(method, resolvedUrl);
            apiContext.setLastResponse(response);
            testExecutionContext.addSharedData("_last_request_method", method);
            testExecutionContext.addSharedData("_last_request_path", resolvedUrl);
            return "Request sent. Status: %s. Response body: '%s'".formatted(response.getStatusCode(),
                    ofNullable(response.getBody()).map(ResponseBody::prettyPrint).orElse(""));
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
                    .filter(apiContext.getCookieFilter())
                    .multiPart(multipartName, file);

            if (apiContext.isRelaxedHttpsValidation()) {
                request.relaxedHTTPSValidation();
            }

            apiContext.getBaseUri().ifPresent(request::baseUri);

            if (headers != null) {
                headers.forEach((k, v) -> request.header(resolveVariables(k), resolveVariables(v)));
            }

            LOG.info("Uploading file {} to {}", resolvedFilePath, resolvedUrl);
            Response response = request.post(resolvedUrl);
            apiContext.setLastResponse(response);

            return "File uploaded. Status: " + response.getStatusCode();
        } catch (Exception e) {
            throw rethrowAsToolException(e, "uploading file to " + url);
        }
    }

    private void applyAuth(RequestSpecification request, AuthType authType,
            String authLocation) {
        if (authType == AuthType.NONE) {
            return;
        }

        switch (authType) {
            case BASIC -> {
                String usernameEnv = org.tarik.ta.ApiTestAgentConfig.getBasicAuthUsernameEnv();
                String passwordEnv = org.tarik.ta.ApiTestAgentConfig.getBasicAuthPasswordEnv();
                String username = CommonUtils.getEnvironmentVariable(usernameEnv);
                String password = CommonUtils.getEnvironmentVariable(passwordEnv);

                if (isBlank(username) || isBlank(password)) {
                    throw new ToolExecutionException(
                            "Username or password environment variables not set or empty for BASIC auth",
                            TRANSIENT_TOOL_ERROR);
                }
                request.auth().preemptive().basic(username, password);
            }
            case BEARER -> {
                String tokenEnv = org.tarik.ta.ApiTestAgentConfig.getBearerTokenEnv();
                String token = CommonUtils.getEnvironmentVariable(tokenEnv);
                if (isBlank(token)) {
                    throw new ToolExecutionException("Bearer token environment variable not set or empty",
                            TRANSIENT_TOOL_ERROR);
                }
                request.auth().oauth2(token);
            }
            case API_KEY -> {
                if (isBlank(authLocation)) {
                    throw new ToolExecutionException("Auth location cannot be null or empty for API_KEY auth type",
                            TRANSIENT_TOOL_ERROR);
                }
                String keyNameEnv = org.tarik.ta.ApiTestAgentConfig.getApiKeyNameEnv();
                String keyValueEnv = org.tarik.ta.ApiTestAgentConfig.getApiKeyValueEnv();
                String key = CommonUtils.getEnvironmentVariable(keyNameEnv);
                String value = CommonUtils.getEnvironmentVariable(keyValueEnv);

                if (isBlank(key) || isBlank(value)) {
                    throw new ToolExecutionException("API Key name or value environment variables not set or empty",
                            TRANSIENT_TOOL_ERROR);
                }

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
