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
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * The test case whose items the recorded procedures are selected for.
 */
public record KnowledgeTestCase(@NotNull String name, @NotNull List<TestCaseItem> items) {
    public KnowledgeTestCase {
        requireNonNull(name, "name");
        items = List.copyOf(items);
    }

    public Optional<TestCaseItem> findItem(@NotNull String itemId) {
        return items.stream().filter(item -> item.id().equals(itemId)).findFirst();
    }

    /**
     * Tells whether the IDs name at least two items of one kind whose numbers follow each other in the given order.
     */
    public boolean isConsecutiveRunOfOneKind(@NotNull List<String> itemIds) {
        if (itemIds.size() < 2) {
            return false;
        }
        var runItems = itemIds.stream().map(this::findItem).flatMap(Optional::stream).toList();
        if (runItems.size() != itemIds.size()) {
            return false;
        }
        var first = runItems.getFirst();
        for (int index = 0; index < runItems.size(); index++) {
            var item = runItems.get(index);
            if (item.kind() != first.kind() || item.number() != first.number() + index) {
                return false;
            }
        }
        return true;
    }
}
