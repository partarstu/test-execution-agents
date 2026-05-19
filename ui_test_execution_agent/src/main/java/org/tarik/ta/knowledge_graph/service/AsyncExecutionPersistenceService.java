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

import io.avaje.inject.Component;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.util.UUID;

/**
 * Singleton service for non-blocking persistence of execution-related graph data.
 * Active only in unattended mode. Delegates to synchronous paths in supervised mode.
 */
@Component
@Singleton
public class AsyncExecutionPersistenceService {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncExecutionPersistenceService.class);

    private final UiTestAgentConfig config;
    private final SatisfiesEdgeService satisfiesEdgeService;
    private final KnowledgeService knowledgeService;
    private final ProcedureRepository procedureRepository;
    private final FailureContextService failureContextService;

    public AsyncExecutionPersistenceService(UiTestAgentConfig config,
                                            SatisfiesEdgeService satisfiesEdgeService,
                                            KnowledgeService knowledgeService,
                                            ProcedureRepository procedureRepository,
                                            FailureContextService failureContextService) {
        this.config = config;
        this.satisfiesEdgeService = satisfiesEdgeService;
        this.knowledgeService = knowledgeService;
        this.procedureRepository = procedureRepository;
        this.failureContextService = failureContextService;
    }

    /**
     * Checks if async persistence should be used.
     * True only if the mode is unattended.
     */
    public boolean isAsyncPersistenceEnabled() {
        return config.getExecutionMode() == ExecutionMode.UNATTENDED;
    }

    public void persistSatisfiesEdges(UUID executedProcedureId) {
        if (isAsyncPersistenceEnabled()) {
            Thread.ofVirtual().start(() -> {
                try {
                    satisfiesEdgeService.persistSatisfiesEdges(executedProcedureId);
                } catch (Exception e) {
                    LOG.error("Async persistence failed: persistSatisfiesEdges for id {}", executedProcedureId, e);
                }
            });
        } else {
            satisfiesEdgeService.persistSatisfiesEdges(executedProcedureId);
        }
    }

    public void updateTimingProfile(UUID id, long actualExecutionMs, long actualVerificationDelayMs) {
        if (isAsyncPersistenceEnabled()) {
            Thread.ofVirtual().start(() -> {
                try {
                    knowledgeService.updateTimingProfile(id, actualExecutionMs, actualVerificationDelayMs);
                } catch (Exception e) {
                    LOG.error("Async persistence failed: updateTimingProfile for id {}", id, e);
                }
            });
        } else {
            knowledgeService.updateTimingProfile(id, actualExecutionMs, actualVerificationDelayMs);
        }
    }

    public void updateElementStability(UUID elementId, boolean located, long locationTimeMs) {
        if (isAsyncPersistenceEnabled()) {
            Thread.ofVirtual().start(() -> {
                try {
                    procedureRepository.updateElementStability(elementId, located, locationTimeMs);
                } catch (Exception e) {
                    LOG.error("Async persistence failed: updateElementStability for id {}", elementId, e);
                }
            });
        } else {
            procedureRepository.updateElementStability(elementId, located, locationTimeMs);
        }
    }

    public void captureFailureContext(UUID procedureId, FailureContext failureContext) {
        if (isAsyncPersistenceEnabled()) {
            Thread.ofVirtual().start(() -> {
                try {
                    failureContextService.captureFailureContext(procedureId, failureContext);
                } catch (Exception e) {
                    LOG.error("Async persistence failed: captureFailureContext for id {}", procedureId, e);
                }
            });
        } else {
            failureContextService.captureFailureContext(procedureId, failureContext);
        }
    }
}
