/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import org.tarik.ta.dto.UiOperationExecutionResult;

class UiTestStepVerificationUiTestAgentTest {

    private MockedStatic<UiCommonUtils> commonUtilsMockedStatic;
    @Mock
    private UiTestAgentConfig configMock;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        commonUtilsMockedStatic = mockStatic(UiCommonUtils.class, CALLS_REAL_METHODS);
        commonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));

        
        lenient().when(configMock.isFullyUnattended()).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        commonUtilsMockedStatic.close();
        closeable.close();
    }

    @Test
    void shouldHandleSuccessfulVerification() {
        UiTestStepVerificationAgent agent = mock(UiTestStepVerificationAgent.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS));

        VerificationExecutionResult verificationResult = new VerificationExecutionResult(true, "Verified");

        OperationExecutionResult<VerificationExecutionResult> result = agent.executeAndGetResult(
                () -> Result.<VerificationExecutionResult>builder().content(verificationResult).build());

        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResultPayload()).isEqualTo(verificationResult);
    }

    @Test
    void shouldHandleFailedVerificationExecution() {
        UiTestStepVerificationAgent agent = mock(UiTestStepVerificationAgent.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS));

        var result = agent.executeAndGetResult(() -> {
            throw new RuntimeException("Verification error");
        });

        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Verification error");
        assertThat(((UiOperationExecutionResult<?>) result).screenshot()).isNotNull();
    }
}
