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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.panel.PromptActions.Action;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * The one open prompt: its answer future, its deadline and the action running for it. Thread-safe; the actions of a prompt run one
 * at a time, because two of them would drive the display and the LLM concurrently.
 */
final class PendingPrompt<A> {
    private static final Logger LOG = LoggerFactory.getLogger(PendingPrompt.class);
    private static final Duration PAUSED_RECHECK = Duration.ofMillis(200);

    private final UUID id;
    private final PromptSpec<A> spec;
    private final Instant openedAt;
    private final Instant upperBound;
    private final CompletableFuture<A> answer = new CompletableFuture<>();
    private final ReentrantLock actionLock = new ReentrantLock();
    private final Object state = new Object();
    private Instant deadline;
    private boolean held;
    private @Nullable Duration pausedRemaining;
    private boolean closed;
    private @Nullable Thread runningAction;
    private boolean runningActionDrivesDisplay;

    PendingPrompt(@NotNull UUID id, @NotNull PromptSpec<A> spec, @NotNull Instant openedAt, @NotNull Duration maxWait) {
        this.id = id;
        this.spec = spec;
        this.openedAt = openedAt;
        this.upperBound = openedAt.plus(maxWait);
        this.deadline = spec.countdown() == null ? upperBound : openedAt.plus(spec.countdown());
    }

    UUID id() {
        return id;
    }

    PromptSpec<A> spec() {
        return spec;
    }

    Instant openedAt() {
        return openedAt;
    }

    CompletableFuture<A> answer() {
        return answer;
    }

    Instant deadline() {
        synchronized (state) {
            return deadline;
        }
    }

    boolean isCountdown() {
        return spec.countdown() != null;
    }

    boolean isHeld() {
        synchronized (state) {
            return held;
        }
    }

    /**
     * Moves the deadline of a running countdown to the prompt upper bound and runs {@code onHeld}; false when there is no countdown
     * to hold.
     */
    boolean hold(@NotNull Runnable onHeld) {
        // onHeld publishes the held prompt, which must reach the panel before the close of a prompt that closes meanwhile
        synchronized (state) {
            if (!isCountdown() || held || closed) {
                return false;
            }
            held = true;
            pausedRemaining = null;
            deadline = upperBound;
            onHeld.run();
            return true;
        }
    }

    /** The time left that a paused countdown resumes with, or null when it is not paused. */
    @Nullable Duration pausedRemaining() {
        synchronized (state) {
            return pausedRemaining;
        }
    }

    /**
     * Freezes the time left of a running countdown, with the deadline at the prompt upper bound until {@link #resume}, and runs
     * {@code onPaused}; false when there is no running countdown to pause.
     */
    boolean pause(@NotNull Instant now, @NotNull Runnable onPaused) {
        synchronized (state) {
            if (!isCountdown() || held || closed || pausedRemaining != null) {
                return false;
            }
            var remaining = Duration.between(now, deadline);
            pausedRemaining = remaining.isNegative() ? Duration.ZERO : remaining;
            deadline = upperBound;
            onPaused.run();
            return true;
        }
    }

    /** Restarts a paused countdown with the time it had left and runs {@code onResumed}; false when it is not paused. */
    boolean resume(@NotNull Instant now, @NotNull Runnable onResumed) {
        synchronized (state) {
            if (closed || pausedRemaining == null) {
                return false;
            }
            var resumed = now.plus(pausedRemaining);
            deadline = resumed.isBefore(upperBound) ? resumed : upperBound;
            pausedRemaining = null;
            onResumed.run();
            return true;
        }
    }

    /** How long the waiter may wait before it reads the deadline again; a paused countdown can resume with an earlier one. */
    @NotNull Duration waitAt(@NotNull Instant now) {
        synchronized (state) {
            var untilDeadline = Duration.between(now, deadline);
            return pausedRemaining == null || untilDeadline.compareTo(PAUSED_RECHECK) < 0 ? untilDeadline : PAUSED_RECHECK;
        }
    }

    /** Runs the action unless another one runs or the prompt has closed, in which case it is empty. */
    @NotNull <R> Optional<Object> runAction(@NotNull Action<R> action, @NotNull Object request) {
        if (!actionLock.tryLock()) {
            return Optional.empty();
        }
        try {
            synchronized (state) {
                if (closed) {
                    return Optional.empty();
                }
                runningAction = Thread.currentThread();
                runningActionDrivesDisplay = action.drivesDisplay();
            }
            try {
                return Optional.of(action.run(request));
            } finally {
                synchronized (state) {
                    runningAction = null;
                    if (closed) {
                        // The close interrupted this request thread for the action only; writing the response must not see it
                        Thread.interrupted();
                    }
                }
            }
        } finally {
            actionLock.unlock();
        }
    }

    /**
     * Rejects every later action and interrupts the running one; one that drives the display is awaited for up to {@code
     * displayActionWait}, so it never keeps driving the screen into what the run does next.
     */
    void close(@NotNull Duration displayActionWait) {
        boolean awaitAction;
        synchronized (state) {
            closed = true;
            if (runningAction == null) {
                return;
            }
            // Under the lock the action thread clears runningAction with, so the interrupt never hits it after the action returned
            runningAction.interrupt();
            awaitAction = runningActionDrivesDisplay;
        }
        if (awaitAction) {
            awaitRunningAction(displayActionWait);
        }
    }

    // The caller may itself be interrupted by a cancel, which must not cut the wait short and must survive it
    private void awaitRunningAction(Duration wait) {
        boolean callerInterrupted = Thread.interrupted();
        try {
            if (actionLock.tryLock(wait.toMillis(), MILLISECONDS)) {
                actionLock.unlock();
            } else {
                LOG.warn("The action of operator prompt {} still runs {} ms after the prompt closed", id, wait.toMillis());
            }
        } catch (InterruptedException _) {
            callerInterrupted = true;
        } finally {
            if (callerInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
