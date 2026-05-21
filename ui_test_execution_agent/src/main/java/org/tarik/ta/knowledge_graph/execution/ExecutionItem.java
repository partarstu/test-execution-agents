/*
 * ui-test-execution-agent - ${project.description}
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
