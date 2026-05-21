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
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import org.tarik.ta.UiTestAgentConfig;

/**
 * Hierarchical decomposition from composite procedures to atomic leaves.
 * Recursively traverses CONTAINS relationships, stopping at atomic nodes.
 *
 * <p>Results are cached per session in a {@link ConcurrentHashMap} for repeated access.
 * Cache is invalidated on session end or after knowledge ingestion.</p>
 */
@Singleton
public class DecompositionService {
    private static final Logger LOG = LoggerFactory.getLogger(DecompositionService.class);

    private final ProcedureRepository repository;
    private final UiTestAgentConfig config;
    private final ConcurrentHashMap<UUID, List<Procedure>> cache = new ConcurrentHashMap<>();

    public DecompositionService(ProcedureRepository repository, UiTestAgentConfig config) {
        this.repository = requireNonNull(repository, "repository");
        this.config = requireNonNull(config, "config");
    }

    /**
     * Decomposes a procedure into a flat ordered list of atomic leaves.
     * If the root is already atomic, returns a singleton list containing it.
     * Results are cached by root ID for the current session.
     *
     * @param rootId the UUID of the root procedure to decompose
     * @return flat ordered list of atomic procedures (depth-first, sequence-ordered)
     * @throws IllegalStateException if the max depth is exceeded, or if a composite procedure has no children
     */
    public List<Procedure> decompose(UUID rootId) {
        requireNonNull(rootId, "rootId");
        return cache.computeIfAbsent(rootId, id -> {
            LOG.debug("Decomposing procedure {} (cache miss)", id);
            var root = repository.findById(id).orElseThrow(() ->
                    new IllegalStateException("Procedure not found: %s".formatted(id)));
            var atomics = new ArrayList<Procedure>();
            decomposeRecursive(root, atomics, 0, config.getKnowledgeMaxDepth(), new ArrayList<>());
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

    private void decomposeRecursive(Procedure current, List<Procedure> atomics, int depth, int maxDepth,
                                    List<Procedure> path) {
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
            var fullPath = new ArrayList<>(path);
            fullPath.add(current);
            throw new IllegalStateException(formatNoChildrenError(current, fullPath));
        }

        var nextPath = new ArrayList<>(path);
        nextPath.add(current);
        for (var child : children) {
            decomposeRecursive(child, atomics, depth + 1, maxDepth, nextPath);
        }
    }

    private static String formatNoChildrenError(Procedure problematic, List<Procedure> fullPath) {
        var sb = new StringBuilder();
        sb.append("Composite procedure '%s' (%s) has no children. Add its sub-steps in the procedure editor."
                .formatted(problematic.description(), problematic.id()));
        if (fullPath.size() > 1) {
            sb.append("\n\nExecution graph:");
            for (int i = 0; i < fullPath.size(); i++) {
                var p = fullPath.get(i);
                var suffix = (i == fullPath.size() - 1) ? "  \u2190 no children" : "";
                sb.append("\n%s\u2192 %s (%s)%s".formatted("  ".repeat(i), p.description(), p.id(), suffix));
            }
        }
        return sb.toString();
    }
}