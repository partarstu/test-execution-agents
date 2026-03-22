/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.knowledge_graph.execution;

import org.tarik.ta.core.dto.TestStep;

import java.util.List;
import java.util.Objects;

/**
 * Sealed interface unifying precondition items and test step items for queue-based execution.
 * Enables exhaustive {@code switch} pattern matching over the two permitted subtypes.
 */
public sealed interface ExecutionItem
        permits ExecutionItem.PreconditionItem, ExecutionItem.TestStepItem {

    String getDescription();

    List<String> getTestData();

    String getExpectedResults();

    /**
     * Represents a test case precondition that must be satisfied before test steps execute.
     */
    record PreconditionItem(String description) implements ExecutionItem {
        @Override
        public String getDescription() { return description; }

        @Override
        public List<String> getTestData() { return List.of(); }

        @Override
        public String getExpectedResults() { return ""; }
    }

    /**
     * Wraps an existing {@link TestStep} for queue-based execution.
     */
    record TestStepItem(TestStep testStep) implements ExecutionItem {
        @Override
        public String getDescription() { return testStep.stepDescription(); }

        @Override
        public List<String> getTestData() { return Objects.requireNonNullElse(testStep.testData(), List.of()); }

        @Override
        public String getExpectedResults() { return Objects.requireNonNullElse(testStep.expectedResults(), ""); }
    }
}
