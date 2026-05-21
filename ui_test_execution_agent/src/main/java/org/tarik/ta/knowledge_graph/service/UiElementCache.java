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
package org.tarik.ta.knowledge_graph.service;

import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class UiElementCache {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementCache.class);

    private final ConcurrentHashMap<UUID, UiElement> cache = new ConcurrentHashMap<>();

    public Optional<UiElement> get(@NotNull UUID id) {
        var element = cache.get(id);
        if (element != null) {
            LOG.debug("Cache hit for UI element with id={}", id);
        }
        return Optional.ofNullable(element);
    }

    public void put(@NotNull UiElement element) {
        var id = element.id();
        if (cache.containsKey(id)) {
            LOG.warn("UiElement '{}' (id={}) is being added to cache but its ID already exists — overwriting", element.name(), id);
        }
        cache.put(id, element);
        LOG.debug("Cached UI element '{}' (id={})", element.name(), id);
    }

    public void update(@NotNull UiElement element) {
        cache.put(element.id(), element);
        LOG.debug("Updated cached element '{}' (id={})", element.name(), element.id());
    }

    public void remove(@NotNull UUID id) {
        var el = cache.remove(id);
        if(el==null){
            LOG.debug("No element with id {} present in cache, nothing to remove", id);
        }else {
            LOG.debug("Removed element id {} from cache", id);
        }
    }
}