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

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.model.edge.SatisfiesEdge;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Singleton
public class SatisfiesEdgeService {
    private static final Logger LOG = LoggerFactory.getLogger(SatisfiesEdgeService.class);
    private static final int SATISFIES_TOP_N = 50;

    private final SatisfiesEdgeRepository satisfiesEdgeRepository;
    private final PhraseEmbeddingRepository phraseEmbeddingRepository;
    private final UiTestAgentConfig config;

    public SatisfiesEdgeService(SatisfiesEdgeRepository satisfiesEdgeRepository, PhraseEmbeddingRepository phraseEmbeddingRepository, UiTestAgentConfig config) {
        this.satisfiesEdgeRepository = satisfiesEdgeRepository;
        this.phraseEmbeddingRepository = phraseEmbeddingRepository;
        this.config = config;
    }

    /**
     * Finds consumer procedures whose prerequisites are semantically satisfied by the
     * executed procedure's effects (via Neo4j vector index) and persists SATISFIES edges.
     * Deduplication keeps only the highest-scoring edge per consumer.
     */
    public void persistSatisfiesEdges(UUID executedProcedureId) {
        try {
            double threshold = config.getSatisfiesSimilarityThreshold();
            var matches = phraseEmbeddingRepository.findPrerequisitesSatisfiedByProducer(executedProcedureId, threshold, SATISFIES_TOP_N);
            if (matches.isEmpty()) {
                LOG.debug("No SATISFIES edges matched for procedure {} (threshold={})", executedProcedureId, threshold);
                return;
            }
            // Deduplication key: one SATISFIES edge per (consumer, prerequisiteNodeId) pair,
            // keeping the highest-scoring match when multiple effects satisfy the same prerequisite.
            Map<String, SatisfiesEdge> merged = new HashMap<>();
            for (var match : matches) {
                var edge = new SatisfiesEdge(executedProcedureId, match.consumerId(), match.score(), match.effectPhrase(), match.prerequisitePhrase(), match.prereqNodeId());
                var key = "%s:%s".formatted(match.consumerId(), match.prereqNodeId());
                merged.merge(key, edge, (e1, e2) -> e1.score() >= e2.score() ? e1 : e2);
            }
            LOG.debug("Persisting {} SATISFIES edge(s) for procedure {}", merged.size(), executedProcedureId);
            satisfiesEdgeRepository.persistSatisfiesEdges(new ArrayList<>(merged.values()));
        } catch (Exception e) {
            LOG.error("Failed to persist SATISFIES edges for procedure: {}", executedProcedureId, e);
        }
    }

    /**
     * Returns the prerequisite phrases of the given atomic step that are not yet satisfied by
     * a current-run effect node that is semantically similar.
     * Logs the best similarity score found per unmet prerequisite to aid diagnosis.
     */
    public List<SatisfiesEdgeRepository.UnsatisfiedPrerequisite> findUnsatisfiedPrerequisites(UUID atomicStepId, Set<UUID> effectNodeIds) {
        double threshold = config.getSatisfiesSimilarityThreshold();
        var unmet = satisfiesEdgeRepository.findUnsatisfiedPrerequisites(atomicStepId, effectNodeIds, threshold);
        unmet.forEach(p -> LOG.debug(
                "Unmet prerequisite '{}': best effect similarity score = {} for effect '{}' (threshold = {})",
                p.phrase(), p.bestScore(), p.bestEffectPhrase(), threshold));
        return unmet;
    }

    public boolean hasSatisfiesEdges() {
        return satisfiesEdgeRepository.hasSatisfiesEdges();
    }

    public List<SatisfiesEdgeRepository.OrderingConflict> findOrderingConflicts(List<UUID> orderedProcedureIds, Map<UUID, Integer> indexMap) {
        return satisfiesEdgeRepository.findOrderingConflicts(orderedProcedureIds, indexMap);
    }
}
