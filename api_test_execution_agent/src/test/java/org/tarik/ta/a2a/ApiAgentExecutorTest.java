/*
 * api-test-execution-agent - ${project.description}
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

import io.avaje.inject.BeanScope;
import io.a2a.spec.Part;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ApiAgentRequestScopeFactory;
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
        ApiAgentRequestScopeFactory requestScopeFactory = mock(ApiAgentRequestScopeFactory.class);
        BeanScope requestScope = mock(BeanScope.class);
        ApiTestAgent apiTestAgent = mock(ApiTestAgent.class);
        ApiAgentExecutor executor = new ApiAgentExecutor(requestScopeFactory);
        String message = "run test";
        TestExecutionResult expectedResult = mock(TestExecutionResult.class);

        when(requestScopeFactory.create()).thenReturn(requestScope);
        when(requestScope.get(ApiTestAgent.class)).thenReturn(apiTestAgent);
        when(apiTestAgent.executeTestCase(message)).thenReturn(expectedResult);

        TestExecutionResult result = executor.executeTestCase(message);

        assertThat(result).isSameAs(expectedResult);
        verify(requestScopeFactory).create();
        verify(requestScope).get(ApiTestAgent.class);
        verify(apiTestAgent).executeTestCase(message);
        verify(requestScope).close();
    }

    @Test
    void testAddSpecificArtifacts_shouldDoNothing() {
        ApiAgentExecutor executor = new ApiAgentExecutor(mock(ApiAgentRequestScopeFactory.class));
        TestExecutionResult result = mock(TestExecutionResult.class);
        List<Part<?>> parts = new ArrayList<>();
        
        executor.addSpecificArtifacts(result, parts);
        
        assertThat(parts).isEmpty();
    }

    @Test
    void testExtractLogs() {
        ApiAgentExecutor executor = new ApiAgentExecutor(mock(ApiAgentRequestScopeFactory.class));
        TestExecutionResult result = mock(TestExecutionResult.class);
        List<String> logs = List.of("log1", "log2");
        when(result.getLogs()).thenReturn(logs);
        
        Optional<List<String>> resultLogs = executor.extractLogs(result);
        
        assertThat(resultLogs).contains(logs);
    }
}
