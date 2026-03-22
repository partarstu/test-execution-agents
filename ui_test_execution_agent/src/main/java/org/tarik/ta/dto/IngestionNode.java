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
package org.tarik.ta.dto;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;
import java.util.UUID;

/**
 * Tree structure for ingesting procedure hierarchies into the knowledge graph.
 * Wraps {@link Procedure} directly — no field duplication.
 */
public sealed interface IngestionNode {

    /**
     * A new procedure to persist. The Procedure has null phrase embeddings — the ingestion
     * service generates them. {@code targetUiElementId} is carried separately because it
     * represents a TARGETS relationship, not a property of Procedure.
     */
    record NewProcedure(
            Procedure procedure,
            @Nullable UUID targetUiElementId,
            List<IngestionNode> children
    ) implements IngestionNode {
        public NewProcedure {
            children = children != null ? List.copyOf(children) : List.of();
        }
    }

    /**
     * A reference to an already-persisted procedure. Ingestion creates only a
     * CONTAINS relationship — no new node.
     */
    record ExistingReference(UUID existingProcedureId) implements IngestionNode {}
}
