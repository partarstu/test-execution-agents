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
package org.tarik.ta.knowledge_graph.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;
import org.tarik.ta.knowledge_graph.model.node.ExecutionRun;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Outcome;
import org.tarik.ta.knowledge_graph.model.node.StepResolution.Source;
import org.tarik.ta.knowledge_graph.repository.ExecutionRunRepository;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.MatchConfidence;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.MatchResult;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.ScoredProcedure;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.model.ContextEntry.StepType.PRECONDITION;
import static org.tarik.ta.core.model.ContextEntry.StepType.TEST_STEP;

@ExtendWith(MockitoExtension.class)
class ExecutionRunServiceTest {
    @Mock
    private ExecutionRunRepository repository;
    @Mock
    private AsyncExecutionPersistenceService persistence;
    @Mock
    private UiTestAgentConfig config;

    private ExecutionRunService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionRunService(repository, persistence, config);
    }

    @Test
    void start_shouldCreateTheRunThroughTheExecutionPersistence() {
        runPersistedWritesAtOnce();
        when(config.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);

        service.start("Checkout");

        var run = ArgumentCaptor.forClass(ExecutionRun.class);
        verify(repository).createRun(run.capture());
        assertThat(run.getValue().testCaseName()).isEqualTo("Checkout");
        assertThat(run.getValue().mode()).isEqualTo(ExecutionMode.UNATTENDED);
        verify(persistence).persistRunHistory(any(), anyString());
    }

    @Test
    void recordResolution_shouldKeepTheCandidatesWithTheirScores_andAppendInSequence() {
        runPersistedWritesAtOnce();
        when(config.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        service.start("Checkout");
        var procedure = Procedure.createAtomic("Click Save", List.of(), "", List.of(), List.of(), false);
        var match = new MatchResult(procedure, MatchConfidence.LOW,
                List.of(new ScoredProcedure(procedure, 1, 2, 0.83, 1, true, List.of("Form open"))), true, true, true);
        var step = new TestStepItem(new TestStep("Save the form", List.of(), "Saved"));

        var first = service.recordResolution(step, Source.OPERATOR_SELECTED, match, procedure.id(), Outcome.SUCCEEDED, 1200);
        var precondition = new PreconditionItem(new Precondition("Open the form", List.of()));
        var second = service.recordResolution(precondition, Source.UNRESOLVED, null, null, Outcome.FAILED, 30);

        assertThat(first.sequence()).isZero();
        assertThat(first.itemType()).isEqualTo(TEST_STEP);
        assertThat(first.confidence()).isEqualTo(MatchConfidence.LOW);
        assertThat(first.wasDemoted()).isTrue();
        assertThat(first.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.semanticScore()).isEqualTo(0.83);
            assertThat(candidate.satisfiedPrerequisites()).isEqualTo(1);
            assertThat(candidate.totalPrerequisites()).isEqualTo(2);
            assertThat(candidate.sharedParentCount()).isEqualTo(1);
            assertThat(candidate.lowStability()).isTrue();
            assertThat(candidate.unmetPrerequisites()).containsExactly("Form open");
        });
        assertThat(second.sequence()).isEqualTo(1);
        assertThat(second.itemType()).isEqualTo(PRECONDITION);
        assertThat(second.candidates()).isEmpty();
        assertThat(second.confidence()).isNull();
        verify(repository).appendResolution(any(), eq(first));
        verify(repository).appendResolution(any(), eq(second));
    }

    @Test
    void recordResolution_shouldStillAnswerTheResolution_butStoreNothing_whenNoRunWasStarted() {
        var precondition = new PreconditionItem(new Precondition("Open the form", List.of()));
        var resolution = service.recordResolution(precondition, Source.KNOWLEDGE, null, null,
                Outcome.NOT_EXECUTED, 0);

        assertThat(resolution.outcome()).isEqualTo(Outcome.NOT_EXECUTED);
        verifyNoInteractions(persistence, repository);
    }

    @Test
    void start_shouldNotFailTheRun_whenTheGraphIsUnreachable() {
        runPersistedWritesAtOnce();
        when(config.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        doThrow(new DatabaseConnectionException("down", new RuntimeException())).when(repository).createRun(any());

        assertThatCode(() -> service.start("Checkout")).doesNotThrowAnyException();
    }

    @Test
    void finish_shouldWriteSynchronously_evenOnAnInterruptedThread_andRestoreTheInterrupt() {
        when(config.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        service.start("Checkout");
        var interruptedDuringWrite = new boolean[1];
        doAnswer(_ -> {
            interruptedDuringWrite[0] = Thread.currentThread().isInterrupted();
            return null;
        }).when(repository).finishRun(any(), any(), eq(ExecutionRun.Status.ABORTED));

        Thread.currentThread().interrupt();
        try {
            service.finish(ExecutionRun.Status.ABORTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(interruptedDuringWrite[0]).isFalse();
        verify(persistence, never()).persistRunHistory(any(), eq("finishRun"));
    }

    @Test
    void finish_shouldAwaitTheQueuedRunHistoryWrites_withoutTheInterrupt_beforeWritingTheEnd() {
        when(config.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        service.start("Checkout");
        var interruptedDuringAwait = new boolean[]{true};
        doAnswer(_ -> {
            interruptedDuringAwait[0] = Thread.currentThread().isInterrupted();
            return null;
        }).when(persistence).awaitRunHistoryWrites();

        Thread.currentThread().interrupt();
        try {
            service.finish(ExecutionRun.Status.ABORTED);
        } finally {
            Thread.interrupted();
        }

        assertThat(interruptedDuringAwait[0]).isFalse();
        var order = inOrder(persistence, repository);
        order.verify(persistence).awaitRunHistoryWrites();
        order.verify(repository).finishRun(any(), any(), eq(ExecutionRun.Status.ABORTED));
    }

    @Test
    void finish_shouldDoNothing_whenNoRunWasStarted() {
        service.finish(ExecutionRun.Status.PASSED);

        verifyNoInteractions(repository);
    }

    private void runPersistedWritesAtOnce() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(persistence).persistRunHistory(any(), anyString());
    }
}
