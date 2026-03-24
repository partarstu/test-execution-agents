/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ApiTestAgent;
import org.tarik.ta.core.dto.TestExecutionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiAgentExecutorTest {

    @Test
    void executeTestCase_shouldDelegateToApiTestAgent() {
        ApiTestAgent apiTestAgent = mock(ApiTestAgent.class);
        ApiAgentExecutor executor = new ApiAgentExecutor(apiTestAgent);
        String message = "run test";
        TestExecutionResult expectedResult = mock(TestExecutionResult.class);

        when(apiTestAgent.executeTestCase(message)).thenReturn(expectedResult);

        TestExecutionResult result = executor.executeTestCase(message);

        assertThat(result).isSameAs(expectedResult);
        verify(apiTestAgent).executeTestCase(message);
    }

    @Test
    void testAddSpecificArtifacts_shouldDoNothing() {
        ApiAgentExecutor executor = new ApiAgentExecutor(mock(ApiTestAgent.class));
        TestExecutionResult result = mock(TestExecutionResult.class);
        List<Part<?>> parts = new ArrayList<>();
        
        executor.addSpecificArtifacts(result, parts);
        
        assertThat(parts).isEmpty();
    }

    @Test
    void testExtractLogs() {
        ApiAgentExecutor executor = new ApiAgentExecutor(mock(ApiTestAgent.class));
        TestExecutionResult result = mock(TestExecutionResult.class);
        List<String> logs = List.of("log1", "log2");
        when(result.getLogs()).thenReturn(logs);
        
        Optional<List<String>> resultLogs = executor.extractLogs(result);
        
        assertThat(resultLogs).contains(logs);
    }
}
