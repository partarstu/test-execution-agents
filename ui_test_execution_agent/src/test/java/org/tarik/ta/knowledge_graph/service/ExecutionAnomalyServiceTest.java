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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ExecutionMode;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.Kind;
import org.tarik.ta.knowledge_graph.repository.ExecutionAnomalyRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionAnomalyServiceTest {

    @Mock
    private ExecutionAnomalyRepository mockRepository;
    @Mock
    private UiTestAgentConfig mockConfig;

    private ExecutionAnomalyService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionAnomalyService(mockRepository, mockConfig);
    }

    @Test
    void record_shouldPersistTheAnomalyWithTheBoundTestCaseAndCappedDetails() {
        when(mockConfig.isAnomalyRecordingEnabled()).thenReturn(true);
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        var subjectId = UUID.randomUUID();
        service.bindTestCase("Login test");

        service.record(Kind.STEP_FAILED, subjectId, "x".repeat(600));

        var captor = ArgumentCaptor.forClass(ExecutionAnomaly.class);
        verify(mockRepository).persist(captor.capture());
        assertThat(captor.getValue().kind()).isEqualTo(Kind.STEP_FAILED);
        assertThat(captor.getValue().subjectId()).isEqualTo(subjectId);
        assertThat(captor.getValue().testCaseName()).isEqualTo("Login test");
        assertThat(captor.getValue().details()).hasSize(500);
        assertThat(captor.getValue().mode()).isEqualTo("UNATTENDED");
    }

    @Test
    void record_shouldNotPropagateTheError_whenTheAnomalyCannotBePersisted() {
        when(mockConfig.isAnomalyRecordingEnabled()).thenReturn(true);
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        doThrow(new DatabaseConnectionException("Neo4j connection failed during complex write query execution",
                new ServiceUnavailableException("down"))).when(mockRepository).persist(any());

        assertThatCode(() -> service.record(Kind.STEP_FAILED, UUID.randomUUID(), "verification failed")).doesNotThrowAnyException();
        verify(mockRepository).persist(any());
    }

    @Test
    void record_shouldPersistNothing_whenRecordingIsDisabled() {
        when(mockConfig.isAnomalyRecordingEnabled()).thenReturn(false);

        service.record(Kind.TIMING_OUTLIER, UUID.randomUUID(), "slow");

        verifyNoInteractions(mockRepository);
    }
}
