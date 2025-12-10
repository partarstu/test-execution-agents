package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.Tool;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.model.AuthType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

public class ApiRequestTools {
    private static final Logger LOG = LoggerFactory.getLogger(ApiRequestTools.class);
    private final ApiContext context;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\\\$\\\\{([^}]+)}");

    public ApiRequestTools(ApiContext context) {
        this.context = context;
    }

    @Tool("Sends an HTTP request. 'headers' is a map of header names to values. 'authValue' depends on authType (e.g., 'user:pass' for BASIC, 'token' for BEARER, 'key=value' for API_KEY).")
    public String sendRequest(String method, String url, Map<String, String> headers, String body, AuthType authType, String authValue, String authLocation) {
        try {
            String resolvedUrl = resolveVariables(url);
            String resolvedBody = resolveVariables(body);

            RequestSpecification request = given()
                    .filter(context.getCookieFilter());

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

            return "Request sent. Status: " + response.getStatusCode();
        } catch (Exception e) {
            LOG.error("Error sending request", e);
            return "Error sending request: " + e.getMessage();
        }
    }

    @Tool("Uploads a file using multipart/form-data.")
    public String uploadFile(String url, String filePath, String multipartName, Map<String, String> headers) {
        try {
            String resolvedUrl = resolveVariables(url);
            String resolvedFilePath = resolveVariables(filePath);
            File file = new File(resolvedFilePath);

            if (!file.exists()) {
                return "Error: File not found at " + resolvedFilePath;
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
            LOG.error("Error uploading file", e);
            return "Error uploading file: " + e.getMessage();
        }
    }

    private void applyAuth(RequestSpecification request, AuthType authType, String authValue, String authLocation) {
        if (authType == null || authType == AuthType.NONE || authValue == null) return;

        String resolvedValue = resolveVariables(authValue);

        switch (authType) {
            case BASIC -> {
                String[] parts = resolvedValue.split(":", 2);
                if (parts.length == 2) {
                    request.auth().basic(parts[0], parts[1]);
                } else {
                    LOG.warn("Invalid Basic Auth value format. Expected 'user:pass'.");
                }
            }
            case BEARER -> request.auth().oauth2(resolvedValue);
            case API_KEY -> {
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
        if (input == null) return null;
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = context.getVariable(varName);
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
