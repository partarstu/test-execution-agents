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