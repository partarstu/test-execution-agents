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
