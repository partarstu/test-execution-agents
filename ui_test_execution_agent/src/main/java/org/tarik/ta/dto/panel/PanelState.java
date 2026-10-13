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
package org.tarik.ta.dto.panel;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.dto.panel.prompt.PromptView;

import java.util.List;
import java.util.UUID;

/**
 * Everything a newly connected panel tab needs to show the run; {@code currentItem} is -1 while no item has started,
 * {@code activeProcedures} holds the procedures being executed, the root first, and {@code outline} the current item's decomposition.
 */
public record PanelState(RunStatus runStatus, @Nullable String testCaseName, List<TimelineItem> items, int currentItem,
                         @Nullable String statusMessage, @Nullable String resultMessage, List<String> logTail,
                         @Nullable PromptView prompt, List<UUID> activeProcedures, @Nullable ItemOutline outline) {

    public PanelState {
        items = List.copyOf(items);
        logTail = List.copyOf(logTail);
        activeProcedures = List.copyOf(activeProcedures);
    }
}
