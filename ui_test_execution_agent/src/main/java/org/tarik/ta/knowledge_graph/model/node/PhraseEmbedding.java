/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.knowledge_graph.model.node;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Represents a {@code PhraseEmbedding} Neo4j node storing a single phrase (prerequisite or effect)
 * with its pre-computed 384-dimensional semantic embedding as a native vector property.
 */
public record PhraseEmbedding(UUID id, String phrase, float[] embedding, PhraseType type) implements Embeddable {

    public static final String LABEL = "PhraseEmbedding";
    public static final String PROP_PHRASE = "phrase";
    public static final String PROP_TYPE = "type";

    public enum PhraseType {
        PREREQUISITE, EFFECT
    }

    public PhraseEmbedding {
        requireNonNull(id, "id");
        requireNonNull(phrase, "phrase");
        requireNonNull(type, "type");
        embedding = embedding != null ? embedding.clone() : null;
    }

    public static PhraseEmbedding createPrerequisite(String phrase, float[] embedding) {
        return new PhraseEmbedding(UUID.randomUUID(), phrase, embedding, PhraseType.PREREQUISITE);
    }

    public static PhraseEmbedding createEffect(String phrase, float[] embedding) {
        return new PhraseEmbedding(UUID.randomUUID(), phrase, embedding, PhraseType.EFFECT);
    }

    @Override
    public float[] embedding() {
        return embedding != null ? embedding.clone() : null;
    }
}