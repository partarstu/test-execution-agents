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
 * Represents a TestCase node in the knowledge graph, tracking which test cases use which procedures.
 */
public record TestCase(UUID id, String name) implements IEntity {

    public static final String LABEL = "TestCase";
    public static final String PROP_NAME = "name";

    public TestCase {
        requireNonNull(id, "id");
        requireNonNull(name, "name");
    }

    public static TestCase create(String name) {
        return new TestCase(UUID.randomUUID(), name);
    }
}
