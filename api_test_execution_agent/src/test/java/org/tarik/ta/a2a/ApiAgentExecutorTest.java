/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ApiTestAgent;
import org.tarik.ta.core.dto.TestExecutionResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ApiAgentExecutorTest {

    @Test
    void executeTestCase_shouldDelegateToApiTestAgent() {
        ApiAgentExecutor executor = new ApiAgentExecutor();
        String message = "run test";
        TestExecutionResult expectedResult = mock(TestExecutionResult.class);

        try (MockedStatic<ApiTestAgent> apiTestAgent = mockStatic(ApiTestAgent.class)) {
            apiTestAgent.when(() -> ApiTestAgent.executeTestCase(message)).thenReturn(expectedResult);

            TestExecutionResult result = executor.executeTestCase(message);

            assertThat(result).isSameAs(expectedResult);
            apiTestAgent.verify(() -> ApiTestAgent.executeTestCase(message));
        }
    }
}
