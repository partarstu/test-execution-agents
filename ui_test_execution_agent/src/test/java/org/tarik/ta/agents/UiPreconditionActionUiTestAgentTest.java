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
package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;

class UiPreconditionActionUiTestAgentTest {

    private MockedStatic<UiCommonUtils> commonUtilsMockedStatic;
    private MockedStatic<UiTestAgentConfig> configMockedStatic;

    @BeforeEach
    void setUp() {
        commonUtilsMockedStatic = mockStatic(UiCommonUtils.class, CALLS_REAL_METHODS);
        commonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
        commonUtilsMockedStatic.when(() -> UiCommonUtils.captureScreen(anyBoolean())).thenReturn(mock(BufferedImage.class));

        configMockedStatic = mockStatic(UiTestAgentConfig.class);
        configMockedStatic.when(UiTestAgentConfig::isFullyUnattended).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        commonUtilsMockedStatic.close();
        configMockedStatic.close();
    }

    @Test
    void shouldHandleSuccessfulExecution() {
        UiPreconditionActionAgent agent = (_, _, _) -> null;

        OperationExecutionResult<EmptyExecutionResult>
                result = agent.executeAndGetResult(() -> Result.builder().content(new EmptyExecutionResult()).build());

        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Execution successful");
        assertThat(result.getResultPayload()).isNotNull();
    }

    @Test
    void shouldHandleFailedExecution() {
        UiPreconditionActionAgent agent = (_, _, _) -> null;

        var result = agent.executeAndGetResult(() -> {
            throw new RuntimeException("Simulated error");
        });

        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Simulated error");
        assertThat(((org.tarik.ta.dto.UiOperationExecutionResult<?>) result).screenshot()).isNotNull();
    }
}
