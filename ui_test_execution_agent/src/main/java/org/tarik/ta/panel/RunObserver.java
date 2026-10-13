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
package org.tarik.ta.panel;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.dto.panel.ProcedureNode;
import org.tarik.ta.dto.panel.ProcedureOutcome;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;

import java.util.List;
import java.util.UUID;

/** Follows a run for the operator panel's timeline, independent of the A2A stream it observes. */
public interface RunObserver {

    void taskStarted(@NotNull TestCase testCase);

    void taskFinished(@NotNull TestExecutionResult result);

    /** Marks the run as aborted when it was cancelled or ended without a result. */
    void taskAborted();

    /** Returns an emitter that forwards every event to {@code a2aEmitter} and to the panel. */
    @NotNull StreamingEventEmitter observe(@NotNull StreamingEventEmitter a2aEmitter);

    /** Shows the current item's decomposition: its test data and its procedures in execution order. */
    void itemOutlined(@NotNull List<String> testData, @NotNull List<ProcedureNode> procedures);

    /** Marks the procedure as executing, nested in the procedures started before it and not yet finished. */
    void procedureStarted(@NotNull UUID procedureId);

    void procedureFinished(@NotNull UUID procedureId, @NotNull ProcedureOutcome outcome);

    /** Explains how the item that just ended was resolved to a procedure. */
    void stepResolved(@NotNull StepResolution resolution);
}
