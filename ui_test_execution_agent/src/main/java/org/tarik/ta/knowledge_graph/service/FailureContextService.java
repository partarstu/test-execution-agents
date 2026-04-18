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

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.repository.FailureContextRepository;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

@Singleton
public class FailureContextService {
    private static final Logger LOG = LoggerFactory.getLogger(FailureContextService.class);

    private final FailureContextRepository failureContextRepository;

    public FailureContextService(FailureContextRepository failureContextRepository) {
        this.failureContextRepository = requireNonNull(failureContextRepository, "failureContextRepository");
    }

    public void captureFailureContext(UUID procedureId, FailureContext failureContext) {
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
