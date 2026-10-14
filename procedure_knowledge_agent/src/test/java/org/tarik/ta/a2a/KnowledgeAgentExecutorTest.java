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
package org.tarik.ta.a2a;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.exceptions.BudgetExceededException;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.telemetry.TokenUsageRecord;
import org.tarik.ta.dto.KnowledgeResponse;
import org.tarik.ta.dto.KnowledgeResponse.ItemProcedures;
import org.tarik.ta.knowledge.KnowledgeSelectionService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.knowledge.ProcedureFixtures.testCase;

@ExtendWith(MockitoExtension.class)
class KnowledgeAgentExecutorTest {
    private static final String TASK_ID = "task-1";
    private static final Message REQUEST = new Message(Message.Role.ROLE_USER, List.of(new DataPart(Map.of("name", "Order"))),
            "message-1", null, null, null, null, null);
    private static final TokenUsageRecord USAGE = new TokenUsageRecord("procedure_item_selection", "google", "model", "model", "host",
            7, 3, 0, 100);

    @Mock
    private KnowledgeRequestReader requestReader;
    @Mock
    private KnowledgeSelectionService selectionService;
    @Mock
    private BudgetManager budgetManager;
    @Mock
    private RequestContext context;
    @Mock
    private AgentEmitter emitter;

    private KnowledgeAgentExecutor executor;

    @BeforeEach
    void setUp() {
        lenient().when(context.getTaskId()).thenReturn(TASK_ID);
        lenient().when(context.getMessage()).thenReturn(REQUEST);
        lenient().when(requestReader.read(REQUEST)).thenReturn(testCase());
        lenient().when(budgetManager.releaseTask(TASK_ID)).thenReturn(List.of(USAGE));
        lenient().when(emitter.newAgentMessage(anyList(), any())).thenAnswer(invocation ->
                new Message(Message.Role.ROLE_AGENT, invocation.getArgument(0), "reply-1", null, null, null, null, null));
        executor = new KnowledgeAgentExecutor(requestReader, selectionService, budgetManager);
    }

    @Test
    void execute_shouldCompleteWithTheSelectionAsDataPart_afterReportingTheTokenUsage() throws Exception {
        when(selectionService.select(TASK_ID, testCase())).thenReturn(new KnowledgeResponse(List.of(new ItemProcedures("S1", List.of())),
                List.of()));

        executor.execute(context, emitter);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Part<?>>> partsCaptor = ArgumentCaptor.forClass(List.class);
        var order = inOrder(emitter);
        order.verify(emitter).submit();
        order.verify(emitter).startWork();
        order.verify(emitter).addArtifact(anyList(), isNull(), eq("token_usage"), isNull());
        order.verify(emitter).addArtifact(partsCaptor.capture(), isNull(), eq(KnowledgeAgentExecutor.RESULT_ARTIFACT), isNull());
        order.verify(emitter).complete();
        assertThat(partsCaptor.getValue()).singleElement().isInstanceOfSatisfying(DataPart.class, part -> assertThat(part.data())
                .isEqualTo(Map.of("items", List.of(Map.of("item_id", "S1", "procedures", List.of())), "aggregations", List.of())));
    }

    @Test
    void execute_shouldFailWithTheReason_whenTheRequestIsInvalid() {
        when(requestReader.read(REQUEST)).thenThrow(new InvalidKnowledgeRequestException("The test case name is blank"));

        executor.execute(context, emitter);

        assertThat(failureReason()).isEqualTo("Invalid request: The test case name is blank");
        verify(emitter, never()).complete();
    }

    @Test
    void execute_shouldFail_whenTheTaskUsedUpItsBudget() throws Exception {
        when(selectionService.select(TASK_ID, testCase())).thenThrow(new BudgetExceededException("Token budget exceeded: 11 > 10"));

        executor.execute(context, emitter);

        assertThat(failureReason()).contains("used up its budget", "Token budget exceeded");
    }

    @Test
    void execute_shouldFail_whenTheItemRunsTimeOut() throws Exception {
        when(selectionService.select(TASK_ID, testCase())).thenThrow(new TimeoutException());

        executor.execute(context, emitter);

        assertThat(failureReason()).isEqualTo("The procedure selection exceeded its execution time budget");
    }

    @Test
    void execute_shouldFailWithoutTheErrorDetails_whenTheSelectionFailsUnexpectedly() throws Exception {
        when(selectionService.select(TASK_ID, testCase())).thenThrow(new IllegalStateException("bolt://neo4j:7687 refused"));

        executor.execute(context, emitter);

        assertThat(failureReason()).isEqualTo("The procedure selection failed; see the agent's logs of task task-1")
                .doesNotContain("neo4j");
    }

    @Test
    void cancel_shouldInterruptTheRunningTask_andEmitNoFurtherEvent() throws Exception {
        var selectionStarted = new CountDownLatch(1);
        var interruptLeftAfterExecute = new AtomicBoolean(true);
        when(selectionService.select(TASK_ID, testCase())).thenAnswer(_ -> {
            selectionStarted.countDown();
            new CountDownLatch(1).await(10, SECONDS);
            return null;
        });

        try (var threads = newVirtualThreadPerTaskExecutor()) {
            var execution = threads.submit(() -> {
                executor.execute(context, emitter);
                interruptLeftAfterExecute.set(Thread.currentThread().isInterrupted());
            });
            assertThat(selectionStarted.await(5, SECONDS)).isTrue();
            var cancelEmitter = mock(AgentEmitter.class);

            executor.cancel(context, cancelEmitter);

            execution.get(5, SECONDS);
            verify(cancelEmitter).cancel();
        }
        verify(emitter, never()).complete();
        verify(emitter, never()).fail(any(Message.class));
        assertThat(interruptLeftAfterExecute).as("the pooled thread must not keep the cancel's interrupt").isFalse();
    }

    @Test
    void cancel_shouldReject_whenTheTaskIsNotRunning() {
        assertThatThrownBy(() -> executor.cancel(context, emitter)).isInstanceOf(TaskNotCancelableError.class);
    }

    private String failureReason() {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).fail(messageCaptor.capture());
        return ((TextPart) messageCaptor.getValue().parts().getFirst()).text();
    }
}
