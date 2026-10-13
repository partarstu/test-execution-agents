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

import org.junit.jupiter.api.Test;
import org.tarik.ta.dto.panel.ItemOutline;
import org.tarik.ta.dto.panel.PanelEvent;
import org.tarik.ta.dto.panel.PanelEvent.Displaced;
import org.tarik.ta.dto.panel.PanelEvent.Displaced.Reason;
import org.tarik.ta.dto.panel.PanelEvent.ItemFinished;
import org.tarik.ta.dto.panel.PanelEvent.ItemOutlined;
import org.tarik.ta.dto.panel.PanelEvent.ItemStarted;
import org.tarik.ta.dto.panel.PanelEvent.LogLine;
import org.tarik.ta.dto.panel.PanelEvent.ProcedureFinished;
import org.tarik.ta.dto.panel.PanelEvent.ProcedureStarted;
import org.tarik.ta.dto.panel.PanelEvent.PromptClosed;
import org.tarik.ta.dto.panel.PanelEvent.PromptOpened;
import org.tarik.ta.dto.panel.PanelEvent.Snapshot;
import org.tarik.ta.dto.panel.PanelEvent.Status;
import org.tarik.ta.dto.panel.PanelEvent.StepResolved;
import org.tarik.ta.dto.panel.PanelEvent.TaskFinished;
import org.tarik.ta.dto.panel.PanelEvent.TaskStarted;
import org.tarik.ta.dto.panel.PanelState;
import org.tarik.ta.dto.panel.ProcedureNode;
import org.tarik.ta.dto.panel.ProcedureOutcome;
import org.tarik.ta.dto.panel.RunStatus;
import org.tarik.ta.dto.panel.TimelineItem;
import org.tarik.ta.dto.panel.prompt.PromptView;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/** Pins the event names and JSON shapes the frontend's reducer reads, so the two sides stay in step. */
class PanelEventSerializationTest {
    private static final TimelineItem ITEM = new TimelineItem(TimelineItem.Kind.STEP, "Click Save", TimelineItem.Status.FAILED, "gone");
    private static final UUID PROMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ItemOutline OUTLINE = new ItemOutline(1, List.of("admin"), List.of(
            ProcedureNode.pending(PROMPT_ID, "Log in", null, false, false, 0).withStatus(ProcedureNode.Status.RUNNING, null),
            ProcedureNode.pending(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Type the password", "The field is filled", true,
                    true, 1).withStatus(ProcedureNode.Status.COMPLETED, 1200L)));

    @Test
    void everyEvent_shouldHaveItsStreamName_andSerializeAsAPlainObjectWithoutATypeField() throws Exception {
        Map<PanelEvent, String> expected = Map.ofEntries(
                entry(new Snapshot(new PanelState(RunStatus.IDLE, null, List.of(), -1, null, null, List.of(), null, List.of(), null)), "snapshot"),
                entry(new TaskStarted("Checkout", List.of(ITEM)), "task-started"),
                entry(new ItemStarted(0, ITEM), "item-started"),
                entry(new ItemFinished(0, ITEM), "item-finished"),
                entry(new LogLine("a line"), "log-line"),
                entry(new Status("Waiting"), "status"),
                entry(new PromptOpened(new PromptView(PROMPT_ID, "message", PanelJson.MAPPER.createObjectNode(), 5, 1, false, 0,
                        false, false, true)), "prompt-opened"),
                entry(new PromptClosed(PROMPT_ID), "prompt-closed"),
                entry(new TaskFinished(RunStatus.ABORTED, null), "task-finished"),
                entry(new Displaced(Reason.TAKEN_OVER, Instant.parse("2026-10-02T10:00:00Z"), "10.0.0.2", "Firefox"), "displaced"),
                entry(new ItemOutlined(OUTLINE), "item-outlined"),
                entry(new ProcedureStarted(PROMPT_ID, 0), "procedure-started"),
                entry(new ProcedureFinished(PROMPT_ID, ProcedureOutcome.ABANDONED, 0, 1200L), "procedure-finished"),
                entry(new StepResolved(0, ITEM), "step-resolved"));

        expected.forEach((event, name) -> assertThat(ExecutionStateBroadcaster.eventName(event)).isEqualTo(name));
        for (var event : expected.keySet()) {
            var json = PanelJson.MAPPER.valueToTree(event);
            assertThat(json.has("type")).isFalse();
            assertThat(json.toString()).doesNotContain("@class");
        }
    }

    @Test
    void itemEvent_shouldCarryTheIndexAndTheWholeItem() throws Exception {
        assertThat(PanelJson.MAPPER.writeValueAsString(new ItemFinished(2, ITEM))).isEqualTo("{\"index\":2,\"item\":{\"kind\":\"STEP\","
                + "\"description\":\"Click Save\",\"status\":\"FAILED\",\"errorMessage\":\"gone\",\"resolution\":null}}");
    }

    @Test
    void displaced_shouldUseTheKebabCaseReason_andAnIsoTimestamp() throws Exception {
        var displaced = new Displaced(Reason.OTHER_TAB, Instant.parse("2026-10-02T10:00:00Z"), "10.0.0.2", "Firefox");

        assertThat(PanelJson.MAPPER.writeValueAsString(displaced))
                .isEqualTo("{\"reason\":\"other-tab\",\"at\":\"2026-10-02T10:00:00Z\",\"sourceIp\":\"10.0.0.2\",\"browser\":\"Firefox\"}");
    }

    @Test
    void snapshot_shouldNestTheWholeStateUnderState() throws Exception {
        var state = new PanelState(RunStatus.RUNNING, "Checkout", List.of(ITEM), 0, "busy", null, List.of("a line"), null, List.of(), null);

        assertThat(PanelJson.MAPPER.writeValueAsString(new Snapshot(state))).isEqualTo("{\"state\":{\"runStatus\":\"RUNNING\","
                + "\"testCaseName\":\"Checkout\",\"items\":[{\"kind\":\"STEP\",\"description\":\"Click Save\",\"status\":\"FAILED\","
                + "\"errorMessage\":\"gone\",\"resolution\":null}],\"currentItem\":0,\"statusMessage\":\"busy\",\"resultMessage\":null,"
                + "\"logTail\":[\"a line\"],\"prompt\":null,\"activeProcedures\":[],\"outline\":null}}");
    }

    @Test
    void itemOutlined_shouldCarryTheItemItsTestDataAndItsProceduresInOrder() throws Exception {
        assertThat(PanelJson.MAPPER.writeValueAsString(new ItemOutlined(OUTLINE))).isEqualTo("{\"outline\":{\"item\":1,"
                + "\"testData\":[\"admin\"],\"procedures\":[{\"id\":\"00000000-0000-0000-0000-000000000001\","
                + "\"description\":\"Log in\",\"expectedResults\":null,\"atomic\":false,\"optional\":false,\"depth\":0,"
                + "\"status\":\"RUNNING\",\"durationMillis\":null},{\"id\":\"00000000-0000-0000-0000-000000000002\","
                + "\"description\":\"Type the password\",\"expectedResults\":\"The field is filled\",\"atomic\":true,"
                + "\"optional\":true,\"depth\":1,\"status\":\"COMPLETED\",\"durationMillis\":1200}]}}");
    }

    @Test
    void procedureFinished_shouldCarryTheOutlineNode_andTheDuration() throws Exception {
        assertThat(PanelJson.MAPPER.writeValueAsString(new ProcedureFinished(PROMPT_ID, ProcedureOutcome.SKIPPED, 3, 40L)))
                .isEqualTo("{\"procedureId\":\"00000000-0000-0000-0000-000000000001\",\"outcome\":\"SKIPPED\",\"node\":3,"
                        + "\"durationMillis\":40}");
    }
}
