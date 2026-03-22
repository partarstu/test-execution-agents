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

import dev.langchain4j.data.embedding.Embedding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.knowledge_graph.Neo4jConnectionManager.getSession;

/**
 * Service for ingesting collected procedures into the knowledge graph.
 *
 * <p>Accepts {@link IngestionNode} trees produced by the dialog layer. The ingestion process:</p>
 * <ol>
 *   <li>Collects all descriptions needing embeddings</li>
 *   <li>Generates embeddings in batch</li>
 *   <li>Persists Procedure nodes via the embedding store</li>
 *   <li>Creates CONTAINS relationships for parent-child hierarchies</li>
 *   <li>Creates TARGETS relationships for atomic steps with UI elements</li>
 *   <li>Batch-creates PhraseEmbedding nodes for all prerequisites and effects</li>
 *   <li>Invalidates the decomposition cache</li>
 * </ol>
 */
public class KnowledgeIngestionService {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final ProcedureRepository procedureRepository;
    private final EmbeddingService embeddingService;
    private final DecompositionService decompositionService;
    private final SatisfiesEdgeRepository satisfiesEdgeRepository;
    private final FailureContextService failureContextService;
    private final PhraseEmbeddingRepository phraseEmbeddingRepository;

    public KnowledgeIngestionService(ProcedureRepository procedureRepository,
                                     EmbeddingService embeddingService,
                                     DecompositionService decompositionService,
                                     SatisfiesEdgeRepository satisfiesEdgeRepository,
                                     FailureContextService failureContextService,
                                     PhraseEmbeddingRepository phraseEmbeddingRepository) {
        this.procedureRepository = requireNonNull(procedureRepository, "procedureRepository");
        this.embeddingService = requireNonNull(embeddingService, "embeddingService");
        this.decompositionService = requireNonNull(decompositionService, "decompositionService");
        this.satisfiesEdgeRepository = requireNonNull(satisfiesEdgeRepository, "satisfiesEdgeRepository");
        this.failureContextService = requireNonNull(failureContextService, "failureContextService");
        this.phraseEmbeddingRepository = requireNonNull(phraseEmbeddingRepository, "phraseEmbeddingRepository");
    }

    /**
     * Ingests a complete procedure tree into the knowledge graph.
     *
     * @param node the ingestion node tree from the dialog
     * @return the UUID of the root procedure created or referenced
     */
    public UUID ingest(IngestionNode node) {
        requireNonNull(node, "node");
        String description = nodeDescription(node);
        LOG.info("Starting ingestion of procedure: '{}'", description);

        try {
            List<String> descriptions = collectDescriptions(node);
            LOG.debug("Collected {} description(s) for embedding", descriptions.size());

            List<Embedding> embeddings = embeddingService.embedBatch(descriptions);
            var embeddingIterator = embeddings.iterator();

            var pendingNodes = new ArrayList<PendingNode>();
            var pendingContains = new ArrayList<PendingContains>();
            var pendingTargets = new ArrayList<PendingTargets>();
            var pendingPhraseTexts = new ArrayList<PendingPhraseTexts>();
            UUID rootId = ingestRecursive(node, null, 0, embeddingIterator, pendingNodes, pendingContains, pendingTargets, pendingPhraseTexts);

            // Save all procedure nodes in a single batch before creating relationships
            procedureRepository.saveAll(
                    pendingNodes.stream().map(PendingNode::procedure).toList(),
                    pendingNodes.stream().map(n -> n.embedding().vector()).toList()
            );

            try (var session = getSession()) {
                session.executeWriteWithoutResult(tx -> {
                    pendingContains.forEach(r -> procedureRepository.linkToParent(r.childId(), r.parentId(), r.sequence(), tx));
                    pendingTargets.forEach(r -> procedureRepository.linkToUiElement(r.spId(), r.elId(), tx));
                });
            }

            // Batch-embed all phrase texts across the whole tree in one call, then create nodes
            createPhraseNodesForTree(pendingPhraseTexts);

            decompositionService.invalidateCache();
            LOG.info("Successfully ingested procedure '{}' (id={}) and invalidated decomposition cache", description, rootId);
            return rootId;

        } catch (Exception e) {
            LOG.error("Failed to ingest procedure '{}': {}", description, e.getMessage(), e);
            throw new RuntimeException("Knowledge ingestion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing complete procedure tree in the knowledge graph.
     *
     * @param existingId the UUID of the existing Procedure to update
     * @param node       the ingestion node with updated values
     */
    public void update(UUID existingId, IngestionNode node) {
        requireNonNull(node, "node");
        requireNonNull(existingId, "existingId");
        String description = nodeDescription(node);
        LOG.info("Starting update of procedure: '{}' (id={})", description, existingId);

        try {
            List<String> descriptions = collectDescriptions(node);
            List<Embedding> embeddings = embeddingService.embedBatch(descriptions);
            var embeddingIterator = embeddings.iterator();

            updateRecursive(existingId, node, null, 0, embeddingIterator);

            decompositionService.invalidateCache();
            failureContextService.cleanupOrphanedFailureContexts();
            LOG.info("Successfully updated procedure '{}' (id={})", description, existingId);
        } catch (Exception e) {
            LOG.error("Failed to update procedure '{}': {}", description, e.getMessage(), e);
            throw new RuntimeException("Knowledge update failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ingests multiple procedure trees in sequence.
     */
    public void ingestAll(List<IngestionNode> nodes) {
        requireNonNull(nodes, "nodes");
        LOG.info("Starting batch ingestion of {} procedure(s)", nodes.size());
        for (IngestionNode node : nodes) {
            ingest(node);
        }
        LOG.info("Batch ingestion completed successfully");
    }

    private List<String> collectDescriptions(IngestionNode node) {
        List<String> descriptions = new ArrayList<>();
        collectDescriptionsRecursive(node, descriptions);
        return descriptions;
    }

    private void collectDescriptionsRecursive(IngestionNode node, List<String> descriptions) {
        switch (node) {
            case IngestionNode.ExistingReference ignored -> {} // referenced procedures don't need a new embedding
            case IngestionNode.NewProcedure np -> {
                descriptions.add(np.procedure().description());
                for (IngestionNode child : np.children()) {
                    collectDescriptionsRecursive(child, descriptions);
                }
            }
        }
    }

    /**
     * Phase 1 of ingestion: recursively collects all new Procedure nodes with their embeddings,
     * all pending CONTAINS/TARGETS relationships, and all pending phrase texts — without touching the database.
     * Phase 2 (in {@link #ingest}) saves nodes in a single batch, then creates relationships and phrase nodes.
     */
    private UUID ingestRecursive(IngestionNode node, UUID parentId, int sequence,
                                 Iterator<Embedding> embeddingIterator,
                                 List<PendingNode> pendingNodes,
                                 List<PendingContains> pendingContains,
                                 List<PendingTargets> pendingTargets,
                                 List<PendingPhraseTexts> pendingPhraseTexts) {
        return switch (node) {
            case IngestionNode.ExistingReference ref -> {
                if (parentId != null) {
                    pendingContains.add(new PendingContains(ref.existingProcedureId(), parentId, sequence));
                }
                yield ref.existingProcedureId();
            }
            case IngestionNode.NewProcedure np -> {
                Procedure procedure = np.procedure();
                Embedding embedding = embeddingIterator.next();
                pendingNodes.add(new PendingNode(procedure, embedding));
                pendingPhraseTexts.add(new PendingPhraseTexts(procedure.id(), procedure.prerequisites(), procedure.effects()));
                LOG.debug("Collected Procedure '{}' (id={}, atomic={})", procedure.description(), procedure.id(), procedure.isAtomic());

                if (parentId != null) {
                    pendingContains.add(new PendingContains(procedure.id(), parentId, sequence));
                }
                if (np.procedure().isAtomic() && np.targetUiElementId() != null) {
                    pendingTargets.add(new PendingTargets(procedure.id(), np.targetUiElementId()));
                }

                int childSequence = 0;
                for (IngestionNode child : np.children()) {
                    ingestRecursive(child, procedure.id(), childSequence++, embeddingIterator, pendingNodes, pendingContains, pendingTargets, pendingPhraseTexts);
                }
                yield procedure.id();
            }
        };
    }

    /**
     * Batch-embeds all phrase texts across the whole procedure tree in one call, then creates
     * {@code PhraseEmbedding} nodes and links them to their procedures.
     */
    private void createPhraseNodesForTree(List<PendingPhraseTexts> pendingPhraseTexts) {
        // Collect all phrase strings in a single flat list for one batch embedding call
        var allPhrases = new ArrayList<String>();
        for (var pp : pendingPhraseTexts) {
            allPhrases.addAll(pp.prerequisites());
            allPhrases.addAll(pp.effects());
        }
        if (allPhrases.isEmpty()) {
            return;
        }
        var phraseEmbeddings = embeddingService.embedBatch(allPhrases);

        // Distribute embeddings back to each procedure's prerequisites and effects
        int offset = 0;
        for (var pp : pendingPhraseTexts) {
            var prereqNodes = buildPhraseNodes(pp.prerequisites(), phraseEmbeddings, offset, PhraseType.PREREQUISITE);
            offset += pp.prerequisites().size();
            var effectNodes = buildPhraseNodes(pp.effects(), phraseEmbeddings, offset, PhraseType.EFFECT);
            offset += pp.effects().size();

            if (!prereqNodes.isEmpty() || !effectNodes.isEmpty()) {
                phraseEmbeddingRepository.saveBatchForProcedure(pp.procedureId(), prereqNodes, effectNodes);
            }
        }
    }

    private static List<PhraseEmbedding> buildPhraseNodes(List<String> phrases, List<Embedding> embeddings, int offset, PhraseType type) {
        var result = new ArrayList<PhraseEmbedding>(phrases.size());
        for (int i = 0; i < phrases.size(); i++) {
            result.add(new PhraseEmbedding(UUID.randomUUID(), phrases.get(i), embeddings.get(offset + i).vector(), type));
        }
        return result;
    }

    private record PendingNode(Procedure procedure, Embedding embedding) {}

    private record PendingContains(UUID childId, UUID parentId, int sequence) {}

    private record PendingTargets(UUID spId, UUID elId) {}

    private record PendingPhraseTexts(UUID procedureId, List<String> prerequisites, List<String> effects) {}

    private void updateRecursive(UUID existingId, IngestionNode node, UUID parentId, int sequence,
                                 Iterator<Embedding> embeddingIterator) {
        switch (node) {
            case IngestionNode.ExistingReference ref -> {
                if (parentId != null) {
                    procedureRepository.linkToParent(ref.existingProcedureId(), parentId, sequence);
                }
            }
            case IngestionNode.NewProcedure np -> {
                UUID currentId = existingId;
                Procedure procedure;

                if (currentId != null) {
                    var existingOpt = procedureRepository.findById(currentId);
                    if (existingOpt.isPresent()) {
                        procedure = np.procedure().withId(currentId, existingOpt.get().createdAt());
                    } else {
                        procedure = np.procedure();
                        currentId = procedure.id();
                    }
                } else {
                    procedure = np.procedure();
                    currentId = procedure.id();
                }

                Embedding embedding = embeddingIterator.next();

                if (existingId != null) {
                    var candidateUiElementIds = procedureRepository.findAllTargetedUiElementIds(currentId);
                    procedureRepository.deleteTargets(currentId);
                    final UUID idForTx = currentId;
                    try (var session = getSession()) {
                        session.executeWriteWithoutResult(tx -> {
                            procedureRepository.deleteDescendants(idForTx, tx);
                            satisfiesEdgeRepository.deleteSatisfiesEdges(idForTx, tx);
                        });
                    }
                    procedureRepository.update(procedure, embedding.vector());
                    procedureRepository.deleteUiElementsIfOrphaned(candidateUiElementIds);
                } else {
                    if (parentId != null) {
                        procedureRepository.saveWithParent(procedure, embedding.vector(), parentId, sequence);
                    } else {
                        procedureRepository.save(procedure, embedding.vector());
                    }
                }

                // Recreate phrase embedding nodes for the updated procedure
                phraseEmbeddingRepository.deleteForProcedure(procedure.id());
                createAndSavePhraseNodes(procedure);

                if (np.procedure().isAtomic() && np.targetUiElementId() != null) {
                    procedureRepository.linkToUiElement(currentId, np.targetUiElementId());
                }

                int childSequence = 0;
                for (IngestionNode child : np.children()) {
                    updateRecursive(null, child, currentId, childSequence++, embeddingIterator);
                }
            }
        }
    }

    private void createAndSavePhraseNodes(Procedure procedure) {
        var allPhrases = new ArrayList<String>(procedure.prerequisites().size() + procedure.effects().size());
        allPhrases.addAll(procedure.prerequisites());
        allPhrases.addAll(procedure.effects());
        if (allPhrases.isEmpty()) {
            return;
        }
        var embeddings = embeddingService.embedBatch(allPhrases);
        var prereqNodes = buildPhraseNodes(procedure.prerequisites(), embeddings, 0, PhraseType.PREREQUISITE);
        var effectNodes = buildPhraseNodes(procedure.effects(), embeddings, procedure.prerequisites().size(), PhraseType.EFFECT);
        phraseEmbeddingRepository.saveBatchForProcedure(procedure.id(), prereqNodes, effectNodes);
    }

    private static String nodeDescription(IngestionNode node) {
        return switch (node) {
            case IngestionNode.NewProcedure np -> np.procedure().description();
            case IngestionNode.ExistingReference ref -> ref.existingProcedureId().toString();
        };
    }
}
