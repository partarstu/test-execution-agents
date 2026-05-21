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
package org.tarik.ta.core.model;

import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the context and state of the current test execution.
 */
@BaseAgentRequestScope
public class TestExecutionContext {
    private final List<TestStepResult> testStepExecutionHistory;
    private final List<PreconditionResult> preconditionExecutionHistory;
    private final Map<String, Object> sharedData;

    public TestExecutionContext() {
        this.testStepExecutionHistory = new ArrayList<>();
        this.preconditionExecutionHistory = new ArrayList<>();
        this.sharedData = new HashMap<>();
    }

    public synchronized List<TestStepResult> getTestStepExecutionHistory() {
        return testStepExecutionHistory;
    }

    public synchronized List<PreconditionResult> getPreconditionExecutionHistory() {
        return preconditionExecutionHistory;
    }

    public synchronized Map<String, Object> getSharedData() {
        return sharedData;
    }

    public synchronized void addStepResult(TestStepResult result) {
        this.testStepExecutionHistory.add(result);
    }

    public synchronized void addPreconditionResult(PreconditionResult result) {
        this.preconditionExecutionHistory.add(result);
    }

    public synchronized void addSharedData(String key, Object value) {
        this.sharedData.put(key, value);
    }
}
