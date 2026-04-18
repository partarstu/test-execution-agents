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
            Thread.ofVirtual().start(() -> satisfiesEdgeService.persistSatisfiesEdges(executedProcedureId));
        } else {
            satisfiesEdgeService.persistSatisfiesEdges(executedProcedureId);
        }
    }

    public void updateTimingProfile(UUID id, long actualExecutionMs, long actualVerificationDelayMs) {
        if (isAsyncPersistenceEnabled()) {
            Thread.ofVirtual().start(() -> knowledgeService.updateTimingProfile(id, actualExecutionMs, actualVerificationDelayMs));
        } else {
            knowledgeService.updateTimingProfile(id, actualExecutionMs, actualVerificationDelayMs);
        }
    }

    public void updateElementStability(UUID elementId, boolean located, long locationTimeMs) {
        if (isAsyncPersistenceEnabled()) {
            Thread.ofVirtual().start(() -> procedureRepository.updateElementStability(elementId, located, locationTimeMs));
        } else {
            procedureRepository.updateElementStability(elementId, located, locationTimeMs);
        }
    }

    public void captureFailureContext(UUID procedureId, FailureContext failureContext) {
        if (isAsyncPersistenceEnabled()) {
            Thread.ofVirtual().start(() -> failureContextService.captureFailureContext(procedureId, failureContext));
        } else {
            failureContextService.captureFailureContext(procedureId, failureContext);
        }
    }
}
