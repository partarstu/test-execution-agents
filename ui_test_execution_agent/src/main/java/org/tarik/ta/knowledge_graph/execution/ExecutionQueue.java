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

import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Queue data structure supporting dynamic item injection for queue-based test execution.
 *
 * <p>This queue holds {@link ExecutionItem} instances (either {@link PreconditionItem}s
 * or {@link TestStepItem}s) and supports injecting prerequisite items at the front of
 * the queue for dynamic precondition resolution.</p>
 *
 * <p>Execution is single-threaded, so no synchronization is required.</p>
 */
public class ExecutionQueue {
    private final LinkedList<ExecutionItem> queue;

    /**
     * Creates a new queue with the given initial items.
     *
     * @param initialItems the items to populate the queue with
     */
    public ExecutionQueue(List<ExecutionItem> initialItems) {
        requireNonNull(initialItems, "initialItems");
        this.queue = new LinkedList<>(initialItems);
    }

    /**
     * Creates an ExecutionQueue from a test case, converting preconditions and test steps
     * to their respective queue items.
     * @param testCase the test case to convert
     * @param startingStepIndex the 0-based index of the first test step to include
     * @return a new ExecutionQueue populated with items from the test case
     */
    public static ExecutionQueue fromTestCase(TestCase testCase, int startingStepIndex) {
        requireNonNull(testCase, "testCase");
        var items = new ArrayList<ExecutionItem>();

        if (startingStepIndex == 0 && testCase.preconditions() != null) {
            for (String precondition : testCase.preconditions()) {
                items.add(new PreconditionItem(precondition));
            }
        }

        var testSteps = testCase.testSteps();
        if (testSteps != null) {
            for (int i = startingStepIndex; i < testSteps.size(); i++) {
                items.add(new TestStepItem(testSteps.get(i)));
            }
        }
        
        return new ExecutionQueue(items);
    }

    /**
     * Checks if there are more items in the queue.
     *
     * @return true if the queue has more items, false otherwise
     */
    public boolean hasNext() {
        return !queue.isEmpty();
    }

    /**
     * Polls and returns the item at the front of the queue.
     *
     * @return the next item, or null if the queue is empty
     */
    public ExecutionItem next() {
        return queue.poll();
    }

    /**
     * Peeks and returns the item at the front of the queue without removing it.
     *
     * @return the next item, or null if the queue is empty
     */
    public ExecutionItem peek() {
        return queue.peek();
    }

    /**
     * Injects items at the front of the queue for prerequisite injection.
     *
     * @param items the items to inject at the front
     */
    public void injectAtFront(List<ExecutionItem> items) {
        requireNonNull(items, "items");
        // Add in reverse order so first item is at the front
        for (int i = items.size() - 1; i >= 0; i--) {
            queue.addFirst(items.get(i));
        }
    }

    /**
     * Returns the number of items remaining in the queue.
     *
     * @return the remaining item count
     */
    public int remainingCount() {
        return queue.size();
    }
}