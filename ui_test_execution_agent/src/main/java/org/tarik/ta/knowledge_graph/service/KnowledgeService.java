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
import static java.util.stream.Collectors.groupingBy;
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
        return Optional.of(new MatchResult(bestMatch, confidence, reranked.procedures(), reranked.wasDemoted(),
                reranked.demotedDueToPrerequisites(), reranked.selectedHasLowStability()));
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
        return Optional.of(new MatchResult(bestMatch, confidence, reranked.procedures(), reranked.wasDemoted(),
                reranked.demotedDueToPrerequisites(), reranked.selectedHasLowStability()));
    }

    /**
     * Re-ranks candidates by state compatibility: proportion of preconditions semantically met by current effects.
     * Procedures with no preconditions always score {@code 1.0}. Within equal scores, higher satisfied count ranks first;
     * within equal counts, original semantic order is preserved (stable sort).
     * Uses Neo4j vector index queries via {@link PhraseEmbeddingRepository} instead of in-memory cosine similarity.
     * Logs a DEBUG message when the top semantic match is demoted by re-ranking, including which prerequisites were unmet.
     */
    private record RerankResult(List<ScoredProcedure> procedures, boolean wasDemoted,
                                boolean demotedDueToPrerequisites, boolean selectedHasLowStability) {}

    private RerankResult reRankByStateCompatibility(List<Procedure> candidates, Map<UUID, Double> scoreById,
                                                    Set<UUID> effectNodeIds, Set<UUID> recentParentIds) {
        record Scored(Procedure procedure, double proportion, int satisfied, int totalPrereqs,
                      int parentSharedCount, double stabilityPenalty, List<String> unmetPrereqs) {}

        double penaltyThreshold = config.getStabilityPenaltyThreshold();
        var candidateIds = candidates.stream().map(Procedure::id).toList();
        var contextById = procedureRepository.findCandidateContextBatch(candidateIds, recentParentIds)
                .stream().collect(toMap(CandidateContext::candidateId, ctx -> ctx));

        var satisfactionResults = phraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(
                candidateIds, effectNodeIds, config.getKnowledgeMatchConfidenceHigh());
        var satisfactionByProc = satisfactionResults.stream()
                .collect(groupingBy(PhraseEmbeddingRepository.PrerequisiteSatisfaction::procedureId));

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

                    var results = satisfactionByProc.getOrDefault(p.id(), List.of());
                    int totalPrereqs = results.size();
                    int satisfied = (int) results.stream().filter(PhraseEmbeddingRepository.PrerequisiteSatisfaction::isMet).count();
                    List<String> unmetPrereqs = results.stream()
                            .filter(r -> !r.isMet())
                            .map(PhraseEmbeddingRepository.PrerequisiteSatisfaction::phrase)
                            .toList();
                    double proportion = totalPrereqs == 0 ? 1.0 : (double) satisfied / totalPrereqs;

                    return new Scored(p, proportion, satisfied, totalPrereqs, parentSharedCount, stabilityPenalty, unmetPrereqs);
                })
                .toList();

        var scoredById = scored.stream().collect(toMap(s -> s.procedure().id(), s -> s));

        // Sort: prerequisite ratio first (gates feasibility), then semantic score, then ancestry.
        var reRanked = scored.stream()
                .sorted(Comparator.comparingDouble(Scored::proportion).reversed()
                        .thenComparing(Comparator.comparingDouble((Scored s) -> scoreById.get(s.procedure().id())).reversed())
                        .thenComparing(Comparator.comparingInt(Scored::parentSharedCount).reversed()))
                .map(s -> new ScoredProcedure(s.procedure(), s.satisfied(), s.totalPrereqs()))
                .toList();

        boolean wasDemoted = false;
        boolean demotedDueToPrerequisites = false;
        if (!candidates.isEmpty() && !reRanked.getFirst().procedure().id().equals(candidates.getFirst().id())) {
            wasDemoted = true;
            var topSemantic = candidates.getFirst();
            var topSemanticScored = scoredById.get(topSemantic.id());
            demotedDueToPrerequisites = topSemanticScored.proportion() < 1.0;
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

        boolean selectedHasLowStability = !reRanked.isEmpty() &&
                scoredById.get(reRanked.getFirst().procedure().id()).stabilityPenalty() > 0;
        if (selectedHasLowStability) {
            LOG.warn("Selected procedure '{}' has low element-location stability (threshold={})",
                    reRanked.getFirst().procedure().description(), penaltyThreshold);
        }

        return new RerankResult(reRanked, wasDemoted, demotedDueToPrerequisites, selectedHasLowStability);
    }

    /**
     * Returns the top semantic match for the given embedding without re-ranking, only when the score meets
     * the HIGH confidence threshold. Using HIGH confidence ensures that steps with no corresponding procedure
     * in the DB are not falsely matched to unrelated procedures (which could happen at the LOW threshold).
     * Intended for lightweight lookups (e.g., ordering conflict detection) where re-ranking overhead is unnecessary.
     */
    public Optional<Procedure> findTopSemanticMatch(Embedding queryEmbedding) {
        requireNonNull(queryEmbedding, "queryEmbedding");
        var matches = procedureRepository.findBySemanticSearch(queryEmbedding.vector(), 1, config.getKnowledgeMatchConfidenceHigh());
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
        return reRankByStateCompatibility(procedures, scoreById, effectNodeIds, recentParentIds).procedures();
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
    public record MatchResult(Procedure procedure, MatchConfidence confidence, List<ScoredProcedure> allMatches,
                              boolean wasDemoted, boolean demotedDueToPrerequisites, boolean selectedHasLowStability) {
        public MatchResult {
            requireNonNull(procedure, "procedure");
            requireNonNull(confidence, "confidence");
            requireNonNull(allMatches, "allMatches");
        }
    }
}