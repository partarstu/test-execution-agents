package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.agents.PreconditionActionAgent;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.AgentExecutionResult;

import org.mockito.MockedStatic;
import org.tarik.ta.utils.CommonUtils;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.SUCCESS;
import static org.mockito.ArgumentMatchers.anyBoolean;

class UiPreconditionActionAgentTest {

    @Test
    void shouldHandleSuccessfulExecution() {
        try (MockedStatic<CommonUtils> commonUtilsMockedStatic = mockStatic(CommonUtils.class, CALLS_REAL_METHODS)) {
            commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
            commonUtilsMockedStatic.when(() -> CommonUtils.captureScreen(anyBoolean())).thenReturn(mock(BufferedImage.class));

            PreconditionActionAgent agent = (_, _) -> null;

            AgentExecutionResult<EmptyExecutionResult> result = agent.executeAndGetResult(() -> Result.builder().content(new EmptyExecutionResult()).build());

            assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Execution successful");
            assertThat(result.getResultPayload()).isNotNull();
        }
    }

    @Test
    void shouldHandleFailedExecution() {
        try (MockedStatic<CommonUtils> commonUtilsMockedStatic = mockStatic(CommonUtils.class, CALLS_REAL_METHODS)) {
            BufferedImage mockImage = mock(BufferedImage.class);
            commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mockImage);
            commonUtilsMockedStatic.when(() -> CommonUtils.captureScreen(anyBoolean())).thenReturn(mockImage);

            UiPreconditionActionAgent agent = (_, _) -> null;

            var result = agent.executeAndGetResult(() -> {
                throw new RuntimeException("Simulated error");
            });

            assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Simulated error");
            assertThat(result.screenshot()).isNotNull();
        }
    }
}
