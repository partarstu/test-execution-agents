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
