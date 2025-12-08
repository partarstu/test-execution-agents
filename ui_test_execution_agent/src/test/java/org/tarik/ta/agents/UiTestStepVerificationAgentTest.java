package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.dto.AgentExecutionResult;
import org.tarik.ta.utils.CommonUtils;

import java.awt.image.BufferedImage;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.SUCCESS;

class UiTestStepVerificationAgentTest {

    private MockedStatic<CommonUtils> commonUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        commonUtilsMockedStatic = mockStatic(CommonUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mock(BufferedImage.class));
    }

    @AfterEach
    void tearDown() {
        commonUtilsMockedStatic.close();
    }

    @Test
    void shouldHandleSuccessfulVerification() {
        UiTestStepVerificationAgent agent = mock(UiTestStepVerificationAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Supplier.class));

        VerificationExecutionResult verificationResult = new VerificationExecutionResult(true, "Verified");

        AgentExecutionResult<VerificationExecutionResult> result = agent.executeAndGetResult(() -> Result.<VerificationExecutionResult>builder().content(verificationResult).build());

        assertThat(result.getExecutionStatus()).isEqualTo(SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResultPayload()).isEqualTo(verificationResult);
    }

    @Test
    void shouldHandleFailedVerificationExecution() {
        UiTestStepVerificationAgent agent = mock(UiTestStepVerificationAgent.class);
        doCallRealMethod().when(agent).executeAndGetResult(any(Supplier.class));

        var result = agent.executeAndGetResult(() -> {
            throw new RuntimeException("Verification error");
        });

        assertThat(result.getExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Verification error");
        assertThat(result.screenshot()).isNotNull();
    }
}
