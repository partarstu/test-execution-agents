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