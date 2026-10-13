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

import java.util.List;

/** What the knowledge graph holds, for the header of the panel's *Knowledge* tab. */
public record KnowledgeSummary(List<Count> nodesByLabel, List<Count> relationshipsByType, int schemaVersion, long atomicProcedures,
                               long atomicProceduresWithTarget, long atomicProceduresWithEffects) {

    public KnowledgeSummary {
        nodesByLabel = List.copyOf(nodesByLabel);
        relationshipsByType = List.copyOf(relationshipsByType);
    }

    public record Count(String name, long count) {
    }
}
