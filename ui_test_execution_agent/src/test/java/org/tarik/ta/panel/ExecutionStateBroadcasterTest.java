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

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.sse.SseClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.model.ContextEntry.StepType;
import org.tarik.ta.core.security.SecretRegistry;
import org.tarik.ta.dto.panel.PanelEvent.Displaced;
import org.tarik.ta.dto.panel.PanelEvent.Displaced.Reason;
import org.tarik.ta.dto.panel.ProcedureNode;
import org.tarik.ta.dto.panel.ProcedureOutcome;
import org.tarik.ta.dto.panel.RunStatus;
import org.tarik.ta.dto.panel.TimelineItem;
import org.tarik.ta.dto.panel.TimelineItem.Kind;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Outcome;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Source;
import org.tarik.ta.knowledge_graph.model.MatchConfidence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.dto.panel.TimelineItem.Status.FAILED;
import static org.tarik.ta.dto.panel.TimelineItem.Status.PASSED;
import static org.tarik.ta.dto.panel.TimelineItem.Status.PENDING;
import static org.tarik.ta.dto.panel.TimelineItem.Status.RUNNING;

class ExecutionStateBroadcasterTest {
    private static final String SESSION_ID = "session-1";
    private static final long WAIT_MILLIS = 5_000;
    private static final Displaced DISPLACEMENT = new Displaced(Reason.OTHER_TAB, Instant.parse("2026-10-02T10:00:00Z"),
            "127.0.0.1", "browser");

    private final PanelAuth auth = mock(PanelAuth.class);
    private ExecutionStateBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        when(auth.isActive(anyString())).thenReturn(true);
        SecretRegistry.global().reset(Pattern.compile("(?i).*password.*"));
        broadcaster = new ExecutionStateBroadcaster(auth);
    }

    @AfterEach
    void tearDown() {
        broadcaster.close();
        SecretRegistry.global().reset(Pattern.compile("(?!)"));
    }

    @Test
    void subscribe_shouldSendTheSnapshotFirst_thenTheDeltas() throws Exception {
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);

        broadcaster.log("first line");

        var events = client.awaitEvents(2);
        assertThat(events).extracting(SentEvent::name).containsExactly("snapshot", "log-line");
        assertThat(json(events.get(1)).get("line").asText()).isEqualTo("first line");
    }

    @Test
    void subscribe_shouldGiveALateSubscriberTheWholeState_inItsSnapshot() throws Exception {
        broadcaster.taskStarted(testCase());
        broadcaster.itemStarted(Kind.PRECONDITION, "User is logged in");
        broadcaster.itemFinished(Kind.PRECONDITION, "User is logged in", PASSED, null);
        broadcaster.itemStarted(Kind.STEP, "Click Save");
        broadcaster.log("a line");
        var client = new RecordingClient();

        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);

        var state = json(client.awaitEvents(1).getFirst()).get("state");
        assertThat(state.get("runStatus").asText()).isEqualTo("RUNNING");
        assertThat(state.get("testCaseName").asText()).isEqualTo("Checkout");
        assertThat(state.get("currentItem").asInt()).isEqualTo(1);
        assertThat(state.get("items")).extracting(item -> item.get("status").asText()).containsExactly("PASSED", "RUNNING", "PENDING");
        assertThat(state.get("logTail")).extracting(JsonNode::asText).containsExactly("a line");
    }

    @Test
    void procedureEvents_shouldKeepTheStackOfActiveProcedures_rootFirst_inTheSnapshot() throws Exception {
        var root = UUID.randomUUID();
        var composite = UUID.randomUUID();
        var atomic = UUID.randomUUID();
        broadcaster.taskStarted(testCase());
        broadcaster.procedureStarted(root);
        broadcaster.procedureStarted(composite);
        broadcaster.procedureStarted(atomic);
        broadcaster.procedureFinished(atomic, ProcedureOutcome.COMPLETED);
        var client = new RecordingClient();

        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);
        broadcaster.procedureFinished(composite, ProcedureOutcome.FAILED);

        var events = client.awaitEvents(2);
        assertThat(json(events.getFirst()).get("state").get("activeProcedures")).extracting(JsonNode::asText)
                .containsExactly(root.toString(), composite.toString());
        assertThat(events.get(1).name()).isEqualTo("procedure-finished");
        assertThat(json(events.get(1)).get("outcome").asText()).isEqualTo("FAILED");
        assertThat(broadcaster.snapshot().activeProcedures()).containsExactly(root);
    }

    @Test
    void procedureEvents_shouldFollowTheOutlineInPreOrder_withAProcedureUsedTwiceAsTwoNodes_andTimeEachNode() throws Exception {
        var now = new AtomicReference<>(Instant.parse("2026-10-11T10:00:00Z"));
        broadcaster.close();
        broadcaster = new ExecutionStateBroadcaster(auth, now::get);
        var root = UUID.randomUUID();
        var composite = UUID.randomUUID();
        var atomic = UUID.randomUUID();
        broadcaster.taskStarted(testCase());
        broadcaster.itemStarted(Kind.STEP, "Click Save");
        broadcaster.itemOutlined(List.of("admin"), List.of(ProcedureNode.pending(root, "Log in", null, false, false, 0),
                ProcedureNode.pending(atomic, "Click Save", "Saved", true, false, 1),
                ProcedureNode.pending(composite, "Confirm", null, false, false, 1),
                ProcedureNode.pending(atomic, "Click Save", "Saved", true, false, 2)));
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);

        broadcaster.procedureStarted(root);
        broadcaster.procedureStarted(atomic);
        now.set(now.get().plusMillis(1500));
        broadcaster.procedureFinished(atomic, ProcedureOutcome.COMPLETED);
        broadcaster.procedureStarted(composite);
        broadcaster.procedureStarted(atomic);
        now.set(now.get().plusMillis(200));
        broadcaster.procedureFinished(atomic, ProcedureOutcome.SKIPPED);

        var events = client.awaitEvents(7).subList(1, 7);
        assertThat(events).extracting(event -> json(event).get("node").asInt()).containsExactly(0, 1, 1, 2, 3, 3);
        assertThat(json(events.get(2)).get("durationMillis").asLong()).isEqualTo(1500);
        assertThat(broadcaster.snapshot().outline()).isNotNull().satisfies(outline -> {
            assertThat(outline.item()).isEqualTo(1);
            assertThat(outline.testData()).containsExactly("admin");
            assertThat(outline.procedures()).extracting(ProcedureNode::status, ProcedureNode::durationMillis).containsExactly(
                    tuple(ProcedureNode.Status.RUNNING, null), tuple(ProcedureNode.Status.COMPLETED, 1500L),
                    tuple(ProcedureNode.Status.RUNNING, null), tuple(ProcedureNode.Status.SKIPPED, 200L));
        });
    }

    @Test
    void procedureEvents_shouldNameNoNode_withoutAnOutline() throws Exception {
        broadcaster.taskStarted(testCase());
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);

        broadcaster.procedureStarted(UUID.randomUUID());

        assertThat(json(client.awaitEvents(2).get(1)).get("node").asInt()).isEqualTo(-1);
    }

    @Test
    void itemOutlined_shouldRedactItsTexts_andTheNextItemShouldClearIt() {
        SecretRegistry.global().register("password", "s3cr3t-value");
        broadcaster.taskStarted(testCase());
        broadcaster.itemStarted(Kind.STEP, "Click Save");

        broadcaster.itemOutlined(List.of("s3cr3t-value"),
                List.of(ProcedureNode.pending(UUID.randomUUID(), "Type s3cr3t-value", "Shows s3cr3t-value", true, false, 0)));

        var outline = broadcaster.snapshot().outline();
        assertThat(outline).isNotNull();
        assertThat(outline.testData()).containsExactly("{{secret:password}}");
        assertThat(outline.procedures()).extracting(ProcedureNode::description, ProcedureNode::expectedResults)
                .containsExactly(tuple("Type {{secret:password}}", "Shows {{secret:password}}"));

        broadcaster.itemStarted(Kind.STEP, "Open the cart");

        assertThat(broadcaster.snapshot().outline()).isNull();
    }

    @Test
    void itemOutlined_shouldPublishNothing_beforeAnyItemStarted() throws Exception {
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);

        broadcaster.itemOutlined(List.of(), List.of(ProcedureNode.pending(UUID.randomUUID(), "Log in", null, true, false, 0)));
        broadcaster.log("after");

        assertThat(client.awaitEvents(2)).extracting(SentEvent::name).containsExactly("snapshot", "log-line");
        assertThat(broadcaster.snapshot().outline()).isNull();
    }

    @Test
    void taskFinished_shouldClearTheActiveProcedures() {
        broadcaster.taskStarted(testCase());
        broadcaster.procedureStarted(UUID.randomUUID());

        broadcaster.taskFinished(RunStatus.ABORTED, null);

        assertThat(broadcaster.snapshot().activeProcedures()).isEmpty();
    }

    @Test
    void stepResolved_shouldAttachTheRedactedResolutionToTheCurrentItem() throws Exception {
        SecretRegistry.global().register("password", "s3cr3t-value");
        broadcaster.taskStarted(testCase());
        broadcaster.itemStarted(Kind.STEP, "Click Save");
        broadcaster.itemFinished(Kind.STEP, "Click Save", PASSED, null);
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);
        var resolution = new StepResolution(UUID.randomUUID(), 0, StepType.TEST_STEP, "Click Save s3cr3t-value", Source.KNOWLEDGE,
                MatchConfidence.HIGH, List.of(), false, false, null, Outcome.SUCCEEDED, 10);

        broadcaster.stepResolved(resolution);

        var event = client.awaitEvents(2).get(1);
        assertThat(event.name()).isEqualTo("step-resolved");
        assertThat(json(event).get("index").asInt()).isEqualTo(1);
        assertThat(json(event).get("item").get("resolution").get("itemDescription").asText())
                .isEqualTo("Click Save {{secret:password}}");
        assertThat(broadcaster.snapshot().items().get(1).resolution().get("source").asText()).isEqualTo("KNOWLEDGE");
    }

    @Test
    void stepResolved_shouldPublishNothing_beforeAnyItemStarted() throws Exception {
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);

        broadcaster.stepResolved(new StepResolution(UUID.randomUUID(), 0, StepType.TEST_STEP, "Click", Source.UNRESOLVED, null,
                List.of(), false, false, null, Outcome.FAILED, 0));
        broadcaster.log("after");

        assertThat(client.awaitEvents(2)).extracting(SentEvent::name).containsExactly("snapshot", "log-line");
    }

    @Test
    void subscribe_whilePublishing_shouldNeitherMissNorDuplicateADelta_andTheReplacedSubscriberShouldGetDisplacedLast()
            throws Exception {
        var firstClient = new RecordingClient();
        broadcaster.subscribe(firstClient.mock, SESSION_ID, DISPLACEMENT);
        var secondClient = new RecordingClient();
        int lineCount = 900;
        var publisher = Thread.ofVirtual().start(() -> IntStream.range(0, lineCount).forEach(i -> broadcaster.log("line " + i)));

        firstClient.awaitEvents(50);
        broadcaster.subscribe(secondClient.mock, SESSION_ID, DISPLACEMENT);
        publisher.join(WAIT_MILLIS);
        broadcaster.log("end");

        var received = secondClient.awaitEventWithLine("end");
        var seen = new ArrayList<String>();
        json(received.getFirst()).get("state").get("logTail").forEach(line -> seen.add(line.asText()));
        received.stream().skip(1).map(event -> json(event).get("line").asText()).forEach(seen::add);
        assertThat(seen.removeLast()).isEqualTo("end");
        // The snapshot holds the newest lines published before the subscription and the deltas every later one, so together
        // they form one gap-free run of lines ending with the last one
        assertThat(seen).containsExactlyElementsOf(
                IntStream.range(lineCount - seen.size(), lineCount).mapToObj(i -> "line " + i).toList());
        verify(firstClient.mock, timeout(WAIT_MILLIS)).close();
        assertThat(firstClient.events.getLast().name()).isEqualTo("displaced");
        assertThat(json(firstClient.events.getLast()).get("reason").asText()).isEqualTo("other-tab");
    }

    @Test
    void publishing_shouldNeverBlock_andCloseASubscriberThatStopsReading_after1000QueuedEvents() throws Exception {
        var release = new CountDownLatch(1);
        var client = new RecordingClient(release);
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);

        long started = System.nanoTime();
        IntStream.rangeClosed(0, ExecutionStateBroadcaster.CLIENT_QUEUE_CAPACITY + 1).forEach(i -> broadcaster.log("line " + i));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        verify(client.mock, timeout(WAIT_MILLIS)).close();
        assertThat(elapsedMillis).isLessThan(WAIT_MILLIS);
        release.countDown();
    }

    @Test
    void log_shouldKeepOnlyTheNewest500Lines() {
        IntStream.range(0, 600).forEach(i -> broadcaster.log("line " + i));

        var logTail = broadcaster.snapshot().logTail();

        assertThat(logTail).hasSize(ExecutionStateBroadcaster.LOG_TAIL_SIZE);
        assertThat(logTail.getFirst()).isEqualTo("line 100");
        assertThat(logTail.getLast()).isEqualTo("line 599");
    }

    @Test
    void publishing_shouldRedactARegisteredSecret_inALogLineAStatusMessageAndAnItemDescription() throws Exception {
        SecretRegistry.global().register("password", "s3cr3t-value");
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);

        broadcaster.log("typed s3cr3t-value into the field");
        broadcaster.status("Waiting with s3cr3t-value");
        broadcaster.itemStarted(Kind.STEP, "Type s3cr3t-value");

        var state = broadcaster.snapshot();
        assertThat(state.logTail()).containsExactly("typed {{secret:password}} into the field");
        assertThat(state.statusMessage()).isEqualTo("Waiting with {{secret:password}}");
        assertThat(state.items()).extracting(TimelineItem::description).containsExactly("Type {{secret:password}}");
        assertThat(client.awaitEvents(4)).extracting(SentEvent::data).noneMatch(data -> data.contains("s3cr3t-value"));
    }

    @Test
    void itemEvents_shouldFollowTheTimeline_includingARerunAndAnUnplannedItem() {
        broadcaster.taskStarted(testCase());

        broadcaster.itemStarted(Kind.STEP, "Click Save");
        broadcaster.itemFinished(Kind.STEP, "Click Save", FAILED, "not found");
        broadcaster.itemStarted(Kind.STEP, "Click Save");
        broadcaster.itemFinished(Kind.STEP, "Click Save", PASSED, null);
        broadcaster.itemStarted(Kind.STEP, "Close the dialog");

        var state = broadcaster.snapshot();
        assertThat(state.items()).extracting(TimelineItem::description, TimelineItem::status).containsExactly(
                tuple("User is logged in", PENDING),
                tuple("Click Save", PASSED),
                tuple("Open the cart", PENDING),
                tuple("Close the dialog", RUNNING));
        assertThat(state.currentItem()).isEqualTo(3);
    }

    @Test
    void taskFinished_shouldEndTheRun_withItsStatusAndMessage() {
        broadcaster.taskStarted(testCase());
        broadcaster.status("busy");

        broadcaster.taskFinished(RunStatus.ABORTED, null);

        var state = broadcaster.snapshot();
        assertThat(state.runStatus()).isEqualTo(RunStatus.ABORTED);
        assertThat(state.statusMessage()).isNull();
    }

    @Test
    void taskFinished_withoutAStartedTask_shouldReplaceThePreviousRun_insteadOfEndingIt() throws Exception {
        broadcaster.taskStarted(testCase());
        broadcaster.itemStarted(Kind.STEP, "Click Save");
        broadcaster.log("a line of the previous run");
        broadcaster.taskFinished(RunStatus.PASSED, null);
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);
        client.awaitEvents(1);

        broadcaster.taskFinished(RunStatus.ERROR, "Failed to extract a valid test case from the provided message.");

        var state = broadcaster.snapshot();
        assertThat(state.runStatus()).isEqualTo(RunStatus.ERROR);
        assertThat(state.resultMessage()).isEqualTo("Failed to extract a valid test case from the provided message.");
        assertThat(state.testCaseName()).isNull();
        assertThat(state.items()).isEmpty();
        assertThat(state.currentItem()).isEqualTo(-1);
        assertThat(state.logTail()).isEmpty();
        var replacement = client.awaitEvents(2).get(1);
        assertThat(replacement.name()).isEqualTo("snapshot");
        assertThat(json(replacement).get("state").get("runStatus").asText()).isEqualTo("ERROR");
    }

    @Test
    void keepAlive_shouldCloseTheSubscriber_whenItsSessionWasInvalidated() throws Exception {
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);
        client.awaitEvents(1);
        when(auth.isActive(SESSION_ID)).thenReturn(false);

        broadcaster.keepAlive();

        verify(client.mock, timeout(WAIT_MILLIS)).close();
    }

    @Test
    void keepAlive_shouldQueueAComment_andRefreshTheSession_whileTheSessionIsValid() throws Exception {
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);

        broadcaster.keepAlive();

        verify(client.mock, timeout(WAIT_MILLIS)).sendComment("keep-alive");
        verify(auth).touch(SESSION_ID);
    }

    @Test
    void closeSession_shouldDeliverTheTakeoverNotice_beforeClosingTheStream() throws Exception {
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);
        client.awaitEvents(1);
        when(auth.isActive(SESSION_ID)).thenReturn(false);

        broadcaster.closeSession(SESSION_ID, new Displaced(Reason.TAKEN_OVER, Instant.now(), "10.0.0.2", "other browser"));

        verify(client.mock, timeout(WAIT_MILLIS)).close();
        assertThat(client.events.getLast().name()).isEqualTo("displaced");
        assertThat(json(client.events.getLast()).get("sourceIp").asText()).isEqualTo("10.0.0.2");
    }

    @Test
    void closeSession_shouldCloseTheStream_withoutANotice_afterALogout() throws Exception {
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);
        client.awaitEvents(1);

        broadcaster.closeSession(SESSION_ID, null);

        verify(client.mock, timeout(WAIT_MILLIS)).close();
        assertThat(client.events).extracting(SentEvent::name).doesNotContain("displaced");
    }

    @Test
    void close_shouldCloseTheClient_andShutDownTheDrainExecutor() throws Exception {
        var client = new RecordingClient();
        broadcaster.subscribe(client.mock, SESSION_ID, DISPLACEMENT);
        client.awaitEvents(1);

        broadcaster.close();

        verify(client.mock, timeout(WAIT_MILLIS)).close();
        assertThatThrownBy(() -> broadcaster.subscribe(new RecordingClient().mock, SESSION_ID, DISPLACEMENT))
                .isInstanceOf(RejectedExecutionException.class);
    }

    private static TestCase testCase() {
        return new TestCase("Checkout", List.of(new Precondition("User is logged in", List.of())), List.of(),
                List.of(new TestStep("Click Save", List.of(), ""), new TestStep("Open the cart", List.of(), "")));
    }

    private static JsonNode json(SentEvent event) {
        try {
            return PanelJson.MAPPER.readTree(event.data());
        } catch (Exception e) {
            throw new AssertionError("Not JSON: " + event.data(), e);
        }
    }

    private record SentEvent(String name, String data) {
    }

    /** A mocked event stream that records what the drain task sends, optionally blocking its first send until released. */
    private static final class RecordingClient {
        private final SseClient mock = mock(SseClient.class);
        private final List<SentEvent> events = new CopyOnWriteArrayList<>();

        private RecordingClient() {
            this(null);
        }

        private RecordingClient(CountDownLatch blockFirstSendUntil) {
            doAnswer(invocation -> {
                if (blockFirstSendUntil != null) {
                    blockFirstSendUntil.await(WAIT_MILLIS, TimeUnit.MILLISECONDS);
                }
                synchronized (events) {
                    events.add(new SentEvent(invocation.getArgument(0), invocation.getArgument(1)));
                    events.notifyAll();
                }
                return null;
            }).when(mock).sendEvent(anyString(), any());
        }

        private List<SentEvent> awaitEvents(int count) throws InterruptedException {
            return awaitUntil(received -> received.size() >= count);
        }

        private List<SentEvent> awaitEventWithLine(String line) throws InterruptedException {
            return awaitUntil(received -> received.stream().anyMatch(event -> event.data().contains("\"line\":\"%s\"".formatted(line))));
        }

        private List<SentEvent> awaitUntil(Predicate<List<SentEvent>> condition) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
            synchronized (events) {
                while (!condition.test(events)) {
                    long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                    if (remainingMillis <= 0) {
                        throw new AssertionError("The expected events did not arrive in time; received %d".formatted(events.size()));
                    }
                    events.wait(remainingMillis);
                }
                return List.copyOf(events);
            }
        }
    }
}
