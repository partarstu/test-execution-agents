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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DecompositionServiceTest {

    private DecompositionService decompositionService;

    @Mock
    private ProcedureRepository mockRepository;

    @Mock
    private UiTestAgentConfig configMock;

    @BeforeEach
    void setUp() {
        
        lenient().when(configMock.getKnowledgeMaxDepth()).thenReturn(5);
        decompositionService = new DecompositionService(mockRepository, configMock);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("decompose should return singleton list for atomic procedure")
    void decompose_shouldReturnSingletonList_whenAtomic() {
        UUID id = UUID.randomUUID();
        Procedure procedure = Procedure.createAtomic("atomic", List.of(), "results", List.of(), List.of(), false);
        // Procedure records generate random UUID if not specified, but Procedure.createAtomic doesn't allow specifying it.
        // Wait, I should check how to create a Procedure with a specific ID if I need to mock it.
        
        when(mockRepository.findById(any())).thenReturn(Optional.of(procedure));

        List<Procedure> result = decompositionService.decompose(id);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).isEqualTo("atomic");
    }

    @Test
    @DisplayName("decompose should recursively find children for composite procedure")
    void decompose_shouldRecursivelyFindChildren_whenComposite() {
        UUID rootId = UUID.randomUUID();
        Procedure root = Procedure.createComposite("root", List.of(), "results", List.of(), List.of(), false);
        Procedure child1 = Procedure.createAtomic("child1", List.of(), "results", List.of(), List.of(), false);
        Procedure child2 = Procedure.createAtomic("child2", List.of(), "results", List.of(), List.of(), false);

        when(mockRepository.findById(any())).thenReturn(Optional.of(root));
        when(mockRepository.findChildrenOrdered(any())).thenReturn(List.of(child1, child2));

        List<Procedure> result = decompositionService.decompose(rootId);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(child1, child2);
    }
}
