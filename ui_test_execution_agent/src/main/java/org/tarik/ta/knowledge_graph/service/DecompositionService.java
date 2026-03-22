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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.UiTestAgentConfig.getKnowledgeMaxDepth;

/**
 * Hierarchical decomposition from composite procedures to atomic leaves.
 * Recursively traverses CONTAINS relationships, stopping at atomic nodes.
 *
 * <p>Results are cached per session in a {@link ConcurrentHashMap} for repeated access.
 * Cache is invalidated on session end or after knowledge ingestion.</p>
 */
public class DecompositionService {
    private static final Logger LOG = LoggerFactory.getLogger(DecompositionService.class);

    private final ProcedureRepository repository;
    private final ConcurrentHashMap<UUID, List<Procedure>> cache = new ConcurrentHashMap<>();

    public DecompositionService(ProcedureRepository repository) {
        this.repository = requireNonNull(repository, "repository");
    }

    /**
     * Decomposes a procedure into a flat ordered list of atomic leaves.
     * If the root is already atomic, returns a singleton list containing it.
     * Results are cached by root ID for the current session.
     *
     * @param rootId the UUID of the root procedure to decompose
     * @return flat ordered list of atomic procedures (depth-first, sequence-ordered)
     * @throws IllegalStateException if the max depth is exceeded
     */
    public List<Procedure> decompose(UUID rootId) {
        requireNonNull(rootId, "rootId");
        return cache.computeIfAbsent(rootId, id -> {
            LOG.debug("Decomposing procedure {} (cache miss)", id);
            var root = repository.findById(id).orElseThrow(() ->
                    new IllegalStateException("Procedure not found: %s".formatted(id)));
            var atomics = new ArrayList<Procedure>();
            decomposeRecursive(root, atomics, 0, getKnowledgeMaxDepth());
            LOG.info("Decomposed procedure '{}' ({}) into {} atomic step(s)", root.description(), id, atomics.size());
            return List.copyOf(atomics);
        });
    }

    /**
     * Invalidates the entire decomposition cache (e.g., on session end).
     */
    public void invalidateCache() {
        cache.clear();
        LOG.debug("Decomposition cache fully invalidated");
    }

    private void decomposeRecursive(Procedure current, List<Procedure> atomics, int depth, int maxDepth) {
        if (depth > maxDepth) {
            throw new IllegalStateException(
                    "Max decomposition depth (%d) exceeded at procedure '%s' (%s). Check for circular CONTAINS relationships."
                            .formatted(maxDepth, current.description(), current.id()));
        }

        if (current.isAtomic()) {
            atomics.add(current);
            return;
        }

        var children = repository.findChildrenOrdered(current.id());
        if (children.isEmpty()) {
            LOG.warn("Composite procedure '{}' ({}) has no children — treating as atomic", current.description(), current.id());
            atomics.add(current);
            return;
        }

        for (var child : children) {
            decomposeRecursive(child, atomics, depth + 1, maxDepth);
        }
    }
}