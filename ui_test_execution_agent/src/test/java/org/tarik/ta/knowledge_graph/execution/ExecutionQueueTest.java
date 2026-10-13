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
package org.tarik.ta.knowledge_graph.execution;

import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionQueueTest {

    private List<ExecutionItem> executionItemsList() {
        return new ArrayList<>();
    }

    @Test
    void constructor_shouldCreateQueueWithInitialItems() {
        // Given
        List<ExecutionItem> items = executionItemsList();
        items.add(new PreconditionItem(new Precondition("precondition 1", List.of())));
        items.add(new TestStepItem(new TestStep("step 1", null, "expected")));

        // When
        var queue = new ExecutionQueue(items);

        // Then
        assertThat(queue.remainingCount()).isEqualTo(2);
    }

    @Test
    void constructor_shouldThrowOnNullInitialItems() {
        // When/Then
        assertThatThrownBy(() -> new ExecutionQueue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("initialItems");
    }

    @Test
    void fromTestCase_shouldCreateQueueWithPreconditionsAndTestSteps() {
        // Given
        var testCase = new TestCase(
                "Test Case",
                List.of(new Precondition("precondition 1", List.of()), new Precondition("precondition 2", List.of())), List.of(),
                List.of(
                        new TestStep("step 1", null, "expected 1"),
                        new TestStep("step 2", null, "expected 2")
                )
        );

        // When
        var queue = ExecutionQueue.fromTestCase(testCase, 0);

        // Then
        assertThat(queue.remainingCount()).isEqualTo(4);
        
        // Verify order: preconditions first, then test steps
        var item1 = queue.next();
        assertThat(item1).isInstanceOf(PreconditionItem.class);
        assertThat(((PreconditionItem) item1).getDescription()).isEqualTo("precondition 1");
        
        var item2 = queue.next();
        assertThat(item2).isInstanceOf(PreconditionItem.class);
        assertThat(((PreconditionItem) item2).getDescription()).isEqualTo("precondition 2");
        
        var item3 = queue.next();
        assertThat(item3).isInstanceOf(TestStepItem.class);
        assertThat(((TestStepItem) item3).testStep().stepDescription()).isEqualTo("step 1");
        
        var item4 = queue.next();
        assertThat(item4).isInstanceOf(TestStepItem.class);
        assertThat(((TestStepItem) item4).testStep().stepDescription()).isEqualTo("step 2");
    }

    @Test
    void fromTestCase_shouldCarryTheRelevantDataOfAPreconditionAsItsTestData() {
        // Given
        var testCase = new TestCase(
                "Test Case",
                List.of(new Precondition("the user is logged in", List.of("user=admin"))), List.of(),
                List.of(new TestStep("step 1", null, "expected 1"))
        );

        // When
        var item = ExecutionQueue.fromTestCase(testCase, 0).next();

        // Then
        assertThat(item.getTestData()).containsExactly("user=admin");
    }

    @Test
    void fromTestCase_shouldRespectStartingStepIndex() {
        // Given
        var testCase = new TestCase(
                "Test Case",
                List.of(new Precondition("precondition 1", List.of())), List.of(),
                List.of(
                        new TestStep("step 1", null, "expected 1"),
                        new TestStep("step 2", null, "expected 2"),
                        new TestStep("step 3", null, "expected 3")
                )
        );

        // When - starting from index 1
        var queue = ExecutionQueue.fromTestCase(testCase, 1);

        // Then - preconditions should be skipped when startingStepIndex > 0
        assertThat(queue.remainingCount()).isEqualTo(2);
        
        var item1 = queue.next();
        assertThat(item1).isInstanceOf(TestStepItem.class);
        assertThat(((TestStepItem) item1).testStep().stepDescription()).isEqualTo("step 2");
        
        var item2 = queue.next();
        assertThat(item2).isInstanceOf(TestStepItem.class);
        assertThat(((TestStepItem) item2).testStep().stepDescription()).isEqualTo("step 3");
    }

    @Test
    void fromTestCase_shouldHandleEmptyPreconditions() {
        // Given
        var testCase = new TestCase(
                "Test Case",
                null, List.of(),
                List.of(new TestStep("step 1", null, "expected 1"))
        );

        // When
        var queue = ExecutionQueue.fromTestCase(testCase, 0);

        // Then
        assertThat(queue.remainingCount()).isEqualTo(1);
        assertThat(queue.next()).isInstanceOf(TestStepItem.class);
    }

    @Test
    void fromTestCase_shouldHandleEmptyTestSteps() {
        // Given
        var testCase = new TestCase(
                "Test Case",
                List.of(new Precondition("precondition 1", List.of())), List.of(),
                null
        );

        // When
        var queue = ExecutionQueue.fromTestCase(testCase, 0);

        // Then
        assertThat(queue.remainingCount()).isEqualTo(1);
        assertThat(queue.next()).isInstanceOf(PreconditionItem.class);
    }

    @Test
    void fromTestCase_shouldThrowOnNullTestCase() {
        // When/Then
        assertThatThrownBy(() -> ExecutionQueue.fromTestCase(null, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("testCase");
    }

    @Test
    void hasNext_shouldReturnTrueWhenItemsExist() {
        // Given
        List<ExecutionItem> items = executionItemsList();
        items.add(new PreconditionItem(new Precondition("test", List.of())));
        var queue = new ExecutionQueue(items);

        // When/Then
        assertThat(queue.hasNext()).isTrue();
    }

    @Test
    void hasNext_shouldReturnFalseWhenEmpty() {
        // Given
        var queue = new ExecutionQueue(List.of());

        // When/Then
        assertThat(queue.hasNext()).isFalse();
    }

    @Test
    void next_shouldReturnAndRemoveHeadItem() {
        // Given
        List<ExecutionItem> items = executionItemsList();
        items.add(new PreconditionItem(new Precondition("first", List.of())));
        items.add(new PreconditionItem(new Precondition("second", List.of())));
        var queue = new ExecutionQueue(items);

        // When
        var first = queue.next();

        // Then
        assertThat(((PreconditionItem) first).getDescription()).isEqualTo("first");
        assertThat(queue.remainingCount()).isEqualTo(1);
    }

    @Test
    void next_shouldReturnNullWhenEmpty() {
        // Given
        var queue = new ExecutionQueue(List.of());

        // When
        var item = queue.next();

        // Then
        assertThat(item).isNull();
    }

    @Test
    void injectAtFront_shouldAddItemsAtFrontOfQueue() {
        // Given
        List<ExecutionItem> originalItems = executionItemsList();
        originalItems.add(new PreconditionItem(new Precondition("original", List.of())));
        var queue = new ExecutionQueue(originalItems);
        
        List<ExecutionItem> itemsToInject = executionItemsList();
        itemsToInject.add(new PreconditionItem(new Precondition("injected 1", List.of())));
        itemsToInject.add(new PreconditionItem(new Precondition("injected 2", List.of())));

        // When
        queue.injectAtFront(itemsToInject);

        // Then
        assertThat(queue.remainingCount()).isEqualTo(3);
        
        // First item should be the first injected item
        var first = queue.next();
        assertThat(((PreconditionItem) first).getDescription()).isEqualTo("injected 1");
        
        var second = queue.next();
        assertThat(((PreconditionItem) second).getDescription()).isEqualTo("injected 2");
        
        var third = queue.next();
        assertThat(((PreconditionItem) third).getDescription()).isEqualTo("original");
    }

    @Test
    void injectAtFront_shouldThrowOnNullItems() {
        // Given
        List<ExecutionItem> items = executionItemsList();
        items.add(new PreconditionItem(new Precondition("test", List.of())));
        var queue = new ExecutionQueue(items);

        // When/Then
        assertThatThrownBy(() -> queue.injectAtFront(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("items");
    }

    @Test
    void injectAtFront_shouldHandleEmptyList() {
        // Given
        List<ExecutionItem> items = executionItemsList();
        items.add(new PreconditionItem(new Precondition("original", List.of())));
        var queue = new ExecutionQueue(items);

        // When
        queue.injectAtFront(List.of());

        // Then
        assertThat(queue.remainingCount()).isEqualTo(1);
        assertThat(((PreconditionItem) queue.next()).getDescription()).isEqualTo("original");
    }

    @Test
    void remainingCount_shouldReturnCorrectCount() {
        // Given
        List<ExecutionItem> items = executionItemsList();
        items.add(new PreconditionItem(new Precondition("1", List.of())));
        items.add(new PreconditionItem(new Precondition("2", List.of())));
        items.add(new PreconditionItem(new Precondition("3", List.of())));
        var queue = new ExecutionQueue(items);

        // When/Then
        assertThat(queue.remainingCount()).isEqualTo(3);
        queue.next();
        assertThat(queue.remainingCount()).isEqualTo(2);
        queue.next();
        assertThat(queue.remainingCount()).isEqualTo(1);
        queue.next();
        assertThat(queue.remainingCount()).isZero();
    }

    @Test
    void queue_shouldProcessItemsInFifoOrder() {
        // Given
        List<ExecutionItem> items = executionItemsList();
        items.add(new PreconditionItem(new Precondition("first", List.of())));
        items.add(new PreconditionItem(new Precondition("second", List.of())));
        items.add(new PreconditionItem(new Precondition("third", List.of())));
        var queue = new ExecutionQueue(items);

        // When/Then
        assertThat(((PreconditionItem) queue.next()).getDescription()).isEqualTo("first");
        assertThat(((PreconditionItem) queue.next()).getDescription()).isEqualTo("second");
        assertThat(((PreconditionItem) queue.next()).getDescription()).isEqualTo("third");
        assertThat(queue.hasNext()).isFalse();
    }

    @Test
    void queue_shouldSupportMultipleInjections() {
        // Given
        List<ExecutionItem> items = executionItemsList();
        items.add(new PreconditionItem(new Precondition("original", List.of())));
        var queue = new ExecutionQueue(items);

        // When
        List<ExecutionItem> inject1 = executionItemsList();
        inject1.add(new PreconditionItem(new Precondition("inject 1", List.of())));
        queue.injectAtFront(inject1);
        queue.next(); // consume "inject 1"
        
        List<ExecutionItem> inject2 = executionItemsList();
        inject2.add(new PreconditionItem(new Precondition("inject 2", List.of())));
        queue.injectAtFront(inject2);

        // Then
        assertThat(queue.remainingCount()).isEqualTo(2);
        assertThat(((PreconditionItem) queue.next()).getDescription()).isEqualTo("inject 2");
        assertThat(((PreconditionItem) queue.next()).getDescription()).isEqualTo("original");
    }
}
