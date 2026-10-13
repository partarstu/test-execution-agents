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
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.dto.panel.TimelineItem.Kind;
import org.tarik.ta.dto.panel.TimelineItem.Status;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Forwards every event to the A2A emitter first and then to the panel, so the panel never delays the A2A stream. It remembers the
 * last started item, so that the A2A activity line returns to it after a prompt closes.
 */
final class BroadcastingEventEmitter implements StreamingEventEmitter {
    private final StreamingEventEmitter a2aEmitter;
    private final ExecutionStateBroadcaster broadcaster;
    private final AtomicReference<Runnable> lastStartedItem = new AtomicReference<>(() -> {
    });

    BroadcastingEventEmitter(StreamingEventEmitter a2aEmitter, ExecutionStateBroadcaster broadcaster) {
        this.a2aEmitter = a2aEmitter;
        this.broadcaster = broadcaster;
    }

    @Override
    public void emitStepStarted(@NotNull TestStep step) {
        a2aEmitter.emitStepStarted(step);
        lastStartedItem.set(() -> a2aEmitter.emitStepStarted(step));
        broadcaster.itemStarted(Kind.STEP, step.stepDescription());
    }

    @Override
    public void emitPreconditionStarted(@NotNull String precondition) {
        a2aEmitter.emitPreconditionStarted(precondition);
        lastStartedItem.set(() -> a2aEmitter.emitPreconditionStarted(precondition));
        broadcaster.itemStarted(Kind.PRECONDITION, precondition);
    }

    /** Tells the A2A client alone what the run waits for; the panel shows the prompt itself. */
    void emitA2aStatus(@NotNull String message) {
        a2aEmitter.emitStatus(message);
    }

    void reemitLastStartedItem() {
        lastStartedItem.get().run();
    }

    @Override
    public void emitStepResult(@NotNull TestStepResult stepResult) {
        a2aEmitter.emitStepResult(stepResult);
        var status = switch (stepResult.getExecutionStatus()) {
            case SUCCESS -> Status.PASSED;
            case FAILURE -> Status.FAILED;
            case ERROR -> Status.ERROR;
        };
        broadcaster.itemFinished(Kind.STEP, stepResult.getTestStep().stepDescription(), status, stepResult.getErrorMessage());
    }

    @Override
    public void emitPreconditionResult(@NotNull PreconditionResult preconditionResult) {
        a2aEmitter.emitPreconditionResult(preconditionResult);
        var status = preconditionResult.isSuccess() ? Status.PASSED : Status.ERROR;
        broadcaster.itemFinished(Kind.PRECONDITION, preconditionResult.getPrecondition(), status, preconditionResult.getErrorMessage());
    }

    @Override
    public void emitLog(@NotNull String logLine) {
        a2aEmitter.emitLog(logLine);
        broadcaster.log(logLine);
    }

    @Override
    public void emitStatus(@NotNull String message) {
        a2aEmitter.emitStatus(message);
        broadcaster.status(message);
    }
}
