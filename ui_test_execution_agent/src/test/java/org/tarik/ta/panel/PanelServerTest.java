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
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tarik.ta.ExecutionMode.SUPERVISED;
import static org.tarik.ta.ExecutionMode.UNATTENDED;

class PanelServerTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String SECOND_BROWSER = "Mozilla/5.0 (Second browser) " + "x".repeat(250);

    private PanelTestFixture panel;

    @AfterEach
    void tearDown() {
        if (panel != null) {
            panel.close();
        }
    }

    @Test
    void sessionProbe_shouldAnswer401WithoutBody_beforeLogin_and200WithTheFlags_afterLogin() {
        panel = PanelTestFixture.builder().start();
        var probeBefore = request().get("/api/session");
        assertThat(probeBefore.statusCode()).isEqualTo(401);
        assertThat(probeBefore.asString()).isEmpty();

        var login = login();
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.jsonPath().getString("executionMode")).isEqualTo("SUPERVISED");

        var probeAfter = request().header("Cookie", sessionCookie(login)).get("/api/session");
        assertThat(probeAfter.statusCode()).isEqualTo(200);
        assertThat(probeAfter.jsonPath().getBoolean("liveView")).isFalse();
        assertThat(probeAfter.jsonPath().getBoolean("liveControl")).isFalse();
    }

    @Test
    void login_shouldAnswerAGenericError_whenThePasswordIsWrong() {
        panel = PanelTestFixture.builder().start();

        var response = sameOrigin().contentType(ContentType.JSON).body("{\"password\":\"wrong-password-of-some-length\"}")
                .post("/api/login");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("error")).isEqualTo("Invalid password");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void login_shouldAnswer400_whenTheBodyHasNoPassword() {
        panel = PanelTestFixture.builder().start();

        assertThat(sameOrigin().contentType(ContentType.JSON).body("{}").post("/api/login").statusCode()).isEqualTo(400);
        assertThat(sameOrigin().contentType(ContentType.JSON).body("not json").post("/api/login").statusCode()).isEqualTo(400);
    }

    @Test
    void login_shouldAnswer429_onTheSixthAttemptWithinAMinute() {
        panel = PanelTestFixture.builder().start();
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(sameOrigin().contentType(ContentType.JSON).body("{\"password\":\"wrong\"}").post("/api/login").statusCode())
                    .isEqualTo(401);
        }

        assertThat(login().statusCode()).isEqualTo(429);
    }

    @Test
    void mutatingRequest_shouldBeRejectedWith403_whenItIsCrossSite() {
        panel = PanelTestFixture.builder().start();
        var cookie = sessionCookie(login());

        var response = request().header("Cookie", cookie).header("Sec-Fetch-Site", "cross-site").post("/api/logout");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(request().header("Cookie", cookie).get("/api/session").statusCode()).isEqualTo(200);
    }

    @Test
    void mutatingRequest_shouldBeRejectedWith403_whenItHasNeitherFetchMetadataNorOrigin() {
        panel = PanelTestFixture.builder().start();
        var cookie = sessionCookie(login());

        assertThat(request().header("Cookie", cookie).post("/api/logout").statusCode()).isEqualTo(403);
    }

    @Test
    void mutatingRequest_shouldBeAccepted_whenItsOriginIsThePanelItself() {
        panel = PanelTestFixture.builder().start();
        var cookie = sessionCookie(login());

        assertThat(request().header("Cookie", cookie).header("Origin", panel.baseUrl()).post("/api/logout").statusCode()).isEqualTo(204);
    }

    @Test
    void login_shouldAnswer415_whenTheBodyIsNotJson() {
        panel = PanelTestFixture.builder().start();

        var response = sameOrigin().contentType(ContentType.TEXT).body("password=" + PanelTestFixture.PASSWORD).post("/api/login");

        assertThat(response.statusCode()).isEqualTo(415);
    }

    @Test
    void session_shouldExpire_afterTheIdleTimeout() {
        panel = PanelTestFixture.builder().sessionTimeouts(Duration.ofMinutes(30), Duration.ofHours(8)).start();
        var cookie = sessionCookie(login());

        panel.advanceClock(Duration.ofMinutes(20));
        assertThat(probe(cookie)).isEqualTo(200);
        panel.advanceClock(Duration.ofMinutes(31));

        assertThat(probe(cookie)).isEqualTo(401);
    }

    @Test
    void session_shouldExpire_atTheAbsoluteTimeout_althoughItIsActive() {
        panel = PanelTestFixture.builder().sessionTimeouts(Duration.ofMinutes(30), Duration.ofMinutes(60)).start();
        var cookie = sessionCookie(login());

        panel.advanceClock(Duration.ofMinutes(25));
        assertThat(probe(cookie)).isEqualTo(200);
        panel.advanceClock(Duration.ofMinutes(25));
        assertThat(probe(cookie)).isEqualTo(200);
        panel.advanceClock(Duration.ofMinutes(11));

        assertThat(probe(cookie)).isEqualTo(401);
    }

    @Test
    void openEventStream_shouldKeepTheSessionAlive_pastTheIdleTimeout_untilTheAbsoluteTimeoutEndsIt() throws Exception {
        panel = PanelTestFixture.builder().sessionTimeouts(Duration.ofMinutes(30), Duration.ofMinutes(60)).start();
        var cookie = sessionCookie(login());
        try (var stream = SseTestClient.open(panel.baseUrl(), cookie, "browser")) {
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");

            panel.advanceClock(Duration.ofMinutes(20));
            panel.keepAliveTick();
            panel.advanceClock(Duration.ofMinutes(20));
            panel.keepAliveTick();
            panel.advanceClock(Duration.ofMinutes(20));
            panel.keepAliveTick();
            assertThat(stream.nextComment(TIMEOUT).text()).isEqualTo("keep-alive");

            panel.advanceClock(Duration.ofMinutes(5));
            panel.keepAliveTick();

            assertThat(stream.awaitEnd(TIMEOUT)).isTrue();
            assertThat(probe(cookie)).isEqualTo(401);
        }
    }

    @Test
    void quietEventStream_shouldReceiveKeepAliveComments_atTheConfiguredInterval() throws Exception {
        panel = PanelTestFixture.builder().keepAliveInterval(Duration.ofMillis(100)).start();
        var cookie = sessionCookie(login());
        try (var stream = SseTestClient.open(panel.baseUrl(), cookie, "browser")) {
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");

            assertThat(stream.nextComment(TIMEOUT).text()).isEqualTo("keep-alive");
            assertThat(stream.nextComment(TIMEOUT).text()).isEqualTo("keep-alive");
        }
        assertThat(PanelServer.KEEP_ALIVE_INTERVAL).isLessThan(Duration.ofSeconds(30));
    }

    @Test
    void secondLogin_shouldDisplaceTheFirstSession_withTheSourceAndTheCappedBrowserOfTheSecondLogin() throws Exception {
        panel = PanelTestFixture.builder().start();
        var firstCookie = sessionCookie(login());
        try (var firstStream = SseTestClient.open(panel.baseUrl(), firstCookie, "first browser")) {
            assertThat(firstStream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");

            var secondLogin = sameOrigin().header("User-Agent", SECOND_BROWSER).contentType(ContentType.JSON)
                    .body("{\"password\":\"%s\"}".formatted(PanelTestFixture.PASSWORD)).post("/api/login");
            assertThat(secondLogin.statusCode()).isEqualTo(200);

            var displaced = firstStream.nextEvent(TIMEOUT);
            assertThat(displaced.name()).isEqualTo("displaced");
            assertThat(displaced.data()).contains("\"reason\":\"taken-over\"").contains("\"sourceIp\":\"127.0.0.1\"")
                    .contains("\"browser\":\"%s\"".formatted(SECOND_BROWSER.substring(0, 200)));
            assertThat(firstStream.awaitEnd(TIMEOUT)).isTrue();
        }
        var replacedProbe = request().header("Cookie", firstCookie).get("/api/session");
        assertThat(replacedProbe.statusCode()).isEqualTo(401);
        assertThat(replacedProbe.jsonPath().getString("reason")).isEqualTo("taken-over");
        assertThat(replacedProbe.jsonPath().getString("browser")).hasSize(200);
        var unknownProbe = request().header("Cookie", "panel_session=unknown").get("/api/session");
        assertThat(unknownProbe.statusCode()).isEqualTo(401);
        assertThat(unknownProbe.asString()).isEmpty();
    }

    @Test
    void secondEventStreamOfTheSameSession_shouldDisplaceTheFirstStream_asAnotherTab() throws Exception {
        panel = PanelTestFixture.builder().start();
        var cookie = sessionCookie(login());
        try (var firstTab = SseTestClient.open(panel.baseUrl(), cookie, "browser");
             var secondTab = SseTestClient.open(panel.baseUrl(), cookie, "browser")) {
            assertThat(secondTab.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");

            assertThat(firstTab.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");
            var displaced = firstTab.nextEvent(TIMEOUT);
            assertThat(displaced.name()).isEqualTo("displaced");
            assertThat(displaced.data()).contains("\"reason\":\"other-tab\"");
            assertThat(firstTab.awaitEnd(TIMEOUT)).isTrue();
            assertThat(probe(cookie)).isEqualTo(200);
        }
    }

    @Test
    void logout_shouldEndTheSessionAndItsEventStream_expireTheCookieAndClearSiteData() throws Exception {
        panel = PanelTestFixture.builder().start();
        var cookie = sessionCookie(login());
        try (var stream = SseTestClient.open(panel.baseUrl(), cookie, "browser")) {
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");

            var logout = sameOrigin().header("Cookie", cookie).post("/api/logout");

            assertThat(logout.statusCode()).isEqualTo(204);
            assertThat(logout.getHeader("Clear-Site-Data")).isEqualTo("\"cookies\", \"storage\"");
            assertThat(logout.getHeader("Set-Cookie")).startsWith("panel_session=;").contains("Expires=Thu, 01 Jan 1970");
            assertThat(stream.awaitEnd(TIMEOUT)).isTrue();
        }
        assertThat(probe(cookie)).isEqualTo(401);
    }

    @Test
    void logout_shouldEndTheSession_althoughATouchReplacedItsRecordDuringTheLookup() throws Exception {
        panel = PanelTestFixture.builder().start();
        var cookie = sessionCookie(login());
        var sessionId = cookie.substring("panel_session=".length());
        try (var stream = SseTestClient.open(panel.baseUrl(), cookie, "browser")) {
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");
            panel.onClockRead(() -> panel.touch(sessionId));

            assertThat(sameOrigin().header("Cookie", cookie).post("/api/logout").statusCode()).isEqualTo(204);

            assertThat(stream.awaitEnd(TIMEOUT)).isTrue();
        }
        panel.onClockRead(() -> {
        });
        assertThat(probe(cookie)).isEqualTo(401);
    }

    @Test
    void eventStream_shouldEnd_whenItsSessionExpires() throws Exception {
        panel = PanelTestFixture.builder().sessionTimeouts(Duration.ofMinutes(30), Duration.ofMinutes(40)).start();
        var cookie = sessionCookie(login());
        try (var stream = SseTestClient.open(panel.baseUrl(), cookie, "browser")) {
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");

            panel.advanceClock(Duration.ofMinutes(41));
            panel.keepAliveTick();

            assertThat(stream.awaitEnd(TIMEOUT)).isTrue();
        }
    }

    @Test
    void eventStream_shouldBeRefused_withoutASession() throws Exception {
        panel = PanelTestFixture.builder().start();

        try (var stream = SseTestClient.open(panel.baseUrl(), "panel_session=forged", "browser")) {
            assertThat(stream.status()).isEqualTo(401);
        }
    }

    @Test
    void plainHttpCookie_shouldBeHttpOnlyAndStrict_withoutSecureOrHostPrefix() {
        panel = PanelTestFixture.builder().start();

        var setCookie = login().getHeader("Set-Cookie");

        assertThat(setCookie).startsWith("panel_session=").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/")
                .doesNotContain("Secure").doesNotContain("__Host-");
    }

    @Test
    void tlsCookie_shouldUseTheHostPrefixAndBeSecure_andResponsesShouldCarryStrictTransportSecurity(@TempDir Path directory)
            throws Exception {
        var pemPair = SelfSignedPemPair.create(directory);
        panel = PanelTestFixture.builder().tls(pemPair.certificate(), pemPair.privateKey()).start();

        var login = sameOrigin().relaxedHTTPSValidation().contentType(ContentType.JSON)
                .body("{\"password\":\"%s\"}".formatted(PanelTestFixture.PASSWORD)).post("/api/login");

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.getHeader("Set-Cookie")).startsWith("__Host-panel_session=").contains("Secure").contains("HttpOnly")
                .contains("SameSite=Strict").contains("Path=/");
        assertThat(login.getHeader("Strict-Transport-Security")).startsWith("max-age=");
    }

    @Test
    void everyResponse_shouldCarryTheSecurityHeaders() {
        panel = PanelTestFixture.builder().start();

        for (var path : List.of("/", "/api/session", "/assets/app-1a2b3c4d.js", "/missing")) {
            var response = request().get(path);

            assertThat(response.getHeader("Content-Security-Policy")).as(path).isEqualTo("default-src 'self'; img-src 'self' blob: data:; "
                    + "object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; require-trusted-types-for 'script'");
            assertThat(response.getHeader("X-Frame-Options")).as(path).isEqualToIgnoringCase("DENY");
            assertThat(response.getHeader("Cross-Origin-Opener-Policy")).as(path).isEqualTo("same-origin");
            assertThat(response.getHeader("Cross-Origin-Resource-Policy")).as(path).isEqualTo("same-origin");
            assertThat(response.getHeader("X-Content-Type-Options")).as(path).isEqualTo("nosniff");
            assertThat(response.getHeader("Referrer-Policy")).as(path).isEqualTo("strict-origin-when-cross-origin");
            assertThat(response.getHeader("Permissions-Policy")).as(path).isEqualTo("geolocation=(), camera=(), microphone=()");
            assertThat(response.getHeader("Strict-Transport-Security")).as(path).isNull();
        }
    }

    @Test
    void apiResponses_shouldNotBeStored() {
        panel = PanelTestFixture.builder().start();

        assertThat(request().get("/api/session").getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void indexHtml_shouldBeRevalidated_andHashedAssets_shouldBeImmutableAndPrecompressed() {
        panel = PanelTestFixture.builder().start();

        var index = request().get("/");
        var asset = request().header("Accept-Encoding", "gzip").get("/assets/app-1a2b3c4d.js");

        assertThat(index.statusCode()).isEqualTo(200);
        assertThat(index.asString()).contains("<div id=\"root\">");
        assertThat(index.getHeader("Cache-Control")).isEqualTo("no-cache");
        assertThat(asset.statusCode()).isEqualTo(200);
        assertThat(asset.getHeader("Cache-Control")).isEqualTo("public, max-age=31536000, immutable");
        assertThat(asset.getHeader("Content-Encoding")).isEqualTo("gzip");
        assertThat(asset.asString()).contains("operator panel test asset");
    }

    @Test
    void close_shouldStopTheKeepAliveScheduler() {
        panel = PanelTestFixture.builder().keepAliveInterval(Duration.ofMillis(50)).start();

        panel.server().close();

        assertThat(panel.server().isKeepAliveStopped()).isTrue();
    }

    @Test
    void start_shouldBeRefused_inSupervisedMode_whenTheFrontendIsMissingFromTheClasspath() {
        var builder = PanelTestFixture.builder().staticFilesDirectory("/missing-panel");

        assertThatThrownBy(builder::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("supervised mode")
                .hasMessageContaining("/missing-panel/index.html");
    }

    @Test
    void start_shouldServeOnlyTheApi_inUnattendedMode_whenTheFrontendIsMissingFromTheClasspath() {
        panel = PanelTestFixture.builder().executionMode(UNATTENDED).staticFilesDirectory("/missing-panel").start();

        assertThat(request().get("/").statusCode()).isEqualTo(404);
        assertThat(login().statusCode()).isEqualTo(200);
    }

    @Test
    void isPasswordAcceptable_shouldRefuseAnEmptyPassword_onANonLoopbackHost() {
        assertThatThrownBy(() -> PanelServer.isPasswordAcceptable("0.0.0.0", Optional.empty(), UNATTENDED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PANEL_PASSWORD");
    }

    @Test
    void isPasswordAcceptable_shouldRefuseAnEmptyPassword_inSupervisedModeEvenOnALoopbackHost() {
        assertThatThrownBy(() -> PanelServer.isPasswordAcceptable("127.0.0.1", Optional.empty(), SUPERVISED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("supervised mode")
                .hasMessageContaining("PANEL_PASSWORD");
    }

    @Test
    void isPasswordAcceptable_shouldSkipThePanel_forAnEmptyPasswordOnALoopbackHost() {
        assertThat(PanelServer.isPasswordAcceptable("127.0.0.1", Optional.empty(), UNATTENDED)).isFalse();
        assertThat(PanelServer.isPasswordAcceptable("localhost", Optional.empty(), UNATTENDED)).isFalse();
    }

    @Test
    void isPasswordAcceptable_shouldRefuseAPasswordShorterThan16Characters_onAnyHost() {
        assertThatThrownBy(() -> PanelServer.isPasswordAcceptable("127.0.0.1", Optional.of("short-password1"), UNATTENDED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 16 characters");
        assertThat(PanelServer.isPasswordAcceptable("0.0.0.0", Optional.of("a-password-of-16"), UNATTENDED)).isTrue();
    }

    private RequestSpecification request() {
        return given().baseUri(panel.baseUrl()).redirects().follow(false);
    }

    private RequestSpecification sameOrigin() {
        return request().header("Sec-Fetch-Site", "same-origin");
    }

    private Response login() {
        return sameOrigin().contentType(ContentType.JSON).body("{\"password\":\"%s\"}".formatted(PanelTestFixture.PASSWORD))
                .post("/api/login");
    }

    private int probe(String cookie) {
        return request().header("Cookie", cookie).get("/api/session").statusCode();
    }

    private static String sessionCookie(Response login) {
        assertThat(login.statusCode()).isEqualTo(200);
        return "panel_session=" + login.getCookie("panel_session");
    }
}
