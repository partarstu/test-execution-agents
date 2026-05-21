/*
 * ui-test-execution-agent - ${project.description}
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
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncExecutionPersistenceServiceTest {

    private AsyncExecutionPersistenceService service;

    @Mock private UiTestAgentConfig mockConfig;
    @Mock private SatisfiesEdgeService mockSatisfiesEdgeService;
    @Mock private KnowledgeService mockKnowledgeService;
    @Mock private ProcedureRepository mockProcedureRepository;
    @Mock private FailureContextService mockFailureContextService;

    @BeforeEach
    void setUp() {
        service = new AsyncExecutionPersistenceService(mockConfig, mockSatisfiesEdgeService,
                mockKnowledgeService, mockProcedureRepository, mockFailureContextService);
    }

    @Test
    @DisplayName("persistSatisfiesEdges runs synchronously in SUPERVISED mode")
    void persistSatisfiesEdges_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();

        service.persistSatisfiesEdges(id);

        verify(mockSatisfiesEdgeService).persistSatisfiesEdges(id);
    }

    @Test
    @DisplayName("persistSatisfiesEdges runs asynchronously in UNATTENDED mode")
    void persistSatisfiesEdges_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mockSatisfiesEdgeService).persistSatisfiesEdges(id);

        service.persistSatisfiesEdges(id);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockSatisfiesEdgeService).persistSatisfiesEdges(id);
    }

    @Test
    @DisplayName("updateTimingProfile runs synchronously in SUPERVISED mode")
    void updateTimingProfile_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();

        service.updateTimingProfile(id, 100, 200);

        verify(mockKnowledgeService).updateTimingProfile(id, 100, 200);
    }

    @Test
    @DisplayName("updateTimingProfile runs asynchronously in UNATTENDED mode")
    void updateTimingProfile_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mockKnowledgeService).updateTimingProfile(id, 100, 200);

        service.updateTimingProfile(id, 100, 200);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockKnowledgeService).updateTimingProfile(id, 100, 200);
    }

    @Test
    @DisplayName("updateElementStability runs synchronously in SUPERVISED mode")
    void updateElementStability_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();

        service.updateElementStability(id, true, 100);

        verify(mockProcedureRepository).updateElementStability(id, true, 100);
    }

    @Test
    @DisplayName("updateElementStability runs asynchronously in UNATTENDED mode")
    void updateElementStability_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mockProcedureRepository).updateElementStability(id, true, 100);

        service.updateElementStability(id, true, 100);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockProcedureRepository).updateElementStability(id, true, 100);
    }

    @Test
    @DisplayName("captureFailureContext runs synchronously in SUPERVISED mode")
    void captureFailureContext_syncInSupervised() {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        UUID id = UUID.randomUUID();
        FailureContext fc = mock(FailureContext.class);

        service.captureFailureContext(id, fc);

        verify(mockFailureContextService).captureFailureContext(id, fc);
    }

    @Test
    @DisplayName("captureFailureContext runs asynchronously in UNATTENDED mode")
    void captureFailureContext_asyncInUnattended() throws InterruptedException {
        when(mockConfig.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        UUID id = UUID.randomUUID();
        FailureContext fc = mock(FailureContext.class);
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mockFailureContextService).captureFailureContext(id, fc);

        service.captureFailureContext(id, fc);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(mockFailureContextService).captureFailureContext(id, fc);
    }
}
