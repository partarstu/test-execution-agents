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

import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.dto.AggregationProcedureSelection;
import org.tarik.ta.dto.ItemProcedureSelection;
import org.tarik.ta.dto.KnowledgeResponse;
import org.tarik.ta.dto.KnowledgeResponse.AggregationProcedures;
import org.tarik.ta.dto.KnowledgeResponse.ItemProcedures;
import org.tarik.ta.dto.KnowledgeTestCase;
import org.tarik.ta.dto.ProcedureNode;
import org.tarik.ta.dto.SelectedProcedure;
import org.tarik.ta.dto.TestCaseItem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import static java.lang.System.nanoTime;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Selects the procedures of one test case for a task: the item runs in parallel, then the aggregation run, and returns the selections
 * as their original trees. Thread-safe: the item runs of all tasks share a fixed number of slots, and the usage of every run is accounted
 * to its task.
 */
@Singleton
public class KnowledgeSelectionService {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeSelectionService.class);

    private final ProcedureSelector selector;
    private final ProcedureTreeAssembler treeAssembler;
    private final BudgetManager budgetManager;
    private final Semaphore itemRunSlots;
    private final Duration selectionTimeout;

    public KnowledgeSelectionService(ProcedureSelector selector, ProcedureTreeAssembler treeAssembler, BudgetManager budgetManager,
                                     KnowledgeAgentConfig config) {
        this.selector = selector;
        this.treeAssembler = treeAssembler;
        this.budgetManager = budgetManager;
        this.itemRunSlots = new Semaphore(config.getItemConcurrency(), true);
        this.selectionTimeout = Duration.ofSeconds(config.getAgentExecutionTimeBudgetSeconds());
    }

    /**
     * Runs the selection runs of the test case with their usage accounted to the task.
     *
     * @throws TimeoutException when the item runs and the aggregation run together take longer than the agent's execution time budget
     * @throws org.tarik.ta.core.exceptions.BudgetExceededException when a run starts after the task has used up its token budget
     */
    public @NotNull KnowledgeResponse select(@NotNull String taskId, @NotNull KnowledgeTestCase testCase)
            throws InterruptedException, TimeoutException {
        LOG.info("Selecting procedures for the {} items of test case '{}' in task {}", testCase.items().size(), testCase.name(), taskId);
        long deadline = nanoTime() + selectionTimeout.toNanos();
        List<ItemProcedureSelection> itemSelections;
        List<AggregationProcedureSelection> aggregations;
        try (var executor = newVirtualThreadPerTaskExecutor()) {
            try {
                itemSelections = awaitAll(testCase.items().stream()
                        .map(item -> executor.submit(() -> selectInSlot(taskId, testCase, item)))
                        .toList(), deadline);
                var aggregationRun = executor.submit(() ->
                        budgetManager.callInTask(taskId, () -> selector.selectAggregations(testCase, itemSelections)));
                aggregations = await(aggregationRun, deadline);
            } catch (InterruptedException | TimeoutException | RuntimeException e) {
                executor.shutdownNow();
                throw e;
            }
        }
        LOG.info("Selected procedures for test case '{}' in task {}: {} items with a match, {} aggregations", testCase.name(), taskId,
                itemSelections.stream().filter(selection -> !selection.procedures().isEmpty()).count(), aggregations.size());
        return new KnowledgeResponse(itemSelections.stream().map(this::toItemProcedures).toList(),
                aggregations.stream().map(this::toAggregationProcedures).toList());
    }

    private ItemProcedureSelection selectInSlot(String taskId, KnowledgeTestCase testCase, TestCaseItem item) throws InterruptedException {
        itemRunSlots.acquire();
        try {
            return budgetManager.callInTask(taskId, () -> selector.selectForItem(testCase, item));
        } finally {
            itemRunSlots.release();
        }
    }

    private static List<ItemProcedureSelection> awaitAll(List<Future<ItemProcedureSelection>> runs, long deadline)
            throws InterruptedException, TimeoutException {
        var selections = new ArrayList<ItemProcedureSelection>(runs.size());
        for (var run : runs) {
            selections.add(await(run, deadline));
        }
        return selections;
    }

    private static <T> T await(Future<T> run, long deadline) throws InterruptedException, TimeoutException {
        try {
            return run.get(Math.max(0, deadline - nanoTime()), NANOSECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException("A selection run failed", e.getCause());
        }
    }

    private ItemProcedures toItemProcedures(ItemProcedureSelection selection) {
        return new ItemProcedures(selection.itemId(), assembleTrees(selection.procedures()));
    }

    private AggregationProcedures toAggregationProcedures(AggregationProcedureSelection selection) {
        return new AggregationProcedures(selection.itemIds(), assembleTrees(selection.procedures()));
    }

    private List<ProcedureNode> assembleTrees(List<SelectedProcedure> procedures) {
        return procedures.stream()
                .map(procedure -> treeAssembler.assembleTree(procedure.id()))
                .flatMap(Optional::stream)
                .toList();
    }
}
