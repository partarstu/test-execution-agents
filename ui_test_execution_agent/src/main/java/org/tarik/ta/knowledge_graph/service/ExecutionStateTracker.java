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
package org.tarik.ta.knowledge_graph.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;
import org.tarik.ta.UiTestAgentConfig;

/**
 * Current-state representation and precondition checking for PDDL-Lite planning.
 *
 * <p>Maintains a thread-safe scope stack of effect node UUIDs representing the current execution state.
 * After each successful atomic step execution, effects are added to the state.</p>
 *
 * <p>Effect node IDs are stored from {@link PhraseEmbedding} objects for graph-based semantic checks.</p>
 */
@UiAgentRequestScope
public class ExecutionStateTracker {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionStateTracker.class);

    private static class ScopeFrame {
        Set<UUID> effectNodeIds = ConcurrentHashMap.newKeySet();
    }

    private final List<ScopeFrame> scopeStack = new CopyOnWriteArrayList<>();
    private final List<Procedure> executedAtomicProcedures = new CopyOnWriteArrayList<>();
    private final Deque<UUID> recentParentIds = new ConcurrentLinkedDeque<>();
    private final UiTestAgentConfig config;
    private final AtomicInteger recentParentIdsSize = new AtomicInteger(0);

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
        ScopeFrame currentFrame = scopeStack.getLast();
        int addedCount = 0;
        for (var effect : effects) {
            if (effect != null && effect.id() != null) {
                if (currentFrame.effectNodeIds.add(effect.id())) {
                    addedCount++;
                }
            }
        }
        if (addedCount > 0) {
            LOG.debug("Added {} effect(s) to current scope", addedCount);
        }
    }

    public void enterCompositeScope(Procedure procedure) {
        scopeStack.add(new ScopeFrame());
        LOG.debug("Entered composite scope for procedure: '{}'", procedure.description());
    }

    public void closeCompositeScope(List<PhraseEmbedding> compositeEffects) {
        scopeStack.removeLast();
        ScopeFrame newTop = scopeStack.getLast();
        int promotedCount = 0;
        if (compositeEffects != null) {
            for (PhraseEmbedding effect : compositeEffects) {
                if (effect != null && effect.id() != null) {
                    if (newTop.effectNodeIds.add(effect.id())) {
                        promotedCount++;
                    }
                }
            }
        }
        LOG.debug("Closed composite scope, promoted {} effect(s) to parent scope", promotedCount);
    }

    public void abandonCompositeScope() {
        scopeStack.removeLast();
        LOG.debug("Abandoned composite scope");
    }

    /**
     * Records an atomic procedure as successfully executed, appending it to the ordered execution log.
     */
    public void addExecutedAtomicProcedure(Procedure procedure) {
        requireNonNull(procedure, "procedure");
        executedAtomicProcedures.add(procedure);
        LOG.debug("Recorded executed atomic procedure: '{}'", procedure.description());
    }

    /**
     * Returns an unmodifiable snapshot of atomic procedures executed so far, in execution order.
     */
    public List<Procedure> getExecutedAtomicProcedures() {
        return List.copyOf(executedAtomicProcedures);
    }

    /**
     * Clears the current state (typically called at session start).
     */
    @PreDestroy
    public void reset() {
        scopeStack.clear();
        scopeStack.add(new ScopeFrame());
        executedAtomicProcedures.clear();
        recentParentIds.clear();
        recentParentIdsSize.set(0);
        LOG.info("Execution state reset - state is now empty");
    }

    /**
     * Records a recently matched parent procedure ID, evicting the oldest if over window size limit.
     */
    public void addRecentParent(UUID parentId) {
        if (parentId == null) return;
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
    }

    /**
     * Returns an unmodifiable set of recently matched parent IDs.
     */
    public Set<UUID> getRecentParentIds() {
        return Set.copyOf(recentParentIds);
    }

    /**
     * Returns an unmodifiable snapshot of all currently visible effect node IDs.
     * Includes effects from every open scope frame: root-level completed procedures plus
     * accumulated completed-children effects at each level of the currently-executing hierarchy.
     */
    public Set<UUID> getEffectNodeIds() {
        Set<UUID> visibleEffects = new HashSet<>();
        for (ScopeFrame frame : scopeStack) {
            visibleEffects.addAll(frame.effectNodeIds);
        }
        return Set.copyOf(visibleEffects);
    }
}
