/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.model;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;

/**
 * HTTP clients for OpenAI-compatible endpoints. A cleartext endpoint is spoken to in HTTP/1.1, because the JDK client's h2c upgrade
 * request makes uvicorn-based servers such as vLLM drop the request body and answer 400.
 */
public final class OpenAiCompatibleHttpClients {
    private static final String CLEARTEXT_SCHEME = "http";

    private OpenAiCompatibleHttpClients() {
    }

    /** HTTP/1.1 for an {@code http://} endpoint, and the JDK default of HTTP/2, negotiated through TLS, for any other. */
    public static @NotNull Version httpVersionFor(@NotNull String endpoint) {
        var scheme = URI.create(endpoint.trim()).getScheme();
        return CLEARTEXT_SCHEME.equalsIgnoreCase(scheme) ? HTTP_1_1 : HTTP_2;
    }

    public static @NotNull JdkHttpClientBuilder httpClientBuilderFor(@NotNull String endpoint) {
        return JdkHttpClient.builder().httpClientBuilder(HttpClient.newBuilder().version(httpVersionFor(endpoint)));
    }
}
