/*
 * knowledge-graph-core - Knowledge graph model, repositories and embeddings shared by the agents that use the knowledge graph.
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
package org.tarik.ta.knowledge_graph.model.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.model.node.UiElement.Screenshot;

import java.time.Instant;
import java.util.UUID;

/**
 * An earlier appearance of a {@link UiElement}, expired when the element was refined; the current appearance stays on the
 * element node. {@code validFrom} is {@code null} for an appearance recorded before elements carried an update timestamp.
 */
public record UiElementAppearance(@NotNull UUID id, @NotNull String ownDescription, @NotNull String anchorsDescription,
                                  @NotNull String parentElementSummary, @Nullable Screenshot screenshot,
                                  @Nullable Instant validFrom, @NotNull Instant validTo) {
    public static final String LABEL = "UiElementAppearance";
    public static final String PROP_VALID_FROM = "validFrom";
    public static final String PROP_VALID_TO = "validTo";
}
