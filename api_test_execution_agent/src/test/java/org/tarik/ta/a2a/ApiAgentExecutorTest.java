/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ApiAgentRequestScopeFactory;
import org.tarik.ta.ApiTestAgent;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestExecutionResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        when(requestScopeFactory.create(any(StreamingEventEmitter.class))).thenReturn(requestScope);
        when(requestScope.get(ApiTestAgent.class)).thenReturn(apiTestAgent);
        when(apiTestAgent.executeTestCase(message)).thenReturn(expectedResult);

        TestExecutionResult result = executor.executeTestCase(message, StreamingEventEmitter.NOOP);

        assertThat(result).isSameAs(expectedResult);
        verify(requestScopeFactory).create(any(StreamingEventEmitter.class));
        verify(requestScope).get(ApiTestAgent.class);
        verify(apiTestAgent).executeTestCase(message);
        verify(requestScope).close();
    }
}
