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

import java.util.Arrays;
import java.util.stream.Collectors;

enum SupportedEmbeddingModel {
    BGE_SMALL_EN_V15("bge-small-en-v15", 384),
    MULTILINGUAL_E5_SMALL("multilingual-e5-small", 384);

    final String configKey;
    final int dimension;

    SupportedEmbeddingModel(String configKey, int dimension) {
        this.configKey = configKey;
        this.dimension = dimension;
    }

    static SupportedEmbeddingModel fromConfigKey(String key) {
        return Arrays.stream(values())
                .filter(m -> m.configKey.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported embedding model: '%s'. Valid values: %s".formatted(
                                key, Arrays.stream(values()).map(m -> m.configKey).collect(Collectors.joining(", ")))));
    }
}
