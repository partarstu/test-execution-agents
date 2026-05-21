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
