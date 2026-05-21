/*
 * ui-test-execution-agent - ${project.description}
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
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import org.tarik.ta.dto.UiOperationExecutionResult;

@SuppressWarnings("unchecked")
class UiPreconditionVerificationUiTestAgentTest {

    private MockedStatic<UiCommonUtils> commonUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        commonUtilsMockedStatic = mockStatic(UiCommonUtils.class, CALLS_REAL_METHODS);
        commonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
    }

    @AfterEach
    void tearDown() {
        commonUtilsMockedStatic.close();
    }

    @Test
    void shouldHandleSuccessfulVerification() {
        var agent = mock(UiPreconditionVerificationAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Supplier.class));
        doCallRealMethod().when(agent).createSuccessResult(any());
        doCallRealMethod().when(agent).extractResult(any());
        var verificationResult = new VerificationExecutionResult(true, "Verified");

        var result = agent.executeAndGetResult(
                () -> Result.<VerificationExecutionResult>builder().content(verificationResult).build());

        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResultPayload()).isEqualTo(verificationResult);
    }

    @Test
    void shouldHandleFailedVerificationExecution() {
        UiPreconditionVerificationAgent agent = mock(UiPreconditionVerificationAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Supplier.class));
        doCallRealMethod().when(agent).createErrorResult(any(), any(), any());
        doCallRealMethod().when(agent).captureErrorScreenshot();

        var result = agent.executeAndGetResult(() -> {
            throw new RuntimeException("Verification error");
        });

        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Verification error");
        assertThat(((UiOperationExecutionResult<?>) result).screenshot()).isNotNull();
    }
}
