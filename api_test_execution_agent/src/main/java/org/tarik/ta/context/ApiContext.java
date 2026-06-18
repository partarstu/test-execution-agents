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
package org.tarik.ta.context;

import io.restassured.filter.cookie.CookieFilter;
import io.restassured.response.Response;
import org.tarik.ta.ApiTestAgentConfig;
import org.tarik.ta.ApiAgentRequestScope;

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
@ApiAgentRequestScope
public class ApiContext {
    private final CookieFilter cookieFilter = new CookieFilter();
    private Response lastResponse;
    private String baseUri;
    private Integer proxyPort;
    private String proxyHost;
    private boolean relaxedHttpsValidation = false;

    /**
     * Creates a new ApiContext initialized from configuration properties.
     * <p>
     * This constructor reads the following from {@link ApiTestAgentConfig}:
     * <ul>
     * <li>Base URI</li>
     * <li>Proxy host and port</li>
     * <li>HTTPS validation settings</li>
     * </ul>
     */
    public ApiContext(ApiTestAgentConfig config) {
        config.getTargetBaseUri().ifPresent(this::setBaseUri);
        config.getProxyHost().ifPresent(this::setProxyHost);
        this.setProxyPort(config.getProxyPort());
        this.setRelaxedHttpsValidation(config.getRelaxedHttpsValidation());
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