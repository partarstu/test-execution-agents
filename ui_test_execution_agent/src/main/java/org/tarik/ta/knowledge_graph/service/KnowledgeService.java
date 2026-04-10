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

import dev.langchain4j.data.embedding.Embedding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.CandidateContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;

import org.tarik.ta.UiTestAgentConfig;

/**
 * Main facade coordinating procedure retrieval and decomposition.
 *
 * <p>
 * This service is the primary entry point for the knowledge-based execution engine.
 * It delegates to {@link ProcedureRepository} for persistence,
 * {@link DecompositionService} for hierarchy traversal, and {@link EmbeddingService} for embedding generation.
 * </p>
 */
@Singleton
public class KnowledgeService {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeService.class);

    private final ProcedureRepository procedureRepository;
    private final EmbeddingService embeddingService;
    private final DecompositionService decompositionService;
    private final PhraseEmbeddingRepository phraseEmbeddingRepository;
    private final UiTestAgentConfig config;

    public KnowledgeService(ProcedureRepository procedureRepository,
                            EmbeddingService embeddingService,
                            DecompositionService decompositionService,
                            PhraseEmbeddingRepository phraseEmbeddingRepository,
                            UiTestAgentConfig config) {
        this.procedureRepository = requireNonNull(procedureRepository, "procedureRepository");
        this.embeddingService = requireNonNull(embeddingService, "embeddingService");
        this.decompositionService = requireNonNull(decompositionService, "decompositionService");
        this.phraseEmbeddingRepository = requireNonNull(phraseEmbeddingRepository, "phraseEmbeddingRepository");
        this.config = requireNonNull(config, "config");
    }

    /**
     * Finds the best-matching procedure using a pre-computed embedding. Avoids re-embedding when the
     * caller has already batch-embedded multiple descriptions (e.g., ordering conflict detection).
     */
    public Optional<MatchResult> findBestMatchByEmbedding(Embedding queryEmbedding, Set<UUID> effectNodeIds, Set<UUID> recentParentIds) {
        requireNonNull(queryEmbedding, "queryEmbedding");
        requireNonNull(effectNodeIds, "effectNodeIds");
        requireNonNull(recentParentIds, "recentParentIds");
        var matches = procedureRepository.findBySemanticSearch(queryEmbedding.vector(), config.getKnowledgeMatchTopN(), config.getKnowledgeMatchConfidenceLow());
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        var scoreById = matches.stream().collect(toMap(m -> m.procedure().id(), ProcedureMatch::score));
        var procedures = matches.stream().map(ProcedureMatch::procedure).toList();
        var reranked = reRankByStateCompatibility(procedures, scoreById, effectNodeIds, recentParentIds);
        var bestMatch = reranked.procedures().getFirst().procedure();
        var confidence = scoreById.get(bestMatch.id()) >= config.getKnowledgeMatchConfidenceHigh() ? MatchConfidence.HIGH : MatchConfidence.LOW;
        return Optional.of(new MatchResult(bestMatch, confidence, reranked.procedures().stream().map(ScoredProcedure::procedure).toList(), reranked.wasDemoted()));
    }

    /**
     * Finds the best-matching procedure for the given description, re-ranked by state compatibility.
     *
     * <p>Candidates are first retrieved by semantic similarity, then re-ranked so that procedures whose
     * preconditions are semantically satisfied by the current execution state come first. Procedures with
     * no preconditions always score {@code 1.0} (universally applicable). The confidence level is based
     * on whether the best re-ranked candidate meets the high-confidence semantic threshold.</p>
     *
     * @param effectNodeIds UUIDs of the current execution effect PhraseEmbeddingNodes
     * @return a {@link MatchResult} containing the best re-ranked match and its confidence, or empty if none meets the low threshold
     */
    public Optional<MatchResult> findBestMatch(String description, Set<UUID> effectNodeIds, Set<UUID> recentParentIds) {
        requireNonNull(description, "description");
        requireNonNull(effectNodeIds, "effectNodeIds");
        requireNonNull(recentParentIds, "recentParentIds");
        var queryEmbedding = embeddingService.embed(description);
        var matches = procedureRepository.findBySemanticSearch(queryEmbedding.vector(), config.getKnowledgeMatchTopN(), config.getKnowledgeMatchConfidenceLow());

        if (matches.isEmpty()) {
            LOG.debug("No procedure match found for description: '{}'", description);
            return Optional.empty();
        }

        var scoreById = matches.stream().collect(toMap(m -> m.procedure().id(), ProcedureMatch::score));
        var procedures = matches.stream().map(ProcedureMatch::procedure).toList();
        var reranked = reRankByStateCompatibility(procedures, scoreById, effectNodeIds, recentParentIds);
        var bestMatch = reranked.procedures().getFirst().procedure();

        var confidence = scoreById.get(bestMatch.id()) >= config.getKnowledgeMatchConfidenceHigh() ? MatchConfidence.HIGH : MatchConfidence.LOW;

        LOG.info("Found {} confidence match for '{}': procedure '{}' ({}) | all candidates: {}",
                confidence, description, bestMatch.description(), bestMatch.id(),
                reranked.procedures().stream().map(sp -> "'%s' [score=%.3f]".formatted(sp.procedure().description(), scoreById.get(sp.procedure().id()))).toList());
        return Optional.of(new MatchResult(bestMatch, confidence, reranked.procedures().stream().map(ScoredProcedure::procedure).toList(), reranked.wasDemoted()));
    }

    /**
     * Re-ranks candidates by state compatibility: proportion of preconditions semantically met by current effects.
     * Procedures with no preconditions always score {@code 1.0}. Within equal scores, higher satisfied count ranks first;
     * within equal counts, original semantic order is preserved (stable sort).
     * Uses Neo4j vector index queries via {@link PhraseEmbeddingRepository} instead of in-memory cosine similarity.
     * Logs a DEBUG message when the top semantic match is demoted by re-ranking, including which prerequisites were unmet.
     */
    private record RerankResult(List<ScoredProcedure> procedures, boolean wasDemoted) {}

    private RerankResult reRankByStateCompatibility(List<Procedure> candidates, Map<UUID, Double> scoreById,
                                                    Set<UUID> effectNodeIds, Set<UUID> recentParentIds) {
        record Scored(Procedure procedure, double proportion, int satisfied, int totalPrereqs,
                      int parentSharedCount, double stabilityPenalty, List<String> unmetPrereqs) {}

        double penaltyThreshold = config.getStabilityPenaltyThreshold();
        var candidateIds = candidates.stream().map(Procedure::id).toList();
        var contextById = procedureRepository.findCandidateContextBatch(candidateIds, recentParentIds)
                .stream().collect(toMap(CandidateContext::candidateId, ctx -> ctx));

        var scored = candidates.stream()
                .map(p -> {
                    var ctx = contextById.get(p.id());
                    double stabilityPenalty = 0.0;
                    if (ctx != null && ctx.stability().isPresent()) {
                        double currentStability = ctx.stability().get().stabilityScore();
                        if (currentStability < penaltyThreshold) {
                            stabilityPenalty = penaltyThreshold - currentStability;
                        }
                    }
                    int parentSharedCount = ctx != null ? ctx.sharedParentCount() : 0;
                    if (parentSharedCount > 0) {
                        LOG.debug("Ancestry boost: procedure '{}' shares {} parent(s) with recent execution context", p.description(), parentSharedCount);
                    }

                    var prereqNodes = phraseEmbeddingRepository.findPrerequisitesForProcedure(p.id());
                    int satisfied = 0;
                    List<String> unmetPrereqs = List.of();
                    double proportion = 1.0;
                    if (!prereqNodes.isEmpty()) {
                        if (!effectNodeIds.isEmpty()) {
                            double threshold = config.getKnowledgeMatchConfidenceHigh();
                            var partitioned = prereqNodes.stream()
                                    .collect(partitioningBy(pe -> phraseEmbeddingRepository.isPrerequisiteMetByEffects(pe.id(), effectNodeIds, threshold)));
                            satisfied = partitioned.get(true).size();
                            unmetPrereqs = partitioned.get(false).stream().map(PhraseEmbedding::phrase).toList();
                        } else {
                            unmetPrereqs = prereqNodes.stream().map(PhraseEmbedding::phrase).toList();
                        }
                        proportion = (double) satisfied / prereqNodes.size();
                    }
                    return new Scored(p, proportion, satisfied, prereqNodes.size(), parentSharedCount, stabilityPenalty, unmetPrereqs);
                })
                .toList();

        var reRanked = scored.stream()
                .sorted(Comparator.comparingDouble(Scored::proportion).reversed()
                        .thenComparing(Comparator.comparingInt(Scored::satisfied).reversed())
                        .thenComparing(Comparator.comparingInt(Scored::parentSharedCount).reversed())
                        .thenComparingDouble(Scored::stabilityPenalty))
                .map(s -> new ScoredProcedure(s.procedure(), s.satisfied(), s.totalPrereqs()))
                .toList();

        boolean wasDemoted = false;
        if (!candidates.isEmpty() && !reRanked.getFirst().procedure().id().equals(candidates.getFirst().id())) {
            wasDemoted = true;
            var topSemantic = candidates.getFirst();
            var scoredById = scored.stream().collect(toMap(s -> s.procedure().id(), s -> s));
            var topSemanticScored = scoredById.get(topSemantic.id());
            var selected = reRanked.getFirst().procedure();
            LOG.debug(
                "Top semantic match '{}' (score={}) was demoted by re-ranking: {}/{} prerequisites met, " +
                "unmet={}, effectNodeIds empty={}. Selected '{}' (score={}) instead with proportion={}",
                topSemantic.description(), scoreById.get(topSemantic.id()),
                topSemanticScored.satisfied(), topSemanticScored.totalPrereqs(),
                topSemanticScored.unmetPrereqs(), effectNodeIds.isEmpty(),
                selected.description(), scoreById.get(selected.id()),
                scoredById.get(selected.id()).proportion());
        }

        return new RerankResult(reRanked, wasDemoted);
    }

    /**
     * Returns the top semantic match for the given embedding without re-ranking.
     * Intended for lightweight lookups (e.g., ordering conflict detection) where re-ranking overhead is unnecessary.
     */
    public Optional<Procedure> findTopSemanticMatch(Embedding queryEmbedding) {
        requireNonNull(queryEmbedding, "queryEmbedding");
        var matches = procedureRepository.findBySemanticSearch(queryEmbedding.vector(), 1, config.getKnowledgeMatchConfidenceLow());
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst().procedure());
    }

    /**
     * Resolves a procedure into its flat ordered list of atomic steps.
     */
    public List<Procedure> resolveToAtomicSteps(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        return decompositionService.decompose(procedureId);
    }

    /**
     * Returns the top semantically-similar procedures for the given description at HIGH confidence
     * threshold, sorted by descending match score. Used for the existing-procedure lookup dialog.
     * Thread-safe: each call creates independent embedding and DB session.
     */
    public List<Procedure> findTopMatches(String description) {
        requireNonNull(description, "description");
        LOG.debug("findTopMatches query: '{}'", description);
        var queryEmbedding = embeddingService.embed(description);
        var matches = procedureRepository.findBySemanticSearch(queryEmbedding.vector(), config.getKnowledgeMatchTopN(),
                config.getKnowledgeMatchConfidenceHigh());
        LOG.debug("findTopMatches found {} result(s) for '{}': {}", matches.size(), description,
                matches.stream().map(m -> m.procedure().description()).toList());
        return matches.stream().map(ProcedureMatch::procedure).toList();
    }

    /**
     * Returns the UUID of the UI element targeted by an atomic procedure via
     * its TARGETS relationship.
     * This provides the execution shortcut: known atomic steps bypass
     * description-based element search.
     */
    public Optional<UUID> findTargetedUiElementId(UUID atomicProcedureId) {
        requireNonNull(atomicProcedureId, "atomicProcedureId");
        return procedureRepository.findTargetedUiElementId(atomicProcedureId);
    }

    /**
     * Returns all top-level procedures from the knowledge graph.
     * These are root entries not contained by any parent procedure.
     */
    public List<Procedure> getAllTopLevelProcedures() {
        return procedureRepository.findAllTopLevel();
    }

    /**
     * Invalidates decomposition cache on session end.
     */
    public void onSessionEnd() {
        invalidateCache("session end");
    }

    /**
     * Invalidates decomposition cache after new knowledge is ingested.
     */
    public void onKnowledgeIngested() {
        invalidateCache("knowledge ingestion");
    }

    private void invalidateCache(String reason) {
        decompositionService.invalidateCache();
        LOG.info("Knowledge service cache invalidated after {}", reason);
    }

    /**
     * Batch-embeds a list of texts. Intended for callers that need to pre-compute embeddings
     * for multiple descriptions at once (e.g., ordering conflict detection at execution start).
     */
    public List<Embedding> embedBatch(List<String> texts) {
        return embeddingService.embedBatch(texts);
    }

    public void updateTimingProfile(UUID id, long actualExecutionMs, long actualVerificationDelayMs) {
        requireNonNull(id, "id");
        procedureRepository.updateTimingProfile(id, actualExecutionMs, actualVerificationDelayMs);
    }

    /**
     * Returns all semantically matching procedures scored by prerequisite satisfaction.
     * Uses the LOW confidence threshold as the search floor, then re-ranks by state compatibility.
     * Intended for lookup dialogs that display matches to the user.
     */
    public List<ScoredProcedure> findTopRankedWithScores(String description, Set<UUID> effectNodeIds, Set<UUID> recentParentIds) {
        requireNonNull(description, "description");
        requireNonNull(effectNodeIds, "effectNodeIds");
        requireNonNull(recentParentIds, "recentParentIds");
        var queryEmbedding = embeddingService.embed(description);
        var matches = procedureRepository.findBySemanticSearch(
                queryEmbedding.vector(), config.getKnowledgeMatchTopN(), config.getKnowledgeMatchConfidenceLow());
        if (matches.isEmpty()) return List.of();
        var scoreById = matches.stream().collect(toMap(m -> m.procedure().id(), ProcedureMatch::score));
        var procedures = matches.stream().map(ProcedureMatch::procedure).toList();
        return reRankByStateCompatibility(procedures, scoreById, effectNodeIds, recentParentIds);
    }

    /**
     * Returns the effects (as PhraseEmbeddingNodes) of the given procedure from the graph.
     */
    public List<PhraseEmbedding> findEffectsForProcedure(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        return phraseEmbeddingRepository.findEffectsForProcedure(procedureId);
    }

    /**
     * Returns the prerequisites (as PhraseEmbeddingNodes) of the given procedure from the graph.
     */
    public List<PhraseEmbedding> findPrerequisitesForProcedure(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        return phraseEmbeddingRepository.findPrerequisitesForProcedure(procedureId);
    }

    ProcedureRepository procedureRepository() { return procedureRepository; }
    EmbeddingService embeddingService() { return embeddingService; }
    DecompositionService decompositionService() { return decompositionService; }
    PhraseEmbeddingRepository phraseEmbeddingRepository() { return phraseEmbeddingRepository; }

    /**
     * Finds a procedure by its UUID.
     */
    public Optional<Procedure> findById(UUID procedureId) {
        requireNonNull(procedureId, "procedureId");
        return procedureRepository.findById(procedureId);
    }

    /**
     * Returns the direct children of a composite procedure.
     */
    public List<Procedure> getChildren(UUID parentId) {
        requireNonNull(parentId, "parentId");
        return procedureRepository.findChildrenOrdered(parentId);
    }

    /**
     * Returns the parent procedures of the given procedure.
     */
    public List<Procedure> findParents(UUID childId) {
        requireNonNull(childId, "childId");
        return procedureRepository.findParents(childId);
    }

    /**
     * A procedure with its prerequisite-satisfaction scores from re-ranking.
     */
    public record ScoredProcedure(Procedure procedure, int satisfied, int totalPrereqs) {}

    /**
     * Confidence level of a semantic match.
     */
    public enum MatchConfidence {
        /**
         * Score >= high confidence threshold — match can be auto-executed.
         */
        HIGH,
        /**
         * Score >= low but < high threshold — caller decides (e.g., ask user to
         * confirm).\
         */
        LOW
    }

    /**
     * A matched procedure with its confidence level.
     */
    public record MatchResult(Procedure procedure, MatchConfidence confidence, List<Procedure> allMatches, boolean wasDemoted) {
        public MatchResult {
            requireNonNull(procedure, "procedure");
            requireNonNull(confidence, "confidence");
            requireNonNull(allMatches, "allMatches");
        }
    }
}