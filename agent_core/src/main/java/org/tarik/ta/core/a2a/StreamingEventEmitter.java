/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.a2a;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;

/**
 * Bridges live test-execution progress to the A2A streaming channel. Implementations forward each incremental event
 * (step result, precondition result, log line) to the active {@code AgentEmitter} so that streaming clients receive it
 * in real time. The abstraction is intentionally free of A2A transport types so that execution components such as
 * {@code TestExecutionContext} and {@code LogCapture} can publish progress without coupling to the transport layer.
 */
public interface StreamingEventEmitter {

    /** Signals that the given test step is about to be executed, so observers can display the current activity. */
    void emitStepStarted(@NotNull TestStep step);

    /** Signals that the given precondition is about to be executed, so observers can display the current activity. */
    void emitPreconditionStarted(@NotNull String precondition);

    void emitStepResult(@NotNull TestStepResult stepResult);

    void emitPreconditionResult(@NotNull PreconditionResult preconditionResult);

    void emitLog(@NotNull String logLine);

    /**
     * Emitter that discards every event. Used when no streaming channel is active, e.g. in unit tests that exercise
     * the execution components in isolation.
     */
    StreamingEventEmitter NOOP = new StreamingEventEmitter() {
        @Override
        public void emitStepStarted(@NotNull TestStep step) {
        }

        @Override
        public void emitPreconditionStarted(@NotNull String precondition) {
        }

        @Override
        public void emitStepResult(@NotNull TestStepResult stepResult) {
        }

        @Override
        public void emitPreconditionResult(@NotNull PreconditionResult preconditionResult) {
        }

        @Override
        public void emitLog(@NotNull String logLine) {
        }
    };
}
