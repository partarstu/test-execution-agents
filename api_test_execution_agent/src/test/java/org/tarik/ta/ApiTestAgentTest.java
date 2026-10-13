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
package org.tarik.ta;

import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.core.dto.DataEntry;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.model.ContextEntry.StepType.PRECONDITION;
import static org.tarik.ta.core.model.ContextEntry.StepType.TEST_STEP;

class ApiTestAgentTest {

    @Test
    void executeTestCase_shouldReturnError_whenTestCaseCannotBeExtracted() {
        ApiTestAgentConfig config = mock(ApiTestAgentConfig.class);
        TestCaseExtractor testCaseExtractor = mock(TestCaseExtractor.class);
        BudgetManager budgetManager = mock(BudgetManager.class);
        TestExecutionContext testExecutionContext = mock(TestExecutionContext.class);
        LogCapture logCapture = mock(LogCapture.class);
        @SuppressWarnings("unchecked")
        Provider<ApiPreconditionActionAgent> preconditionActionAgentProvider = mock(Provider.class);
        @SuppressWarnings("unchecked")
        Provider<ApiTestStepActionAgent> testStepActionAgentProvider = mock(Provider.class);
        when(testCaseExtractor.extractTestCase("run test")).thenReturn(Optional.empty());
        ApiTestAgent agent = new ApiTestAgent(config, testCaseExtractor, budgetManager, testExecutionContext, logCapture,
                preconditionActionAgentProvider, testStepActionAgentProvider);

        TestExecutionResult result = agent.executeTestCase("run test");

        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.ERROR);
        assertThat(result.getGeneralErrorMessage()).isEqualTo("Could not extract test case");
        verify(budgetManager).reset();
        verify(budgetManager).activateTimeBudget();
        verify(budgetManager).activateToolCallBudget();
    }

    @Test
    void executeTestCase_shouldReturnErrorResultWithCapturedLogs_whenExecutionThrows() {
        ApiTestAgentConfig config = mock(ApiTestAgentConfig.class);
        TestCaseExtractor testCaseExtractor = mock(TestCaseExtractor.class);
        BudgetManager budgetManager = mock(BudgetManager.class);
        TestExecutionContext testExecutionContext = mock(TestExecutionContext.class);
        LogCapture logCapture = mock(LogCapture.class);
        @SuppressWarnings("unchecked")
        Provider<ApiPreconditionActionAgent> preconditionActionAgentProvider = mock(Provider.class);
        @SuppressWarnings("unchecked")
        Provider<ApiTestStepActionAgent> testStepActionAgentProvider = mock(Provider.class);
        var testCase = new TestCase("Broken run", List.of(new Precondition("the service is reachable", List.of())), List.of(),
                List.of(new TestStep("call it", null, null)));
        when(testCaseExtractor.extractTestCase("run test")).thenReturn(Optional.of(testCase));
        when(preconditionActionAgentProvider.get()).thenThrow(new IllegalStateException("no agent available"));
        when(logCapture.getLogs()).thenReturn(List.of("Log before the failure"));
        ApiTestAgent agent = new ApiTestAgent(config, testCaseExtractor, budgetManager, testExecutionContext, logCapture,
                preconditionActionAgentProvider, testStepActionAgentProvider);

        TestExecutionResult result = agent.executeTestCase("run test");

        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.ERROR);
        assertThat(result.getGeneralErrorMessage()).contains("Unexpected error during test case execution");
        assertThat(result.getLogs()).contains("Log before the failure");
        verify(logCapture).stop();
    }

    @Test
    void executeTestCase_shouldSeedTheDataTableAndStartThePreconditionStepInTheContext() {
        ApiTestAgentConfig config = mock(ApiTestAgentConfig.class);
        TestCaseExtractor testCaseExtractor = mock(TestCaseExtractor.class);
        BudgetManager budgetManager = mock(BudgetManager.class);
        TestExecutionContext testExecutionContext = mock(TestExecutionContext.class);
        LogCapture logCapture = mock(LogCapture.class);
        @SuppressWarnings("unchecked")
        Provider<ApiPreconditionActionAgent> preconditionActionAgentProvider = mock(Provider.class);
        @SuppressWarnings("unchecked")
        Provider<ApiTestStepActionAgent> testStepActionAgentProvider = mock(Provider.class);
        var dataTable = List.of(new DataEntry("user", "admin"));
        var testCase = new TestCase("Seeded run", List.of(new Precondition("the service is reachable", List.of("token=abc"))),
                dataTable, List.of(new TestStep("call it", null, null)));
        when(testCaseExtractor.extractTestCase("run test")).thenReturn(Optional.of(testCase));
        when(preconditionActionAgentProvider.get()).thenReturn(mock(ApiPreconditionActionAgent.class));
        ApiTestAgent agent = new ApiTestAgent(config, testCaseExtractor, budgetManager, testExecutionContext, logCapture,
                preconditionActionAgentProvider, testStepActionAgentProvider);

        agent.executeTestCase("run test");

        var inOrder = inOrder(testExecutionContext);
        inOrder.verify(testExecutionContext).seedFromDataTable(dataTable);
        inOrder.verify(testExecutionContext).startStep(PRECONDITION, "the service is reachable", List.of("token=abc"));
    }

    @Test
    void executeTestCase_shouldStartTheTestStepInTheContext() {
        ApiTestAgentConfig config = mock(ApiTestAgentConfig.class);
        TestCaseExtractor testCaseExtractor = mock(TestCaseExtractor.class);
        TestExecutionContext testExecutionContext = mock(TestExecutionContext.class);
        @SuppressWarnings("unchecked")
        Provider<ApiPreconditionActionAgent> preconditionActionAgentProvider = mock(Provider.class);
        @SuppressWarnings("unchecked")
        Provider<ApiTestStepActionAgent> testStepActionAgentProvider = mock(Provider.class);
        var testCase = new TestCase("Step run", List.of(), List.of(), List.of(new TestStep("call it", List.of("page=1"), null)));
        when(testCaseExtractor.extractTestCase("run test")).thenReturn(Optional.of(testCase));
        when(testStepActionAgentProvider.get()).thenReturn(mock(ApiTestStepActionAgent.class));
        ApiTestAgent agent = new ApiTestAgent(config, testCaseExtractor, mock(BudgetManager.class), testExecutionContext,
                mock(LogCapture.class), preconditionActionAgentProvider, testStepActionAgentProvider);

        agent.executeTestCase("run test");

        verify(testExecutionContext).startStep(TEST_STEP, "call it", List.of("page=1"));
    }

    @Test
    void buildPreconditionUserMessage_shouldRenderThePreconditionItsDataAndTheContextAsSections() {
        var message = ApiTestAgent.buildPreconditionUserMessage("An order exists", List.of("user=admin", "tenant=staging"),
                "[ {\"name\" : \"orderId\"} ]");

        assertThat(message).isEqualTo("""
                ## Precondition
                An order exists

                ## Data relevant to this precondition
                - user=admin
                - tenant=staging

                ## Test execution context data
                [ {"name" : "orderId"} ]""");
    }

    @Test
    void buildTestStepUserMessage_shouldLeaveOutTheBlankSectionsAndKeepPlaceholdersVerbatim() {
        var message = ApiTestAgent.buildTestStepUserMessage("GET /orders/{{orderId}}", "", List.of(), "");

        assertThat(message).isEqualTo("## Test step action\nGET /orders/{{orderId}}");
    }

    @Test
    void buildTestStepUserMessage_shouldRenderEveryPartAsItsOwnSection() {
        var message = ApiTestAgent.buildTestStepUserMessage("GET /orders", "Status 200\n## injected", List.of("page=1"),
                "[ {\"name\" : \"token\"} ]");

        assertThat(message).isEqualTo("""
                ## Test step action
                GET /orders

                ## Expected results to verify
                Status 200
                \\## injected

                ## Data related to the test step
                - page=1

                ## Test execution context data
                [ {"name" : "token"} ]""");
    }
}
