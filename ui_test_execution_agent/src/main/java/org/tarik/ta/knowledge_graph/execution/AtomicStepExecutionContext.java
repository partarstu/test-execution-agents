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
package org.tarik.ta.knowledge_graph.execution;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.Procedure.TimingProfile;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.timing.TimingRecorder;

import java.util.List;

/**
 * Everything the execution of one atomic step needs beyond the step itself. {@code ancestorChain} lists the composites the step
 * was decomposed from, outermost first, and is empty for a step executed on its own.
 */
public record AtomicStepExecutionContext(
        @Nullable TimingProfile timingProfile,
        TimingRecorder timingRecorder,
        List<String> failureHints,
        String uiElementId,
        String effectiveExpectedResults,
        @Nullable UiElement targetElement,
        @Nullable ElementLocationHistory locationHistory,
        List<Procedure> ancestorChain
) {
    public AtomicStepExecutionContext {
        ancestorChain = List.copyOf(ancestorChain);
    }
}