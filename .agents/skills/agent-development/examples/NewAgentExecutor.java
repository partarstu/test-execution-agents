/*
 * Test Execution Agent Parent - Parent build/dependency management for the Test Execution Agents system.
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

import org.a2aproject.sdk.spec.Part;
import jakarta.inject.Singleton;
import org.tarik.ta.NewTestAgent;
import org.tarik.ta.core.a2a.AbstractAgentExecutor;
import org.tarik.ta.core.dto.TestExecutionResult;

import java.util.List;
import java.util.Optional;

@Singleton
public class NewAgentExecutor extends AbstractAgentExecutor {
    private final NewTestAgent agent;

    public NewAgentExecutor(NewTestAgent agent) {
        this.agent = agent;
    }

    @Override
    protected TestExecutionResult executeTestCase(String message) {
        return agent.executeTestCase(message);
    }

    @Override
    protected void addSpecificArtifacts(TestExecutionResult result, List<Part<?>> parts) {
        // Add agent-specific artifacts (screenshots, logs, etc.)
    }

    @Override
    protected Optional<List<String>> extractLogs(TestExecutionResult result) {
        return Optional.ofNullable(result.getLogs());
    }
}
