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

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.model.node.Procedure.TimingProfile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A procedure as the details panel shows it; {@code timing} holds the last success, and {@code chosenFor} the newest step
 * resolutions that chose it.
 */
public record ProcedureDetails(UUID id, String description, boolean atomic, boolean precondition, boolean optional,
                               List<String> prerequisites, List<String> effects, List<String> negatedEffects, List<String> testData,
                               String expectedResults, @Nullable String additionalInfo, Instant createdAt, Instant updatedAt,
                               long successCount, @Nullable TimingProfile timing, List<ProcedureRef> parents,
                               List<ProcedureRef> children, @Nullable ElementRef target, List<String> testCases,
                               List<Anomaly> recentAnomalies, List<ResolutionView> chosenFor) {

    public ProcedureDetails {
        prerequisites = List.copyOf(prerequisites);
        effects = List.copyOf(effects);
        negatedEffects = List.copyOf(negatedEffects);
        testData = List.copyOf(testData);
        parents = List.copyOf(parents);
        children = List.copyOf(children);
        testCases = List.copyOf(testCases);
        recentAnomalies = List.copyOf(recentAnomalies);
        chosenFor = List.copyOf(chosenFor);
    }

    public record ElementRef(UUID id, String name) {
    }

    public record Anomaly(String kind, Instant occurredAt, @Nullable String testCaseName, String details) {
    }
}
