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

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.repository.FailureContextRepository;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

@Singleton
public class FailureContextService {
    private static final Logger LOG = LoggerFactory.getLogger(FailureContextService.class);

    private final FailureContextRepository failureContextRepository;
    private final UiTestAgentConfig config;

    public FailureContextService(FailureContextRepository failureContextRepository, UiTestAgentConfig config) {
        this.failureContextRepository = requireNonNull(failureContextRepository, "failureContextRepository");
        this.config = config;
    }

    public void captureFailureContext(UUID procedureId, FailureContext failureContext) {
        if (!config.isLocationHistoryAndFailureHintsCollectionEnabled()) {
            return;
        }
        failureContextRepository.persistFailureContext(procedureId, failureContext);
        LOG.info("Captured failure context for procedure '{}': [{}] {}", 
                procedureId, failureContext.category(), failureContext.symptom());
    }

    public List<String> findFailureHints(UUID procedureId) {
        var hints = failureContextRepository.findFailureContexts(procedureId).stream()
                .map(fc -> "[%s] %s -> %s".formatted(fc.category().name(), fc.symptom(), fc.resolution()))
                .toList();
        LOG.debug("Found {} failure hint(s) for procedure '{}'", hints.size(), procedureId);
        return hints;
    }

    public void cleanupOrphanedFailureContexts() {
        LOG.debug("Cleaning up orphaned FailureContext nodes");
        failureContextRepository.deleteOrphanedFailureContexts();
    }
}