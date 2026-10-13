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
package org.tarik.ta.a2a;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.FilePart;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiAgentRequestScopeFactory;
import org.tarik.ta.UiTestAgent;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.panel.RunObserver;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;

@ExtendWith(MockitoExtension.class)
class UiAgentExecutorTest {
    private static final BufferedImage SCREENSHOT = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

    @Test
    void executeTestCase_shouldDelegateToRequestScopedUiTestAgent_withTheObservedEmitter_andReportTheResult() {
        UiAgentRequestScopeFactory requestScopeFactory = mock(UiAgentRequestScopeFactory.class);
        BeanScope requestScope = mock(BeanScope.class);
        UiTestAgent uiTestAgent = mock(UiTestAgent.class);
        RunObserver runObserver = mock(RunObserver.class);
        StreamingEventEmitter observedEmitter = mock(StreamingEventEmitter.class);
        UiAgentExecutor executor = new UiAgentExecutor(requestScopeFactory, runObserver);
        TestExecutionResult expectedResult = mock(TestExecutionResult.class);

        when(runObserver.observe(StreamingEventEmitter.NOOP)).thenReturn(observedEmitter);
        when(requestScopeFactory.create(any(VisualState.class), eq(observedEmitter))).thenReturn(requestScope);
        when(requestScope.get(UiTestAgent.class)).thenReturn(uiTestAgent);
        when(uiTestAgent.executeTestCase("run test")).thenReturn(expectedResult);

        try (MockedStatic<UiCommonUtils> commonUtils = mockStatic(UiCommonUtils.class)) {
            commonUtils.when(UiCommonUtils::captureScreen).thenReturn(SCREENSHOT);

            TestExecutionResult result = executor.executeTestCase("run test", StreamingEventEmitter.NOOP);

            assertThat(result).isSameAs(expectedResult);
            verify(uiTestAgent).executeTestCase("run test");
            verify(requestScope).close();
            verify(runObserver).taskFinished(expectedResult);
            verify(runObserver, never()).taskAborted();
        }
    }

    @Test
    void executeTestCase_shouldReportTheTaskAborted_whenTheRunThrows() {
        UiAgentRequestScopeFactory requestScopeFactory = mock(UiAgentRequestScopeFactory.class);
        BeanScope requestScope = mock(BeanScope.class);
        UiTestAgent uiTestAgent = mock(UiTestAgent.class);
        RunObserver runObserver = mock(RunObserver.class);
        UiAgentExecutor executor = new UiAgentExecutor(requestScopeFactory, runObserver);

        when(runObserver.observe(any())).thenReturn(StreamingEventEmitter.NOOP);
        when(requestScopeFactory.create(any(VisualState.class), any(StreamingEventEmitter.class))).thenReturn(requestScope);
        when(requestScope.get(UiTestAgent.class)).thenReturn(uiTestAgent);
        when(uiTestAgent.executeTestCase("run test")).thenThrow(new IllegalStateException("boom"));

        try (MockedStatic<UiCommonUtils> commonUtils = mockStatic(UiCommonUtils.class)) {
            commonUtils.when(UiCommonUtils::captureScreen).thenReturn(SCREENSHOT);

            assertThatThrownBy(() -> executor.executeTestCase("run test", StreamingEventEmitter.NOOP))
                    .isInstanceOf(IllegalStateException.class);

            verify(runObserver).taskAborted();
            verify(runObserver, never()).taskFinished(any());
        }
    }

    @Test
    void executeTestCase_shouldReportTheTaskAborted_whenTheTaskWasCancelled_evenThoughTheRunReturnedAResult() {
        UiAgentRequestScopeFactory requestScopeFactory = mock(UiAgentRequestScopeFactory.class);
        BeanScope requestScope = mock(BeanScope.class);
        UiTestAgent uiTestAgent = mock(UiTestAgent.class);
        RunObserver runObserver = mock(RunObserver.class);
        UiAgentExecutor executor = new UiAgentExecutor(requestScopeFactory, runObserver);
        RequestContext requestContext = mock(RequestContext.class);
        Task task = mock(Task.class);
        when(requestContext.getTask()).thenReturn(task);
        when(task.status()).thenReturn(new TaskStatus(TaskState.TASK_STATE_WORKING, null, null));
        executor.cancel(requestContext, mock(AgentEmitter.class));

        when(runObserver.observe(any())).thenReturn(StreamingEventEmitter.NOOP);
        when(requestScopeFactory.create(any(VisualState.class), any(StreamingEventEmitter.class))).thenReturn(requestScope);
        when(requestScope.get(UiTestAgent.class)).thenReturn(uiTestAgent);
        when(uiTestAgent.executeTestCase("run test")).thenReturn(mock(TestExecutionResult.class));

        try (MockedStatic<UiCommonUtils> commonUtils = mockStatic(UiCommonUtils.class)) {
            commonUtils.when(UiCommonUtils::captureScreen).thenReturn(SCREENSHOT);

            executor.executeTestCase("run test", StreamingEventEmitter.NOOP);

            verify(runObserver).taskAborted();
            verify(runObserver, never()).taskFinished(any());
        }
    }

    @Test
    void buildStepArtifactParts_shouldAddScreenshotPart_whenStepHasScreenshot() {
        UiAgentExecutor executor = new UiAgentExecutor(mock(UiAgentRequestScopeFactory.class), mock(RunObserver.class));
        BufferedImage screenshot = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        UiTestStepResult stepResult = new UiTestStepResult(new TestStep("click the button", null, null),
                SUCCESS, null, "done", screenshot, Instant.now(), Instant.now());

        List<Part<?>> parts = executor.buildStepArtifactParts(stepResult);

        assertThat(parts).hasSize(1);
        assertThat(parts.getFirst()).isInstanceOf(FilePart.class);
    }

    @Test
    void buildStepArtifactParts_shouldBeEmpty_whenStepHasNoScreenshot() {
        UiAgentExecutor executor = new UiAgentExecutor(mock(UiAgentRequestScopeFactory.class), mock(RunObserver.class));
        UiTestStepResult stepResult = new UiTestStepResult(new TestStep("click the button", null, null),
                SUCCESS, null, "done", null, Instant.now(), Instant.now());

        assertThat(executor.buildStepArtifactParts(stepResult)).isEmpty();
    }

    @Test
    void buildFinalArtifactParts_shouldBeEmpty_whenResultHasNoScreenshot() {
        UiAgentExecutor executor = new UiAgentExecutor(mock(UiAgentRequestScopeFactory.class), mock(RunObserver.class));
        UiTestExecutionResult result = mock(UiTestExecutionResult.class);
        when(result.getScreenshot()).thenReturn(null);

        assertThat(executor.buildFinalArtifactParts(result)).isEmpty();
    }

    @Test
    void buildFinalArtifactParts_shouldContainOnlyGeneralScreenshot_withoutRebundlingStepScreenshots() {
        UiAgentExecutor executor = new UiAgentExecutor(mock(UiAgentRequestScopeFactory.class), mock(RunObserver.class));
        BufferedImage screenshot = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        UiTestExecutionResult result = mock(UiTestExecutionResult.class);
        when(result.getTestCaseName()).thenReturn("sample test");
        when(result.getScreenshot()).thenReturn(screenshot);

        List<Part<?>> parts = executor.buildFinalArtifactParts(result);

        assertThat(parts).hasSize(1);
        assertThat(parts.getFirst()).isInstanceOf(FilePart.class);
    }
}
