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
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.dto.panel.TimelineItem.Kind;
import org.tarik.ta.dto.panel.TimelineItem.Status;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BroadcastingEventEmitterTest {
    private static final String PRECONDITION = "User is logged in";

    private final StreamingEventEmitter a2aEmitter = mock(StreamingEventEmitter.class);
    private final ExecutionStateBroadcaster broadcaster = mock(ExecutionStateBroadcaster.class);
    private final BroadcastingEventEmitter emitter = new BroadcastingEventEmitter(a2aEmitter, broadcaster);

    @Test
    void emitPreconditionResult_shouldShowAnError_whenThePreconditionFailed() {
        var result = new PreconditionResult(PRECONDITION, false, "the login screen is still visible", null, null);

        emitter.emitPreconditionResult(result);

        verify(a2aEmitter).emitPreconditionResult(result);
        verify(broadcaster).itemFinished(Kind.PRECONDITION, PRECONDITION, Status.ERROR, "the login screen is still visible");
    }

    @Test
    void emitPreconditionResult_shouldShowAPass_whenThePreconditionSucceeded() {
        var result = new PreconditionResult(PRECONDITION, true, null, null, null);

        emitter.emitPreconditionResult(result);

        verify(broadcaster).itemFinished(Kind.PRECONDITION, PRECONDITION, Status.PASSED, null);
    }
}
