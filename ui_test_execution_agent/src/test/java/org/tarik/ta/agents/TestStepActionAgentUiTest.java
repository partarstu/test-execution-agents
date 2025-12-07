package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.tools.AgentExecutionResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.tarik.ta.utils.CommonUtils;

import java.awt.image.BufferedImage;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.tools.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.tools.AgentExecutionResult.ExecutionStatus.SUCCESS;

class TestStepActionAgentUiTest {

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
        UiTestStepActionAgent agent = mock(UiTestStepActionAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Supplier.class));

        AgentExecutionResult<EmptyExecutionResult> result = agent.executeAndGetResult(() -> Result.<EmptyExecutionResult>builder().content(new EmptyExecutionResult()).build());

        assertThat(result.executionStatus()).isEqualTo(SUCCESS);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Execution successful");
    }

    @Test
    void shouldHandleFailedExecution() {
        UiTestStepActionAgent agent = mock(UiTestStepActionAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Supplier.class));

        AgentExecutionResult<EmptyExecutionResult> result = agent.executeAndGetResult(() -> {
            throw new RuntimeException("Action execution error");
        });

        assertThat(result.executionStatus()).isEqualTo(ERROR);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Action execution error");
        assertThat(result.screenshot()).isNotNull();
    }
}
