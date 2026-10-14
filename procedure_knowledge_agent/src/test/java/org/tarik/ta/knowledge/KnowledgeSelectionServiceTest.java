/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
package org.tarik.ta.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.core.exceptions.BudgetExceededException;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.dto.AggregationProcedureSelection;
import org.tarik.ta.dto.ItemKind;
import org.tarik.ta.dto.ItemProcedureSelection;
import org.tarik.ta.dto.KnowledgeResponse.AggregationProcedures;
import org.tarik.ta.dto.KnowledgeResponse.ItemProcedures;
import org.tarik.ta.dto.KnowledgeTestCase;
import org.tarik.ta.dto.ProcedureNode;
import org.tarik.ta.dto.SelectedProcedure;
import org.tarik.ta.dto.TestCaseItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.knowledge.ProcedureFixtures.testCase;

@ExtendWith(MockitoExtension.class)
class KnowledgeSelectionServiceTest {
    private static final UUID LOGIN_ID = UUID.randomUUID();
    private static final UUID CHECKOUT_ID = UUID.randomUUID();

    @Mock
    private ProcedureSelector selector;
    @Mock
    private ProcedureTreeAssembler treeAssembler;
    @Mock
    private BudgetManager budgetManager;
    @Mock
    private KnowledgeAgentConfig config;

    @BeforeEach
    void setUp() {
        lenient().when(config.getItemConcurrency()).thenReturn(8);
        lenient().when(config.getAgentExecutionTimeBudgetSeconds()).thenReturn(30);
        lenient().when(budgetManager.callInTask(anyString(), any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        lenient().when(selector.selectForItem(any(), any())).thenAnswer(invocation -> noSelection(invocation.getArgument(1)));
    }

    @Test
    void select_shouldReturnTheTreesOfTheItemAndAggregationSelections() throws Exception {
        var testCase = testCase();
        when(selector.selectForItem(testCase, testCase.findItem("P1").orElseThrow()))
                .thenReturn(new ItemProcedureSelection("P1", List.of(new SelectedProcedure(LOGIN_ID, "Log in"))));
        when(selector.selectAggregations(eq(testCase), any())).thenReturn(List.of(
                new AggregationProcedureSelection(List.of("S2", "S3"), List.of(new SelectedProcedure(CHECKOUT_ID, "Check out")))));
        when(treeAssembler.assembleTree(LOGIN_ID)).thenReturn(Optional.of(node(LOGIN_ID, "Log in")));
        when(treeAssembler.assembleTree(CHECKOUT_ID)).thenReturn(Optional.of(node(CHECKOUT_ID, "Check out")));

        var response = newService().select("task-1", testCase);

        assertThat(response.items()).containsExactly(
                new ItemProcedures("P1", List.of(node(LOGIN_ID, "Log in"))), new ItemProcedures("P2", List.of()),
                new ItemProcedures("S1", List.of()), new ItemProcedures("S2", List.of()), new ItemProcedures("S3", List.of()));
        assertThat(response.aggregations()).containsExactly(new AggregationProcedures(List.of("S2", "S3"),
                List.of(node(CHECKOUT_ID, "Check out"))));
    }

    @Test
    void select_shouldStartTheAggregationRunFromTheItemSelectionsInItemOrder() throws Exception {
        var testCase = testCase();

        newService().select("task-1", testCase);

        verify(selector).selectAggregations(testCase, List.of(new ItemProcedureSelection("P1", List.of()),
                new ItemProcedureSelection("P2", List.of()), new ItemProcedureSelection("S1", List.of()),
                new ItemProcedureSelection("S2", List.of()), new ItemProcedureSelection("S3", List.of())));
    }

    @Test
    void select_shouldAccountEveryRunToTheTask() throws Exception {
        newService().select("task-1", testCase());

        verify(budgetManager, times(testCase().items().size() + 1)).callInTask(eq("task-1"), any());
    }

    @Test
    void select_shouldLeaveOutASelectedProcedureWhoseTreeIsNoLongerInTheGraph() throws Exception {
        var testCase = testCase();
        when(selector.selectForItem(testCase, testCase.findItem("S1").orElseThrow()))
                .thenReturn(new ItemProcedureSelection("S1", List.of(new SelectedProcedure(LOGIN_ID, "Log in"))));
        when(treeAssembler.assembleTree(LOGIN_ID)).thenReturn(Optional.empty());

        var response = newService().select("task-1", testCase);

        assertThat(response.items()).contains(new ItemProcedures("S1", List.of()));
    }

    @Test
    void select_shouldFail_whenARunStartsAfterTheTaskUsedUpItsBudget() {
        doThrow(new BudgetExceededException("Token budget exceeded: 11 > 10")).when(selector).selectForItem(any(), any());

        assertThatThrownBy(() -> newService().select("task-1", testCase()))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("Token budget exceeded");
    }

    @Test
    void select_shouldTimeOutAndInterruptTheRunningItemRuns_whenTheyExceedTheTimeBudget() {
        when(config.getAgentExecutionTimeBudgetSeconds()).thenReturn(1);
        var release = new CountDownLatch(1);
        var interrupted = new AtomicBoolean(false);
        doAnswer(invocation -> {
            try {
                release.await(5, SECONDS);
            } catch (InterruptedException _) {
                interrupted.set(true);
            }
            return noSelection(invocation.getArgument(1));
        }).when(selector).selectForItem(any(), any());

        assertThatThrownBy(() -> newService().select("task-1", testCase())).isInstanceOf(TimeoutException.class);
        assertThat(interrupted).isTrue();
    }

    @Test
    void select_shouldTimeOutAndInterruptTheAggregationRun_whenItExceedsTheRestOfTheTimeBudget() {
        when(config.getAgentExecutionTimeBudgetSeconds()).thenReturn(1);
        var interrupted = new AtomicBoolean(false);
        when(selector.selectAggregations(any(), any())).thenAnswer(_ -> {
            try {
                new CountDownLatch(1).await(5, SECONDS);
            } catch (InterruptedException _) {
                interrupted.set(true);
            }
            return List.of();
        });

        assertThatThrownBy(() -> newService().select("task-1", testCase())).isInstanceOf(TimeoutException.class);
        assertThat(interrupted).isTrue();
    }

    @Test
    void select_shouldShareTheItemRunSlotsBetweenTasks() throws Exception {
        when(config.getItemConcurrency()).thenReturn(1);
        var service = newService();
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        var firstRunStarted = new CountDownLatch(1);
        var secondRunStarted = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            firstRunStarted.countDown();
            secondRunStarted.countDown();
            release.await(5, SECONDS);
            inFlight.decrementAndGet();
            return noSelection(invocation.getArgument(1));
        }).when(selector).selectForItem(any(), any());

        try (var tasks = newVirtualThreadPerTaskExecutor()) {
            var first = tasks.submit(() -> service.select("task-1", singleStep("First")));
            var second = tasks.submit(() -> service.select("task-2", singleStep("Second")));
            assertThat(firstRunStarted.await(5, SECONDS)).isTrue();
            assertThat(secondRunStarted.await(300, MILLISECONDS)).as("the other task's run must wait for the slot").isFalse();
            release.countDown();
            first.get(5, SECONDS);
            second.get(5, SECONDS);
        }
        assertThat(maxInFlight).hasValue(1);
    }

    private KnowledgeSelectionService newService() {
        return new KnowledgeSelectionService(selector, treeAssembler, budgetManager, config);
    }

    private static ItemProcedureSelection noSelection(TestCaseItem item) {
        return new ItemProcedureSelection(item.id(), List.of());
    }

    private static KnowledgeTestCase singleStep(String name) {
        return new KnowledgeTestCase(name, List.of(new TestCaseItem(ItemKind.STEP, 1, "Open the catalog", List.of(), "")));
    }

    private static ProcedureNode node(UUID id, String description) {
        return new ProcedureNode(id.toString(), description, false, false, false, List.of(), "Done", List.of(), List.of(), List.of(), null,
                1, null, List.of(), false);
    }
}
