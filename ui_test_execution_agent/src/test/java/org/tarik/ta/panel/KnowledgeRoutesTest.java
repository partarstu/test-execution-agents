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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.security.SecretRegistry;
import org.tarik.ta.dto.panel.knowledge.ElementPage;
import org.tarik.ta.dto.panel.knowledge.ElementPage.Sort;
import org.tarik.ta.dto.panel.knowledge.GraphEdge;
import org.tarik.ta.dto.panel.knowledge.GraphNode;
import org.tarik.ta.dto.panel.knowledge.GraphNode.Flag;
import org.tarik.ta.dto.panel.knowledge.GraphNode.Kind;
import org.tarik.ta.dto.panel.knowledge.GraphSlice;
import org.tarik.ta.dto.panel.knowledge.KnowledgeSummary;
import org.tarik.ta.dto.panel.knowledge.KnowledgeSummary.Count;
import org.tarik.ta.dto.panel.knowledge.SearchResults;
import org.tarik.ta.dto.panel.knowledge.WeeklyTrends;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.model.node.UiElement.Screenshot;
import org.tarik.ta.knowledge_graph.service.KnowledgeViewService;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeRoutesTest {
    private static final UUID PROCEDURE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ELEMENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID APPEARANCE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G'};

    private final KnowledgeViewService service = mock(KnowledgeViewService.class);
    private PanelTestFixture panel;

    @BeforeEach
    void setUp() {
        SecretRegistry.global().reset(Pattern.compile("(?i).*password.*"));
        panel = PanelTestFixture.builder().knowledgeViewService(service).start();
    }

    @AfterEach
    void tearDown() {
        panel.close();
        SecretRegistry.global().reset(Pattern.compile("(?!)"));
    }

    @Test
    void everyKnowledgeRoute_shouldAnswer401_withoutASession() {
        var paths = List.of("/api/knowledge/summary", "/api/knowledge/search?q=save",
                "/api/knowledge/procedures/%s/neighbourhood".formatted(PROCEDURE_ID), "/api/knowledge/test-cases/procedures?name=x",
                "/api/knowledge/procedures/%s".formatted(PROCEDURE_ID), "/api/knowledge/elements",
                "/api/knowledge/elements/%s".formatted(ELEMENT_ID), "/api/knowledge/elements/%s/screenshot".formatted(ELEMENT_ID),
                "/api/knowledge/elements/%s/appearances/%s/screenshot".formatted(ELEMENT_ID, APPEARANCE_ID),
                "/api/knowledge/overview", "/api/knowledge/trends",
                "/api/knowledge/failures?procedureId=%s".formatted(PROCEDURE_ID));

        assertThat(paths).allSatisfy(path -> assertThat(given().baseUri(panel.baseUrl()).get(path).statusCode()).as(path).isEqualTo(401));
        verifyNoInteractions(service);
    }

    @Test
    void summary_shouldAnswerThePinnedJsonShape() {
        when(service.summary()).thenReturn(new KnowledgeSummary(List.of(new Count("Procedure", 4)), List.of(new Count("CONTAINS", 3)),
                11, 3, 2, 1));

        var response = get(login(), "/api/knowledge/summary");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo("{\"nodesByLabel\":[{\"name\":\"Procedure\",\"count\":4}],"
                + "\"relationshipsByType\":[{\"name\":\"CONTAINS\",\"count\":3}],\"schemaVersion\":11,\"atomicProcedures\":3,"
                + "\"atomicProceduresWithTarget\":2,\"atomicProceduresWithEffects\":1}");
    }

    @Test
    void neighbourhood_shouldAnswerTheSliceWithItsCapFlag_andTheEdgeValues() {
        var node = new GraphNode(PROCEDURE_ID.toString(), Kind.ATOMIC, "Click Save", Set.of(Flag.NO_TARGET), 2);
        var edge = new GraphEdge(PROCEDURE_ID.toString(), "other", GraphEdge.Kind.SATISFIES, null, 0.91, "Saved", "Data is saved");
        when(service.neighbourhood(PROCEDURE_ID)).thenReturn(Optional.of(new GraphSlice(List.of(node), List.of(edge), true)));

        var json = get(login(), "/api/knowledge/procedures/%s/neighbourhood".formatted(PROCEDURE_ID)).jsonPath();

        assertThat(json.getBoolean("capped")).isTrue();
        assertThat(json.getString("nodes[0].kind")).isEqualTo("ATOMIC");
        assertThat(json.getList("nodes[0].flags")).containsExactly("NO_TARGET");
        assertThat(json.getInt("nodes[0].testCases")).isEqualTo(2);
        assertThat(json.getString("edges[0].kind")).isEqualTo("SATISFIES");
        assertThat(json.getDouble("edges[0].score")).isEqualTo(0.91);
        assertThat(json.getString("edges[0].prerequisitePhrase")).isEqualTo("Data is saved");
    }

    @Test
    void procedure_shouldAnswer404_forAnIdUnknownToTheGraph() {
        when(service.procedureDetails(PROCEDURE_ID)).thenReturn(Optional.empty());

        var response = get(login(), "/api/knowledge/procedures/%s".formatted(PROCEDURE_ID));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.jsonPath().getString("error")).isEqualTo("Not found");
    }

    @Test
    void neighbourhood_shouldAnswer400WithoutAQuery_forAnIdThatIsNotACanonicalUuid() {
        var cookie = login();

        assertThat(List.of("not-a-uuid", "1-1-1-1-1")).allSatisfy(id ->
                assertThat(get(cookie, "/api/knowledge/procedures/%s/neighbourhood".formatted(id)).statusCode()).as(id).isEqualTo(400));
        verifyNoInteractions(service);
    }

    @Test
    void knowledgeRoutes_shouldAnswer400WithoutAQuery_forInvalidParameters() {
        var cookie = login();
        var paths = List.of("/api/knowledge/search", "/api/knowledge/search?q=%20%20", "/api/knowledge/elements?sort=name",
                "/api/knowledge/elements?offset=-1", "/api/knowledge/elements?offset=many", "/api/knowledge/trends?weeks=0",
                "/api/knowledge/trends?weeks=53", "/api/knowledge/failures", "/api/knowledge/test-cases/procedures");

        assertThat(paths).allSatisfy(path -> assertThat(get(cookie, path).statusCode()).as(path).isEqualTo(400));
        verifyNoInteractions(service);
    }

    @Test
    void search_shouldAnswer400_forAQueryLongerThan100Characters() {
        var response = get(login(), "/api/knowledge/search?q=" + "a".repeat(KnowledgeRoutes.MAX_SEARCH_LENGTH + 1));

        assertThat(response.statusCode()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @Test
    void testCaseLens_shouldAnswer400_forANameLongerThan500Characters() {
        var name = "a".repeat(KnowledgeRoutes.MAX_TEST_CASE_NAME_LENGTH + 1);

        var response = get(login(), "/api/knowledge/test-cases/procedures?name=" + name);

        assertThat(response.statusCode()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @Test
    void search_shouldRedactARegisteredSecret_inTheProcedureDescription() {
        SecretRegistry.global().register("password", "s3cr3t-value");
        var hit = new GraphNode(PROCEDURE_ID.toString(), Kind.ATOMIC, "Type s3cr3t-value", Set.of(), 0);
        when(service.search("type")).thenReturn(new SearchResults(List.of(hit), false));

        var response = get(login(), "/api/knowledge/search?q=type");

        assertThat(response.asString()).doesNotContain("s3cr3t-value");
        assertThat(response.jsonPath().getString("hits[0].label")).isEqualTo("Type {{secret:password}}");
        assertThat(response.jsonPath().getString("hits[0].id")).isEqualTo(PROCEDURE_ID.toString());
    }

    @Test
    void elements_shouldPassTheSortAndTheOffset_andDefaultToTheLeastStableFirst() {
        when(service.elements(any(), anyInt())).thenReturn(new ElementPage(List.of(), 0, false));
        var cookie = login();

        get(cookie, "/api/knowledge/elements?sort=retries&offset=60");
        get(cookie, "/api/knowledge/elements");

        verify(service).elements(Sort.RETRIES, 60);
        verify(service).elements(Sort.STABILITY, 0);
    }

    @Test
    void trends_shouldDefaultToTwelveWeeks() {
        when(service.trends(12)).thenReturn(new WeeklyTrends(List.of()));

        assertThat(get(login(), "/api/knowledge/trends").statusCode()).isEqualTo(200);
    }

    @Test
    void knowledgeRoutes_shouldAnswer503WithAGenericBody_whenAQueryFails() {
        when(service.overview()).thenThrow(new DatabaseConnectionException("bolt://neo4j:7687 refused", new RuntimeException()));

        var response = get(login(), "/api/knowledge/overview");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.jsonPath().getString("error")).isEqualTo("Knowledge graph unavailable");
        assertThat(response.asString()).doesNotContain("bolt");
    }

    @Test
    void elementScreenshot_shouldAnswerTheImageBytesWithTheStoredType_forAPng() {
        when(service.elementScreenshot(ELEMENT_ID)).thenReturn(Optional.of(screenshot("image/png")));

        var response = get(login(), "/api/knowledge/elements/%s/screenshot".formatted(ELEMENT_ID));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.asByteArray()).isEqualTo(PNG_BYTES);
        assertThat(response.header("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void elementScreenshot_shouldAnswer404_forATypeOutsideTheAllowList() {
        when(service.elementScreenshot(ELEMENT_ID)).thenReturn(Optional.of(screenshot("image/svg+xml")));

        assertThat(get(login(), "/api/knowledge/elements/%s/screenshot".formatted(ELEMENT_ID)).statusCode()).isEqualTo(404);
    }

    @Test
    void elementScreenshot_shouldAnswer404_whenTheStoredImageIsNotValidBase64() {
        when(service.elementScreenshot(ELEMENT_ID)).thenReturn(Optional.of(new Screenshot("png", "image/png", "not*base64")));

        assertThat(get(login(), "/api/knowledge/elements/%s/screenshot".formatted(ELEMENT_ID)).statusCode()).isEqualTo(404);
    }

    @Test
    void appearanceScreenshot_shouldAnswer404_forAnUnknownAppearance() {
        when(service.appearanceScreenshot(ELEMENT_ID, APPEARANCE_ID)).thenReturn(Optional.empty());

        var response = get(login(), "/api/knowledge/elements/%s/appearances/%s/screenshot".formatted(ELEMENT_ID, APPEARANCE_ID));

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private static Screenshot screenshot(String mimeType) {
        return new Screenshot("png", mimeType, Base64.getEncoder().encodeToString(PNG_BYTES));
    }

    private String login() {
        var response = given().baseUri(panel.baseUrl()).header("Sec-Fetch-Site", "same-origin").contentType(ContentType.JSON)
                .body("{\"password\":\"%s\"}".formatted(PanelTestFixture.PASSWORD)).post("/api/login");
        assertThat(response.statusCode()).isEqualTo(200);
        return "panel_session=" + response.getCookie("panel_session");
    }

    private Response get(String cookie, String path) {
        return given().baseUri(panel.baseUrl()).urlEncodingEnabled(false).header("Cookie", cookie).get(path);
    }
}
