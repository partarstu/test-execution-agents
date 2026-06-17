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
