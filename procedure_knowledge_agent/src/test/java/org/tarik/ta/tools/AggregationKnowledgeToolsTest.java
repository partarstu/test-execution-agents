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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.knowledge.ProcedureTreeAssembler;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.tarik.ta.knowledge.ProcedureFixtures.atomic;
import static org.tarik.ta.knowledge.ProcedureFixtures.composite;

@ExtendWith(MockitoExtension.class)
class AggregationKnowledgeToolsTest {
    @Mock
    private ProcedureRepository procedureRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private KnowledgeAgentConfig config;

    private AggregationKnowledgeTools tools;

    @BeforeEach
    void setUp() {
        lenient().when(config.getExplorationMaxToolCalls()).thenReturn(40);
        lenient().when(config.getMaxTreeDepth()).thenReturn(5);
        lenient().when(config.getSearchTopN()).thenReturn(10);
        tools = new AggregationKnowledgeTools(procedureRepository, embeddingService, new ProcedureTreeAssembler(procedureRepository, config),
                config);
    }

    @Test
    void findCommonContainingProcedures_shouldReturnTheProceduresContainingAllGivenOnesAtAnyDepth() {
        var enterUser = atomic("Enter the username");
        var submit = atomic("Click Sign in");
        var login = composite("Log in");
        when(procedureRepository.findCommonAncestors(List.of(enterUser.id(), submit.id()), 5, 10)).thenReturn(List.of(login));

        var json = tools.findCommonContainingProcedures(List.of(enterUser.id().toString(), submit.id().toString()));

        assertThat(json).contains("\"description\":\"Log in\"").doesNotContain("children");
        assertThat(tools.findReturnedDescription(login.id().toString())).contains("Log in");
    }

    @Test
    void findCommonContainingProcedures_shouldReportTheOperation_whenTheGraphCannotBeRead() {
        var first = atomic("Enter the username");
        var second = atomic("Click Sign in");
        when(procedureRepository.findCommonAncestors(List.of(first.id(), second.id()), 5, 10)).thenThrow(new IllegalStateException("graph down"));

        assertThatThrownBy(() -> tools.findCommonContainingProcedures(List.of(first.id().toString(), second.id().toString())))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("graph down");
    }

    @Test
    void findCommonContainingProcedures_shouldReportARetryableError_whenFewerThanTwoProceduresAreGiven() {
        assertThatThrownBy(() -> tools.findCommonContainingProcedures(List.of(atomic("Enter the username").id().toString())))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("between 2 and 20");
        verifyNoInteractions(procedureRepository);
    }
}
