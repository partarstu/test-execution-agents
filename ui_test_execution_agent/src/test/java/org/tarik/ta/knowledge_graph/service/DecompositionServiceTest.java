/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

@ExtendWith(MockitoExtension.class)
class DecompositionServiceTest {

    private DecompositionService decompositionService;

    @Mock
    private ProcedureRepository mockRepository;

    private MockedStatic<UiTestAgentConfig> configMock;

    @BeforeEach
    void setUp() {
        configMock = mockStatic(UiTestAgentConfig.class);
        configMock.when(UiTestAgentConfig::getKnowledgeMaxDepth).thenReturn(5);
        decompositionService = new DecompositionService(mockRepository);
    }

    @AfterEach
    void tearDown() {
        configMock.close();
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
