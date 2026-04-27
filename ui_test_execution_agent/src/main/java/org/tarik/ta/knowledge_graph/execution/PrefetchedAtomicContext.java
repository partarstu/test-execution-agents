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
