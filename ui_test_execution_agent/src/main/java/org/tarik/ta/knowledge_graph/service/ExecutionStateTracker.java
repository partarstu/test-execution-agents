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
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.knowledge_graph.execution.ClosingComposite;
import org.tarik.ta.knowledge_graph.execution.ExecutionStateSnapshot;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.UiTestAgentConfig;

/**
 * Current-state representation and precondition checking for PDDL-Lite planning.
 *
 * <p>Maintains a thread-safe scope stack of effect node UUIDs representing the current execution state.
 * After each successful atomic step execution, the effects it negates are removed from the state and its effects are added.</p>
 *
 * <p>Effect node IDs are stored from {@link PhraseEmbedding} objects for graph-based semantic checks.</p>
 */
@UiAgentRequestScope
public class ExecutionStateTracker {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionStateTracker.class);

    // Guarded by stateLock; the encounter order is the order in which the effects were produced.
    private static class ScopeFrame {
        SequencedMap<UUID, String> effectPhrasesById = new LinkedHashMap<>();

        boolean addEffect(PhraseEmbedding effect) {
            return effectPhrasesById.putLast(effect.id(), effect.phrase()) == null;
        }

        int addEffects(@Nullable List<PhraseEmbedding> effects) {
            if (effects == null) {
                return 0;
            }
            int addedCount = 0;
            for (var effect : effects) {
                if (effect != null && effect.id() != null && addEffect(effect)) {
                    addedCount++;
                }
            }
            return addedCount;
        }

        int removeEffects(Set<UUID> effectNodeIds) {
            int sizeBefore = effectPhrasesById.size();
            effectPhrasesById.keySet().removeAll(effectNodeIds);
            return sizeBefore - effectPhrasesById.size();
        }

        ScopeFrame copy() {
            var copy = new ScopeFrame();
            copy.effectPhrasesById.putAll(effectPhrasesById);
            return copy;
        }
    }

    private final List<ScopeFrame> scopeStack = new CopyOnWriteArrayList<>();
    private final List<Procedure> executedAtomicProcedures = new CopyOnWriteArrayList<>();
    private final Deque<UUID> recentParentIds = new ConcurrentLinkedDeque<>();
    private final UiTestAgentConfig config;
    private final AtomicInteger recentParentIdsSize = new AtomicInteger(0);
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

    public ExecutionStateTracker(UiTestAgentConfig config) {
        this.config = config;
        this.scopeStack.add(new ScopeFrame());
    }

    /**
     * Adds all effect node IDs to the current scope frame.
     *
     * @param effects the list of phrase embedding nodes to add
     */
    public void addEffects(List<PhraseEmbedding> effects) {
        requireNonNull(effects, "effects");
        stateLock.writeLock().lock();
        try {
            int addedCount = scopeStack.getLast().addEffects(effects);
            if (addedCount > 0) {
                LOG.debug("Added {} effect(s) to current scope", addedCount);
            }
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /** Removes the given effect node IDs from every scope frame, since an effect negated inside a composite may live in an outer frame. */
    public void removeEffects(Set<UUID> effectNodeIds) {
        requireNonNull(effectNodeIds, "effectNodeIds");
        if (effectNodeIds.isEmpty()) {
            return;
        }
        stateLock.writeLock().lock();
        try {
            int removedCount = removeFromAllFrames(scopeStack, effectNodeIds);
            LOG.debug("Removed {} negated effect(s) from the current state", removedCount);
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    private static int removeFromAllFrames(List<ScopeFrame> frames, Set<UUID> effectNodeIds) {
        int removedCount = 0;
        for (var frame : frames) {
            removedCount += frame.removeEffects(effectNodeIds);
        }
        return removedCount;
    }

    public void enterCompositeScope(Procedure procedure) {
        stateLock.writeLock().lock();
        try {
            scopeStack.add(new ScopeFrame());
            LOG.debug("Entered composite scope for procedure: '{}'", procedure.description());
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /** Closes the innermost composite scope, removing the effects the composite negates before promoting its own effects. */
    public void closeCompositeScope(List<PhraseEmbedding> compositeEffects, Set<UUID> negatedEffectIds) {
        stateLock.writeLock().lock();
        try {
            int promotedCount = closeScope(scopeStack, compositeEffects, negatedEffectIds);
            LOG.debug("Closed composite scope, promoted {} effect(s) to parent scope", promotedCount);
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    private static int closeScope(List<ScopeFrame> frames, @Nullable List<PhraseEmbedding> compositeEffects,
                                  Set<UUID> negatedEffectIds) {
        frames.removeLast();
        removeFromAllFrames(frames, negatedEffectIds);
        return frames.getLast().addEffects(compositeEffects);
    }

    public void abandonCompositeScope() {
        stateLock.writeLock().lock();
        try {
            scopeStack.removeLast();
            LOG.debug("Abandoned composite scope");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Records an atomic procedure as successfully executed, appending it to the ordered execution log.
     */
    public void addExecutedAtomicProcedure(Procedure procedure) {
        requireNonNull(procedure, "procedure");
        stateLock.writeLock().lock();
        try {
            executedAtomicProcedures.add(procedure);
            LOG.debug("Recorded executed atomic procedure: '{}'", procedure.description());
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Returns an unmodifiable snapshot of atomic procedures executed so far, in execution order.
     */
    public List<Procedure> getExecutedAtomicProcedures() {
        stateLock.readLock().lock();
        try {
            return List.copyOf(executedAtomicProcedures);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /** Captures the effects of every scope and the execution log, so that a failed attempt of a test step can be rolled back. */
    public Checkpoint checkpoint() {
        stateLock.readLock().lock();
        try {
            return new Checkpoint(copyOf(scopeStack), executedAtomicProcedures.size());
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /**
     * Restores the state captured by the checkpoint before the whole test step is rerun, so that the effects the failed attempt
     * added are dropped and the ones it negated are back.
     */
    public void restore(@NotNull Checkpoint checkpoint) {
        stateLock.writeLock().lock();
        try {
            scopeStack.clear();
            scopeStack.addAll(copyOf(checkpoint.frames));
            int currentSize = executedAtomicProcedures.size();
            if (currentSize > checkpoint.executedAtomicCount) {
                executedAtomicProcedures.subList(checkpoint.executedAtomicCount, currentSize).clear();
            }
            LOG.debug("Restored the execution state, rolled back {} executed atomic procedure(s)",
                    Math.max(0, currentSize - checkpoint.executedAtomicCount));
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public static final class Checkpoint {
        private final List<ScopeFrame> frames;
        private final int executedAtomicCount;

        private Checkpoint(List<ScopeFrame> frames, int executedAtomicCount) {
            this.frames = frames;
            this.executedAtomicCount = executedAtomicCount;
        }
    }

    /**
     * Clears the current state (typically called at session start).
     */
    @PreDestroy
    public void reset() {
        stateLock.writeLock().lock();
        try {
            scopeStack.clear();
            scopeStack.add(new ScopeFrame());
            executedAtomicProcedures.clear();
            recentParentIds.clear();
            recentParentIdsSize.set(0);
            LOG.info("Execution state reset - state is now empty");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Records a recently matched parent procedure ID, evicting the oldest if over window size limit.
     */
    public void addRecentParent(UUID parentId) {
        if (parentId == null) return;
        stateLock.writeLock().lock();
        try {
            recentParentIds.addLast(parentId);
            recentParentIdsSize.incrementAndGet();
            while (recentParentIdsSize.get() > config.getAncestryWindowSize()) {
                if (recentParentIds.pollFirst() != null) {
                    recentParentIdsSize.decrementAndGet();
                } else {
                    break; // queue is empty
                }
            }
            LOG.debug("Added recent parent {} to ancestry window (size: {})", parentId, recentParentIdsSize.get());
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Returns an unmodifiable set of recently matched parent IDs.
     */
    public Set<UUID> getRecentParentIds() {
        stateLock.readLock().lock();
        try {
            return Set.copyOf(recentParentIds);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /**
     * Returns an unmodifiable snapshot of all currently visible effect node IDs.
     * Includes effects from every open scope frame: root-level completed procedures plus
     * accumulated completed-children effects at each level of the currently-executing hierarchy.
     */
    public Set<UUID> getEffectNodeIds() {
        stateLock.readLock().lock();
        try {
            return Set.copyOf(visibleEffectIds(scopeStack));
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /**
     * Returns the phrases of the currently visible effects in the order in which they were produced.
     */
    public List<String> getEffectPhrasesInExecutionOrder() {
        stateLock.readLock().lock();
        try {
            return phrasesOf(scopeStack);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /**
     * Returns the phrases of the effects produced before the currently executed item in the order in which they were produced,
     * which are the only effects a procedure replacing that item sees after a rerun.
     */
    public List<String> getEffectPhrasesBeforeCurrentItem() {
        stateLock.readLock().lock();
        try {
            return phrasesOf(List.of(scopeStack.getFirst()));
        } finally {
            stateLock.readLock().unlock();
        }
    }

    private static List<String> phrasesOf(List<ScopeFrame> frames) {
        return frames.stream().flatMap(frame -> frame.effectPhrasesById.values().stream()).toList();
    }

    /**
     * Produces a consistent point-in-time snapshot of the execution state for prefetch evaluation.
     */
    public ExecutionStateSnapshot snapshot() {
        stateLock.readLock().lock();
        try {
            return snapshotOf(scopeStack, recentParentIds, executedAtomicProcedures);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /**
     * Captures the current state and returns the snapshot it will have once the currently executed atomic procedure succeeds and
     * the composites it is the last child of close, given innermost first; their effects and negations are resolved only when it
     * is called.
     */
    public Supplier<ExecutionStateSnapshot> projectedSnapshot(@NotNull List<PhraseEmbedding> atomicEffects,
                                                              @NotNull Set<UUID> atomicNegatedEffectIds,
                                                              @NotNull List<ClosingComposite> closingComposites) {
        List<ScopeFrame> framesAfterAtomic;
        List<UUID> capturedRecentParentIds;
        List<Procedure> capturedExecutedAtomicProcedures;
        stateLock.readLock().lock();
        try {
            framesAfterAtomic = copyOf(scopeStack);
            capturedRecentParentIds = List.copyOf(recentParentIds);
            capturedExecutedAtomicProcedures = List.copyOf(executedAtomicProcedures);
        } finally {
            stateLock.readLock().unlock();
        }
        removeFromAllFrames(framesAfterAtomic, atomicNegatedEffectIds);
        framesAfterAtomic.getLast().addEffects(atomicEffects);
        return () -> {
            List<ScopeFrame> projectedFrames = copyOf(framesAfterAtomic);
            closingComposites.forEach(composite -> {
                var negatedEffectIds = composite.negatedEffectIds().apply(visibleEffectIds(projectedFrames));
                closeScope(projectedFrames, composite.effects().get(), negatedEffectIds);
            });
            return snapshotOf(projectedFrames, capturedRecentParentIds, capturedExecutedAtomicProcedures);
        };
    }

    private static List<ScopeFrame> copyOf(List<ScopeFrame> frames) {
        return frames.stream().map(ScopeFrame::copy).collect(toCollection(ArrayList::new));
    }

    private static Set<UUID> visibleEffectIds(List<ScopeFrame> frames) {
        Set<UUID> visibleEffects = new HashSet<>();
        for (ScopeFrame frame : frames) {
            visibleEffects.addAll(frame.effectPhrasesById.keySet());
        }
        return visibleEffects;
    }

    private static ExecutionStateSnapshot snapshotOf(List<ScopeFrame> frames, Collection<UUID> recentParentIds,
                                                     List<Procedure> executedAtomicProcedures) {
        return new ExecutionStateSnapshot(
            visibleEffectIds(frames),
            new ArrayList<>(recentParentIds),
            new ArrayList<>(executedAtomicProcedures)
        );
    }
}
