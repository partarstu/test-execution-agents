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
package org.tarik.ta.smoke;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.security.SecretRegistry;
import org.tarik.ta.dto.panel.ProcedureNode;
import org.tarik.ta.dto.panel.knowledge.GraphNode;
import org.tarik.ta.dto.panel.knowledge.KnowledgeSummary;
import org.tarik.ta.dto.panel.knowledge.KnowledgeSummary.Count;
import org.tarik.ta.knowledge_graph.health.HealthFinding;
import org.tarik.ta.knowledge_graph.health.HealthFinding.NodeRef;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.Screenshot;
import org.tarik.ta.knowledge_graph.repository.GraphHealthRepository;
import org.tarik.ta.knowledge_graph.repository.KnowledgeViewRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureUsageByTestCaseTrackingRepository;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.GraphHealthServices;
import org.tarik.ta.knowledge_graph.service.KnowledgeViewService;
import org.tarik.ta.panel.PanelTestFixture;
import org.tarik.ta.panel.SseTestClient;
import org.tarik.ta.panel.WebSocketTestClient;
import org.tarik.ta.panel.XvncStub;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hermetic end-to-end smoke test of the operator panel: the real panel server with its login, event stream, live view relay and
 * health and knowledge routes on a random local port, with a Unix-domain-socket stub standing in for Xvnc and stubbed repositories
 * for Neo4j.
 */
@Tag("smoke")
@DisplayName("Operator panel - hermetic end-to-end smoke")
class PanelSmokeTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final UUID PROCEDURE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @TempDir
    private Path directory;
    private XvncStub xvnc;
    private PanelTestFixture panel;
    private final KnowledgeViewRepository knowledgeRepository = mock(KnowledgeViewRepository.class);
    private final ProcedureRepository procedureRepository = mock(ProcedureRepository.class);
    private final UiElementRepository elementRepository = mock(UiElementRepository.class);

    @BeforeEach
    void setUp() throws Exception {
        SecretRegistry.global().reset(Pattern.compile("(?i).*password.*"));
        xvnc = XvncStub.listen(directory.resolve("xvnc.sock"));
        var repository = mock(GraphHealthRepository.class);
        when(repository.findUnresolvedFailureHints()).thenReturn(List.of(new HealthFinding(
                "Procedure[a1] 'Type s3cr3t-value': [TIMING] Field not ready (seen 3 times, no known resolution)",
                List.of(new NodeRef("Procedure", PROCEDURE_ID)))));
        var config = mock(UiTestAgentConfig.class);
        when(config.getHealthWarningThreshold()).thenReturn(1);
        when(config.getHealthCriticalThreshold()).thenReturn(10);
        when(config.getSatisfiesStaleDays()).thenReturn(30);
        when(config.getKnowledgeMaxDepth()).thenReturn(3);
        when(config.getHealthRecentWindowDays()).thenReturn(30);
        panel = PanelTestFixture.builder()
                .staticFilesDirectory("/panel")
                .vncSocketPath(xvnc.socketPath())
                .webSocketIdleTimeout(Duration.ofMillis(500))
                .keepAliveInterval(Duration.ofMillis(100))
                .healthService(GraphHealthServices.over(repository, config))
                .knowledgeViewService(new KnowledgeViewService(knowledgeRepository, procedureRepository,
                        mock(ProcedureUsageByTestCaseTrackingRepository.class), elementRepository, config, InstantSource.system()))
                .start();
    }

    @AfterEach
    void tearDown() throws Exception {
        panel.close();
        xvnc.close();
        SecretRegistry.global().reset(Pattern.compile("(?!)"));
    }

    @Test
    @DisplayName("The login opens a session whose probe answers the flags, and cross-site or origin-less writes are refused")
    void loginAndRequestChecks() {
        assertThat(request().get("/api/session").statusCode()).isEqualTo(401);

        var cookie = login();

        var probe = request().header("Cookie", cookie).get("/api/session");
        assertThat(probe.statusCode()).isEqualTo(200);
        assertThat(probe.jsonPath().getBoolean("liveView")).isTrue();
        assertThat(request().header("Cookie", cookie).header("Sec-Fetch-Site", "cross-site").post("/api/logout").statusCode())
                .isEqualTo(403);
        assertThat(request().header("Cookie", cookie).post("/api/logout").statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("The built frontend has no inline script, and every response carries the security headers")
    void frontendAndSecurityHeaders() {
        var index = request().get("/");

        assertThat(index.statusCode()).isEqualTo(200);
        assertThat(index.asString()).contains("<div id=\"root\">").doesNotContainPattern("<script(?![^>]*\\ssrc=)[^>]*>");
        for (var response : List.of(index, request().get("/api/session"), request().get("/missing"))) {
            assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'").contains("require-trusted-types-for");
            assertThat(response.getHeader("X-Frame-Options")).isEqualToIgnoringCase("DENY");
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("Permissions-Policy")).isEqualTo("geolocation=(), camera=(), microphone=()");
        }
    }

    @Test
    @DisplayName("The stream starts with the snapshot, carries the run as deltas, and redacts a registered secret in a log line")
    void eventStreamCarriesTheRun() throws Exception {
        SecretRegistry.global().register("password", "s3cr3t-value");
        var cookie = login();
        try (var stream = SseTestClient.open(panel.baseUrl(), cookie, "browser")) {
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");
            var observer = panel.runObserver();
            var emitter = observer.observe(StreamingEventEmitter.NOOP);

            observer.taskStarted(new TestCase("Login", List.of(new Precondition("Login page is open", List.of())), List.of(),
                    List.of(new TestStep("Type the password", List.of(), "Password typed"))));
            emitter.emitPreconditionStarted("Login page is open");
            emitter.emitPreconditionResult(new PreconditionResult("Login page is open", true, null, Instant.now(), Instant.now()));
            emitter.emitLog("typed s3cr3t-value into the password field");

            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("task-started");
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("item-started");
            assertThat(stream.nextEvent(TIMEOUT).data()).contains("\"status\":\"PASSED\"");
            var logLine = stream.nextEvent(TIMEOUT);
            assertThat(logLine.name()).isEqualTo("log-line");
            assertThat(logLine.data()).contains("{{secret:password}}").doesNotContain("s3cr3t-value");
        }
    }

    @Test
    @DisplayName("The stream carries the outline of the current step, redacted, and names the outline node of each started procedure")
    void eventStreamCarriesTheStepOutline() throws Exception {
        SecretRegistry.global().register("password", "s3cr3t-value");
        var cookie = login();
        try (var stream = SseTestClient.open(panel.baseUrl(), cookie, "browser")) {
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");
            var observer = panel.runObserver();
            var emitter = observer.observe(StreamingEventEmitter.NOOP);
            var root = UUID.randomUUID();
            var typePassword = UUID.randomUUID();

            observer.taskStarted(new TestCase("Login", List.of(), List.of(), List.of(new TestStep("Log in", List.of("s3cr3t-value"), ""))));
            emitter.emitStepStarted(new TestStep("Log in", List.of("s3cr3t-value"), ""));
            observer.itemOutlined(List.of("s3cr3t-value"), List.of(ProcedureNode.pending(root, "Log in", null, false, false, 0),
                    ProcedureNode.pending(typePassword, "Type s3cr3t-value", "Typed", true, false, 1)));
            observer.procedureStarted(root);
            observer.procedureStarted(typePassword);

            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("task-started");
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("item-started");
            var outlined = stream.nextEvent(TIMEOUT);
            assertThat(outlined.name()).isEqualTo("item-outlined");
            assertThat(outlined.data()).contains("\"item\":0", "Type {{secret:password}}").doesNotContain("s3cr3t-value");
            assertThat(stream.nextEvent(TIMEOUT).data()).contains("\"node\":0");
            assertThat(stream.nextEvent(TIMEOUT).data()).contains("\"node\":1");
        }
    }

    @Test
    @DisplayName("A logout ends the open event stream and the relay connection of the session")
    void logoutEndsStreamAndRelay() throws Exception {
        var cookie = login();
        try (var stream = SseTestClient.open(panel.baseUrl(), cookie, "browser");
             var browser = WebSocketTestClient.connect(panel.baseUrl(), cookie)) {
            var connection = xvnc.awaitConnection(TIMEOUT);
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");

            assertThat(request().header("Cookie", cookie).header("Sec-Fetch-Site", "same-origin").post("/api/logout").statusCode())
                    .isEqualTo(204);

            assertThat(stream.awaitEnd(TIMEOUT)).isTrue();
            browser.awaitClose(TIMEOUT);
            assertThat(XvncStub.isClosedByPeer(connection)).isTrue();
        }
    }

    @Test
    @DisplayName("A second login displaces the first session: its stream gets the notice, its probe then answers with it")
    void secondLoginDisplacesTheFirst() throws Exception {
        var firstCookie = login();
        try (var stream = SseTestClient.open(panel.baseUrl(), firstCookie, "first browser")) {
            assertThat(stream.nextEvent(TIMEOUT).name()).isEqualTo("snapshot");

            login();

            var displaced = stream.nextEvent(TIMEOUT);
            assertThat(displaced.name()).isEqualTo("displaced");
            assertThat(displaced.data()).contains("\"reason\":\"taken-over\"");
        }
        var probe = request().header("Cookie", firstCookie).get("/api/session");
        assertThat(probe.statusCode()).isEqualTo(401);
        assertThat(probe.jsonPath().getString("reason")).isEqualTo("taken-over");
    }

    @Test
    @DisplayName("The relay round-trips bytes, a new connection replaces the open one, and a quiet one outlives the idle timeout")
    void relay() throws Exception {
        var cookie = login();
        try (var firstBrowser = WebSocketTestClient.connect(panel.baseUrl(), cookie)) {
            var firstConnection = xvnc.awaitConnection(TIMEOUT);
            XvncStub.write(firstConnection, "RFB 003.008\n".getBytes(UTF_8));
            assertThat(new String(firstBrowser.awaitReceived(12, TIMEOUT), UTF_8)).isEqualTo("RFB 003.008\n");
            firstBrowser.send("RFB 003.008\n".getBytes(UTF_8));
            assertThat(new String(XvncStub.read(firstConnection, 12), UTF_8)).isEqualTo("RFB 003.008\n");

            try (var secondBrowser = WebSocketTestClient.connect(panel.baseUrl(), cookie)) {
                var secondConnection = xvnc.awaitConnection(TIMEOUT);
                firstBrowser.awaitClose(TIMEOUT);
                assertThat(XvncStub.isClosedByPeer(firstConnection)).isTrue();

                assertThatThrownBy(() -> secondBrowser.awaitClose(Duration.ofMillis(1_500))).isInstanceOf(TimeoutException.class);
                XvncStub.write(secondConnection, new byte[]{42});
                assertThat(secondBrowser.awaitReceived(1, TIMEOUT)).containsExactly(42);
            }
        }
    }

    @Test
    @DisplayName("The health route needs a session and answers the categories, severities and refs with a secret redacted")
    void healthRoute() {
        SecretRegistry.global().register("password", "s3cr3t-value");
        assertThat(request().get("/api/health/graph").statusCode()).isEqualTo(401);

        var response = request().header("Cookie", login()).get("/api/health/graph");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).doesNotContain("s3cr3t-value");
        var json = response.jsonPath();
        assertThat(json.getList("categories")).hasSize(16);
        assertThat(json.getString("categories.find { it.name == 'Unresolved Failure Hints' }.severity")).isEqualTo("WARNING");
        assertThat(json.getString("categories.find { it.name == 'Unresolved Failure Hints' }.findings[0].text"))
                .contains("{{secret:password}}");
        assertThat(json.getString("categories.find { it.name == 'Unresolved Failure Hints' }.findings[0].refs[0].id"))
                .isEqualTo(PROCEDURE_ID.toString());
        assertThat(json.getString("categories.find { it.name == 'Orphaned UI Elements' }.severity")).isEqualTo("OK");
    }

    @Test
    @DisplayName("The knowledge routes need a session and answer the summary, a neighbourhood, a screenshot and trends")
    void knowledgeRoutes() {
        SecretRegistry.global().register("password", "s3cr3t-value");
        var elementId = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        var png = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        var paths = List.of("/api/knowledge/summary", "/api/knowledge/procedures/%s/neighbourhood".formatted(PROCEDURE_ID),
                "/api/knowledge/elements/%s/screenshot".formatted(elementId), "/api/knowledge/trends");
        when(knowledgeRepository.summary()).thenReturn(new KnowledgeSummary(List.of(new Count("Procedure", 2)), List.of(), 11, 1, 1, 0));
        var node = new GraphNode(PROCEDURE_ID.toString(), GraphNode.Kind.ATOMIC, "Type s3cr3t-value", Set.of(GraphNode.Flag.NO_EFFECTS), 1);
        when(knowledgeRepository.neighbourhood(PROCEDURE_ID, KnowledgeViewService.MAX_MAP_NODES + 1)).thenReturn(List.of(node));
        when(knowledgeRepository.edgesBetween(eq(List.of(node)), anyInt())).thenReturn(List.of());
        when(elementRepository.findById(elementId)).thenReturn(Optional.of(new UiElement(elementId, "Save", "", "", "",
                new Screenshot("png", "image/png", Base64.getEncoder().encodeToString(png)), false)));
        when(knowledgeRepository.trendRows(any(), any())).thenReturn(List.of());

        assertThat(paths).allSatisfy(path -> assertThat(request().get(path).statusCode()).as(path).isEqualTo(401));
        var cookie = login();
        var authorized = request().header("Cookie", cookie);

        assertThat(authorized.get(paths.get(0)).jsonPath().getInt("schemaVersion")).isEqualTo(11);
        var neighbourhood = request().header("Cookie", cookie).get(paths.get(1));
        assertThat(neighbourhood.asString()).doesNotContain("s3cr3t-value");
        assertThat(neighbourhood.jsonPath().getString("nodes[0].label")).isEqualTo("Type {{secret:password}}");
        var screenshot = request().header("Cookie", cookie).get(paths.get(2));
        assertThat(screenshot.contentType()).isEqualTo("image/png");
        assertThat(screenshot.asByteArray()).isEqualTo(png);
        assertThat(request().header("Cookie", cookie).get(paths.get(3)).jsonPath().getList("weeks")).hasSize(12);
    }

    private RequestSpecification request() {
        return given().baseUri(panel.baseUrl());
    }

    private String login() {
        Response response = request().header("Sec-Fetch-Site", "same-origin").contentType(ContentType.JSON)
                .body("{\"password\":\"%s\"}".formatted(PanelTestFixture.PASSWORD)).post("/api/login");
        assertThat(response.statusCode()).isEqualTo(200);
        return "panel_session=" + response.getCookie("panel_session");
    }
}
