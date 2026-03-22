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
package org.tarik.ta.core.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentsInterfacesTest {

    @Test
    void testStepVerificationAgentDefaults() {
        TestStepVerificationAgent agent = new TestStepVerificationAgent() {
        };
        assertThat(agent.getAgentTaskDescription()).isEqualTo("Verifying test step actual results");
        assertThat(agent.getRetryPolicy()).isEqualTo(new AgentConfig().getVerificationRetryPolicy());
    }

    @Test
    void testStepActionAgentDefaults() {
        TestStepActionAgent agent = new TestStepActionAgent() {
        };
        assertThat(agent.getAgentTaskDescription()).isEqualTo("Executing test step action");
        assertThat(agent.getRetryPolicy()).isEqualTo(new AgentConfig().getActionRetryPolicy());
    }

    @Test
    void preconditionVerificationAgentDefaults() {
        PreconditionVerificationAgent agent = new PreconditionVerificationAgent() {
        };
        assertThat(agent.getAgentTaskDescription()).isEqualTo("Verifying precondition");
        assertThat(agent.getRetryPolicy()).isEqualTo(new AgentConfig().getVerificationRetryPolicy());
    }

    @Test
    void preconditionActionAgentDefaults() {
        PreconditionActionAgent agent = new PreconditionActionAgent() {
            @Override
            public Result<String> execute(String precondition, String sharedData) {
                return null;
            }
        };
        assertThat(agent.getAgentTaskDescription()).isEqualTo("Executing precondition action");
        assertThat(agent.getRetryPolicy()).isEqualTo(new AgentConfig().getActionRetryPolicy());
    }

    @Test
    void testCaseExtractionAgentDefaults() {
        TestCaseExtractionAgent agent = new TestCaseExtractionAgent() {
            @Override
            public Result<String> extractTestCase(String userRequest) {
                return null;
            }
        };
        assertThat(agent.getAgentTaskDescription()).isEqualTo("Extracting test case from user request");
        assertThat(agent.getRetryPolicy()).isEqualTo(new AgentConfig().getActionRetryPolicy());
    }

    @Test
    void genericAiAgentDefaultMethods() {
        GenericAiAgent<TestFinalResult> agent = new GenericAiAgent<>() {
            @Override
            public RetryPolicy getRetryPolicy() {
                return new RetryPolicy(1, 1, 1);
            }

            @Override
            public String getAgentTaskDescription() {
                return "test task";
            }
        };

        TestFinalResult payload = new TestFinalResult("test");
        var successResult = agent.createSuccessResult(payload);
        assertThat(successResult.isSuccess()).isTrue();
        assertThat(successResult.getResultPayload()).isEqualTo(payload);

        var errorResult = agent.createErrorResult(org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR, "error", payload);
        assertThat(errorResult.isSuccess()).isFalse();
        assertThat(errorResult.getMessage()).isEqualTo("error");
        assertThat(errorResult.getResultPayload()).isEqualTo(payload);

        Result<Object> resultWrapper = mock(Result.class);
        when(resultWrapper.content()).thenReturn(payload);
        assertThat(agent.extractResult(resultWrapper)).isEqualTo(payload);

        when(resultWrapper.content()).thenReturn(null);
        dev.langchain4j.service.tool.ToolExecution toolExecution = mock(dev.langchain4j.service.tool.ToolExecution.class);
        when(toolExecution.resultObject()).thenReturn(payload);
        when(resultWrapper.toolExecutions()).thenReturn(List.of(toolExecution));
        assertThat(agent.extractResult(resultWrapper)).isEqualTo(payload);
    }

    private record TestFinalResult(String value) implements FinalResult {
    }
}
