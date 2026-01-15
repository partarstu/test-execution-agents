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

    // Implementation stub
    static class TestAgentExecutor extends AbstractAgentExecutor {
        private TestExecutionResult resultToReturn;
        private boolean throwException = false;

        public void setResultToReturn(TestExecutionResult result) {
            this.resultToReturn = result;
        }

        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
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
            // No-op
        }

        @Override
        protected Optional<List<String>> extractLogs(TestExecutionResult result) {
            // No logs in tests
            return Optional.empty();
        }
    }
}
