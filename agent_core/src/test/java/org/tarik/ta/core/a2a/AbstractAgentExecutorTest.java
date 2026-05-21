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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    void execute_shouldAddLogsArtifact_andHandleArtifactException() {
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
        executor.setLogsToReturn(List.of("log line 1", "log line 2"));

        executor.execute(requestContext, agentEmitter);

        // Verify addArtifact was called (for logs and the main json)
        verify(agentEmitter).addArtifact(any(), any(), any(), any());
        verify(agentEmitter).complete(any());
    }

    @Test
    void execute_shouldFailTask_whenArtifactCreationFails() {
        when(requestContext.getTask()).thenReturn(null);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.ROLE_USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
                null, null, null);
        when(requestContext.getMessage()).thenReturn(message);

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
        executor.setThrowOnArtifacts(true);

        executor.execute(requestContext, agentEmitter);

        // It should fail because addSpecificArtifacts throws
        verify(agentEmitter).fail(ArgumentMatchers.<Message>any());
    }

    // Implementation stub
    static class TestAgentExecutor extends AbstractAgentExecutor {
        private TestExecutionResult resultToReturn;
        private boolean throwException = false;
        private boolean throwOnArtifacts = false;
        private List<String> logsToReturn = null;

        public void setResultToReturn(TestExecutionResult result) {
            this.resultToReturn = result;
        }

        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }

        public void setThrowOnArtifacts(boolean throwOnArtifacts) {
            this.throwOnArtifacts = throwOnArtifacts;
        }

        public void setLogsToReturn(List<String> logsToReturn) {
            this.logsToReturn = logsToReturn;
        }

        @Override
        protected TestExecutionResult executeTestCase(String message) {
            if (throwException) {
                throw new RuntimeException("Simulated error");
            }
            return resultToReturn;
        }

        @Override
        protected void addSpecificArtifacts(TestExecutionResult result, List<Part<?>> parts) {
            if (throwOnArtifacts) {
                throw new RuntimeException("Simulated artifact error");
            }
        }

        @Override
        protected Optional<List<String>> extractLogs(TestExecutionResult result) {
            return Optional.ofNullable(logsToReturn);
        }
    }
}
