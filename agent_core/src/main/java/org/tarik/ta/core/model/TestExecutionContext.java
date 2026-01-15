/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.core.model;

import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.dto.TestCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the context and state of the current test execution.
 */
public class TestExecutionContext {
    private final TestCase testCase;
    private final List<TestStepResult> testStepExecutionHistory;
    private final List<PreconditionResult> preconditionExecutionHistory;
    private final Map<String, Object> sharedData;

    public TestExecutionContext(TestCase testCase) {
        this.testCase = testCase;
        this.testStepExecutionHistory = new ArrayList<>();
        this.preconditionExecutionHistory = new ArrayList<>();
        this.sharedData = new HashMap<>();
    }

    public synchronized TestCase getTestCase() {
        return testCase;
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