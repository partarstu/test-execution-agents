package org.tarik.ta.context;

import io.restassured.filter.cookie.CookieFilter;
import io.restassured.response.Response;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context to hold the state of the API session (cookies, variables, config).
 */
public class ApiContext {
    private final CookieFilter cookieFilter = new CookieFilter();
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private Response lastResponse;
    private String baseUri;
    private String proxyHost;
    private Integer proxyPort;
    private boolean relaxedHttpsValidation = true;

    public CookieFilter getCookieFilter() {
        return cookieFilter;
    }

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public Map<String, Object> getVariables() {
        return variables;
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
    
    public void clear() {
        variables.clear();
        lastResponse = null;
        // cookieFilter cannot be easily cleared without recreating, but we can't replace the reference if others hold it.
        // Actually CookieFilter is stateful.
    }
}
