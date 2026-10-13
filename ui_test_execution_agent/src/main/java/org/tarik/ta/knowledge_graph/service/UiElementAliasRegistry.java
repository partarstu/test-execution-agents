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

import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;

/**
 * Short aliases standing in for UI element UUIDs wherever an element ID is shown to a model. Self-hosted models mistype single
 * characters of a 36-character UUID often enough to fail the lookup and the step, so they reproduce a few-character alias that
 * only this registry maps back.
 */
@Singleton
public class UiElementAliasRegistry {
    private static final String ALIAS_PREFIX = "E";

    private final Map<UUID, String> aliasesByElementId = new ConcurrentHashMap<>();
    private final Map<String, UUID> elementIdsByAlias = new ConcurrentHashMap<>();
    private final AtomicInteger aliasCounter = new AtomicInteger();

    /** The alias of the given element, assigned on first use and kept afterwards so a model recognises it across turns. */
    public @NotNull String aliasFor(@NotNull UUID elementId) {
        return aliasesByElementId.computeIfAbsent(elementId, id -> {
            var alias = ALIAS_PREFIX + aliasCounter.incrementAndGet();
            elementIdsByAlias.put(alias, id);
            return alias;
        });
    }

    /**
     * Convenience for call sites which hold the ID as the nullable string the execution context carries.
     */
    public @Nullable String aliasFor(@Nullable String elementId) {
        return isNotBlank(elementId) ? aliasFor(UUID.fromString(elementId)) : null;
    }

    /**
     * The element behind an alias, or behind a plain UUID - IDs also travel to the model inside the results of the knowledge
     * tools, and those are not aliased.
     */
    public @NotNull Optional<UUID> resolve(@NotNull String aliasOrElementId) {
        var elementId = elementIdsByAlias.get(aliasOrElementId.trim());
        if (elementId != null) {
            return of(elementId);
        }
        try {
            return of(UUID.fromString(aliasOrElementId.trim()));
        } catch (IllegalArgumentException _) {
            return empty();
        }
    }
}
