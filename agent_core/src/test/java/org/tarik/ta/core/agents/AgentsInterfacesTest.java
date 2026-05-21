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
package org.tarik.ta.core.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.error.RetryPolicy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentsInterfacesTest {

    @Test
    void testCaseExtractionAgentDefaults() {
        TestCaseExtractionAgent agent = new TestCaseExtractionAgent() {
            @Override
            public Result<String> extractTestCase(String userRequest) {
                return null;
            }
        };
        assertThat(agent.getAgentTaskDescription()).isEqualTo("Extracting test case from user request");
    }

    @Test
    void genericAiAgentDefaultMethods() {
        GenericAiAgent<TestFinalResult> agent = new GenericAiAgent<>() {
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
