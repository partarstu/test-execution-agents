package org.tarik.ta.knowledge_graph.execution;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.service.FailureContextService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.UiElementCache;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Plain object orchestrating prediction and prefetching for the next atomic procedure
 * during unattended execution.
 * <p>
 * Instantiated once per execution run, passed down the call chain, and closed in a {@code finally}
 * block. No-op when execution mode is not {@link ExecutionMode#UNATTENDED}.
 * </p>
 * <p>
 * Two prediction modes are supported:
 * <ol>
 *   <li>Deterministic — the next atomic is already known from the current decomposition; only
 *       context payload is prefetched (element, history, failure hints).</li>
 *   <li>Semantic search — no next atomic in the current decomposition; a similarity search is run
 *       against the next queue item and the first atomic of the predicted procedure is prefetched.</li>
 * </ol>
 * </p>
 */
public class NextAtomicPrefetchCoordinator implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NextAtomicPrefetchCoordinator.class);
    private static final int TAKE_TIMEOUT_SECONDS = 2;

    private final ExecutionMode executionMode;
    private final KnowledgeService knowledgeService;
    private final UiElementCache uiElementCache;
    private final ElementLocationHistoryLookup elementLocationHistoryLookup;
    private final FailureContextService failureContextService;
    @Nullable private final ExecutorService prefetchExecutor;

    private volatile CompletableFuture<PrefetchedAtomicContext> pendingFuture;
    private volatile boolean invalidated = false;

    public NextAtomicPrefetchCoordinator(@NotNull ExecutionMode executionMode,
                                         @NotNull KnowledgeService knowledgeService,
                                         @NotNull UiElementCache uiElementCache,
                                         @NotNull ElementLocationHistoryLookup elementLocationHistoryLookup,
                                         @NotNull FailureContextService failureContextService) {
        this.executionMode = executionMode;
        this.knowledgeService = knowledgeService;
        this.uiElementCache = uiElementCache;
        this.elementLocationHistoryLookup = elementLocationHistoryLookup;
        this.failureContextService = failureContextService;
        this.prefetchExecutor = executionMode == ExecutionMode.UNATTENDED
                ? Executors.newVirtualThreadPerTaskExecutor()
                : null;
    }

    /**
     * Schedules prefetch of the most likely next atomic procedure context on the success path.
     * No-op outside of unattended mode.
     */
    public void scheduleSuccessPathPrefetch(@NotNull Procedure currentAtomicProcedure,
                                            @NotNull ExecutionItem currentExecutionItem,
                                            @Nullable Procedure nextAtomicInDecomposition,
                                            @Nullable ExecutionItem nextQueueItem,
                                            @NotNull ExecutionStateSnapshot currentSnapshot) {
        if (executionMode != ExecutionMode.UNATTENDED) {
            return;
        }
        cancelPending();
        invalidated = false;

        if (nextAtomicInDecomposition != null) {
            LOG.debug("Scheduling deterministic prefetch for next atomic '{}' in decomposition",
                    nextAtomicInDecomposition.description());
            pendingFuture = startContextPrefetch(nextAtomicInDecomposition, currentExecutionItem);
        } else if (nextQueueItem != null) {
            LOG.debug("Scheduling semantic-search prefetch for next queue item '{}'",
                    nextQueueItem.getDescription());
            pendingFuture = startSearchAndContextPrefetch(nextQueueItem, currentSnapshot);
        } else {
            LOG.debug("No next atomic or queue item available for prefetch after '{}'",
                    currentAtomicProcedure.description());
            pendingFuture = null;
        }
    }

    /**
     * Returns the valid prefetched context if the prediction matches the expected next atomic.
     * Returns empty on any staleness, validation failure, or timeout — never throws.
     */
    @NotNull
    public Optional<PrefetchedAtomicContext> takeIfValid(@NotNull Procedure expectedNextAtomic,
                                                         @NotNull ExecutionItem expectedNextItem) {
        if (executionMode != ExecutionMode.UNATTENDED) {
            return Optional.empty();
        }
        var future = pendingFuture;
        if (future == null || invalidated) {
            return Optional.empty();
        }
        try {
            PrefetchedAtomicContext ctx = future.get(TAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (ctx == null) {
                return Optional.empty();
            }
            if (!ctx.predictedAtomicProcedure().id().equals(expectedNextAtomic.id())) {
                LOG.debug("Prefetch miss: predicted '{}', actual '{}'",
                        ctx.predictedAtomicProcedure().description(), expectedNextAtomic.description());
                return Optional.empty();
            }
            LOG.debug("Prefetch hit for '{}'", expectedNextAtomic.description());
            pendingFuture = null;
            return Optional.of(ctx);
        } catch (TimeoutException e) {
            LOG.debug("Prefetch timed out for '{}' after {}s — falling back to synchronous build",
                    expectedNextAtomic.description(), TAKE_TIMEOUT_SECONDS);
            return Optional.empty();
        } catch (Exception e) {
            LOG.debug("Prefetch failed for '{}': {} — falling back to synchronous build",
                    expectedNextAtomic.description(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Invalidates any in-flight or completed prefetch when the success-path assumption is broken
     * (e.g. on retry, failure, or re-decomposition).
     */
    public void invalidate(@NotNull String reason) {
        if (executionMode != ExecutionMode.UNATTENDED) {
            return;
        }
        invalidated = true;
        cancelPending();
        LOG.debug("Prefetch invalidated. Reason: {}", reason);
    }

    @Override
    public void close() {
        invalidate("execution run ending");
        if (prefetchExecutor != null) {
            prefetchExecutor.shutdownNow();
        }
    }

    // ---- private helpers ----

    private void cancelPending() {
        var existing = pendingFuture;
        if (existing != null) {
            existing.cancel(false);
            pendingFuture = null;
        }
    }

    /**
     * Starts parallel prefetch tasks for a known next atomic (deterministic path).
     * Element lookup, element cache, location history, and failure hints all run concurrently.
     */
    private CompletableFuture<PrefetchedAtomicContext> startContextPrefetch(@NotNull Procedure nextAtomic,
                                                                             @NotNull ExecutionItem nextItem) {
        var hintsFuture = CompletableFuture.supplyAsync(
                () -> safeGet(() -> failureContextService.findFailureHints(nextAtomic.id()), List.<String>of()),
                prefetchExecutor);

        // Element ID is required; if it fails the whole prefetch is discarded.
        var elementContextFuture = CompletableFuture
                .supplyAsync(() -> knowledgeService.findTargetedUiElementId(nextAtomic.id())
                        .map(UUID::toString).orElse(null), prefetchExecutor)
                .thenComposeAsync(this::buildElementContextFuture, prefetchExecutor);

        return elementContextFuture
                .thenCombine(hintsFuture, (elemCtx, hints) -> buildContext(nextAtomic, nextItem, elemCtx, hints));
    }

    /**
     * Starts a semantic-search prefetch for the next queue item (non-deterministic path).
     * Similarity search runs first; if a high-confidence match is found, its first atomic's context
     * is prefetched in parallel.
     */
    private CompletableFuture<PrefetchedAtomicContext> startSearchAndContextPrefetch(
            @NotNull ExecutionItem nextQueueItem,
            @NotNull ExecutionStateSnapshot snapshot) {
        return CompletableFuture
                .supplyAsync(() -> findFirstAtomicForQueueItem(nextQueueItem, snapshot), prefetchExecutor)
                .thenComposeAsync(firstAtomic -> {
                    if (firstAtomic == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return startContextPrefetch(firstAtomic, nextQueueItem);
                }, prefetchExecutor);
    }

    @Nullable
    private Procedure findFirstAtomicForQueueItem(@NotNull ExecutionItem nextQueueItem,
                                                  @NotNull ExecutionStateSnapshot snapshot) {
        var match = knowledgeService.findBestMatch(
                nextQueueItem.getDescription(),
                snapshot.effectNodeIds(),
                snapshot.recentParentIds().isEmpty()
                        ? java.util.Set.of()
                        : new java.util.HashSet<>(snapshot.recentParentIds()));
        if (match.isEmpty() || match.get().confidence() != KnowledgeService.MatchConfidence.HIGH) {
            LOG.debug("No high-confidence match for prefetch of queue item '{}'",
                    nextQueueItem.getDescription());
            return null;
        }
        Procedure procedure = match.get().procedure();
        try {
            List<Procedure> atomics = procedure.isAtomic()
                    ? List.of(procedure)
                    : knowledgeService.resolveToAtomicSteps(procedure.id());
            return atomics.isEmpty() ? null : atomics.get(0);
        } catch (Exception e) {
            LOG.debug("Could not decompose procedure '{}' for prefetch: {}",
                    procedure.description(), e.getMessage());
            return null;
        }
    }

    private CompletableFuture<ElementContext> buildElementContextFuture(@Nullable String elementId) {
        if (elementId == null) {
            return CompletableFuture.completedFuture(new ElementContext(null, null, null));
        }
        UUID uuid = UUID.fromString(elementId);
        var elementFuture = CompletableFuture.supplyAsync(
                () -> safeGet(() -> uiElementCache.get(uuid).orElse(null), (UiElement) null),
                prefetchExecutor);
        var historyFuture = CompletableFuture.supplyAsync(
                () -> safeGet(() -> elementLocationHistoryLookup.lookup(uuid).orElse(null),
                        (ElementLocationHistory) null),
                prefetchExecutor);
        return elementFuture.thenCombine(historyFuture,
                (element, history) -> new ElementContext(elementId, element, history));
    }

    private static PrefetchedAtomicContext buildContext(@NotNull Procedure nextAtomic,
                                                        @NotNull ExecutionItem nextItem,
                                                        @NotNull ElementContext elemCtx,
                                                        @NotNull List<String> hints) {
        var metadata = new PrefetchedAtomicContext.PrefetchMetadata(
                true,
                elemCtx.elementId() != null,
                elemCtx.locationHistory() != null,
                !hints.isEmpty());
        return new PrefetchedAtomicContext(
                nextAtomic, nextItem, null,
                elemCtx.elementId(), elemCtx.element(), elemCtx.locationHistory(),
                hints, metadata);
    }

    @Nullable
    private static <T> T safeGet(@NotNull java.util.concurrent.Callable<T> task, @Nullable T fallback) {
        try {
            return task.call();
        } catch (Exception e) {
            LOG.debug("Optional prefetch sub-task failed: {}", e.getMessage());
            return fallback;
        }
    }

    private record ElementContext(@Nullable String elementId,
                                  @Nullable UiElement element,
                                  @Nullable ElementLocationHistory locationHistory) {}
}
