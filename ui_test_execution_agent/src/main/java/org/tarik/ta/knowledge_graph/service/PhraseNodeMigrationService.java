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

import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import static java.util.Objects.requireNonNull;

/**
 * Startup migration that backfills missing {@code HAS_EFFECT} and {@code HAS_PREREQUISITE}
 * {@link org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding} nodes for legacy procedures
 * that were ingested before phrase-node creation was part of the ingestion pipeline.
 */
@Singleton
class PhraseNodeMigrationService {
    private static final Logger LOG = LoggerFactory.getLogger(PhraseNodeMigrationService.class);

    private final ProcedureRepository procedureRepository;
    private final PhraseEmbeddingRepository phraseEmbeddingRepository;
    private final KnowledgeIngestionService knowledgeIngestionService;

    PhraseNodeMigrationService(@NotNull ProcedureRepository procedureRepository,
                               @NotNull PhraseEmbeddingRepository phraseEmbeddingRepository,
                               @NotNull KnowledgeIngestionService knowledgeIngestionService) {
        this.procedureRepository = requireNonNull(procedureRepository, "procedureRepository");
        this.phraseEmbeddingRepository = requireNonNull(phraseEmbeddingRepository, "phraseEmbeddingRepository");
        this.knowledgeIngestionService = requireNonNull(knowledgeIngestionService, "knowledgeIngestionService");
    }

    @PostConstruct
    void migrateMissingPhraseNodes() {
        forwardMigration();
        backwardRepair();
    }

    /**
     * Forward pass: procedures with non-empty prerequisites/effects but no linked phrase nodes get phrase nodes created.
     */
    private void forwardMigration() {
        var orphans = procedureRepository.findWithMissingPhraseNodes();
        if (orphans.isEmpty()) {
            LOG.debug("Phrase node migration: all procedures have up-to-date phrase nodes");
            return;
        }
        LOG.info("Phrase node migration: found {} procedure(s) with missing HAS_EFFECT/HAS_PREREQUISITE nodes — backfilling", orphans.size());
        int migrated = 0;
        int failed = 0;
        for (var procedure : orphans) {
            try {
                phraseEmbeddingRepository.deleteForProcedure(procedure.id());
                knowledgeIngestionService.createAndSavePhraseNodes(procedure);
                migrated++;
                LOG.debug("Migrated phrase nodes for procedure '{}' (id={})", procedure.description(), procedure.id());
            } catch (Exception e) {
                var msg = "Phrase node migration failed for procedure '%s' (id=%s)".formatted(procedure.description(), procedure.id());
                LOG.error(msg, e);
                failed++;
            }
        }
        if (failed > 0) {
            LOG.error("Phrase node migration failed: {}/{} procedure(s) failed to migrate", failed, orphans.size());
            throw new IllegalStateException(
                    "Phrase node migration failed for %d procedure(s) — startup aborted. Check logs above for details.".formatted(failed));
        }
        LOG.info("Phrase node migration complete: {}/{} procedure(s) migrated", migrated, orphans.size());
    }

    /**
     * Backward pass: procedures whose node-property lists differ from the ordered phrase nodes get their
     * node properties repaired. Phrase nodes are the authoritative source.
     */
    private void backwardRepair() {
        var mismatches = procedureRepository.findWithPhrasePropertyMismatches();
        if (mismatches.isEmpty()) {
            LOG.debug("Phrase property repair: all procedure node properties are in sync with phrase nodes");
            return;
        }
        LOG.info("Phrase property repair: found {} procedure(s) with stale prerequisites/effects — repairing", mismatches.size());
        int repaired = 0;
        int failed = 0;
        for (var mismatch : mismatches) {
            try {
                procedureRepository.updatePhraseProperties(mismatch.procedure().id(), mismatch.phrasePrerequisites(), mismatch.phraseEffects());
                repaired++;
                LOG.debug("Repaired phrase properties for procedure '{}' (id={})", mismatch.procedure().description(), mismatch.procedure().id());
            } catch (Exception e) {
                var msg = "Phrase property repair failed for procedure '%s' (id=%s)".formatted(mismatch.procedure().description(), mismatch.procedure().id());
                LOG.error(msg, e);
                failed++;
            }
        }
        if (failed > 0) {
            LOG.error("Phrase property repair failed: {}/{} procedure(s) failed to repair", failed, mismatches.size());
            throw new IllegalStateException(
                    "Phrase property repair failed for %d procedure(s) — startup aborted. Check logs above for details.".formatted(failed));
        }
        LOG.info("Phrase property repair complete: {}/{} procedure(s) repaired", repaired, mismatches.size());
    }
}
