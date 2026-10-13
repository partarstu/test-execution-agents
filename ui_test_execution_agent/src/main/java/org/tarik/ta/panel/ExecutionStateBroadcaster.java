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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.sse.SseClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.security.SecretRegistry;
import org.tarik.ta.dto.panel.ItemOutline;
import org.tarik.ta.dto.panel.PanelEvent;
import org.tarik.ta.dto.panel.PanelEvent.Displaced;
import org.tarik.ta.dto.panel.PanelEvent.ItemFinished;
import org.tarik.ta.dto.panel.PanelEvent.ItemOutlined;
import org.tarik.ta.dto.panel.PanelEvent.ItemStarted;
import org.tarik.ta.dto.panel.PanelEvent.LogLine;
import org.tarik.ta.dto.panel.PanelEvent.PromptClosed;
import org.tarik.ta.dto.panel.PanelEvent.ProcedureFinished;
import org.tarik.ta.dto.panel.PanelEvent.ProcedureStarted;
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
import org.tarik.ta.dto.panel.TimelineItem.Kind;
import org.tarik.ta.dto.panel.prompt.PromptView;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.tarik.ta.dto.panel.RunStatus.IDLE;
import static org.tarik.ta.dto.panel.RunStatus.RUNNING;
import static org.tarik.ta.dto.panel.TimelineItem.Status.PENDING;

/**
 * Keeps the panel's run state and streams it to the one connected tab: a snapshot on connect, then deltas. Thread-safe: state
 * changes and subscriptions share one lock, and each tab is written only by its own drain task, so a publisher never waits for a
 * browser.
 */
@Singleton
final class ExecutionStateBroadcaster implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionStateBroadcaster.class);
    static final int LOG_TAIL_SIZE = 500;
    static final int CLIENT_QUEUE_CAPACITY = 1000;
    private static final String KEEP_ALIVE_COMMENT = "keep-alive";
    private static final int NO_ITEM = -1;
    private static final int NO_NODE = -1;

    private final PanelAuth auth;
    private final InstantSource clock;
    private final ExecutorService drainExecutor = newVirtualThreadPerTaskExecutor();
    private final ReentrantLock lock = new ReentrantLock();
    private RunStatus runStatus = IDLE;
    private @Nullable String testCaseName;
    private final List<TimelineItem> items = new ArrayList<>();
    private int currentItem = NO_ITEM;
    private @Nullable String statusMessage;
    private @Nullable String resultMessage;
    private final ArrayDeque<String> logTail = new ArrayDeque<>(LOG_TAIL_SIZE);
    private @Nullable OpenPrompt openPrompt;
    private final ArrayDeque<UUID> activeProcedures = new ArrayDeque<>();
    private @Nullable ItemOutline outline;
    private int lastStartedNode = NO_NODE;
    private final Map<Integer, Instant> nodeStartedAt = new HashMap<>();
    private @Nullable Subscriber subscriber;

    @Inject
    ExecutionStateBroadcaster(PanelAuth auth) {
        this(auth, InstantSource.system());
    }

    ExecutionStateBroadcaster(@NotNull PanelAuth auth, @NotNull InstantSource clock) {
        this.auth = auth;
        this.clock = clock;
    }

    void taskStarted(@NotNull TestCase testCase) {
        var timeline = Stream.concat(
                testCase.preconditions().stream().map(precondition -> pendingItem(Kind.PRECONDITION, precondition.description())),
                testCase.testSteps().stream().map(step -> pendingItem(Kind.STEP, step.stepDescription()))).toList();
        var name = redact(testCase.name());
        update(() -> {
            clearRun();
            runStatus = RUNNING;
            testCaseName = name;
            items.addAll(timeline);
            return new TaskStarted(name, timeline);
        });
    }

    void itemStarted(@NotNull Kind kind, @NotNull String description) {
        var redactedDescription = redact(description);
        update(() -> {
            int index = indexForStart(kind, redactedDescription);
            var item = new TimelineItem(kind, redactedDescription, TimelineItem.Status.RUNNING, null);
            putItem(index, item);
            currentItem = index;
            clearOutline();
            return new ItemStarted(index, item);
        });
    }

    void itemFinished(@NotNull Kind kind, @NotNull String description, TimelineItem.@NotNull Status status, @Nullable String errorMessage) {
        var redactedDescription = redact(description);
        var redactedError = errorMessage == null ? null : redact(errorMessage);
        update(() -> {
            int index = isCurrentItem(kind, redactedDescription) ? currentItem : indexForStart(kind, redactedDescription);
            var item = new TimelineItem(kind, redactedDescription, status, redactedError);
            putItem(index, item);
            return new ItemFinished(index, item);
        });
    }

    void log(@NotNull String line) {
        var redactedLine = redact(line);
        update(() -> {
            if (logTail.size() == LOG_TAIL_SIZE) {
                logTail.removeFirst();
            }
            logTail.addLast(redactedLine);
            return new LogLine(redactedLine);
        });
    }

    void status(@Nullable String message) {
        var redactedMessage = message == null ? null : redact(message);
        update(() -> {
            statusMessage = redactedMessage;
            return new Status(redactedMessage);
        });
    }

    /** Shows the prompt, with every text of its payload redacted, until {@link #promptClosed} closes it. */
    void promptOpened(@NotNull PendingPrompt<?> prompt) {
        var spec = prompt.spec();
        var opened = new OpenPrompt(prompt.id(), spec.type(), PanelJson.redactedTree(spec.payload()), prompt.openedAt(),
                prompt.deadline(), prompt.pausedRemaining(), spec.countdown(), prompt.isHeld(), spec.defaultAnswer() != null);
        update(() -> {
            openPrompt = opened;
            return new PromptOpened(viewOf(opened));
        });
    }

    void promptClosed(@NotNull UUID id) {
        update(() -> {
            if (openPrompt != null && openPrompt.id().equals(id)) {
                openPrompt = null;
            }
            return new PromptClosed(id);
        });
    }

    /** Shows the decomposition of the current item, with every text redacted, until the next item or run starts. */
    void itemOutlined(@NotNull List<String> testData, @NotNull List<ProcedureNode> procedures) {
        var redactedData = testData.stream().map(ExecutionStateBroadcaster::redact).toList();
        var redactedProcedures = procedures.stream()
                .map(node -> node.withTexts(redact(node.description()), node.expectedResults() == null ? null : redact(node.expectedResults())))
                .toList();
        update(() -> {
            if (currentItem == NO_ITEM) {
                return null;
            }
            clearOutline();
            outline = new ItemOutline(currentItem, redactedData, redactedProcedures);
            return new ItemOutlined(outline);
        });
    }

    void procedureStarted(@NotNull UUID procedureId) {
        update(() -> {
            activeProcedures.addLast(procedureId);
            int node = startedNode(procedureId);
            if (node != NO_NODE) {
                setNode(node, outlineNode(node).withStatus(ProcedureNode.Status.RUNNING, null));
                nodeStartedAt.put(node, clock.instant());
                lastStartedNode = node;
            }
            return new ProcedureStarted(procedureId, node);
        });
    }

    void procedureFinished(@NotNull UUID procedureId, @NotNull ProcedureOutcome outcome) {
        update(() -> {
            activeProcedures.removeLastOccurrence(procedureId);
            int node = runningNode(procedureId);
            Long durationMillis = null;
            if (node != NO_NODE) {
                var startedAt = nodeStartedAt.remove(node);
                durationMillis = startedAt == null ? null : Duration.between(startedAt, clock.instant()).toMillis();
                setNode(node, outlineNode(node).withStatus(ProcedureNode.Status.of(outcome), durationMillis));
            }
            return new ProcedureFinished(procedureId, outcome, node, durationMillis);
        });
    }

    /** Attaches the resolution, with every text redacted, to the current item; it is recorded right after that item ended. */
    void stepResolved(@NotNull StepResolution resolution) {
        var redactedResolution = PanelJson.redactedTree(resolution);
        update(() -> {
            if (currentItem == NO_ITEM) {
                return null;
            }
            var item = items.get(currentItem).withResolution(redactedResolution);
            items.set(currentItem, item);
            return new StepResolved(currentItem, item);
        });
    }

    /** Ends the running task; a request that ended before its test case started replaces the previous run instead of ending it. */
    void taskFinished(@NotNull RunStatus status, @Nullable String message) {
        var redactedMessage = message == null ? null : redact(message);
        update(() -> {
            boolean taskWasStarted = runStatus == RUNNING;
            if (!taskWasStarted) {
                clearRun();
            }
            runStatus = status;
            resultMessage = redactedMessage;
            statusMessage = null;
            activeProcedures.clear();
            return taskWasStarted ? new TaskFinished(status, redactedMessage) : new Snapshot(snapshotUnderLock());
        });
    }

    /** Makes the client the one connected tab, starting with a snapshot; the tab it replaces receives {@code displacement} last. */
    void subscribe(@NotNull SseClient client, @NotNull String sessionId, @NotNull Displaced displacement) {
        var newSubscriber = new Subscriber(client, sessionId, displacement.sourceIp());
        lock.lock();
        try {
            var replaced = subscriber;
            if (replaced != null) {
                displace(replaced, displacement);
                LOG.info("Operator panel event stream of {} replaced by another tab of {}", replaced.sourceIp, displacement.sourceIp());
            }
            serialize(new Snapshot(snapshotUnderLock())).ifPresent(newSubscriber.queue::offer);
            subscriber = newSubscriber;
            client.onClose(() -> drop(newSubscriber));
            newSubscriber.drain = drainExecutor.submit(() -> drain(newSubscriber));
        } finally {
            lock.unlock();
        }
    }

    /** Ends the stream of the session, delivering the notice first when it was taken over. */
    void closeSession(@NotNull String sessionId, @Nullable Displaced displaced) {
        Subscriber ended;
        lock.lock();
        try {
            if (subscriber == null || !subscriber.sessionId.equals(sessionId)) {
                return;
            }
            ended = subscriber;
            if (displaced != null) {
                displace(ended, displaced);
                return;
            }
        } finally {
            lock.unlock();
        }
        drop(ended);
    }

    /** Queues a comment so a quiet stream outlives the connection idle timeout, and keeps the watching session alive. */
    void keepAlive() {
        var connected = currentSubscriber();
        if (connected == null) {
            return;
        }
        if (!auth.isActive(connected.sessionId)) {
            drop(connected);
            return;
        }
        auth.touch(connected.sessionId);
        if (!connected.queue.offer(new Outgoing.KeepAlive())) {
            drop(connected);
        }
    }

    @NotNull PanelState snapshot() {
        lock.lock();
        try {
            return snapshotUnderLock();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        var connected = currentSubscriber();
        if (connected != null) {
            drop(connected);
        }
        drainExecutor.shutdownNow();
        drainExecutor.close();
    }

    private void update(EventProducer producer) {
        lock.lock();
        try {
            var event = producer.applyAndDescribe();
            if (event == null) {
                return;
            }
            var connected = subscriber;
            if (connected != null && serialize(event).map(outgoing -> !connected.queue.offer(outgoing)).orElse(false)) {
                drop(connected);
                LOG.warn("Closed the operator panel event stream of {}: {} events were waiting for it", connected.sourceIp,
                        CLIENT_QUEUE_CAPACITY);
            }
        } finally {
            lock.unlock();
        }
    }

    private void drain(Subscriber connected) {
        try {
            while (!connected.client.terminated()) {
                var outgoing = connected.queue.take();
                var displacement = connected.displacement;
                if (displacement != null) {
                    connected.client.sendEvent(displacement.name(), displacement.json());
                    return;
                }
                if (!auth.isActive(connected.sessionId)) {
                    return;
                }
                switch (outgoing) {
                    case Outgoing.Event(var name, var json) -> connected.client.sendEvent(name, json);
                    case Outgoing.KeepAlive _ -> connected.client.sendComment(KEEP_ALIVE_COMMENT);
                    case Outgoing.Wake _ -> {
                    }
                }
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } finally {
            drop(connected);
        }
    }

    // Caller holds the lock
    private void displace(Subscriber replaced, Displaced displacement) {
        serialize(displacement).ifPresentOrElse(outgoing -> {
            replaced.displacement = outgoing;
            replaced.queue.clear();
            replaced.queue.offer(new Outgoing.Wake());
            subscriber = subscriber == replaced ? null : subscriber;
        }, () -> drop(replaced));
    }

    private void drop(Subscriber connected) {
        if (!connected.closed.compareAndSet(false, true)) {
            return;
        }
        lock.lock();
        try {
            if (subscriber == connected) {
                subscriber = null;
            }
        } finally {
            lock.unlock();
        }
        connected.client.close();
        var drainTask = connected.drain;
        if (drainTask != null) {
            drainTask.cancel(true);
        }
    }

    private @Nullable Subscriber currentSubscriber() {
        lock.lock();
        try {
            return subscriber;
        } finally {
            lock.unlock();
        }
    }

    // Caller holds the lock
    private void clearRun() {
        testCaseName = null;
        items.clear();
        currentItem = NO_ITEM;
        statusMessage = null;
        resultMessage = null;
        logTail.clear();
        activeProcedures.clear();
        clearOutline();
    }

    // Caller holds the lock
    private void clearOutline() {
        outline = null;
        lastStartedNode = NO_NODE;
        nodeStartedAt.clear();
    }

    // Execution walks the outline in pre-order, so a started procedure is the next node with its id after the last started one
    private int startedNode(UUID procedureId) {
        if (outline == null) {
            return NO_NODE;
        }
        var procedures = outline.procedures();
        for (int index = lastStartedNode + 1; index < procedures.size(); index++) {
            if (procedures.get(index).id().equals(procedureId)) {
                return index;
            }
        }
        return NO_NODE;
    }

    // Procedures finish innermost first, so the finished one is the last running node with its id
    private int runningNode(UUID procedureId) {
        if (outline == null) {
            return NO_NODE;
        }
        var procedures = outline.procedures();
        for (int index = procedures.size() - 1; index >= 0; index--) {
            var node = procedures.get(index);
            if (node.id().equals(procedureId) && node.status() == ProcedureNode.Status.RUNNING) {
                return index;
            }
        }
        return NO_NODE;
    }

    private ProcedureNode outlineNode(int index) {
        return requireNonNull(outline).procedures().get(index);
    }

    private void setNode(int index, ProcedureNode node) {
        outline = requireNonNull(outline).withProcedure(index, node);
    }

    private PanelState snapshotUnderLock() {
        var prompt = openPrompt == null ? null : viewOf(openPrompt);
        return new PanelState(runStatus, testCaseName, items, currentItem, statusMessage, resultMessage, List.copyOf(logTail), prompt,
                List.copyOf(activeProcedures), outline);
    }

    // The browser counts down from the time left when the view is sent, so its own clock never matters
    private PromptView viewOf(OpenPrompt prompt) {
        var now = clock.instant();
        var pausedRemaining = prompt.pausedRemaining();
        var remaining = pausedRemaining != null ? pausedRemaining : Duration.between(now, prompt.deadline());
        long remainingMillis = Math.max(0, remaining.toMillis());
        long waitedMillis = Math.max(0, Duration.between(prompt.openedAt(), now).toMillis());
        var countdown = prompt.countdown();
        return new PromptView(prompt.id(), prompt.type(), prompt.payload(), remainingMillis, waitedMillis, countdown != null,
                countdown == null ? 0 : countdown.toMillis(), prompt.held(), pausedRemaining != null, prompt.hasDefault());
    }

    private int indexForStart(Kind kind, String description) {
        int firstPending = NO_ITEM;
        int lastMatch = NO_ITEM;
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            if (item.kind() == kind && item.description().equals(description)) {
                lastMatch = index;
                if (firstPending == NO_ITEM && item.status() == PENDING) {
                    firstPending = index;
                }
            }
        }
        if (firstPending != NO_ITEM) {
            return firstPending;
        }
        return lastMatch != NO_ITEM ? lastMatch : items.size();
    }

    private boolean isCurrentItem(Kind kind, String description) {
        if (currentItem == NO_ITEM) {
            return false;
        }
        var item = items.get(currentItem);
        return item.kind() == kind && item.description().equals(description);
    }

    private void putItem(int index, TimelineItem item) {
        if (index == items.size()) {
            items.add(item);
        } else {
            items.set(index, item);
        }
    }

    private static TimelineItem pendingItem(Kind kind, String description) {
        return new TimelineItem(kind, redact(description), PENDING, null);
    }

    private static String redact(String text) {
        return SecretRegistry.global().redact(text);
    }

    private static Optional<Outgoing.Event> serialize(PanelEvent event) {
        try {
            return Optional.of(new Outgoing.Event(eventName(event), PanelJson.MAPPER.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            LOG.error("Could not serialize the operator panel event {}", eventName(event), e);
            return Optional.empty();
        }
    }

    static String eventName(PanelEvent event) {
        return switch (event) {
            case Snapshot _ -> "snapshot";
            case TaskStarted _ -> "task-started";
            case ItemStarted _ -> "item-started";
            case ItemFinished _ -> "item-finished";
            case LogLine _ -> "log-line";
            case Status _ -> "status";
            case PromptOpened _ -> "prompt-opened";
            case PromptClosed _ -> "prompt-closed";
            case TaskFinished _ -> "task-finished";
            case Displaced _ -> "displaced";
            case ItemOutlined _ -> "item-outlined";
            case ProcedureStarted _ -> "procedure-started";
            case ProcedureFinished _ -> "procedure-finished";
            case StepResolved _ -> "step-resolved";
        };
    }

    private record OpenPrompt(UUID id, String type, JsonNode payload, Instant openedAt, Instant deadline,
                              @Nullable Duration pausedRemaining, @Nullable Duration countdown, boolean held, boolean hasDefault) {
    }

    @FunctionalInterface
    private interface EventProducer {
        @Nullable PanelEvent applyAndDescribe();
    }

    private sealed interface Outgoing {
        record Event(String name, String json) implements Outgoing {
        }

        record KeepAlive() implements Outgoing {
        }

        record Wake() implements Outgoing {
        }
    }

    private static final class Subscriber {
        private final SseClient client;
        private final String sessionId;
        private final String sourceIp;
        private final LinkedBlockingQueue<Outgoing> queue = new LinkedBlockingQueue<>(CLIENT_QUEUE_CAPACITY);
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Outgoing.@Nullable Event displacement;
        private volatile @Nullable Future<?> drain;

        private Subscriber(SseClient client, String sessionId, String sourceIp) {
            this.client = client;
            this.sessionId = sessionId;
            this.sourceIp = sourceIp;
        }
    }
}
