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
package org.tarik.ta.user_dialogs.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class SimilaritySearchTask {
    private static final Logger LOG = LoggerFactory.getLogger(SimilaritySearchTask.class);

    public static void execute(KnowledgeService knowledgeService, List<ChildProcedureInDialog> steps, Set<UUID> excludedIds, Consumer<Set<Integer>> onComplete) {
        Thread.ofVirtual().start(() -> {
            Map<Integer, Boolean> badgeResults = new HashMap<>();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Map.Entry<Integer, Future<Boolean>>> futures = new ArrayList<>();
                for (int i = 0; i < steps.size(); i++) {
                    final int idx = i;
                    final String desc = steps.get(i).description();
                    // Only show badge if there's a selectable match (not already linked as another step)
                    futures.add(Map.entry(idx, executor.submit(() ->
                            knowledgeService.findTopMatches(desc).stream()
                                    .anyMatch(p -> !excludedIds.contains(p.id())))));
                }
                for (var entry : futures) {
                    try {
                        badgeResults.put(entry.getKey(), entry.getValue().get());
                    } catch (Exception e) {
                        LOG.warn("Similarity check failed for step at index {}", entry.getKey(), e);
                        badgeResults.put(entry.getKey(), false);
                    }
                }
            }
            SwingUtilities.invokeLater(() -> {
                Set<Integer> results = new HashSet<>();
                badgeResults.forEach((idx, hasSimilar) -> {
                    if (hasSimilar) {
                        results.add(idx);
                    }
                });
                onComplete.accept(results);
            });
        });
    }
}
