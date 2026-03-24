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
package org.tarik.ta;

import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.utils.TestCaseExtractor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiTestAgentTest {

    @Test
    void executeTestCase_shouldReturnError_whenTestCaseCannotBeExtracted() {
        ModelFactory modelFactory = mock(ModelFactory.class);
        ApiTestAgentConfig config = mock(ApiTestAgentConfig.class);
        TestCaseExtractor testCaseExtractor = mock(TestCaseExtractor.class);
        BudgetManager budgetManager = mock(BudgetManager.class);
        BeanScope appScope = mock(BeanScope.class);
        when(testCaseExtractor.extractTestCase("run test")).thenReturn(Optional.empty());
        ApiTestAgent agent = new ApiTestAgent(modelFactory, config, testCaseExtractor, budgetManager, appScope);

        TestExecutionResult result = agent.executeTestCase("run test");

        assertThat(result.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.ERROR);
        assertThat(result.getGeneralErrorMessage()).isEqualTo("Could not extract test case");
        verify(budgetManager).reset();
        verify(budgetManager).activateTimeBudget();
    }
}
