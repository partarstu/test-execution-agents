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
package org.tarik.ta.tools;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.BaseUiAgent;
import org.tarik.ta.agents.UiPreconditionVerificationAgent;
import org.tarik.ta.agents.UiTestStepVerificationAgent;
import org.tarik.ta.core.AgentConfig.ModelProvider;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.model.ImageContentCreator;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.model.VisualStateHolder;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationToolsTest {

    @Mock
    private BudgetManager budgetManager;
    @Mock
    private UiTestAgentConfig config;
    @Mock
    private UiTestStepVerificationAgent stepVerificationAgent;
    @Mock
    private UiPreconditionVerificationAgent preconditionVerificationAgent;

    private final BufferedImage beforeActionScreenshot = screenshotOfColor(0xFFFFFF);
    private TestExecutionContext context;
    private VisualStateHolder visualState;
    private VerificationTools verificationTools;
    private MockedStatic<UiCommonUtils> uiCommonUtils;

    @BeforeEach
    void setUp() {
        var screenshot = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        uiCommonUtils = mockStatic(UiCommonUtils.class);
        uiCommonUtils.when(UiCommonUtils::captureScreen).thenReturn(screenshot);
        context = new TestExecutionContext(StreamingEventEmitter.NOOP);
        visualState = new VisualStateHolder(new VisualState(screenshot));
        verificationTools = new VerificationTools(budgetManager, config);
        lenient().when(config.getVerificationRetryPolicy()).thenReturn(new RetryPolicy(1, 0, 0));
    }

    @AfterEach
    void tearDown() {
        uiCommonUtils.close();
    }

    @Test
    void verifyTestStep_shouldRenderTheDataTheHierarchyAndTheContextDataIntoTheUserMessage() {
        var imageContentCreator = new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000);
        lenient().when(stepVerificationAgent.getImageContentCreator(any())).thenReturn(imageContentCreator);
        runSupplierAndReturn(stepVerificationAgent, verificationResult());
        context.addSharedData("orderId", "4711");

        var result = verificationTools.verifyTestStep("the dashboard is shown", "Log in", "user=admin", context, visualState,
                beforeActionScreenshot, stepVerificationAgent, "## Procedure hierarchy\nInnermost first.");

        assertThat(result.success()).isTrue();
        assertThat(capturedTestStepMessage()).isEqualTo("""
                Verify that the expected results from the "Expected results" section are met.

                ## Expected results
                the dashboard is shown

                ## Action executed before this verification
                Log in

                ## Data relevant to this action
                user=admin

                ## Procedure hierarchy
                Innermost first.

                ## Test execution context data
                [{"entries":[{"name":"orderId","value":"4711","source":"stored during execution"}]}]""");
    }

    @Test
    void verifyTestStep_shouldOmitTheDataAndContextSections_whenTheyAreBlank() {
        var imageContentCreator = new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000);
        lenient().when(stepVerificationAgent.getImageContentCreator(any())).thenReturn(imageContentCreator);
        runSupplierAndReturn(stepVerificationAgent, verificationResult());

        verificationTools.verifyTestStep("the dashboard is shown", "Log in", "", context, visualState, beforeActionScreenshot,
                stepVerificationAgent, "");

        assertThat(capturedTestStepMessage()).isEqualTo("""
                Verify that the expected results from the "Expected results" section are met.

                ## Expected results
                the dashboard is shown

                ## Action executed before this verification
                Log in""");
    }

    @Test
    void verifyTestStep_shouldKeepAllLinesOfMultiLineExpectedResultsInTheirSectionAndEscapeTheirHeadings() {
        var imageContentCreator = new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000);
        lenient().when(stepVerificationAgent.getImageContentCreator(any())).thenReturn(imageContentCreator);
        runSupplierAndReturn(stepVerificationAgent, verificationResult());

        verificationTools.verifyTestStep("The dashboard is shown\n## The 'Sales' tab is selected", "Log in", "", context,
                visualState, beforeActionScreenshot, stepVerificationAgent, "");

        assertThat(capturedTestStepMessage()).contains("""
                ## Expected results
                The dashboard is shown
                \\## The 'Sales' tab is selected

                ## Action executed before this verification""");
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyTestStep_shouldSendTheSameBeforeScreenshotAndAFreshAfterScreenshot_onEveryAttempt() {
        var imageContentCreator = new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000);
        when(stepVerificationAgent.getImageContentCreator(any())).thenReturn(imageContentCreator);
        when(config.getVerificationRetryPolicy()).thenReturn(new RetryPolicy(1, 0, 10_000));
        var firstAfterScreenshot = screenshotOfColor(0xFF0000);
        var secondAfterScreenshot = screenshotOfColor(0x00FF00);
        uiCommonUtils.when(UiCommonUtils::captureScreen).thenReturn(firstAfterScreenshot, secondAfterScreenshot);
        when(stepVerificationAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<?>>getArgument(0).get();
            return new UiOperationExecutionResult<>(ExecutionStatus.SUCCESS, "ok",
                    new VerificationExecutionResult(false, "state differs"), null);
        }).thenAnswer(invocation -> {
            invocation.<Supplier<?>>getArgument(0).get();
            return verificationResult();
        });

        var result = verificationTools.verifyTestStep("the map is zoomed in", "Zoom in", "", context, visualState,
                beforeActionScreenshot, stepVerificationAgent, "");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<List<Content>> screenshots = ArgumentCaptor.forClass(List.class);
        verify(stepVerificationAgent, times(2)).verify(any(), screenshots.capture());
        var beforeActionImage = imageContentCreator.createImageContent(beforeActionScreenshot);
        assertThat(screenshots.getAllValues()).containsExactly(
                List.of(TextContent.from("## Screenshot before the action"), beforeActionImage,
                        TextContent.from("## Screenshot after the action"), imageContentCreator.createImageContent(firstAfterScreenshot)),
                List.of(TextContent.from("## Screenshot before the action"), beforeActionImage,
                        TextContent.from("## Screenshot after the action"), imageContentCreator.createImageContent(secondAfterScreenshot)));
    }

    @Test
    void verifyPrecondition_shouldRenderTheStepHierarchyIntoTheUserMessage() {
        var imageContentCreator = new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000);
        lenient().when(preconditionVerificationAgent.getImageContentCreator(any())).thenReturn(imageContentCreator);
        runSupplierAndReturn(preconditionVerificationAgent, verificationResult());

        var result = verificationTools.verifyPrecondition("the user is logged in", context, visualState, preconditionVerificationAgent,
                "user=admin", "## Procedure hierarchy\nInnermost first.");

        assertThat(result.success()).isTrue();
        assertThat(capturedPreconditionMessage()).isEqualTo("""
                Verify that the precondition from the "Precondition" section is met.

                ## Precondition
                the user is logged in

                ## Data relevant to this precondition
                user=admin

                ## Procedure hierarchy
                Innermost first.

                ## Screenshot""");
    }

    @Test
    void verifyPrecondition_shouldRenderTheContextData_whenTheContextHoldsAny() {
        var imageContentCreator = new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000);
        lenient().when(preconditionVerificationAgent.getImageContentCreator(any())).thenReturn(imageContentCreator);
        runSupplierAndReturn(preconditionVerificationAgent, verificationResult());
        context.addSharedData("orderId", "4711");

        verificationTools.verifyPrecondition("the user is logged in", context, visualState, preconditionVerificationAgent, "", "");

        assertThat(capturedPreconditionMessage())
                .endsWith("""
                        ## Test execution context data
                        [{"entries":[{"name":"orderId","value":"4711","source":"stored during execution"}]}]

                        ## Screenshot""");
    }

    @Test
    void verifyPrecondition_shouldOmitEverySection_whenTheDataTheHierarchyAndTheContextAreBlank() {
        var imageContentCreator = new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000);
        lenient().when(preconditionVerificationAgent.getImageContentCreator(any())).thenReturn(imageContentCreator);
        runSupplierAndReturn(preconditionVerificationAgent, verificationResult());

        verificationTools.verifyPrecondition("the user is logged in", context, visualState, preconditionVerificationAgent, "", "");

        assertThat(capturedPreconditionMessage()).isEqualTo("""
                Verify that the precondition from the "Precondition" section is met.

                ## Precondition
                the user is logged in

                ## Screenshot""");
    }

    @SuppressWarnings("unchecked")
    private String capturedTestStepMessage() {
        ArgumentCaptor<List<Content>> message = ArgumentCaptor.forClass(List.class);
        verify(stepVerificationAgent).verify(message.capture(), any());
        return singleText(message.getValue());
    }

    @SuppressWarnings("unchecked")
    private String capturedPreconditionMessage() {
        ArgumentCaptor<List<Content>> message = ArgumentCaptor.forClass(List.class);
        verify(preconditionVerificationAgent).verify(message.capture(), any());
        return singleText(message.getValue());
    }

    private static String singleText(List<Content> userMessage) {
        assertThat(userMessage).singleElement().isInstanceOf(TextContent.class);
        return ((TextContent) userMessage.getFirst()).text();
    }

    /**
     * Makes the mocked agent run the operation supplier, so the user message {@link VerificationTools} assembles actually
     * reaches the agent and can be captured.
     */
    private static void runSupplierAndReturn(BaseUiAgent<VerificationExecutionResult> agent,
                                             UiOperationExecutionResult<VerificationExecutionResult> result) {
        when(agent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<?>>getArgument(0).get();
            return result;
        });
    }

    private static BufferedImage screenshotOfColor(int rgb) {
        var screenshot = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        screenshot.setRGB(0, 0, rgb);
        return screenshot;
    }

    private static UiOperationExecutionResult<VerificationExecutionResult> verificationResult() {
        return new UiOperationExecutionResult<>(ExecutionStatus.SUCCESS, "ok",
                new VerificationExecutionResult(true, "state matches"), null);
    }
}
