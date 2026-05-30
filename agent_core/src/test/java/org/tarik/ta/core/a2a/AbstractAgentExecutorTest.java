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
import org.tarik.ta.core.dto.TestStep;

import java.time.Instant;
import java.util.ArrayList;
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
        executor.addLogToEmit("first line");
        executor.addLogToEmit("second line");

        executor.execute(requestContext, agentEmitter);

        // The first line establishes the growing "execution_log.log" artifact (append=false); the second is appended (append=true).
        verify(agentEmitter).addArtifact(anyList(), eq("execution_log"), eq("execution_log.log"), any(), eq(false), eq(false));
        verify(agentEmitter).addArtifact(anyList(), eq("execution_log"), eq("execution_log.log"), any(), eq(true), eq(false));
        verify(agentEmitter).complete(any(Message.class));
    }

    @Test
    void execute_shouldStreamCurrentActivityWhenStepStarts() {
        when(requestContext.getTask()).thenReturn(null);
        when(requestContext.getTaskId()).thenReturn("task-123");
        Message message = new Message(Message.Role.ROLE_USER, List.of(new TextPart("run test", null)), "msg-1", null, null,
                null, null, null);
        when(requestContext.getMessage()).thenReturn(message);
        when(agentEmitter.newAgentMessage(anyList(), any())).thenReturn(new Message(Message.Role.ROLE_USER, List.of(new TextPart("dummy", null)), "id", null, null, null, null, null));

        executor.setResultToReturn(passedResult());
        executor.setStepToStart(new TestStep("click the button", null, null));

        executor.execute(requestContext, agentEmitter);

        // The step-about-to-run is streamed as a WORKING status update before its result, and the run completes.
        verify(agentEmitter).updateStatus(eq(TaskState.TASK_STATE_WORKING), any(Message.class));
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
        private final List<String> logsToEmit = new ArrayList<>();
        private TestStep stepToStart = null;

        public void setResultToReturn(TestExecutionResult result) {
            this.resultToReturn = result;
        }

        public void setStepToStart(TestStep stepToStart) {
            this.stepToStart = stepToStart;
        }

        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }

        public void setThrowOnFinalArtifacts(boolean throwOnFinalArtifacts) {
            this.throwOnFinalArtifacts = throwOnFinalArtifacts;
        }

        public void addLogToEmit(String logLine) {
            this.logsToEmit.add(logLine);
        }

        @Override
        protected TestExecutionResult executeTestCase(String message, StreamingEventEmitter eventEmitter) {
            if (throwException) {
                throw new RuntimeException("Simulated error");
            }
            if (stepToStart != null) {
                eventEmitter.emitStepStarted(stepToStart);
            }
            logsToEmit.forEach(eventEmitter::emitLog);
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
