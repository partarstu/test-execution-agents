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
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.ProcedureWithPhraseMismatch;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
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
    @DisplayName("migrateMissingPhraseNodes continues with the remaining procedures when backwardRepair fails for one")
    void migrateMissingPhraseNodes_continuesWithRemainingProcedures_whenBackwardRepairFailsForOne() {
        Procedure failing = Procedure.createAtomic("failing", List.of(), "", List.of(), List.of(), false);
        Procedure healthy = Procedure.createAtomic("healthy", List.of(), "", List.of(), List.of(), false);
        var failingMismatch = new ProcedureWithPhraseMismatch(failing, List.of("prereq"), List.of("effect"), List.of());
        var healthyMismatch = new ProcedureWithPhraseMismatch(healthy, List.of("prereq"), List.of("effect"), List.of("negated"));
        when(mockProcedureRepository.findWithMissingPhraseNodes()).thenReturn(List.of());
        when(mockProcedureRepository.findWithPhrasePropertyMismatches()).thenReturn(List.of(failingMismatch, healthyMismatch));
        doThrow(new RuntimeException("db failure")).when(mockProcedureRepository)
                .updatePhraseProperties(eq(failing.id()), any(), any(), any());

        assertThatCode(() -> service.migrateMissingPhraseNodes()).doesNotThrowAnyException();

        verify(mockProcedureRepository).updatePhraseProperties(healthy.id(), List.of("prereq"), List.of("effect"), List.of("negated"));
    }

    @Test
    void migrateMissingPhraseNodes_shouldSkipTheMigrationWithoutThrowing_whenNeo4jIsUnreachable() {
        when(mockProcedureRepository.findWithMissingPhraseNodes())
                .thenThrow(new DatabaseConnectionException("unreachable", new RuntimeException()));

        assertThatCode(() -> service.migrateMissingPhraseNodes()).doesNotThrowAnyException();

        verify(mockProcedureRepository, never()).findWithPhrasePropertyMismatches();
    }
}
