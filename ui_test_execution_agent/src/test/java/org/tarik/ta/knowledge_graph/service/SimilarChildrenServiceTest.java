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
package org.tarik.ta.knowledge_graph.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.dto.EditorChild;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimilarChildrenServiceTest {
    @Mock
    private KnowledgeService knowledgeService;

    @Test
    void findStepsWithSimilarProcedures_shouldReturnTheIndicesOfStepsWithASimilarProcedure() {
        var similar = procedure("click the Save button");
        var steps = List.<EditorChild>of(newStep("press Save"), newStep("type the name"));
        when(knowledgeService.findTopMatches("press Save")).thenReturn(List.of(similar));
        when(knowledgeService.findTopMatches("type the name")).thenReturn(List.of());

        var indices = new SimilarChildrenService(knowledgeService).findStepsWithSimilarProcedures(steps, Set.of());

        assertThat(indices).containsExactly(0);
    }

    @Test
    void findStepsWithSimilarProcedures_shouldIgnoreAProcedureAlreadyLinkedAsAnotherStep() {
        var linked = procedure("click the Save button");
        when(knowledgeService.findTopMatches("press Save")).thenReturn(List.of(linked));

        var indices = new SimilarChildrenService(knowledgeService)
                .findStepsWithSimilarProcedures(List.of(newStep("press Save")), Set.of(linked.id()));

        assertThat(indices).isEmpty();
    }

    @Test
    void findStepsWithSimilarProcedures_shouldSkipAStep_whenItsSimilarityCheckFails() {
        when(knowledgeService.findTopMatches("press Save")).thenThrow(new IllegalStateException("the graph is unreachable"));
        when(knowledgeService.findTopMatches("type the name")).thenReturn(List.of(procedure("enter the name")));

        var indices = new SimilarChildrenService(knowledgeService)
                .findStepsWithSimilarProcedures(List.of(newStep("press Save"), newStep("type the name")), Set.of());

        assertThat(indices).containsExactly(1);
    }

    private static EditorChild newStep(String description) {
        return new EditorChild.New(procedure(description), null, null, true, List.of());
    }

    private static Procedure procedure(String description) {
        return Procedure.createAtomic(description, List.of(), "", List.of(), List.of(), false);
    }
}
