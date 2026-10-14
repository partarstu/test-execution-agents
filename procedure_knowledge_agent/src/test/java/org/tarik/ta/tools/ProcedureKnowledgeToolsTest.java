/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
package org.tarik.ta.tools;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.knowledge.ProcedureTreeAssembler;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.TargetedUiElement;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.error.ErrorCategory.NON_RETRYABLE_ERROR;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.knowledge.ProcedureFixtures.atomic;
import static org.tarik.ta.knowledge.ProcedureFixtures.child;
import static org.tarik.ta.knowledge.ProcedureFixtures.composite;
import static org.tarik.ta.knowledge.ProcedureFixtures.root;

@ExtendWith(MockitoExtension.class)
class ProcedureKnowledgeToolsTest {
    private static final float[] QUERY_VECTOR = {0.1f, 0.2f};
    private static final int TOP_N = 10;
    private static final double MIN_SCORE = 0.8;
    private static final int MAX_TOOL_CALLS = 3;

    @Mock
    private ProcedureRepository procedureRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private KnowledgeAgentConfig config;

    private ProcedureKnowledgeTools tools;

    @BeforeEach
    void setUp() {
        lenient().when(config.getSearchTopN()).thenReturn(TOP_N);
        lenient().when(config.getSearchMinScore()).thenReturn(MIN_SCORE);
        lenient().when(config.getExplorationMaxToolCalls()).thenReturn(MAX_TOOL_CALLS);
        lenient().when(config.getMaxTreeDepth()).thenReturn(5);
        lenient().when(config.getMaxTreeNodes()).thenReturn(200);
        tools = new ProcedureKnowledgeTools(procedureRepository, embeddingService, new ProcedureTreeAssembler(procedureRepository, config),
                config);
    }

    @Test
    void searchProcedures_shouldReturnTheHitsAboveTheFloorWithTheirScoreAndOwnDataOnly() {
        var login = composite("Log in as administrator");
        var enterUser = atomic("Enter the username");
        when(embeddingService.embedForQuery("log in")).thenReturn(Embedding.from(QUERY_VECTOR));
        when(procedureRepository.findBySemanticSearch(QUERY_VECTOR, TOP_N, MIN_SCORE))
                .thenReturn(List.of(new ProcedureMatch(login, 0.93), new ProcedureMatch(enterUser, 0.85)));
        when(procedureRepository.findTargetedUiElements(List.of(enterUser.id())))
                .thenReturn(Map.of(enterUser.id(), new TargetedUiElement("Username field", "Text input", "Below the logo", "Login form")));

        var json = tools.searchProcedures("log in");

        assertThat(json)
                .contains("\"id\":\"%s\"".formatted(login.id()), "\"score\":0.93", "\"description\":\"Log in as administrator\"")
                .contains("\"is_precondition\":true", "\"ui_element\":{\"name\":\"Username field\"", "\"negated_effects\":[\"logged out\"]")
                .doesNotContain("children", "test_data", "expected_results");
        assertThat(tools.findReturnedDescription(login.id().toString())).contains("Log in as administrator");
        assertThat(tools.findReturnedDescription(enterUser.id().toString())).contains("Enter the username");
    }

    @Test
    void searchProcedures_shouldRejectTheQuery_whenItIsBlankOrTooLong() {
        assertThatThrownBy(() -> tools.searchProcedures(" "))
                .isInstanceOfSatisfying(ToolExecutionException.class, e -> assertThat(e.getErrorCategory()).isEqualTo(TRANSIENT_TOOL_ERROR));
        assertThatThrownBy(() -> tools.searchProcedures("x".repeat(ProcedureKnowledgeTools.MAX_QUERY_LENGTH + 1)))
                .isInstanceOf(ToolExecutionException.class);
        verifyNoInteractions(embeddingService);
    }

    @Test
    void getProcedureTree_shouldReturnTheWholeTreeAndRememberEveryNode() {
        var login = composite("Log in");
        var enterUser = atomic("Enter the username");
        when(procedureRepository.findSubtree(login.id(), 5, 200)).thenReturn(List.of(root(login, 1), child(enterUser, login, 1, 0)));

        var json = tools.getProcedureTree(login.id().toString());

        assertThat(json).contains("\"children\":[{\"id\":\"%s\"".formatted(enterUser.id()), "\"success_count\":2", "\"test_data\":[\"username\"]");
        assertThat(tools.findReturnedDescription(enterUser.id().toString())).contains("Enter the username");
    }

    @Test
    void getProcedureTree_shouldReportARetryableError_whenTheIdIsInvalidOrUnknown() {
        var unknownId = UUID.randomUUID();
        when(procedureRepository.findSubtree(any(), anyInt(), anyInt())).thenReturn(List.of());

        assertThatThrownBy(() -> tools.getProcedureTree("not-an-id"))
                .isInstanceOfSatisfying(ToolExecutionException.class, e -> assertThat(e.getErrorCategory()).isEqualTo(TRANSIENT_TOOL_ERROR));
        assertThatThrownBy(() -> tools.getProcedureTree(unknownId.toString()))
                .isInstanceOfSatisfying(ToolExecutionException.class, e -> assertThat(e.getErrorCategory()).isEqualTo(TRANSIENT_TOOL_ERROR))
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void getContainingProcedures_shouldReturnTheParentsWithTheirOwnDataOnly() {
        var enterUser = atomic("Enter the username");
        var login = composite("Log in");
        when(procedureRepository.findParents(enterUser.id())).thenReturn(List.of(login));

        var json = tools.getContainingProcedures(enterUser.id().toString());

        assertThat(json).contains("\"description\":\"Log in\"").doesNotContain("children", "score");
        assertThat(tools.findReturnedDescription(login.id().toString())).contains("Log in");
        assertThat(tools.findReturnedDescription(enterUser.id().toString())).isEmpty();
    }

    @Test
    void tools_shouldReportTheOperation_whenTheGraphCannotBeRead() {
        var procedureId = UUID.randomUUID();
        when(embeddingService.embedForQuery(any())).thenReturn(Embedding.from(QUERY_VECTOR));
        when(procedureRepository.findBySemanticSearch(any(), anyInt(), anyDouble())).thenThrow(new IllegalStateException("graph down"));
        when(procedureRepository.findParents(procedureId)).thenThrow(new IllegalStateException("graph down"));

        assertThatThrownBy(() -> tools.searchProcedures("log in"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("searching the recorded procedures: graph down");
        assertThatThrownBy(() -> tools.getContainingProcedures(procedureId.toString()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("procedures containing procedure %s: graph down".formatted(procedureId));
    }

    @Test
    void findReturnedDescription_shouldFindNothing_whenTheIdIsBlankOrInvalid() {
        assertThat(tools.findReturnedDescription(" ")).isEmpty();
        assertThat(tools.findReturnedDescription(null)).isEmpty();
        assertThat(tools.findReturnedDescription("not-an-id")).isEmpty();
    }

    @Test
    void toolCalls_shouldEndTheRunWithATerminalError_whenTheCapIsExceeded() {
        when(embeddingService.embedForQuery(any())).thenReturn(Embedding.from(QUERY_VECTOR));
        when(procedureRepository.findBySemanticSearch(any(), anyInt(), anyDouble())).thenReturn(List.of());
        for (int call = 0; call < MAX_TOOL_CALLS; call++) {
            tools.searchProcedures("log in");
        }

        assertThatThrownBy(() -> tools.searchProcedures("log in"))
                .isInstanceOfSatisfying(ToolExecutionException.class, e -> assertThat(e.getErrorCategory()).isEqualTo(NON_RETRYABLE_ERROR))
                .hasMessageContaining("budget of %d tool calls".formatted(MAX_TOOL_CALLS));
    }
}
