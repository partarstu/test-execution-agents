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
