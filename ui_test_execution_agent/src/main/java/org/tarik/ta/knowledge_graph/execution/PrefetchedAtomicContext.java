package org.tarik.ta.knowledge_graph.execution;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;

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
        PrefetchMetadata metadata
) {
    /**
     * Metadata tracking which parts of the prefetch were cache hits vs. misses.
     */
    public record PrefetchMetadata(
            boolean procedureMatched,
            boolean uiElementFound,
            boolean locationHistoryFound,
            boolean failureHintsFound
    ) {}
}
