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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.repository.FailureContextRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailureContextServiceTest {

    @Mock
    private FailureContextRepository mockRepository;

    @Mock
    private org.tarik.ta.UiTestAgentConfig mockConfig;

    @InjectMocks
    private FailureContextService failureContextService;

    @Test
    @DisplayName("captureFailureContext should delegate to repository when enabled")
    void captureFailureContext_shouldDelegateToRepositoryWhenEnabled() {
        when(mockConfig.isLocationHistoryAndFailureHintsCollectionEnabled()).thenReturn(true);
        UUID procedureId = UUID.randomUUID();
        FailureContext context = new FailureContext(
                UUID.randomUUID(), "symptom", ErrorCategory.UNKNOWN, 
                "resolution", 1, Instant.now(), FailureContext.Mode.SUPERVISED
        );

        failureContextService.captureFailureContext(procedureId, context);

        verify(mockRepository).persistFailureContext(procedureId, context);
    }

    @Test
    @DisplayName("captureFailureContext should skip when disabled")
    void captureFailureContext_shouldSkipWhenDisabled() {
        when(mockConfig.isLocationHistoryAndFailureHintsCollectionEnabled()).thenReturn(false);
        UUID procedureId = UUID.randomUUID();
        FailureContext context = new FailureContext(
                UUID.randomUUID(), "symptom", ErrorCategory.UNKNOWN,
                "resolution", 1, Instant.now(), FailureContext.Mode.SUPERVISED
        );

        failureContextService.captureFailureContext(procedureId, context);

        org.mockito.Mockito.verifyNoInteractions(mockRepository);
    }

    @Test
    @DisplayName("findFailureHints should format all returned contexts as hints even when collection is disabled")
    void findFailureHints_shouldFormatAllContextsEvenWhenCollectionDisabled() {
        UUID procedureId = UUID.randomUUID();
        FailureContext fc1 = new FailureContext(
                UUID.randomUUID(), "slow element", ErrorCategory.TRANSIENT_TOOL_ERROR,
                "wait 3s", 1, Instant.now(), FailureContext.Mode.SUPERVISED
        );

        when(mockRepository.findFailureContexts(procedureId)).thenReturn(List.of(fc1));

        List<String> hints = failureContextService.findFailureHints(procedureId);

        assertThat(hints).containsExactly(
                "[TRANSIENT_TOOL_ERROR] slow element -> wait 3s"
        );
    }

    @Test
    @DisplayName("cleanupOrphanedFailureContexts should delegate to repository")
    void cleanupOrphanedFailureContexts_shouldDelegate() {
        failureContextService.cleanupOrphanedFailureContexts();
        verify(mockRepository).deleteOrphanedFailureContexts();
    }
}
