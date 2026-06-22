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
package org.tarik.ta.core;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tarik.ta.core.a2a.AgentExecutionResource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AbstractServerTest {

    private static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";
    private static final String TOKEN = "s3cr3t-token";

    private Javalin runningServer;

    @AfterEach
    void tearDown() {
        if (runningServer != null) {
            runningServer.stop();
            runningServer = null;
        }
    }

    @Test
    void start_shouldInitializeAndStartServer() {
        var resource = mock(AgentExecutionResource.class);
        var config = mock(AgentConfig.class);
        when(config.getStartPort()).thenReturn(8080);
        when(config.getHost()).thenReturn("localhost");

        AbstractServer server = new AbstractServer(resource, config);

        Javalin mockJavalin = mock(Javalin.class);
        when(mockJavalin.start(anyString(), anyInt())).thenReturn(mockJavalin);

        try (MockedStatic<Javalin> mockedJavalin = mockStatic(Javalin.class)) {
            mockedJavalin.when(() -> Javalin.create(any())).thenReturn(mockJavalin);

            server.start();

            mockedJavalin.verify(() -> Javalin.create(any()));
            verify(mockJavalin).start(anyString(), anyInt());
        }
    }

    @Test
    void requireValidToken_shouldThrowUnauthorized_whenHeaderMissing() {
        Context context = mock(Context.class);
        when(context.header("Authorization")).thenReturn(null);

        assertThatThrownBy(() -> AbstractServer.requireValidToken(context, TOKEN))
                .isInstanceOf(UnauthorizedResponse.class);
    }

    @Test
    void requireValidToken_shouldThrowUnauthorized_whenTokenWrong() {
        Context context = mock(Context.class);
        when(context.header("Authorization")).thenReturn("Bearer wrong-token");

        assertThatThrownBy(() -> AbstractServer.requireValidToken(context, TOKEN))
                .isInstanceOf(UnauthorizedResponse.class);
    }

    @Test
    void requireValidToken_shouldPass_whenTokenCorrect() {
        Context context = mock(Context.class);
        when(context.header("Authorization")).thenReturn("Bearer " + TOKEN);

        assertThatCode(() -> AbstractServer.requireValidToken(context, TOKEN)).doesNotThrowAnyException();
    }

    @Test
    void mainEndpoint_shouldReturn401_whenTokenConfiguredButRequestUnauthenticated() throws Exception {
        int port = startServerWithToken(Optional.of(TOKEN));

        HttpResponse<String> response = send("POST", mainUrl(port), null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void mainEndpoint_shouldReturn401_whenTokenWrong() throws Exception {
        int port = startServerWithToken(Optional.of(TOKEN));

        HttpResponse<String> response = send("POST", mainUrl(port), "Bearer nope");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void mainEndpoint_shouldReturn200_whenTokenCorrect() throws Exception {
        int port = startServerWithToken(Optional.of(TOKEN));

        HttpResponse<String> response = send("POST", mainUrl(port), "Bearer " + TOKEN);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void agentCard_shouldBeReachable_withoutToken_whenTokenConfigured() throws Exception {
        int port = startServerWithToken(Optional.of(TOKEN));

        HttpResponse<String> response = send("GET", "http://localhost:" + port + AGENT_CARD_PATH, null);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void mainEndpoint_shouldBeReachable_whenNoTokenConfigured() throws Exception {
        int port = startServerWithToken(Optional.empty());

        HttpResponse<String> response = send("POST", mainUrl(port), null);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private int startServerWithToken(Optional<String> token) throws IOException {
        int port = findFreePort();
        var resource = mock(AgentExecutionResource.class);
        var config = mock(AgentConfig.class);
        when(config.getStartPort()).thenReturn(port);
        when(config.getHost()).thenReturn("localhost");
        when(config.getAuthToken()).thenReturn(token);

        runningServer = new AbstractServer(resource, config).start();
        return port;
    }

    private static String mainUrl(int port) {
        return "http://localhost:" + port + "/";
    }

    private static HttpResponse<String> send(String method, String url, String authHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.GET();
        }
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
