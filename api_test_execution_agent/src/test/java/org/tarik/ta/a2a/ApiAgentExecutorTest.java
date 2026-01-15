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
