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

import io.avaje.inject.External;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the context and state of the current test execution.
 */
@BaseAgentRequestScope
public class TestExecutionContext {
    private final List<TestStepResult> testStepExecutionHistory;
    private final List<PreconditionResult> preconditionExecutionHistory;
    private final Map<String, Object> sharedData;
    private final StreamingEventEmitter eventEmitter;
    private final ReentrantLock lock = new ReentrantLock();

    public TestExecutionContext(@External @NotNull StreamingEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
        this.testStepExecutionHistory = new ArrayList<>();
        this.preconditionExecutionHistory = new ArrayList<>();
        this.sharedData = new HashMap<>();
    }

    public @NotNull StreamingEventEmitter getEventEmitter() {
        return eventEmitter;
    }

    public List<TestStepResult> getTestStepExecutionHistory() {
        lock.lock();
        try {
            return new ArrayList<>(testStepExecutionHistory);
        } finally {
            lock.unlock();
        }
    }

    public List<PreconditionResult> getPreconditionExecutionHistory() {
        lock.lock();
        try {
            return new ArrayList<>(preconditionExecutionHistory);
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Object> getSharedData() {
        lock.lock();
        try {
            return new HashMap<>(sharedData);
        } finally {
            lock.unlock();
        }
    }

    public void addStepResult(TestStepResult result) {
        lock.lock();
        try {
            this.testStepExecutionHistory.add(result);
        } finally {
            lock.unlock();
        }
        eventEmitter.emitStepResult(result);
    }

    public void addPreconditionResult(PreconditionResult result) {
        lock.lock();
        try {
            this.preconditionExecutionHistory.add(result);
        } finally {
            lock.unlock();
        }
        eventEmitter.emitPreconditionResult(result);
    }

    public void addSharedData(String key, Object value) {
        lock.lock();
        try {
            this.sharedData.put(key, value);
        } finally {
            lock.unlock();
        }
    }
}
