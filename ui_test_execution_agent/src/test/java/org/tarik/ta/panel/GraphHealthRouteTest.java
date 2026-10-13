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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.security.SecretRegistry;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.health.GraphHealthReport;
import org.tarik.ta.knowledge_graph.health.HealthCheckCategory;
import org.tarik.ta.knowledge_graph.health.HealthCheckCategory.Severity;
import org.tarik.ta.knowledge_graph.health.HealthFinding;
import org.tarik.ta.knowledge_graph.health.HealthFinding.NodeRef;
import org.tarik.ta.knowledge_graph.service.GraphHealthService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphHealthRouteTest {
    private static final UUID PROCEDURE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_PROCEDURE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final GraphHealthReport REPORT = new GraphHealthReport(List.of(
            new HealthCheckCategory("Duplicate Atomic Procedures", "Atomic procedures on the same UI element",
                    List.of(new HealthFinding("Procedure[a1] 'Click Save' ~ Procedure[a2] 'Press Save'",
                            List.of(new NodeRef("Procedure", PROCEDURE_ID), new NodeRef("Procedure", OTHER_PROCEDURE_ID)))),
                    Severity.OK)),
            Instant.parse("2026-10-02T10:15:30Z"));

    private final GraphHealthService healthService = mock(GraphHealthService.class);
    private PanelTestFixture panel;

    @BeforeEach
    void setUp() {
        SecretRegistry.global().reset(Pattern.compile("(?i).*password.*"));
        panel = PanelTestFixture.builder().healthService(healthService).start();
    }

    @AfterEach
    void tearDown() {
        panel.close();
        SecretRegistry.global().reset(Pattern.compile("(?!)"));
    }

    @Test
    void healthRoute_shouldAnswer401_withoutASession() {
        assertThat(given().baseUri(panel.baseUrl()).get("/api/health/graph").statusCode()).isEqualTo(401);
    }

    @Test
    void healthRoute_shouldAnswerTheReport_withCategoriesFindingsRefsAndAnIsoTimestamp() {
        when(healthService.runFullHealthCheck()).thenReturn(REPORT);

        var response = getHealth(login());

        assertThat(response.statusCode()).isEqualTo(200);
        var json = response.jsonPath();
        assertThat(json.getString("generatedAt")).isEqualTo("2026-10-02T10:15:30Z");
        assertThat(json.getString("categories[0].name")).isEqualTo("Duplicate Atomic Procedures");
        assertThat(json.getString("categories[0].description")).isEqualTo("Atomic procedures on the same UI element");
        assertThat(json.getString("categories[0].severity")).isEqualTo("OK");
        assertThat(json.getString("categories[0].findings[0].text")).isEqualTo("Procedure[a1] 'Click Save' ~ Procedure[a2] 'Press Save'");
        assertThat(json.getList("categories[0].findings[0].refs.label")).containsExactly("Procedure", "Procedure");
        assertThat(json.getList("categories[0].findings[0].refs.id"))
                .containsExactly(PROCEDURE_ID.toString(), OTHER_PROCEDURE_ID.toString());
    }

    @Test
    void report_shouldSerializeToThePinnedJsonShape() throws Exception {
        var json = PanelJson.MAPPER.writeValueAsString(REPORT);

        assertThat(json).isEqualTo("""
                {"categories":[{"name":"Duplicate Atomic Procedures","description":"Atomic procedures on the same UI element",\
                "findings":[{"text":"Procedure[a1] 'Click Save' ~ Procedure[a2] 'Press Save'","refs":[\
                {"label":"Procedure","id":"00000000-0000-0000-0000-0000000000a1"},\
                {"label":"Procedure","id":"00000000-0000-0000-0000-0000000000a2"}]}],"severity":"OK"}],\
                "generatedAt":"2026-10-02T10:15:30Z"}""");
    }

    @Test
    void concurrentRequests_shouldRunOneCheck_andBothAnswerItsReport_whileALaterRequestStartsANewOne() throws Exception {
        var checkStarted = new CountDownLatch(1);
        var releaseCheck = new CountDownLatch(1);
        when(healthService.runFullHealthCheck()).thenAnswer(_ -> {
            checkStarted.countDown();
            releaseCheck.await(10, TimeUnit.SECONDS);
            return REPORT;
        });
        var cookie = login();

        try (var executor = newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> getHealth(cookie));
            assertThat(checkStarted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> getHealth(cookie));
            // Gives the second request time to reach the route and join the running check before the check finishes
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseCheck.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).jsonPath().getString("generatedAt")).isEqualTo("2026-10-02T10:15:30Z");
            assertThat(second.get(10, TimeUnit.SECONDS).jsonPath().getString("generatedAt")).isEqualTo("2026-10-02T10:15:30Z");
        }
        verify(healthService, times(1)).runFullHealthCheck();

        assertThat(getHealth(cookie).statusCode()).isEqualTo(200);
        verify(healthService, times(2)).runFullHealthCheck();
    }

    @Test
    void healthRoute_shouldRedactARegisteredSecret_inTheCategoryAndTheFindings() {
        SecretRegistry.global().register("password", "s3cr3t-value");
        when(healthService.runFullHealthCheck()).thenReturn(new GraphHealthReport(List.of(
                new HealthCheckCategory("Name s3cr3t-value", "Description s3cr3t-value",
                        List.of(new HealthFinding("Procedure 'Type s3cr3t-value'", List.of(new NodeRef("Procedure", PROCEDURE_ID)))),
                        Severity.OK)), Instant.parse("2026-10-02T10:15:30Z")));

        var response = getHealth(login());

        assertThat(response.asString()).doesNotContain("s3cr3t-value");
        assertThat(response.jsonPath().getString("categories[0].description")).isEqualTo("Description {{secret:password}}");
        assertThat(response.jsonPath().getString("categories[0].findings[0].text")).isEqualTo("Procedure 'Type {{secret:password}}'");
        assertThat(response.jsonPath().getString("categories[0].findings[0].refs[0].id")).isEqualTo(PROCEDURE_ID.toString());
    }

    @Test
    void healthRoute_shouldAnswer503WithAGenericBody_andLogTheCause_whenTheCheckFails() {
        when(healthService.runFullHealthCheck())
                .thenThrow(new DatabaseConnectionException("bolt://neo4j:7687 refused", new RuntimeException()));
        var logger = (Logger) LoggerFactory.getLogger(GraphHealthRoute.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        Response response;
        try {
            response = getHealth(login());
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.jsonPath().getString("error")).isEqualTo("Knowledge graph unavailable");
        assertThat(response.asString()).doesNotContain("bolt");
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).contains("refused");
        });
    }

    private String login() {
        var response = given().baseUri(panel.baseUrl()).header("Sec-Fetch-Site", "same-origin").contentType(ContentType.JSON)
                .body("{\"password\":\"%s\"}".formatted(PanelTestFixture.PASSWORD)).post("/api/login");
        assertThat(response.statusCode()).isEqualTo(200);
        return "panel_session=" + response.getCookie("panel_session");
    }

    private Response getHealth(String cookie) {
        return given().baseUri(panel.baseUrl()).header("Cookie", cookie).get("/api/health/graph");
    }
}
