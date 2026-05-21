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
package org.tarik.ta.knowledge_graph.execution;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository.UnsatisfiedPrerequisite;

import java.util.List;

/**
 * Encapsulates the raw prefetched data for the next atomic procedure.
 * The caller assembles the final {@link AtomicStepExecutionContext} from these fields
 * because {@code isSingle}/{@code isLast} are only known at consumption time.
 */
public record PrefetchedAtomicContext(
        Procedure predictedAtomicProcedure,
        ExecutionItem predictedExecutionItem,
        @Nullable ExecutionStateSnapshot predictedStateSnapshot,
        @Nullable String targetElementId,
        @Nullable UiElement targetElement,
        @Nullable ElementLocationHistory locationHistory,
        List<String> failureHints,
        List<PhraseEmbedding> declaredEffects,
        List<UnsatisfiedPrerequisite> unsatisfiedPrerequisites,
        PrefetchMetadata metadata
) {
    /**
     * Metadata tracking which parts of the prefetch were cache hits vs. misses.
     */
    public record PrefetchMetadata(
            boolean procedureMatched,
            boolean uiElementFound,
            boolean locationHistoryFound,
            boolean failureHintsFound,
            boolean prerequisitesChecked
    ) {}
}
