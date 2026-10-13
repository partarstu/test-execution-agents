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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.dto.panel.prompt.PromptView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An event of the operator panel's stream; the stream names it, so it is serialized as a plain object without a type field. */
public sealed interface PanelEvent {

    record Snapshot(PanelState state) implements PanelEvent {
    }

    record TaskStarted(String testCaseName, List<TimelineItem> items) implements PanelEvent {
        public TaskStarted {
            items = List.copyOf(items);
        }
    }

    record ItemStarted(int index, TimelineItem item) implements PanelEvent {
    }

    record ItemFinished(int index, TimelineItem item) implements PanelEvent {
    }

    record LogLine(String line) implements PanelEvent {
    }

    /** The agent's current activity, e.g. while a tool works; a null message clears it. */
    record Status(@Nullable String message) implements PanelEvent {
    }

    record PromptOpened(PromptView prompt) implements PanelEvent {
    }

    record PromptClosed(UUID id) implements PanelEvent {
    }

    record TaskFinished(RunStatus status, @Nullable String message) implements PanelEvent {
    }

    /** The decomposition of the current item, replacing the previous one. */
    record ItemOutlined(ItemOutline outline) implements PanelEvent {
    }

    /** {@code node} is the procedure's index in the outline, -1 when the outline has no node for it. */
    record ProcedureStarted(UUID procedureId, int node) implements PanelEvent {
    }

    record ProcedureFinished(UUID procedureId, ProcedureOutcome outcome, int node, @Nullable Long durationMillis) implements PanelEvent {
    }

    /** The item at {@code index} with the resolution that explains why its procedure was chosen. */
    record StepResolved(int index, TimelineItem item) implements PanelEvent {
    }

    /** Tells a replaced tab when, from where and from which browser it was replaced. */
    record Displaced(Reason reason, Instant at, String sourceIp, String browser) implements PanelEvent {

        public enum Reason {
            @JsonProperty("taken-over") TAKEN_OVER,
            @JsonProperty("other-tab") OTHER_TAB
        }
    }
}
