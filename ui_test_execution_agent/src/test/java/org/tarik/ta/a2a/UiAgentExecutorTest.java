/*
 * Copyright Â© 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.a2a;

import io.a2a.spec.Part;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiAgentRequestScopeFactory;
import org.tarik.ta.UiTestAgent;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiAgentExecutorTest {

    @Test
    void executeTestCase_shouldDelegateToRequestScopedUiTestAgent() {
        UiAgentRequestScopeFactory requestScopeFactory = mock(UiAgentRequestScopeFactory.class);
        BeanScope requestScope = mock(BeanScope.class);
        UiTestAgent uiTestAgent = mock(UiTestAgent.class);
        BufferedImage screenshot = mock(BufferedImage.class);
        UiAgentExecutor executor = new UiAgentExecutor(requestScopeFactory);
        TestExecutionResult expectedResult = mock(TestExecutionResult.class);

        when(requestScopeFactory.create(any(VisualState.class))).thenReturn(requestScope);
        when(requestScope.get(UiTestAgent.class)).thenReturn(uiTestAgent);
        when(uiTestAgent.executeTestCase("run test")).thenReturn(expectedResult);

        try (MockedStatic<UiCommonUtils> commonUtils = org.mockito.Mockito.mockStatic(UiCommonUtils.class)) {
            commonUtils.when(UiCommonUtils::captureScreen).thenReturn(screenshot);

            TestExecutionResult result = executor.executeTestCase("run test");

            assertThat(result).isSameAs(expectedResult);
            verify(requestScopeFactory).create(any(VisualState.class));
            verify(requestScope).get(UiTestAgent.class);
            verify(uiTestAgent).executeTestCase("run test");
            verify(requestScope).close();
        }
    }

    @Test
    void addSpecificArtifacts_shouldLeavePartsEmptyWhenNoScreenshotsExist() {
        UiAgentExecutor executor = new UiAgentExecutor(mock(UiAgentRequestScopeFactory.class));
        UiTestExecutionResult result = mock(UiTestExecutionResult.class);
        List<Part<?>> parts = new ArrayList<>();

        when(result.getStepResults()).thenReturn(List.of());

        executor.addSpecificArtifacts(result, parts);

        assertThat(parts).isEmpty();
    }

    @Test
    void extractLogs_shouldReturnLogsWhenPresent() {
        UiAgentExecutor executor = new UiAgentExecutor(mock(UiAgentRequestScopeFactory.class));
        TestExecutionResult result = mock(TestExecutionResult.class);
        List<String> logs = List.of("log1", "log2");

        when(result.getLogs()).thenReturn(logs);

        Optional<List<String>> resultLogs = executor.extractLogs(result);

        assertThat(resultLogs).contains(logs);
    }
}
