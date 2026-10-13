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
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Acknowledgement;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class PendingPromptTest {
    private static final Instant OPENED_AT = Instant.parse("2026-10-03T10:00:00Z");
    private static final Instant UPPER_BOUND = OPENED_AT.plus(Duration.ofMinutes(10));
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void hold_shouldPublishBeforeAConcurrentCloseMarksThePromptClosed() throws Exception {
        var prompt = countdownPrompt();
        var publishing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = Thread.ofVirtual().start(() -> prompt.hold(() -> {
            publishing.countDown();
            await(release);
        }));
        assertThat(publishing.await(WAIT.toSeconds(), SECONDS)).isTrue();

        var closer = Thread.ofVirtual().start(() -> prompt.close(Duration.ZERO));

        assertThat(closer.join(Duration.ofMillis(200))).as("close waits for the hold's publish").isFalse();
        release.countDown();
        assertThat(holder.join(WAIT)).isTrue();
        assertThat(closer.join(WAIT)).isTrue();
    }

    @Test
    void hold_shouldPublishNothing_afterThePromptClosed() {
        var prompt = countdownPrompt();
        prompt.close(Duration.ZERO);
        var published = new AtomicBoolean();

        assertThat(prompt.hold(() -> published.set(true))).isFalse();
        assertThat(published).isFalse();
    }

    @Test
    void hold_shouldMoveTheDeadlineToTheUpperBoundOnce() {
        var prompt = countdownPrompt();

        assertThat(prompt.hold(() -> { })).isTrue();
        assertThat(prompt.deadline()).isEqualTo(OPENED_AT.plus(Duration.ofMinutes(10)));
        assertThat(prompt.hold(() -> { })).isFalse();
    }

    @Test
    void pause_shouldFreezeTheTimeLeft_untilResumeRestartsTheCountdownWithIt() {
        var prompt = countdownPrompt(Duration.ofSeconds(10));
        var published = new AtomicInteger();

        assertThat(prompt.pause(OPENED_AT.plusMillis(500), published::incrementAndGet)).isTrue();
        assertThat(prompt.pausedRemaining()).isEqualTo(Duration.ofMillis(9_500));
        assertThat(prompt.deadline()).isEqualTo(UPPER_BOUND);
        assertThat(prompt.pause(OPENED_AT.plusSeconds(1), published::incrementAndGet)).isFalse();

        assertThat(prompt.resume(OPENED_AT.plusSeconds(60), published::incrementAndGet)).isTrue();
        assertThat(prompt.pausedRemaining()).isNull();
        assertThat(prompt.deadline()).isEqualTo(OPENED_AT.plusSeconds(60).plusMillis(9_500));
        assertThat(prompt.resume(OPENED_AT.plusSeconds(61), published::incrementAndGet)).isFalse();
        assertThat(published).hasValue(2);
    }

    @Test
    void pause_shouldFreezeExactlyTheTimeLeft_nearTheEndOfAShortCountdown_andNothingPastItsDeadline() {
        var nearlyOver = countdownPrompt(Duration.ofSeconds(2));
        var overdue = countdownPrompt(Duration.ofSeconds(2));

        nearlyOver.pause(OPENED_AT.plusMillis(1_700), () -> { });
        overdue.pause(OPENED_AT.plusMillis(2_100), () -> { });

        assertThat(nearlyOver.pausedRemaining()).isEqualTo(Duration.ofMillis(300));
        assertThat(overdue.pausedRemaining()).isEqualTo(Duration.ZERO);
    }

    @Test
    void resume_shouldNeverMoveTheDeadlinePastTheUpperBound() {
        var prompt = countdownPrompt();
        prompt.pause(OPENED_AT, () -> { });

        assertThat(prompt.resume(UPPER_BOUND.minusMillis(500), () -> { })).isTrue();
        assertThat(prompt.deadline()).isEqualTo(UPPER_BOUND);
    }

    @Test
    void hold_shouldEndAPause_soThatResumeIsRefused() {
        var prompt = countdownPrompt();
        prompt.pause(OPENED_AT, () -> { });

        assertThat(prompt.hold(() -> { })).isTrue();
        assertThat(prompt.pausedRemaining()).isNull();
        assertThat(prompt.resume(OPENED_AT.plusSeconds(1), () -> { })).isFalse();
        assertThat(prompt.deadline()).isEqualTo(UPPER_BOUND);
        assertThat(prompt.pause(OPENED_AT.plusSeconds(1), () -> { })).isFalse();
    }

    @Test
    void pauseAndResume_shouldPublishNothing_afterThePromptClosed() {
        var paused = countdownPrompt();
        paused.pause(OPENED_AT, () -> { });
        paused.close(Duration.ZERO);
        var closed = countdownPrompt();
        closed.close(Duration.ZERO);
        var published = new AtomicBoolean();

        assertThat(paused.resume(OPENED_AT, () -> published.set(true))).isFalse();
        assertThat(closed.pause(OPENED_AT, () -> published.set(true))).isFalse();
        assertThat(published).isFalse();
    }

    @Test
    void pause_shouldBeRefused_forAPromptWithoutACountdown() {
        var spec = PromptSpec.of(new Message("Note", "Read", PopupType.INFO, null), Acknowledgement.class, new PromptImages());
        var prompt = new PendingPrompt<>(UUID.randomUUID(), spec, OPENED_AT, Duration.ofMinutes(10));

        assertThat(prompt.pause(OPENED_AT, () -> { })).isFalse();
    }

    @Test
    void waitAt_shouldWaitForTheDeadline_butRecheckShortlyWhilePaused() {
        var prompt = countdownPrompt();

        assertThat(prompt.waitAt(OPENED_AT)).isEqualTo(Duration.ofSeconds(2));
        prompt.pause(OPENED_AT, () -> { });
        assertThat(prompt.waitAt(OPENED_AT)).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void close_shouldLeaveNoInterruptOnTheRequestThread_afterTheInterruptedActionReturned() throws Exception {
        var prompt = countdownPrompt();
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var actions = PromptActions.builder().add("slow", Object.class, _ -> {
            running.countDown();
            awaitIgnoringInterrupts(release);
            return "done";
        }).build();
        var interruptedAfterAction = new AtomicBoolean(true);
        var requestThread = Thread.ofVirtual().start(() -> {
            prompt.runAction(actions.find("slow").orElseThrow(), new Object());
            interruptedAfterAction.set(Thread.currentThread().isInterrupted());
        });
        assertThat(running.await(WAIT.toSeconds(), SECONDS)).isTrue();

        prompt.close(Duration.ZERO);
        release.countDown();

        assertThat(requestThread.join(WAIT)).isTrue();
        assertThat(interruptedAfterAction).isFalse();
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        while (true) {
            try {
                latch.await(WAIT.toSeconds(), SECONDS);
                return;
            } catch (InterruptedException _) {
                // The action ignores the interrupt on purpose, like an agent that only stops at its next interruptible point
            }
        }
    }

    private static PendingPrompt<Acknowledgement> countdownPrompt() {
        return countdownPrompt(Duration.ofSeconds(2));
    }

    private static PendingPrompt<Acknowledgement> countdownPrompt(Duration countdown) {
        var spec = PromptSpec.of(new Message("Note", "Read", PopupType.INFO, null), Acknowledgement.class, new PromptImages())
                .withCountdown(countdown);
        return new PendingPrompt<>(UUID.randomUUID(), spec, OPENED_AT, Duration.ofMinutes(10));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(WAIT.toSeconds(), SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
