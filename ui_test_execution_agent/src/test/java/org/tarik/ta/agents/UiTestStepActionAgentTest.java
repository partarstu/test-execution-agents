package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.AgentExecutionResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.tarik.ta.utils.CommonUtils;

import java.awt.image.BufferedImage;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.SUCCESS;

class UiTestStepActionAgentTest {

    private MockedStatic<CommonUtils> commonUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        commonUtilsMockedStatic = mockStatic(CommonUtils.class, CALLS_REAL_METHODS);
        commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
    }

    @AfterEach
    void tearDown() {
        commonUtilsMockedStatic.close();
    }



    @Test
    void shouldHandleSuccessfulExecution() {
        UiTestStepActionAgent agent = mock(UiTestStepActionAgent.class, CALLS_REAL_METHODS);

        AgentExecutionResult<EmptyExecutionResult> result = agent.executeAndGetResult(() -> Result.<EmptyExecutionResult>builder().content(new EmptyExecutionResult()).build());

        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Execution successful");
    }

    @Test
    void shouldHandleFailedExecution() {
        UiTestStepActionAgent agent = mock(UiTestStepActionAgent.class, CALLS_REAL_METHODS);

        var result = agent.executeAndGetResult(() -> {
            throw new RuntimeException("Action execution error");
        });

        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Action execution error");
        assertThat(result.screenshot()).isNotNull();
    }
}
