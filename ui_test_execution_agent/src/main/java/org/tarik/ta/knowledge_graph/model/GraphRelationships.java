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
package org.tarik.ta.knowledge_graph.model;

public final class GraphRelationships {

    /** Parent-to-child procedure relationship with a {@code sequence} property for ordering. */
    public static final String REL_CONTAINS = "CONTAINS";
    /** Atomic procedure to UI element relationship indicating the target element for action. */
    public static final String REL_TARGETS = "TARGETS";
    public static final String REL_SATISFIES = "SATISFIES";
    public static final String REL_USES_PROCEDURE = "USES_PROCEDURE";
    public static final String REL_HAS_FAILURE_CONTEXT = "HAS_FAILURE_CONTEXT";
    /** Procedure to PhraseEmbedding relationship for prerequisites, with {@code sequence} ordering. */
    public static final String REL_HAS_PREREQUISITE = "HAS_PREREQUISITE";
    /** Procedure to PhraseEmbedding relationship for effects, with {@code sequence} ordering. */
    public static final String REL_HAS_EFFECT = "HAS_EFFECT";

    public static final String PROP_SEQUENCE = "sequence";

    private GraphRelationships() {
    }
}
