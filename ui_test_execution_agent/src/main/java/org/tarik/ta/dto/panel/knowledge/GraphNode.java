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
package org.tarik.ta.dto.panel.knowledge;

import java.util.Set;

/**
 * A procedure, UI element or test case as the knowledge views draw it. Test cases carry no id in the graph, so theirs is
 * {@code test-case:} followed by the name.
 */
public record GraphNode(String id, Kind kind, String label, Set<Flag> flags, int testCases) {
    public static final String TEST_CASE_ID_PREFIX = "test-case:";

    public GraphNode {
        flags = Set.copyOf(flags);
    }

    public enum Kind { COMPOSITE, ATOMIC, ELEMENT, TEST_CASE }

    /** The health conditions a view colours a node by. */
    public enum Flag { NO_TARGET, NO_EFFECTS, LOW_STABILITY, RECENT_ANOMALY }
}
