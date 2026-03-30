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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;


import static java.util.Objects.requireNonNull;
import org.tarik.ta.UiTestAgentConfig;

/**
 * Current-state representation and precondition checking for PDDL-Lite planning.
 *
 * <p>Maintains a thread-safe set of effect phrases representing the current execution state.
 * After each successful atomic step execution, effects are added to the state. Before
 * executing a step, preconditions are checked against the current state.</p>
 *
 * <p>State comparisons are case-insensitive and whitespace-normalized to handle
 * variations in how effects and preconditions are expressed.</p>
 *
 * <p>Effect node IDs are stored from {@link PhraseEmbedding} objects for graph-based semantic checks.</p>
 */
@UiAgentRequestScope
public class ExecutionStateTracker {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionStateTracker.class);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Set<String> currentState = ConcurrentHashMap.newKeySet();
    private final Map<String, UUID> effectNodeIds = new ConcurrentHashMap<>();
    private final List<Procedure> executedAtomicProcedures = new CopyOnWriteArrayList<>();
    private final Deque<UUID> recentParentIds = new ConcurrentLinkedDeque<>();
    private final UiTestAgentConfig config;
    private final AtomicInteger recentParentIdsSize = new AtomicInteger(0);

    public ExecutionStateTracker(UiTestAgentConfig config) {
        this.config = config;
    }

    /**
     * Adds all effect phrases to the current state after successful execution.
     * Effects are normalized (lowercase, whitespace trimmed) before storage.
     * Each node's UUID is tracked for later semantic precondition checking via the graph.
     *
     * @param effects the list of phrase embedding nodes to add
     */
    public void addEffects(List<PhraseEmbedding> effects) {
        requireNonNull(effects, "effects");
        for (var effect : effects) {
            if (effect != null && effect.phrase() != null && !effect.phrase().isBlank()) {
                String normalized = normalize(effect.phrase());
                if (currentState.add(normalized)) {
                    effectNodeIds.put(normalized, effect.id());
                    LOG.debug("Added effect to state: '{}'", normalized);
                }
            }
        }
        LOG.debug("Current state now contains {} effect(s)", currentState.size());
    }

    /**
     * Adds raw effect phrases to the current state, bypassing the graph.
     * Used as a fallback when no {@link PhraseEmbedding} nodes are linked in the graph for a procedure.
     *
     * @param phrases the effect phrase strings to add
     */
    public void addEffectPhrases(List<String> phrases) {
        requireNonNull(phrases, "phrases");
        for (var phrase : phrases) {
            if (phrase != null && !phrase.isBlank()) {
                String normalized = normalize(phrase);
                if (currentState.add(normalized)) {
                    LOG.debug("Added effect phrase to state (fallback): '{}'", normalized);
                }
            }
        }
        LOG.debug("Current state now contains {} effect(s)", currentState.size());
    }

    /**
     * Returns precondition phrases that are not satisfied by the current state.
     * Comparison is case-insensitive and whitespace-normalized.
     *
     * @param preconditions the list of precondition phrases to check
     * @return list of unsatisfied precondition phrases
     */
    public List<String> findMissingPrerequisites(List<String> preconditions) {
        requireNonNull(preconditions, "preconditions");
        return preconditions.stream()
                .filter(p -> p != null && !p.isBlank())
                .filter(phrase -> !currentState.contains(normalize(phrase)))
                .toList();
    }

    /**
     * Checks if all preconditions are satisfied by the current state.
     * Comparison is case-insensitive and whitespace-normalized.
     *
     * @param preconditions the list of precondition phrases to check
     * @return true if all preconditions are in the current state
     */
    public boolean arePrerequisitesMet(List<String> preconditions) {
        requireNonNull(preconditions, "preconditions");
        return findMissingPrerequisites(preconditions).isEmpty();
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
        currentState.clear();
        effectNodeIds.clear();
        executedAtomicProcedures.clear();
        recentParentIds.clear();
        recentParentIdsSize.set(0);
        LOG.info("Execution state reset - state is now empty");
    }

    /**
     * Returns an unmodifiable copy of the current state.
     *
     * @return unmodifiable set of effect phrases in the current state
     */
    public Set<String> getCurrentState() {
        return Set.copyOf(currentState);
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
     * Returns an unmodifiable snapshot of effect node IDs for semantic precondition checking via the graph.
     */
    public Set<UUID> getEffectNodeIds() {
        return Set.copyOf(effectNodeIds.values());
    }

    /**
     * Normalizes a phrase for comparison: lowercase and trimmed whitespace.
     * Multiple whitespace characters are collapsed to single spaces.
     */
    private String normalize(String phrase) {
        return WHITESPACE.matcher(phrase.trim().toLowerCase()).replaceAll(" ");
    }
}