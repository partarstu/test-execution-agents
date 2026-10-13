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

import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Knowledge growth and run quality per ISO week in UTC, oldest week first, with a row for every week of the range. */
public record WeeklyTrends(List<Week> weeks) {

    public WeeklyTrends {
        weeks = List.copyOf(weeks);
    }

    /** The counts of the week starting on Monday {@code start}; a kind or a source without events is missing from its map. */
    public record Week(LocalDate start, int proceduresCreated, Map<ExecutionAnomaly.Kind, Integer> anomalies,
                       Map<StepResolution.Source, Integer> resolutions, Map<StepResolution.Outcome, Integer> outcomes) {
        public Week {
            anomalies = Map.copyOf(anomalies);
            resolutions = Map.copyOf(resolutions);
            outcomes = Map.copyOf(outcomes);
        }
    }
}
