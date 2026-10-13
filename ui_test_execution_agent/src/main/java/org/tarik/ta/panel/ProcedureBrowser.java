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
package org.tarik.ta.panel;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ProcedureHit;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ProcedureHits;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.ScoredProcedure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.tarik.ta.knowledge_graph.ProcedureSelectionRules.visibleResults;
import static org.tarik.ta.panel.PromptInput.MAX_QUERY_LENGTH;
import static org.tarik.ta.panel.PromptInput.text;

/** Searches the procedures one prompt offers and remembers every hit, so an answer can only pick a procedure it was shown. */
final class ProcedureBrowser {
    private final KnowledgeService knowledgeService;
    private final Map<UUID, Procedure> offered = new ConcurrentHashMap<>();

    ProcedureBrowser(@NotNull KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * The procedures the search may show: none of {@code excludedIds}, prerequisites scored by {@code effectNodeIds}, re-ranked by
     * {@code recentParentIds} and, unless all types are asked for, only of the type of the item they are looked up for.
     */
    record SearchScope(Set<UUID> excludedIds, Set<UUID> effectNodeIds, Set<UUID> recentParentIds, boolean precondition) {
    }

    @NotNull ProcedureHits search(String query, boolean showAllTypes, @NotNull SearchScope scope) {
        var text = text(query, MAX_QUERY_LENGTH, "The search text").trim();
        if (text.isEmpty()) {
            return new ProcedureHits(List.of());
        }
        var found = knowledgeService.findTopRankedWithScores(text, scope.effectNodeIds(), scope.recentParentIds());
        var visible = visibleResults(found, scope.excludedIds(), scope.precondition(), showAllTypes);
        visible.forEach(scored -> offered.put(scored.procedure().id(), scored.procedure()));
        return new ProcedureHits(visible.stream().map(ProcedureBrowser::hitOf).toList());
    }

    @NotNull Optional<Procedure> offered(@NotNull UUID procedureId) {
        return Optional.ofNullable(offered.get(procedureId));
    }

    private static ProcedureHit hitOf(ScoredProcedure scored) {
        var procedure = scored.procedure();
        return new ProcedureHit(procedure.id(), procedure.description(), procedure.isAtomic(), procedure.isPrecondition(),
                scored.satisfied(), scored.totalPrereqs());
    }
}
