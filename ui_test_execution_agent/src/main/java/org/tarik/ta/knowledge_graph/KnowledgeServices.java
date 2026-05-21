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
package org.tarik.ta.knowledge_graph;

import org.tarik.ta.knowledge_graph.service.*;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public record KnowledgeServices(
        KnowledgeService mainKnowledgeService,
        KnowledgeIngestionService ingestionService,
        SatisfiesEdgeService satisfiesEdgeService,
        ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService,
        FailureContextService failureContextService,
        LocationHistoryRecorder locationHistoryRecorder,
        Function<UUID, Optional<ElementLocationHistory>> stabilityLookup) {
}
