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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.dto.EditorChild;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Finds the child steps of an edited procedure for which the knowledge graph already holds a similar procedure. */
@UiAgentRequestScope
public class SimilarChildrenService {
    private static final Logger LOG = LoggerFactory.getLogger(SimilarChildrenService.class);

    private final KnowledgeService knowledgeService;

    public SimilarChildrenService(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * Returns the indices of the steps with a similar procedure that is not among {@code excludedIds}, i.e. not already linked as
     * another step; blocks until every step is checked.
     */
    public Set<Integer> findStepsWithSimilarProcedures(List<EditorChild> steps, Set<UUID> excludedIds) {
        Set<Integer> stepsWithSimilarProcedures = new HashSet<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Map.Entry<Integer, Future<Boolean>>> futures = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                final String description = steps.get(i).description();
                futures.add(Map.entry(i, executor.submit(() ->
                        knowledgeService.findTopMatches(description).stream()
                                .anyMatch(p -> !excludedIds.contains(p.id())))));
            }
            for (var entry : futures) {
                try {
                    if (entry.getValue().get()) {
                        stepsWithSimilarProcedures.add(entry.getKey());
                    }
                } catch (Exception e) {
                    LOG.warn("Similarity check failed for step at index {}", entry.getKey(), e);
                }
            }
        }
        return Set.copyOf(stepsWithSimilarProcedures);
    }
}
