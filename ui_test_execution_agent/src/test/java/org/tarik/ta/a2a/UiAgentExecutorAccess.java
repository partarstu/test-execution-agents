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
package org.tarik.ta.a2a;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestExecutionResult;

/** Lets the manual tests of other packages run a test case through the executor without widening its protected entry point. */
public final class UiAgentExecutorAccess {
    private UiAgentExecutorAccess() {
    }

    public static TestExecutionResult executeTestCase(@NotNull UiAgentExecutor executor, @NotNull String message,
                                                      @NotNull StreamingEventEmitter eventEmitter) {
        return executor.executeTestCase(message, eventEmitter);
    }
}
