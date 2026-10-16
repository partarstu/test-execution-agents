/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The selected procedures of a test case as their original trees: per precondition or test step, and per run of consecutive items
 * that one recorded workflow covers.
 */
public record KnowledgeResponse(@NotNull List<ItemProcedures> items, @NotNull List<AggregationProcedures> aggregations) {
    public KnowledgeResponse {
        items = List.copyOf(items);
        aggregations = List.copyOf(aggregations);
    }

    public record ItemProcedures(@NotNull String itemId, @NotNull List<ProcedureNode> procedures) {
        public ItemProcedures {
            procedures = List.copyOf(procedures);
        }
    }

    public record AggregationProcedures(@NotNull List<String> itemIds, @NotNull List<ProcedureNode> procedures) {
        public AggregationProcedures {
            itemIds = List.copyOf(itemIds);
            procedures = List.copyOf(procedures);
        }
    }
}
