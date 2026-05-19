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
