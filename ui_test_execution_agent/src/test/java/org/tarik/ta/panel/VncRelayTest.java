/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.panel;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VncRelayTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MESSAGE_TOO_BIG = 1009;

    @TempDir
    private Path directory;
    private XvncStub xvnc;
    private PanelTestFixture panel;

    @BeforeEach
    void setUp() throws Exception {
        xvnc = XvncStub.listen(directory.resolve("xvnc.sock"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (panel != null) {
            panel.close();
        }
        xvnc.close();
    }

    @Test
    void relay_shouldPassBytesBothWays() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).start();
        try (var browser = WebSocketTestClient.connect(panel.baseUrl(), login())) {
            var connection = xvnc.awaitConnection(TIMEOUT);

            XvncStub.write(connection, "RFB 003.008\n".getBytes(UTF_8));
            assertThat(new String(browser.awaitReceived(12, TIMEOUT), UTF_8)).isEqualTo("RFB 003.008\n");
            browser.send("RFB 003.008\n".getBytes(UTF_8));
            assertThat(new String(XvncStub.read(connection, 12), UTF_8)).isEqualTo("RFB 003.008\n");
        }
    }

    @Test
    void secondConnection_shouldCloseTheFirst() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).start();
        var cookie = login();
        try (var firstBrowser = WebSocketTestClient.connect(panel.baseUrl(), cookie)) {
            var firstConnection = xvnc.awaitConnection(TIMEOUT);
            try (var secondBrowser = WebSocketTestClient.connect(panel.baseUrl(), cookie)) {
                var secondConnection = xvnc.awaitConnection(TIMEOUT);

                firstBrowser.awaitClose(TIMEOUT);
                assertThat(XvncStub.isClosedByPeer(firstConnection)).isTrue();
                XvncStub.write(secondConnection, new byte[]{1, 2, 3});
                assertThat(secondBrowser.awaitReceived(3, TIMEOUT)).containsExactly(1, 2, 3);
            }
        }
    }

    @Test
    void browserThatStopsReading_shouldStallThePump_insteadOfBufferingEverythingXvncSends() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).start();
        try (var browser = WebSocketTestClient.connectWithoutReading(panel.baseUrl(), login())) {
            var connection = xvnc.awaitConnection(TIMEOUT);
            connection.configureBlocking(false);
            long offered = 512L * 1024 * 1024;

            long accepted = writeUntilStalled(connection, offered);

            assertThat(accepted).isLessThan(offered / 4);
            assertThat(browser.isClosed()).isFalse();
        }
    }

    @Test
    void inboundMessage_largerThan64KB_shouldCloseTheConnection() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).start();
        try (var browser = WebSocketTestClient.connect(panel.baseUrl(), login())) {
            xvnc.awaitConnection(TIMEOUT);

            try {
                browser.send(new byte[64 * 1024 + 1]);
            } catch (ExecutionException | CompletionException _) {
                // The server may close the connection before the client finished sending
            }

            assertThat(browser.awaitClose(TIMEOUT)).isIn(MESSAGE_TOO_BIG, -1);
        }
    }

    @Test
    void quietConnection_shouldOutliveTheIdleTimeout_thanksToTheKeepAlivePings() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).webSocketIdleTimeout(Duration.ofMillis(500))
                .keepAliveInterval(Duration.ofMillis(100)).start();
        try (var browser = WebSocketTestClient.connect(panel.baseUrl(), login())) {
            var connection = xvnc.awaitConnection(TIMEOUT);

            assertThatThrownBy(() -> browser.awaitClose(Duration.ofMillis(1_500))).isInstanceOf(TimeoutException.class);

            XvncStub.write(connection, new byte[]{7});
            assertThat(browser.awaitReceived(1, TIMEOUT)).containsExactly(7);
        }
    }

    @Test
    void quietConnection_shouldBeClosedByTheIdleTimeout_withoutKeepAlivePings() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).webSocketIdleTimeout(Duration.ofMillis(300)).start();
        try (var browser = WebSocketTestClient.connect(panel.baseUrl(), login())) {
            xvnc.awaitConnection(TIMEOUT);

            assertThat(browser.awaitClose(TIMEOUT)).isNotNull();
        }
    }

    @Test
    void xvncClosingTheSocket_shouldCloseTheBrowserConnection() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).start();
        try (var browser = WebSocketTestClient.connect(panel.baseUrl(), login())) {
            var connection = xvnc.awaitConnection(TIMEOUT);

            connection.close();

            assertThat(browser.awaitClose(TIMEOUT)).isNotNull();
        }
    }

    @Test
    void browserClosingTheConnection_shouldCloseTheXvncSocket() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).start();
        var browser = WebSocketTestClient.connect(panel.baseUrl(), login());
        var connection = xvnc.awaitConnection(TIMEOUT);

        browser.close();

        assertThat(XvncStub.isClosedByPeer(connection)).isTrue();
    }

    @Test
    void logout_shouldCloseTheRelayConnectionOfTheSession() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).start();
        var cookie = login();
        try (var browser = WebSocketTestClient.connect(panel.baseUrl(), cookie)) {
            var connection = xvnc.awaitConnection(TIMEOUT);

            given().baseUri(panel.baseUrl()).header("Sec-Fetch-Site", "same-origin").header("Cookie", cookie).post("/api/logout");

            browser.awaitClose(TIMEOUT);
            assertThat(XvncStub.isClosedByPeer(connection)).isTrue();
        }
    }

    @Test
    void expiredSession_shouldEndTheRelayConnection_atTheNextKeepAliveTick() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath())
                .sessionTimeouts(Duration.ofMinutes(30), Duration.ofMinutes(40)).start();
        try (var browser = WebSocketTestClient.connect(panel.baseUrl(), login())) {
            var connection = xvnc.awaitConnection(TIMEOUT);

            panel.advanceClock(Duration.ofMinutes(41));
            panel.keepAliveTick();

            browser.awaitClose(TIMEOUT);
            assertThat(XvncStub.isClosedByPeer(connection)).isTrue();
        }
    }

    @Test
    void upgrade_shouldBeRefused_withoutASession() {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).start();

        assertThatThrownBy(() -> WebSocketTestClient.connect(panel.baseUrl(), "panel_session=forged"))
                .cause()
                .isInstanceOfSatisfying(WebSocketHandshakeException.class,
                        refused -> assertThat(refused.getResponse().statusCode()).isEqualTo(401));
    }

    @Test
    void close_shouldCloseTheOpenConnection() throws Exception {
        panel = PanelTestFixture.builder().vncSocketPath(xvnc.socketPath()).start();
        try (var browser = WebSocketTestClient.connect(panel.baseUrl(), login())) {
            var connection = xvnc.awaitConnection(TIMEOUT);

            panel.relay().close();

            browser.awaitClose(TIMEOUT);
            assertThat(XvncStub.isClosedByPeer(connection)).isTrue();
        }
    }

    private String login() {
        var response = given().baseUri(panel.baseUrl()).header("Sec-Fetch-Site", "same-origin").contentType(ContentType.JSON)
                .body("{\"password\":\"%s\"}".formatted(PanelTestFixture.PASSWORD)).post("/api/login");
        assertThat(response.statusCode()).isEqualTo(200);
        return "panel_session=" + response.getCookie("panel_session");
    }

    // A non-blocking write accepts bytes only while the relay keeps reading, so the total shows how much it buffered
    private static long writeUntilStalled(SocketChannel connection, long limit) throws Exception {
        var chunk = ByteBuffer.allocate(64 * 1024);
        long accepted = 0;
        long lastProgress = System.nanoTime();
        while (accepted < limit && System.nanoTime() - lastProgress < TimeUnit.SECONDS.toNanos(2)) {
            chunk.clear();
            int written = connection.write(chunk);
            if (written > 0) {
                accepted += written;
                lastProgress = System.nanoTime();
            } else {
                Thread.onSpinWait();
            }
        }
        return accepted;
    }
}
