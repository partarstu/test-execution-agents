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
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeServicesBeanFactoryTest {

    @Mock
    private ProcedureRepository mockRepository;

    @Mock
    private UiTestAgentConfig mockConfig;

    @Mock
    private AsyncExecutionPersistenceService mockAsyncService;

    private KnowledgeServicesBeanFactory factory;

    @BeforeEach
    void setUp() {
        factory = new KnowledgeServicesBeanFactory(mockRepository, mockConfig);
    }

    @Test
    @DisplayName("locationHistoryRecorder should be no-op when collection is disabled")
    void locationHistoryRecorder_shouldBeNoOpWhenCollectionDisabled() {
        when(mockConfig.isLocationHistoryAndFailureHintsCollectionEnabled()).thenReturn(false);

        LocationHistoryRecorder recorder = factory.locationHistoryRecorder(mockAsyncService);
        recorder.record(UUID.randomUUID(), true, 100);

        verifyNoInteractions(mockAsyncService);
    }

    @Test
    @DisplayName("locationHistoryRecorder should delegate when enabled")
    void locationHistoryRecorder_shouldDelegateWhenEnabled() {
        when(mockConfig.isLocationHistoryAndFailureHintsCollectionEnabled()).thenReturn(true);

        LocationHistoryRecorder recorder = factory.locationHistoryRecorder(mockAsyncService);
        UUID elementId = UUID.randomUUID();
        recorder.record(elementId, true, 100);

        verify(mockAsyncService).updateElementStability(elementId, true, 100);
    }

    @Test
    @DisplayName("elementStabilityLookup should delegate to repository even when collection is disabled")
    void elementStabilityLookup_shouldDelegateToRepositoryEvenWhenCollectionDisabled() {
        UUID elementId = UUID.randomUUID();
        when(mockRepository.getElementLocationHistory(elementId)).thenReturn(Optional.empty());

        ElementLocationHistoryLookup lookup = factory.elementStabilityLookup();
        lookup.lookup(elementId);

        verify(mockRepository).getElementLocationHistory(elementId);
    }
}
