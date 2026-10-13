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
package org.tarik.ta.knowledge_graph.service;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.repository.GraphHealthRepository;

import static org.mockito.Mockito.mock;

/** Builds the real health check over a stubbed repository for tests outside this package, such as the smoke suite. */
public final class GraphHealthServices {

    private GraphHealthServices() {
    }

    public static @NotNull GraphHealthService over(@NotNull GraphHealthRepository repository, @NotNull UiTestAgentConfig config) {
        return new GraphHealthService(repository, new GraphHealthHtmlReportGenerator(), config, mock(FailureContextService.class));
    }
}
