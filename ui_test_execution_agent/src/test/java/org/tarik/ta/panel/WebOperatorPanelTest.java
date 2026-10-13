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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.panel.RunStatus;
import org.tarik.ta.dto.panel.TimelineItem;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Acknowledgement;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Message;
import org.tarik.ta.dto.panel.prompt.PromptPayload.StartStep;
import org.tarik.ta.dto.panel.prompt.PromptView;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;
import org.tarik.ta.panel.ProcedureEditorPrompt.EditorServices;
import org.tarik.ta.tools.UiElementRefinementHelper;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebOperatorPanelTest {
    private static final TestStep STEP = new TestStep("Click Save", List.of(), "Saved");
    private static final TestCase TEST_CASE = new TestCase("Checkout", List.of(new Precondition("User is logged in", List.of())),
            List.of(), List.of(STEP));
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Message MESSAGE = new Message("Note", "Something happened", PopupType.INFO, null);

    private final TestClock clock = new TestClock();
    private final ExecutionStateBroadcaster broadcaster = new ExecutionStateBroadcaster(mock(PanelAuth.class), clock);
    private final UiTestAgentConfig config = mock(UiTestAgentConfig.class);
    private final EditorServices editorServices = new EditorServices(mock(KnowledgeIngestionService.class),
            mock(UiElementRepository.class), mock(ProcedureUsageByTestCaseTrackingService.class), mock(UiElementRefinementHelper.class));
    private final ExecutorService executor = newVirtualThreadPerTaskExecutor();
    private final WebOperatorPanel panel = panel(Duration.ofMinutes(5));

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        broadcaster.close();
    }

    @Test
    void observe_shouldForwardEveryEventToTheA2aEmitter_andShowItOnTheTimeline() {
        var a2aEmitter = mock(StreamingEventEmitter.class);
        panel.taskStarted(TEST_CASE);
        var emitter = panel.observe(a2aEmitter);
        var preconditionResult = new PreconditionResult("User is logged in", true, null, Instant.now(), Instant.now());
        var stepResult = new TestStepResult(STEP, TestStepResultStatus.FAILURE, "Save is disabled", null, Instant.now(), Instant.now());

        emitter.emitPreconditionStarted("User is logged in");
        emitter.emitPreconditionResult(preconditionResult);
        emitter.emitStepStarted(STEP);
        emitter.emitStepResult(stepResult);
        emitter.emitLog("a line");
        emitter.emitStatus("Waiting for operator: confirmation");

        var inOrder = inOrder(a2aEmitter);
        inOrder.verify(a2aEmitter).emitPreconditionStarted("User is logged in");
        inOrder.verify(a2aEmitter).emitPreconditionResult(preconditionResult);
        inOrder.verify(a2aEmitter).emitStepStarted(STEP);
        inOrder.verify(a2aEmitter).emitStepResult(stepResult);
        inOrder.verify(a2aEmitter).emitLog("a line");
        inOrder.verify(a2aEmitter).emitStatus("Waiting for operator: confirmation");
        var state = broadcaster.snapshot();
        assertThat(state.items()).extracting(TimelineItem::description, TimelineItem::status, TimelineItem::errorMessage)
                .containsExactly(tuple("User is logged in", TimelineItem.Status.PASSED, null),
                        tuple("Click Save", TimelineItem.Status.FAILED, "Save is disabled"));
        assertThat(state.logTail()).containsExactly("a line");
        assertThat(state.statusMessage()).isEqualTo("Waiting for operator: confirmation");
    }

    @Test
    void taskFinished_shouldShowTheResultStatusAndMessage() {
        var shownStatuses = Map.of(TestExecutionStatus.PASSED, RunStatus.PASSED, TestExecutionStatus.FAILED, RunStatus.FAILED,
                TestExecutionStatus.ERROR, RunStatus.ERROR);
        shownStatuses.forEach((resultStatus, shownStatus) -> {
            var result = mock(TestExecutionResult.class);
            when(result.getTestExecutionStatus()).thenReturn(resultStatus);
            when(result.getGeneralErrorMessage()).thenReturn("message");
            panel.taskStarted(TEST_CASE);

            panel.taskFinished(result);

            assertThat(broadcaster.snapshot().runStatus()).isEqualTo(shownStatus);
            assertThat(broadcaster.snapshot().resultMessage()).isEqualTo("message");
        });
    }

    @Test
    void taskAborted_shouldLeaveNoRunningTask() {
        panel.taskStarted(TEST_CASE);
        assertThat(broadcaster.snapshot().runStatus()).isEqualTo(RunStatus.RUNNING);

        panel.taskAborted();

        assertThat(broadcaster.snapshot().runStatus()).isEqualTo(RunStatus.ABORTED);
    }

    @Test
    void taskStarted_shouldListThePreconditionsAndStepsAsPending() {
        panel.taskStarted(TEST_CASE);

        var state = broadcaster.snapshot();
        assertThat(state.testCaseName()).isEqualTo("Checkout");
        assertThat(state.currentItem()).isEqualTo(-1);
        assertThat(state.items()).extracting(TimelineItem::kind, TimelineItem::status).containsExactly(
                tuple(TimelineItem.Kind.PRECONDITION, TimelineItem.Status.PENDING),
                tuple(TimelineItem.Kind.STEP, TimelineItem.Status.PENDING));
    }

    @Test
    void ask_shouldReturnTheAnswer_andCloseThePrompt_whenTheOperatorAnswers() throws Exception {
        var asked = askAsync(panel, startStep());
        var prompt = awaitOpenPrompt();

        assertThat(prompt.type()).isEqualTo("start-step");
        answerOpenPrompt(new StartStep.Answer(1));

        assertThat(asked.get(WAIT.toSeconds(), SECONDS).stepIndex()).isEqualTo(1);
        assertThat(broadcaster.snapshot().prompt()).isNull();
        assertThat(panel.pendingPrompt(prompt.id())).isEmpty();
    }

    @Test
    void ask_shouldApplyTheDefault_whenTheCountdownElapses() throws Exception {
        var realClockPanel = new WebOperatorPanel(broadcaster, config, editorServices, InstantSource.system(), Duration.ofMinutes(5));

        var asked = askAsync(realClockPanel, PromptSpec.of(MESSAGE, Acknowledgement.class, new PromptImages())
                .withDefault(new Acknowledgement()).withCountdown(Duration.ofMillis(50)));

        assertThat(asked.get(WAIT.toSeconds(), SECONDS)).isEqualTo(new Acknowledgement());
        assertThat(broadcaster.snapshot().prompt()).isNull();
    }

    @Test
    void snapshot_shouldRecomputeTheRemainingTime_whenItIsSentLater() {
        askAsync(panel, startStep().withDefault(new StartStep.Answer(0)).withCountdown(Duration.ofSeconds(10)));
        var opened = awaitOpenPrompt();
        assertThat(opened.remainingMillis()).isEqualTo(10_000);
        assertThat(opened.waitedMillis()).isZero();
        assertThat(opened.countdown()).isTrue();
        assertThat(opened.hasDefault()).isTrue();

        clock.advance(Duration.ofSeconds(4));

        assertThat(broadcaster.snapshot().prompt().remainingMillis()).isEqualTo(6_000);
        assertThat(broadcaster.snapshot().prompt().waitedMillis()).isEqualTo(4_000);
        answerOpenPrompt(new StartStep.Answer(0));
    }

    @Test
    void hold_shouldMoveTheCountdownToTheUpperBound_andApplyTheDefaultThere() throws Exception {
        var shortBoundPanel = new WebOperatorPanel(broadcaster, config, editorServices, InstantSource.system(), Duration.ofMillis(400));
        var asked = askAsync(shortBoundPanel, PromptSpec.of(MESSAGE, Acknowledgement.class, new PromptImages())
                .withDefault(new Acknowledgement()).withCountdown(Duration.ofMillis(100)));
        var opened = awaitOpenPrompt();
        long start = System.nanoTime();

        assertThat(shortBoundPanel.hold(shortBoundPanel.pendingPrompt(opened.id()).orElseThrow())).isTrue();

        assertThat(broadcaster.snapshot().prompt().held()).isTrue();
        assertThat(asked.get(WAIT.toSeconds(), SECONDS)).isEqualTo(new Acknowledgement());
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThan(Duration.ofMillis(200));
    }

    @Test
    void resume_shouldApplyTheDefaultWhenTheTimeLeftAtThePauseRunsOut_longBeforeTheUpperBound() throws Exception {
        var realClockPanel = new WebOperatorPanel(broadcaster, config, editorServices, InstantSource.system(), Duration.ofMinutes(5));
        var asked = askAsync(realClockPanel, PromptSpec.of(MESSAGE, Acknowledgement.class, new PromptImages())
                .withDefault(new Acknowledgement()).withCountdown(Duration.ofMillis(300)));
        var prompt = realClockPanel.pendingPrompt(awaitOpenPrompt().id()).orElseThrow();

        assertThat(realClockPanel.pause(prompt)).isTrue();
        assertThat(broadcaster.snapshot().prompt()).extracting(PromptView::paused, PromptView::countdownMillis).containsExactly(true, 300L);
        assertThatThrownBy(() -> asked.get(500, MILLISECONDS)).as("the paused countdown outlives its 300 ms")
                .isInstanceOf(TimeoutException.class);

        assertThat(realClockPanel.resume(prompt)).isTrue();
        assertThat(broadcaster.snapshot().prompt().paused()).isFalse();
        assertThat(asked.get(WAIT.toSeconds(), SECONDS)).isEqualTo(new Acknowledgement());
    }

    @Test
    void hold_shouldBeRefused_forAPromptWithoutACountdown() {
        askAsync(panel, startStep());
        var opened = awaitOpenPrompt();

        assertThat(panel.hold(panel.pendingPrompt(opened.id()).orElseThrow())).isFalse();
        answerOpenPrompt(new StartStep.Answer(0));
    }

    @Test
    void ask_shouldBeRefused_whileAnotherPromptIsPending() {
        askAsync(panel, startStep());
        awaitOpenPrompt();

        assertThatThrownBy(() -> panel.ask(startStep())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another prompt is pending");
        answerOpenPrompt(new StartStep.Answer(0));
    }

    @Test
    void ask_shouldThrowTheTimeoutException_andCloseThePrompt_whenAPromptWithoutDefaultReachesTheUpperBound() {
        var shortBoundPanel = new WebOperatorPanel(broadcaster, config, editorServices, InstantSource.system(), Duration.ofMillis(100));

        assertThatThrownBy(() -> shortBoundPanel.ask(startStep())).isInstanceOf(OperatorPromptTimeoutException.class)
                .hasMessageContaining("start-step");
        assertThat(broadcaster.snapshot().prompt()).isNull();
    }

    @Test
    void showMessage_shouldApplyTheAcknowledgement_whenNobodyAnswersBeforeTheUpperBound() {
        var shortBoundPanel = new WebOperatorPanel(broadcaster, config, editorServices, InstantSource.system(), Duration.ofMillis(100));

        shortBoundPanel.showMessage("Note", "read me", null, PopupType.WARNING);

        assertThat(broadcaster.snapshot().prompt()).isNull();
    }

    @Test
    void ask_shouldThrowTheInterruptedException_keepTheInterruptFlag_andCloseThePrompt_whenTheRunIsInterrupted() throws Exception {
        var interruptFlagKept = new AtomicBoolean();
        var thrown = new AtomicReference<Throwable>();
        var worker = Thread.ofVirtual().start(() -> {
            try {
                panel.ask(startStep());
            } catch (RuntimeException e) {
                thrown.set(e);
                interruptFlagKept.set(Thread.currentThread().isInterrupted());
            }
        });
        awaitOpenPrompt();

        worker.interrupt();
        worker.join(WAIT);

        assertThat(thrown.get()).isInstanceOf(OperatorPromptInterruptedException.class);
        assertThat(interruptFlagKept).isTrue();
        assertThat(broadcaster.snapshot().prompt()).isNull();
    }

    @Test
    void ask_shouldInterruptARunningDisplayActionAndWaitForIt_whenTheRunIsCancelled() throws Exception {
        when(config.getElementResolutionMaxDurationMillis()).thenReturn((int) WAIT.toMillis());
        var actionStarted = new CountDownLatch(1);
        var actionInterrupted = new AtomicBoolean();
        var actionReturned = new AtomicBoolean();
        var actions = PromptActions.builder().addDrivingDisplay("locate-element", Object.class, _ -> {
            actionStarted.countDown();
            try {
                new CountDownLatch(1).await(WAIT.toSeconds(), SECONDS);
            } catch (InterruptedException _) {
                actionInterrupted.set(true);
                LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
            }
            actionReturned.set(true);
            return "done";
        }).build();
        var actionReturnedWhenAskEnded = new AtomicBoolean();
        var worker = Thread.ofVirtual().start(() -> {
            try {
                panel.ask(startStep().withActions(actions));
            } catch (OperatorPromptInterruptedException _) {
                actionReturnedWhenAskEnded.set(actionReturned.get());
            }
        });
        var opened = awaitOpenPrompt();
        var prompt = panel.pendingPrompt(opened.id()).orElseThrow();
        var action = executor.submit(() -> prompt.runAction(actions.find("locate-element").orElseThrow(), new Object()));
        assertThat(actionStarted.await(WAIT.toSeconds(), SECONDS)).isTrue();

        worker.interrupt();
        worker.join(WAIT);

        assertThat(actionInterrupted).isTrue();
        assertThat(actionReturnedWhenAskEnded).isTrue();
        assertThat(action.get(WAIT.toSeconds(), SECONDS)).contains("done");
        assertThat(prompt.runAction(actions.find("locate-element").orElseThrow(), new Object())).isEmpty();
    }

    @Test
    void runAction_shouldRefuseASecondAction_whileOneIsRunning() throws Exception {
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var actions = PromptActions.builder().add("slow", Object.class, _ -> {
            started.countDown();
            awaitQuietly(release);
            return "done";
        }).build();
        askAsync(panel, startStep().withActions(actions));
        var prompt = panel.pendingPrompt(awaitOpenPrompt().id()).orElseThrow();
        var slow = actions.find("slow").orElseThrow();
        var first = executor.submit(() -> prompt.runAction(slow, new Object()));
        assertThat(started.await(WAIT.toSeconds(), SECONDS)).isTrue();

        assertThat(prompt.runAction(slow, new Object())).isEmpty();

        release.countDown();
        assertThat(first.get(WAIT.toSeconds(), SECONDS)).contains("done");
        answerOpenPrompt(new StartStep.Answer(0));
    }

    @Test
    void ask_shouldTellTheA2aClientWhatItWaitsFor_andReturnItsActivityToTheLastItem_whenThePromptCloses() throws Exception {
        var a2aEmitter = mock(StreamingEventEmitter.class);
        panel.observe(a2aEmitter).emitStepStarted(STEP);
        var asked = askAsync(panel, startStep());
        awaitOpenPrompt();

        answerOpenPrompt(new StartStep.Answer(0));
        asked.get(WAIT.toSeconds(), SECONDS);

        var inOrder = inOrder(a2aEmitter);
        inOrder.verify(a2aEmitter).emitStepStarted(STEP);
        inOrder.verify(a2aEmitter).emitStatus("Waiting for operator: start-step");
        inOrder.verify(a2aEmitter).emitStepStarted(STEP);
        verify(a2aEmitter, times(2)).emitStepStarted(STEP);
    }

    @Test
    void ask_shouldBeRefused_inUnattendedMode() {
        when(config.isFullyUnattended()).thenReturn(true);

        assertThatThrownBy(() -> panel.ask(startStep())).isInstanceOf(IllegalStateException.class).hasMessageContaining("unattended");
        assertThat(broadcaster.snapshot().prompt()).isNull();
    }

    @Test
    void showStatus_shouldShowTheMessage_andClearStatusShouldRemoveIt() {
        panel.showStatus("Locating the element");
        assertThat(broadcaster.snapshot().statusMessage()).isEqualTo("Locating the element");

        panel.clearStatus();

        assertThat(broadcaster.snapshot().statusMessage()).isNull();
    }

    @Test
    void selectStartingStep_shouldStartFromTheFirstStep_withoutAskingWhenThereAreNoSteps() {
        assertThat(panel.selectStartingStep(List.of())).isZero();
        assertThat(broadcaster.snapshot().prompt()).isNull();
    }

    private WebOperatorPanel panel(Duration maxWait) {
        return new WebOperatorPanel(broadcaster, config, editorServices, clock, maxWait);
    }

    private static PromptSpec<StartStep.Answer> startStep() {
        return PromptSpec.of(new StartStep(List.of(new StartStep.StepRow("Click Save", List.of(), "Saved"))), StartStep.Answer.class,
                new PromptImages());
    }

    private <A> Future<A> askAsync(WebOperatorPanel askingPanel, PromptSpec<A> spec) {
        return executor.submit(() -> askingPanel.ask(spec));
    }

    private PromptView awaitOpenPrompt() {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            var prompt = broadcaster.snapshot().prompt();
            if (prompt != null) {
                return prompt;
            }
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
        }
        throw new AssertionError(new TimeoutException("No prompt opened within %s".formatted(WAIT)));
    }

    @SuppressWarnings("unchecked")
    private <A> void answerOpenPrompt(A answer) {
        var prompt = (PendingPrompt<A>) panel.pendingPrompt(awaitOpenPrompt().id()).orElseThrow();
        prompt.answer().complete(answer);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(WAIT.toSeconds(), SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    /** A clock the test moves by hand; prompts on it never reach their deadline unless a test advances it there. */
    private static final class TestClock implements InstantSource {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-03T10:00:00Z"));

        @Override
        public Instant instant() {
            return now.get();
        }

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }
    }
}
