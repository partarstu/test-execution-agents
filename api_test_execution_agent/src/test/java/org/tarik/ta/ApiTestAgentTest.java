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
package org.tarik.ta;

import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    }
}
