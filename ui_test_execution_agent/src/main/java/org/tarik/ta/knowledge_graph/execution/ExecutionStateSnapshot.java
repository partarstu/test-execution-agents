/*
 * ui-test-execution-agent - ${project.description}
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

import org.tarik.ta.knowledge_graph.model.node.Procedure;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.Collections;

/**
 * Immutable snapshot of the execution state used for prediction and prefetch.
 */
public record ExecutionStateSnapshot(
        Set<UUID> effectNodeIds,
        List<UUID> recentParentIds,
        List<Procedure> executedAtomicProcedures
) {
    public ExecutionStateSnapshot {
        effectNodeIds = Set.copyOf(effectNodeIds);
        recentParentIds = List.copyOf(recentParentIds);
        executedAtomicProcedures = List.copyOf(executedAtomicProcedures);
    }

    /**
     * Creates a synthetic state snapshot assuming the given atomic procedure completes successfully.
     * 
     * @param declaredEffectsOfCurrentAtomic the declared effects of the atomic procedure that is about to start
     * @return a new snapshot containing the union of current effects and the atomic's declared effects
     */
    public ExecutionStateSnapshot withPredictedEffects(Set<UUID> declaredEffectsOfCurrentAtomic) {
        Set<UUID> syntheticEffects = new HashSet<>(effectNodeIds);
        if (declaredEffectsOfCurrentAtomic != null) {
            syntheticEffects.addAll(declaredEffectsOfCurrentAtomic);
        }
        return new ExecutionStateSnapshot(
                syntheticEffects,
                recentParentIds,
                executedAtomicProcedures
        );
    }
}
