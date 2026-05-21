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
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
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
import org.mockito.MockedConstruction;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractAgentExecutorTest {

    @Mock
    private RequestContext requestContext;
    @Mock
    private EventQueue eventQueue;
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
        // Create a message with text to satisfy extractTextFromMessage
        Message message = new Message(Message.Role.USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
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

        try (MockedConstruction<TaskUpdater> mockedUpdater = mockConstruction(TaskUpdater.class,
                (mock, context) -> {
                    when(mock.newAgentMessage(anyList(), any())).thenReturn(new Message(Message.Role.USER, List.of(new TextPart("dummy", null)), "id", null, null, null, null, null));
                })) {
            executor.execute(requestContext, eventQueue);

            TaskUpdater updater = mockedUpdater.constructed().get(0);
            verify(updater).submit(); // Verified because context.getTask() is null
            verify(updater).startWork();
            verify(updater).complete(any(Message.class));
        }
    }

    @Test
    void execute_shouldNotSubmitTask_whenTaskIsNotNull() {
        when(requestContext.getTask()).thenReturn(task);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
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

        try (MockedConstruction<TaskUpdater> mockedUpdater = mockConstruction(TaskUpdater.class,
                (mock, context) -> {
                    when(mock.newAgentMessage(anyList(), any())).thenReturn(new Message(Message.Role.USER, List.of(new TextPart("dummy", null)), "id", null, null, null, null, null));
                })) {
            executor.execute(requestContext, eventQueue);

            TaskUpdater updater = mockedUpdater.constructed().get(0);
            verify(updater, never()).submit();
            verify(updater).startWork();
            verify(updater).complete(any(Message.class));
        }
    }

    @Test
    void execute_shouldFailTask_whenMessageIsEmpty() {
        when(requestContext.getTask()).thenReturn(task);
        when(requestContext.getTaskId()).thenReturn("task-123");
        // Message with empty text
        Message message = new Message(Message.Role.USER, List.of(new TextPart("   ", null)), "msg-1", null, null, null,
                null, null);
        when(requestContext.getMessage()).thenReturn(message);

        try (MockedConstruction<TaskUpdater> mockedUpdater = mockConstruction(TaskUpdater.class)) {
            executor.execute(requestContext, eventQueue);

            TaskUpdater updater = mockedUpdater.constructed().get(0);
            verify(updater).startWork();
            verify(updater).fail(any());
        }
    }

    @Test
    void execute_shouldFailTask_whenExceptionDuringExecution() {
        when(requestContext.getTask()).thenReturn(task);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
                null, null, null);
        when(requestContext.getMessage()).thenReturn(message);

        executor.setThrowException(true);

        try (MockedConstruction<TaskUpdater> mockedUpdater = mockConstruction(TaskUpdater.class)) {
            executor.execute(requestContext, eventQueue);

            TaskUpdater updater = mockedUpdater.constructed().get(0);
            verify(updater).startWork();
            verify(updater).fail(any());
        }
    }

    @Test
    void cancel_shouldCancel_whenStateIsValid() {
        when(requestContext.getTask()).thenReturn(task);
        when(task.getStatus()).thenReturn(new TaskStatus(TaskState.SUBMITTED, null, null));

        try (MockedConstruction<TaskUpdater> mockedUpdater = mockConstruction(TaskUpdater.class)) {
            executor.cancel(requestContext, eventQueue);

            TaskUpdater updater = mockedUpdater.constructed().get(0);
            verify(updater).cancel();
        }
    }

    @Test
    void cancel_shouldThrowTaskNotCancelableError_whenStateIsCanceled() {
        when(requestContext.getTask()).thenReturn(task);
        when(task.getStatus()).thenReturn(new TaskStatus(TaskState.CANCELED, null, null));

        assertThatThrownBy(() -> executor.cancel(requestContext, eventQueue))
                .isInstanceOf(io.a2a.spec.TaskNotCancelableError.class);
    }

    @Test
    void cancel_shouldThrowTaskNotCancelableError_whenStateIsCompleted() {
        when(requestContext.getTask()).thenReturn(task);
        when(task.getStatus()).thenReturn(new TaskStatus(TaskState.COMPLETED, null, null));

        assertThatThrownBy(() -> executor.cancel(requestContext, eventQueue))
                .isInstanceOf(io.a2a.spec.TaskNotCancelableError.class);
    }

    @Test
    void execute_shouldAddLogsArtifact_andHandleArtifactException() {
        when(requestContext.getTask()).thenReturn(null);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
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
        executor.setLogsToReturn(List.of("log line 1", "log line 2"));

        try (MockedConstruction<TaskUpdater> mockedUpdater = mockConstruction(TaskUpdater.class,
                (mock, context) -> {
                    when(mock.newAgentMessage(anyList(), any())).thenReturn(new Message(Message.Role.USER, List.of(new TextPart("dummy", null)), "id", null, null, null, null, null));
                })) {
            executor.execute(requestContext, eventQueue);

            TaskUpdater updater = mockedUpdater.constructed().get(0);
            // Verify addArtifact was called (for logs and the main json)
            verify(updater).addArtifact(any(), any(), any(), any());
            verify(updater).complete(any());
        }
    }

    @Test
    void execute_shouldFailTask_whenArtifactCreationFails() {
        when(requestContext.getTask()).thenReturn(null);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
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

        try (MockedConstruction<TaskUpdater> mockedUpdater = mockConstruction(TaskUpdater.class)) {
            executor.execute(requestContext, eventQueue);

            TaskUpdater updater = mockedUpdater.constructed().get(0);
            // It should fail because addSpecificArtifacts throws
            verify(updater).fail(any());
        }
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
