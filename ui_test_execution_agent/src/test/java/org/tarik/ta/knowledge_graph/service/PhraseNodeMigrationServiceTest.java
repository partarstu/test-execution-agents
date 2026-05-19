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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.ProcedureWithPhraseMismatch;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhraseNodeMigrationServiceTest {

    @Mock
    private ProcedureRepository mockProcedureRepository;
    @Mock
    private PhraseEmbeddingRepository mockPhraseEmbeddingRepository;
    @Mock
    private KnowledgeIngestionService mockKnowledgeIngestionService;

    private PhraseNodeMigrationService service;

    @BeforeEach
    void setUp() {
        service = new PhraseNodeMigrationService(mockProcedureRepository, mockPhraseEmbeddingRepository, mockKnowledgeIngestionService);
    }

    @Test
    @DisplayName("migrateMissingPhraseNodes throws IllegalStateException when forwardMigration fails for a procedure")
    void migrateMissingPhraseNodes_throwsIllegalStateException_whenForwardMigrationFails() {
        Procedure proc = Procedure.createAtomic("proc", List.of(), "", List.of(), List.of(), false);
        when(mockProcedureRepository.findWithMissingPhraseNodes()).thenReturn(List.of(proc));
        doThrow(new RuntimeException("embedding failure")).when(mockKnowledgeIngestionService).createAndSavePhraseNodes(proc);

        assertThatThrownBy(() -> service.migrateMissingPhraseNodes())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 procedure(s)");
    }

    @Test
    @DisplayName("migrateMissingPhraseNodes throws IllegalStateException when backwardRepair fails for a procedure")
    void migrateMissingPhraseNodes_throwsIllegalStateException_whenBackwardRepairFails() {
        Procedure proc = Procedure.createAtomic("proc", List.of(), "", List.of(), List.of(), false);
        var mismatch = new ProcedureWithPhraseMismatch(proc, List.of("prereq"), List.of("effect"));
        when(mockProcedureRepository.findWithMissingPhraseNodes()).thenReturn(List.of());
        when(mockProcedureRepository.findWithPhrasePropertyMismatches()).thenReturn(List.of(mismatch));
        doThrow(new RuntimeException("db failure")).when(mockProcedureRepository).updatePhraseProperties(any(), any(), any());

        assertThatThrownBy(() -> service.migrateMissingPhraseNodes())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 procedure(s)");
    }
}
