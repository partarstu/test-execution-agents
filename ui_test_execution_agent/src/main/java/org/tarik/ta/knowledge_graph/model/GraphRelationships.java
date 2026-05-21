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
