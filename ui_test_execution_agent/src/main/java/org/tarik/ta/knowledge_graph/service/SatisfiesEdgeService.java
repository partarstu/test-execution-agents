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
     * Asynchronously finds consumer procedures whose prerequisites are semantically satisfied by the
     * executed procedure's effects (via Neo4j vector index) and persists SATISFIES edges.
     * Deduplication keeps only the highest-scoring edge per consumer.
     */
    public void persistSatisfiesEdgesAsync(UUID executedProcedureId) {
        Thread.ofVirtual().start(() -> {
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
                LOG.error("Failed to persist SATISFIES edges async for procedure: {}", executedProcedureId, e);
            }
        });
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
