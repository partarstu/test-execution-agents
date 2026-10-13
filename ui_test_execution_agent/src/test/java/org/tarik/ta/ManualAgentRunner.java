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
package org.tarik.ta;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.avaje.inject.BeanScope;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.a2a.UiAgentExecutor;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.panel.PanelServer;

import java.nio.file.Paths;

import static org.tarik.ta.a2a.UiAgentExecutorAccess.executeTestCase;
import static org.tarik.ta.core.utils.CommonUtils.deleteFolderContents;

/**
 * Bootstraps the agent the same way the server does, operator panel included, and executes a test case against whatever is
 * displayed on the screen at that moment. Shared by the manual tests so that each of them consists of nothing but the test case it runs.
 */
final class ManualAgentRunner {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ManualAgentRunner() {
    }

    static @NotNull TestExecutionResult runOnCurrentScreen(@NotNull TestCase testCase) throws Exception {
        deleteFolderContents(Paths.get("screens"));
        try (BeanScope scope = BeanScope.builder().build()) {
            scope.get(PanelServer.class).start();
            return executeTestCase(scope.get(UiAgentExecutor.class), OBJECT_MAPPER.writeValueAsString(testCase), StreamingEventEmitter.NOOP);
        }
    }
}
