package org.tarik.ta.context;

import io.restassured.filter.cookie.CookieFilter;
import io.restassured.response.Response;
import org.tarik.ta.ApiTestAgentConfig;

import java.util.Optional;

/**
 * Context to hold the state of the API session (cookies, variables, config).
 * <p>
 * This class manages:
 * <ul>
 * <li>Cookie handling across requests</li>
 * <li>Variable storage for request/response data extraction</li>
 * <li>HTTP client configuration (base URI, proxy, SSL)</li>
 * <li>Last response for assertions and extractions</li>
 * </ul>
 * 
 * @see ApiTestAgentConfig for default configuration values
 */
public class ApiContext {
    private final CookieFilter cookieFilter = new CookieFilter();
    private Response lastResponse;
    private String baseUri;
    private Integer proxyPort;
    private String proxyHost;
    private boolean relaxedHttpsValidation = true;

    /**
     * Creates a new ApiContext with default settings.
     */
    public ApiContext() {
    }

    /**
     * Creates a new ApiContext initialized from configuration properties.
     * <p>
     * This factory method reads the following from {@link ApiTestAgentConfig}:
     * <ul>
     * <li>Base URI</li>
     * <li>Proxy host and port</li>
     * <li>HTTPS validation settings</li>
     * </ul>
     *
     * @return a new ApiContext configured from properties
     */
    public static ApiContext createFromConfig() {
        ApiContext context = new ApiContext();
        ApiTestAgentConfig.getTargetBaseUri().ifPresent(context::setBaseUri);
        ApiTestAgentConfig.getProxyHost().ifPresent(context::setProxyHost);
        context.setProxyPort(ApiTestAgentConfig.getProxyPort());
        context.setRelaxedHttpsValidation(ApiTestAgentConfig.getRelaxedHttpsValidation());
        return context;
    }

    public CookieFilter getCookieFilter() {
        return cookieFilter;
    }

    public Optional<Response> getLastResponse() {
        return Optional.ofNullable(lastResponse);
    }

    public void setLastResponse(Response lastResponse) {
        this.lastResponse = lastResponse;
    }

    public Optional<String> getBaseUri() {
        return Optional.ofNullable(baseUri);
    }

    public void setBaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    public Optional<String> getProxyHost() {
        return Optional.ofNullable(proxyHost);
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Optional<Integer> getProxyPort() {
        return Optional.ofNullable(proxyPort);
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isRelaxedHttpsValidation() {
        return relaxedHttpsValidation;
    }

    public void setRelaxedHttpsValidation(boolean relaxedHttpsValidation) {
        this.relaxedHttpsValidation = relaxedHttpsValidation;
    }

    /**
     * Clears the context state, removing all variables and the last response.
     * Note: Cookie filter state cannot be easily cleared.
     */
    public void clear() {
        lastResponse = null;
    }
}
