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
        var orphans = procedureRepository.findWithMissingPhraseNodes();
        if (orphans.isEmpty()) {
            LOG.debug("Phrase node migration: all procedures have up-to-date phrase nodes");
            return;
        }
        LOG.info("Phrase node migration: found {} procedure(s) with missing HAS_EFFECT/HAS_PREREQUISITE nodes — backfilling", orphans.size());
        int migrated = 0;
        for (var procedure : orphans) {
            try {
                // Delete any partial phrase nodes first to avoid duplicates on re-create
                phraseEmbeddingRepository.deleteForProcedure(procedure.id());
                knowledgeIngestionService.createAndSavePhraseNodes(procedure);
                migrated++;
                LOG.debug("Migrated phrase nodes for procedure '{}' (id={})", procedure.description(), procedure.id());
            } catch (Exception e) {
                var msg = "Phrase node migration failed for procedure '%s' (id=%s)".formatted(procedure.description(), procedure.id());
                LOG.error(msg, e);
            }
        }
        LOG.info("Phrase node migration complete: {}/{} procedure(s) migrated", migrated, orphans.size());
    }
}
