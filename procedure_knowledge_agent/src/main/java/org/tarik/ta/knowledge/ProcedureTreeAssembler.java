/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
package org.tarik.ta.knowledge;

import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.dto.ProcedureNode;
import org.tarik.ta.dto.ProcedureSummary;
import org.tarik.ta.dto.UiElementView;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.SubtreeEntry;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.TargetedUiElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Comparator.comparingInt;

/**
 * Builds the original procedure data of the knowledge graph in the form the model and the caller receive: the whole tree of a procedure
 * and the own data of single procedures.
 */
@Singleton
public class ProcedureTreeAssembler {
    private final ProcedureRepository procedureRepository;
    private final KnowledgeAgentConfig config;

    public ProcedureTreeAssembler(ProcedureRepository procedureRepository, KnowledgeAgentConfig config) {
        this.procedureRepository = procedureRepository;
        this.config = config;
    }

    /**
     * Returns the tree of the procedure down to its atomic procedures, capped in depth and size, or nothing when it does not exist.
     */
    public Optional<ProcedureNode> assembleTree(@NotNull UUID procedureId) {
        var entries = procedureRepository.findSubtree(procedureId, config.getMaxTreeDepth(), config.getMaxTreeNodes());
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        var childrenByParent = groupChildrenByParent(entries);
        var root = entries.stream().filter(entry -> entry.parentId() == null).findFirst().orElse(entries.getFirst());
        return Optional.of(toNode(root, childrenByParent, new HashSet<>()));
    }

    /**
     * Returns the own data of each procedure, with the UI element of the atomic ones and the score of a search hit, if given.
     */
    public List<ProcedureSummary> summarize(@NotNull List<Procedure> procedures, @NotNull Map<UUID, Double> scores) {
        var atomicIds = procedures.stream().filter(Procedure::isAtomic).map(Procedure::id).toList();
        var uiElements = procedureRepository.findTargetedUiElements(atomicIds);
        return procedures.stream()
                .map(procedure -> new ProcedureSummary(procedure.id().toString(), scores.get(procedure.id()), procedure.description(),
                        procedure.isAtomic(), procedure.isPrecondition(), toView(uiElements.get(procedure.id())), procedure.additionalInfo(),
                        procedure.prerequisites(), procedure.effects(), procedure.negatedEffects()))
                .toList();
    }

    // A parent reached through several paths returns its children once per path, so they are kept once per parent and position.
    private static Map<UUID, List<SubtreeEntry>> groupChildrenByParent(List<SubtreeEntry> entries) {
        var childrenByParent = new HashMap<UUID, List<SubtreeEntry>>();
        var seenLinks = new HashSet<String>();
        for (var entry : entries) {
            var parentId = entry.parentId();
            if (parentId != null && seenLinks.add("%s>%s#%d".formatted(parentId, entry.procedure().id(), entry.sequence()))) {
                childrenByParent.computeIfAbsent(parentId, _ -> new ArrayList<>()).add(entry);
            }
        }
        childrenByParent.values().forEach(children -> children.sort(comparingInt(SubtreeEntry::sequence)));
        return childrenByParent;
    }

    // The ancestors on the current path guard against a CONTAINS cycle, which would otherwise expand without end.
    private static ProcedureNode toNode(SubtreeEntry entry, Map<UUID, List<SubtreeEntry>> childrenByParent, Set<UUID> ancestors) {
        var procedure = entry.procedure();
        ancestors.add(procedure.id());
        var children = new ArrayList<ProcedureNode>();
        for (var child : childrenByParent.getOrDefault(procedure.id(), List.of())) {
            if (!ancestors.contains(child.procedure().id())) {
                children.add(toNode(child, childrenByParent, ancestors));
            }
        }
        ancestors.remove(procedure.id());
        var childrenTruncated = !procedure.isAtomic() && children.size() < entry.childCount();
        return new ProcedureNode(procedure.id().toString(), procedure.description(), procedure.isAtomic(), procedure.isPrecondition(),
                procedure.optional(), procedure.testData(), procedure.expectedResults(), procedure.prerequisites(), procedure.effects(),
                procedure.negatedEffects(), procedure.additionalInfo(), entry.successCount(), toView(entry.uiElement()), children,
                childrenTruncated);
    }

    private static @Nullable UiElementView toView(@Nullable TargetedUiElement element) {
        return element == null ? null
                : new UiElementView(element.name(), element.ownDescription(), element.anchorsDescription(), element.parentElementSummary());
    }
}
