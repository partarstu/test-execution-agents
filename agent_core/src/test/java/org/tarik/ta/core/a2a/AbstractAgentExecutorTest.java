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
package org.tarik.ta.core.a2a;

import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.tasks.AgentEmitter;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentMatchers;

@ExtendWith(MockitoExtension.class)
class AbstractAgentExecutorTest {

    @Mock
    private RequestContext requestContext;
    @Mock
    private AgentEmitter agentEmitter;
    @Mock
    private Task task;

    private TestAgentExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TestAgentExecutor();
    }

    @Test
    void execute_shouldSubmitTask_whenTaskIsNull() {
        when(requestContext.getTask()).thenReturn(null);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.ROLE_USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
                null, null, null);
        when(requestContext.getMessage()).thenReturn(message);
        when(agentEmitter.newAgentMessage(anyList(), any())).thenReturn(new Message(Message.Role.ROLE_USER, List.of(new TextPart("dummy", null)), "id", null, null, null, null, null));

        TestExecutionResult result = new TestExecutionResult(
                "test-case",
                TestExecutionStatus.PASSED,
                Collections.emptyList(),
                Collections.emptyList(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                null);
        executor.setResultToReturn(result);

        executor.execute(requestContext, agentEmitter);

        verify(agentEmitter).submit(); // Verified because context.getTask() is null
        verify(agentEmitter).startWork();
        verify(agentEmitter).complete(any(Message.class));
    }

    @Test
    void execute_shouldNotSubmitTask_whenTaskIsNotNull() {
        when(requestContext.getTask()).thenReturn(task);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.ROLE_USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
                null, null, null);
        when(requestContext.getMessage()).thenReturn(message);
        when(agentEmitter.newAgentMessage(anyList(), any())).thenReturn(new Message(Message.Role.ROLE_USER, List.of(new TextPart("dummy", null)), "id", null, null, null, null, null));

        TestExecutionResult result = new TestExecutionResult(
                "test-case",
                TestExecutionStatus.PASSED,
                Collections.emptyList(),
                Collections.emptyList(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                null);
        executor.setResultToReturn(result);

        executor.execute(requestContext, agentEmitter);

        verify(agentEmitter, never()).submit();
        verify(agentEmitter).startWork();
        verify(agentEmitter).complete(any(Message.class));
    }

    @Test
    void execute_shouldFailTask_whenMessageIsEmpty() {
        when(requestContext.getTask()).thenReturn(task);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.ROLE_USER, List.of(new TextPart("   ", null)), "msg-1", null, null, null,
                null, null);
        when(requestContext.getMessage()).thenReturn(message);

        executor.execute(requestContext, agentEmitter);

        verify(agentEmitter).startWork();
        verify(agentEmitter).fail(ArgumentMatchers.<Message>any());
    }

    @Test
    void execute_shouldFailTask_whenExceptionDuringExecution() {
        when(requestContext.getTask()).thenReturn(task);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.ROLE_USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
                null, null, null);
        when(requestContext.getMessage()).thenReturn(message);

        executor.setThrowException(true);

        executor.execute(requestContext, agentEmitter);

        verify(agentEmitter).startWork();
        verify(agentEmitter).fail(ArgumentMatchers.<Message>any());
    }

    @Test
    void cancel_shouldCancel_whenStateIsValid() {
        when(requestContext.getTask()).thenReturn(task);
        when(task.status()).thenReturn(new TaskStatus(TaskState.TASK_STATE_SUBMITTED, null, null));

        executor.cancel(requestContext, agentEmitter);

        verify(agentEmitter).cancel();
    }

    @Test
    void cancel_shouldThrowTaskNotCancelableError_whenStateIsCanceled() {
        when(requestContext.getTask()).thenReturn(task);
        when(task.status()).thenReturn(new TaskStatus(TaskState.TASK_STATE_CANCELED, null, null));

        assertThatThrownBy(() -> executor.cancel(requestContext, agentEmitter))
                .isInstanceOf(io.a2a.spec.TaskNotCancelableError.class);
    }

    @Test
    void cancel_shouldThrowTaskNotCancelableError_whenStateIsCompleted() {
        when(requestContext.getTask()).thenReturn(task);
        when(task.status()).thenReturn(new TaskStatus(TaskState.TASK_STATE_COMPLETED, null, null));

        assertThatThrownBy(() -> executor.cancel(requestContext, agentEmitter))
                .isInstanceOf(io.a2a.spec.TaskNotCancelableError.class);
    }

    @Test
    void execute_shouldStreamLogLinesAsLogFileArtifacts() {
        when(requestContext.getTask()).thenReturn(null);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.ROLE_USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
                null, null, null);
        when(requestContext.getMessage()).thenReturn(message);
        when(agentEmitter.newAgentMessage(anyList(), any())).thenReturn(new Message(Message.Role.ROLE_USER, List.of(new TextPart("dummy", null)), "id", null, null, null, null, null));

        executor.setResultToReturn(passedResult());
        executor.setLogToEmit("streamed log line");

        executor.execute(requestContext, agentEmitter);

        // The log line is streamed live as a dedicated ".log" file artifact, and the run completes with a message.
        verify(agentEmitter).addArtifact(anyList(), any(), eq("execution_log"), any());
        verify(agentEmitter).complete(any(Message.class));
    }

    @Test
    void execute_shouldFailTask_whenFinalArtifactCreationFails() {
        when(requestContext.getTask()).thenReturn(null);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.ROLE_USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
                null, null, null);
        when(requestContext.getMessage()).thenReturn(message);

        executor.setResultToReturn(passedResult());
        executor.setThrowOnFinalArtifacts(true);

        executor.execute(requestContext, agentEmitter);

        verify(agentEmitter).fail(ArgumentMatchers.<Message>any());
    }

    private static TestExecutionResult passedResult() {
        return new TestExecutionResult(
                "test-case",
                TestExecutionStatus.PASSED,
                Collections.emptyList(),
                Collections.emptyList(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                null);
    }

    // Implementation stub
    static class TestAgentExecutor extends AbstractAgentExecutor {
        private TestExecutionResult resultToReturn;
        private boolean throwException = false;
        private boolean throwOnFinalArtifacts = false;
        private String logToEmit = null;

        public void setResultToReturn(TestExecutionResult result) {
            this.resultToReturn = result;
        }

        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }

        public void setThrowOnFinalArtifacts(boolean throwOnFinalArtifacts) {
            this.throwOnFinalArtifacts = throwOnFinalArtifacts;
        }

        public void setLogToEmit(String logToEmit) {
            this.logToEmit = logToEmit;
        }

        @Override
        protected TestExecutionResult executeTestCase(String message, StreamingEventEmitter eventEmitter) {
            if (throwException) {
                throw new RuntimeException("Simulated error");
            }
            if (logToEmit != null) {
                eventEmitter.emitLog(logToEmit);
            }
            return resultToReturn;
        }

        @Override
        protected List<Part<?>> buildFinalArtifactParts(TestExecutionResult result) {
            if (throwOnFinalArtifacts) {
                throw new RuntimeException("Simulated artifact error");
            }
            return List.of();
        }
    }
}
