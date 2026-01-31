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
package org.tarik.ta;

import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.agents.PreconditionActionAgent;
import org.tarik.ta.core.dto.*;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.core.model.DefaultToolErrorHandler;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.dto.UiPreconditionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.core.dto.SystemInfo;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.error.RetryState;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.tools.*;
import org.tarik.ta.user_dialogs.TestStepSelectionPopup;
import org.tarik.ta.utils.ScreenRecorder;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.service.AiServices.builder;
import static java.lang.String.join;
import static java.time.Instant.now;
import static java.util.Optional.*;
import static org.tarik.ta.UiTestAgentConfig.*;
import static org.tarik.ta.core.agents.PreconditionVerificationAgent.RETRY_POLICY;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.*;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import static org.tarik.ta.core.error.ErrorCategory.*;
import static org.tarik.ta.core.manager.BudgetManager.resetToolCallUsage;
import static org.tarik.ta.core.model.ModelFactory.getModel;
import static org.tarik.ta.core.utils.CommonUtils.*;
import static org.tarik.ta.rag.RetrieverFactory.getUiElementRetriever;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.utils.ImageUtils.singleImageContent;

public class UiTestAgent {
    private static final Logger LOG = LoggerFactory.getLogger(UiTestAgent.class);
    protected static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();
    public static final String MODE_SPECIFIC_RULES_PLACEHOLDER = "mode_specific_rules";

    private UiTestAgent() {
    }

    public static TestExecutionResult executeTestCase(String receivedMessage) {
        var testExecutionStartTimestamp = now();
        ScreenRecorder screenRecorder = new ScreenRecorder();
        LogCapture logCapture = new LogCapture();
        SystemInfo systemInfo = null;

        try {
            BudgetManager.reset();
            var extractedTestCase = TestCaseExtractor.extractTestCase(receivedMessage);
            if (extractedTestCase.isEmpty()) {
                var errorMessage = "Failed to extract a valid test case from the provided message. " +
                        "Please ensure the message contains all required information (test case name, test steps with descriptions).";
                LOG.error(errorMessage);
                systemInfo = getSystemInfo();
                return new UiTestExecutionResult("Unknown Test Case", ERROR, List.of(), List.of(), captureScreen(),
                        systemInfo, null, List.of(), testExecutionStartTimestamp, now(), errorMessage);
            }

            TestCase testCase = extractedTestCase.get();
            LOG.info("Starting execution of the test case '{}'", testCase.name());
            screenRecorder.beginScreenCapture();
            logCapture.start();
            systemInfo = getSystemInfo();

            try {
                var context = new UiTestExecutionContext(testCase, new VisualState(captureScreen()));
                var imageVerificationAgent = getImageVerificationAgent(new RetryState());
                var verificationTools = new VerificationTools(context, imageVerificationAgent);
                var userInteractionTools = switch (getExecutionMode()) {
                    case ATTENDED -> new AttendedModeCommonUserInteractionTools(getUiElementRetriever(), context);
                    case SEMI_ATTENDED -> new SemiAttendedModeCommonUserInteractionTools(getUiElementRetriever(), context);
                    case UNATTENDED -> null;
                };
                var preconditionCommonTools = new CommonTools();
                var testStepCommonTools = new CommonTools();

                // Select starting step for attended/semi-attended modes
                int startingStepIndex = 0;
                if (!isFullyUnattended()) {
                    startingStepIndex = TestStepSelectionPopup.displayAndGetSelection(testCase.testSteps());
                    if (startingStepIndex > 0) {
                        LOG.info("Operator selected to start from step #{} (0-indexed)", startingStepIndex);
                    }
                }

                if (startingStepIndex == 0 && testCase.preconditions() != null && !testCase.preconditions().isEmpty()) {
                    var preconditionActionAgent =
                            getPreconditionActionAgent(preconditionCommonTools, userInteractionTools, new RetryState());
                    executePreconditions(context, preconditionActionAgent);
                    if (hasPreconditionFailures(context)) {
                        var failedPrecondition = context.getPreconditionExecutionHistory().getLast();
                        return getTestExecutionResultWithError(context, testExecutionStartTimestamp,
                                failedPrecondition.getErrorMessage(), context.getVisualState().screenshot(),
                                systemInfo, screenRecorder.getCurrentRecordingPath(), logCapture.getLogs());
                    }
                }

                var testStepActionAgent = getTestStepActionAgent(testStepCommonTools, userInteractionTools, new RetryState());
                executeTestSteps(context, testStepActionAgent, verificationTools, userInteractionTools, startingStepIndex);
                if (hasStepFailures(context)) {
                    var lastStep = context.getTestStepExecutionHistory().getLast();
                    if (lastStep.getExecutionStatus() == FAILURE) {
                        return getFailedTestExecutionResult(context, testExecutionStartTimestamp, lastStep.getErrorMessage(),
                                systemInfo, screenRecorder.getCurrentRecordingPath(), logCapture.getLogs());
                    } else {
                        return getTestExecutionResultWithError(context, testExecutionStartTimestamp, lastStep.getErrorMessage(),
                                null, systemInfo, screenRecorder.getCurrentRecordingPath(), logCapture.getLogs());
                    }
                } else {
                    return new UiTestExecutionResult(testCase.name(), PASSED, context.getPreconditionExecutionHistory(),
                            context.getTestStepExecutionHistory(), null, systemInfo, screenRecorder.getCurrentRecordingPath(),
                            logCapture.getLogs(), testExecutionStartTimestamp, now(), null);
                }
            } finally {
                LOG.info("Finished execution of the test case '{}'", testCase.name());
            }
        } catch (Exception e) {
            LOG.error("Unexpected error during test case execution", e);
            if (systemInfo == null) {
                systemInfo = getSystemInfo();
            }
            return new UiTestExecutionResult("Unknown Test Case", ERROR, List.of(), List.of(), captureScreen(),
                    systemInfo, screenRecorder.getCurrentRecordingPath(), logCapture.getLogs(), testExecutionStartTimestamp, now(),
                    "Unexpected error during test case execution: " + e.getMessage());
        } finally {
            screenRecorder.endScreenCapture();
            logCapture.stop();
        }
    }

    private static void executePreconditions(UiTestExecutionContext context,
                                             UiPreconditionActionAgent preconditionActionAgent) {
        List<String> preconditions = context.getTestCase().preconditions();
        var preconditionVerificationAgent = getPreconditionVerificationAgent(new RetryState());
        if (preconditions != null && !preconditions.isEmpty()) {
            LOG.info("Executing and verifying preconditions for test case: {}", context.getTestCase().name());
            for (String precondition : preconditions) {
                var executionStartTimestamp = now();
                LOG.info("Executing precondition: {}", precondition);
                var screenshot = captureScreen();
                context.setVisualState(new VisualState(screenshot));
                var preconditionExecutionResult = preconditionActionAgent.executeAndGetResult(
                        () -> preconditionActionAgent.execute(precondition, context.getSharedData().toString(),
                                singleImageContent(screenshot)));
                resetToolCallUsage();

                if (!preconditionExecutionResult.isSuccess()) {
                    var errorMessage = "Failure while executing precondition '%s'. Root cause: %s"
                            .formatted(precondition, preconditionExecutionResult.getMessage());
                    context.addPreconditionResult(new UiPreconditionResult(precondition, false, errorMessage, captureScreen(),
                            executionStartTimestamp, now()));
                    return;
                }
                LOG.info("Precondition execution complete.");

                LOG.info("Verifying if precondition was successfully executed.");
                var verificationExecutionResult = preconditionVerificationAgent.executeWithRetry(() -> {
                    var screenshot = captureScreen();
                    context.setVisualState(new VisualState(screenshot));
                    return preconditionVerificationAgent.verify(precondition, context.getSharedData().toString(),
                            singleImageContent(screenshot));
                }, r -> r == null || !r.success());
                resetToolCallUsage();

                if (!verificationExecutionResult.isSuccess()) {
                    var errorMessage = "Error while verifying precondition '%s'. Root cause: %s"
                            .formatted(precondition, verificationExecutionResult.getMessage());
                    context.addPreconditionResult(new UiPreconditionResult(precondition, false, errorMessage,
                            context.getVisualState().screenshot(), executionStartTimestamp, now()));
                    return;
                }

                var verificationResult = verificationExecutionResult.getResultPayload();
                if (verificationResult == null) {
                    var errorMessage = "Precondition verification failed. Got no verification result from the model.";
                    context.addPreconditionResult(new UiPreconditionResult(precondition, false, errorMessage,
                            context.getVisualState().screenshot(), executionStartTimestamp, now()));
                    return;
                }
                if (!verificationResult.success()) {
                    var errorMessage = "Precondition verification failed. %s".formatted(verificationResult.message());
                    context.addPreconditionResult(new UiPreconditionResult(precondition, false, errorMessage,
                            context.getVisualState().screenshot(), executionStartTimestamp, now()));
                    return;
                }
                context.addPreconditionResult(
                        new UiPreconditionResult(precondition, true, null, null, executionStartTimestamp, now()));
                LOG.info("Precondition '{}' is met.", precondition);
            }
            LOG.info("All preconditions are met for test case: {}", context.getTestCase().name());
        }
    }

    private static void executeTestSteps(UiTestExecutionContext context, UiTestStepActionAgent uiTestStepActionAgent,
                                         VerificationTools verificationTools,
                                         CommonUserInteractionTools userInteractionTools, int startingStepIndex) {
        var testStepVerificationAgent = getTestStepVerificationAgent(verificationTools, userInteractionTools, new RetryState());
        var testSteps = context.getTestCase().testSteps();
        for (int i = startingStepIndex; i < testSteps.size(); i++) {
            TestStep testStep = testSteps.get(i);
            var actionInstruction = testStep.stepDescription();
            var testData = ofNullable(testStep.testData()).map(Object::toString).orElse("");
            try {
                var executionStartTimestamp = now();
                LOG.info("Executing test step: {}", actionInstruction);
                var screenshot = captureScreen();
                context.setVisualState(new VisualState(screenshot));
                var actionResult = ((UiOperationExecutionResult<EmptyExecutionResult>) uiTestStepActionAgent.executeAndGetResult(() -> {
                    uiTestStepActionAgent.execute(actionInstruction, testData, context.getSharedData().toString(),
                            !isFullyUnattended(), singleImageContent(screenshot));
                    return null;
                }));
                resetToolCallUsage();
                if (!actionResult.isSuccess()) {
                    var message = "There was an error while executing test step action '%s'. Please see agent logs for details"
                            .formatted(actionInstruction);
                    addFailedTestStep(context, testStep, message, null, executionStartTimestamp, now(),
                            captureScreen(), TestStepResultStatus.ERROR);
                    return;
                }
                LOG.info("Action execution complete.");

                var verificationInstruction = testStep.expectedResults();
                if (isNotBlank(verificationInstruction)) {
                    LOG.info("Verifying that '{}'", verificationInstruction);
                    String testDataString = testStep.testData() == null ? null : join(", ", testStep.testData());
                    sleepMillis(ACTION_VERIFICATION_DELAY_MILLIS);
                    var agentResult =
                            (UiOperationExecutionResult<VerificationExecutionResult>) testStepVerificationAgent.executeAndGetResult(() ->
                                    testStepVerificationAgent.verify(verificationInstruction, actionInstruction, testDataString,
                                            context.getSharedData().toString()));
                    resetToolCallUsage();

                    if (!agentResult.isSuccess()) {
                        var message = "There was an error while verifying that '%s'. Please see agent logs for details"
                                .formatted(verificationInstruction);
                        addFailedTestStep(context, testStep, message, null, executionStartTimestamp, now(),
                                captureScreen(), TestStepResultStatus.ERROR);
                        return;
                    }

                    VerificationExecutionResult verificationResult = agentResult.getResultPayload();
                    // If result payload is null (which shouldn't happen on success unless empty), check message
                    if (verificationResult == null) {
                        verificationResult = new VerificationExecutionResult(false, "No verification result returned.");
                    }

                    if (!verificationResult.success()) {
                        var generalMessage = "Verification failed. %s".formatted(verificationResult.message());
                        LOG.warn("Interrupting test case execution because the verification failed. {}", verificationResult.message());
                        addFailedTestStep(context, testStep, generalMessage, verificationResult.message(), executionStartTimestamp, now(),
                                context.getVisualState().screenshot(), FAILURE);
                        return;
                    } else {
                        LOG.info("Verification succeeded.");
                        context.addStepResult(new UiTestStepResult(testStep, SUCCESS, null, verificationResult.message(), null,
                                executionStartTimestamp, now()));
                    }
                } else {
                    context.addStepResult(new UiTestStepResult(testStep, SUCCESS, null, "No verification required",
                            null, executionStartTimestamp, now()));
                }
            } catch (Exception e) {
                LOG.error("Unexpected error while executing the test step: '{}'", testStep.stepDescription(), e);
                addFailedTestStep(context, testStep, e.getMessage(), null, now(), now(), captureScreen(), TestStepResultStatus.ERROR);
                return;
            }
        }
    }

    private static boolean hasPreconditionFailures(TestExecutionContext context) {
        return !context.getPreconditionExecutionHistory().stream().allMatch(PreconditionResult::isSuccess);
    }

    private static boolean hasStepFailures(TestExecutionContext context) {
        return context.getTestStepExecutionHistory().stream().map(TestStepResult::getExecutionStatus)
                .anyMatch(s -> s != SUCCESS);
    }

    private static UiTestStepVerificationAgent getTestStepVerificationAgent(VerificationTools verificationTools,
                                                                            CommonUserInteractionTools userInteractionTools,
                                                                            RetryState retryState) {
        var testStepVerificationAgentModel = getModel(getTestStepVerificationAgentModelName(),
                getTestStepVerificationAgentModelProvider(), getVerificationModelMaxRetries());
        var testStepVerificationAgentPrompt = loadSystemPrompt("test_step/verifier",
                getTestStepVerificationAgentPromptVersion(), "main_verification_prompt.txt");
        var modeSpecificPrompt = getVerifierModeSpecificSystemPrompt();
        var finalPrompt = PromptTemplate.from(testStepVerificationAgentPrompt)
                .apply(Map.of(MODE_SPECIFIC_RULES_PLACEHOLDER, modeSpecificPrompt))
                .text()
                .trim();

        var agentBuilder = builder(UiTestStepVerificationAgent.class)
                .chatModel(testStepVerificationAgentModel.chatModel())
                .systemMessageProvider(_ -> finalPrompt)
                .maxSequentialToolsInvocations(getEffectiveToolCallsBudget())
                .toolExecutionErrorHandler(new UiToolErrorHandler(UiTestStepVerificationAgent.RETRY_POLICY, retryState));

        List<Object> tools = new ArrayList<>();
        tools.add(verificationTools);
        if (userInteractionTools != null) {
            tools.add(userInteractionTools);
        }
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(tools, VerificationExecutionResult.class));

        return agentBuilder.build();
    }

    private static ImageVerificationAgent getImageVerificationAgent(RetryState retryState) {
        var model = getModel(getTestStepVerificationAgentModelName(),
                getTestStepVerificationAgentModelProvider(), getVerificationModelMaxRetries());
        var prompt = loadSystemPrompt("test_step/verifier",
                getTestStepVerificationAgentPromptVersion(), "verification_execution_prompt.txt");
        var finalPrompt = PromptTemplate.from(prompt)
                .apply(Map.of(MODE_SPECIFIC_RULES_PLACEHOLDER, ""))
                .text()
                .trim();

        return builder(ImageVerificationAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> finalPrompt)
                .maxSequentialToolsInvocations(getAgentToolCallsBudget())
                .toolProvider(new InheritanceAwareToolProvider<>(VerificationExecutionResult.class))
                .toolExecutionErrorHandler(
                        new DefaultToolErrorHandler(ImageVerificationAgent.RETRY_POLICY, retryState, isFullyUnattended()))
                .build();
    }

    private static @NonNull String getVerifierModeSpecificSystemPrompt() {
        var fileName = switch (getExecutionMode()) {
            case ATTENDED, SEMI_ATTENDED -> "attended_mode_rules.txt";
            case UNATTENDED -> "unattended_mode_rules.txt";
        };
        return loadSystemPrompt("test_step/verifier", getTestStepVerificationAgentPromptVersion(), fileName);
    }

    private static UiTestStepActionAgent getTestStepActionAgent(CommonTools commonTools,
                                                                CommonUserInteractionTools userInteractionTools,
                                                                RetryState retryState) {
        var testStepActionAgentModel = getModel(getTestStepActionAgentModelName(),
                getTestStepActionAgentModelProvider());
        var testStepActionAgentPrompt = loadSystemPrompt("test_step/executor",
                getTestStepActionAgentPromptVersion(), "test_step_action_agent_system_prompt.txt");
        var modeSpecificPrompt = getModeSpecificTestStepActionSystemPrompt();
        var finalPrompt = PromptTemplate.from(testStepActionAgentPrompt)
                .apply(Map.of("mode_specific_rules", modeSpecificPrompt))
                .text()
                .trim();
        var agentBuilder = builder(UiTestStepActionAgent.class)
                .chatModel(testStepActionAgentModel.chatModel())
                .systemMessageProvider(_ -> finalPrompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(UiTestStepActionAgent.RETRY_POLICY, retryState))
                .maxSequentialToolsInvocations(getEffectiveToolCallsBudget());

        List<Object> tools = new ArrayList<>(List.of(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(), commonTools));
        if (userInteractionTools != null) {
            tools.add(userInteractionTools);
        }
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(tools, EmptyExecutionResult.class));

        return agentBuilder.build();
    }

    private static @NonNull String getModeSpecificTestStepActionSystemPrompt() {
        var fileName = switch (getExecutionMode()) {
            case ATTENDED -> "attended_mode_rules.txt";
            case SEMI_ATTENDED -> "semi_attended_mode_rules.txt";
            case UNATTENDED -> "unattended_mode_rules.txt";
        };
        return loadSystemPrompt("test_step/executor", getTestStepActionAgentPromptVersion(), fileName);
    }

    private static UiPreconditionVerificationAgent getPreconditionVerificationAgent(RetryState retryState) {
        var preconditionVerificationAgentModel = getModel(getPreconditionVerificationAgentModelName(),
                getPreconditionVerificationAgentModelProvider(), getVerificationModelMaxRetries());
        var preconditionVerificationAgentPrompt = loadSystemPrompt("precondition/verifier",
                getPreconditionVerificationAgentPromptVersion(), "precondition_verification_prompt.txt");
        return builder(UiPreconditionVerificationAgent.class)
                .chatModel(preconditionVerificationAgentModel.chatModel())
                .systemMessageProvider(_ -> preconditionVerificationAgentPrompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(RETRY_POLICY, retryState))
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(), VerificationExecutionResult.class))
                .maxSequentialToolsInvocations(getEffectiveToolCallsBudget())
                .build();
    }

    private static UiPreconditionActionAgent getPreconditionActionAgent(CommonTools commonTools,
                                                                        CommonUserInteractionTools userInteractionTools,
                                                                        RetryState retryState) {
        var preconditionAgentModel = getModel(getPreconditionActionAgentModelName(),
                getPreconditionActionAgentModelProvider());
        var preconditionAgentPrompt = loadSystemPrompt("precondition/executor",
                getPreconditionAgentPromptVersion(), "precondition_action_agent_system_prompt.txt");
        var agentBuilder = builder(UiPreconditionActionAgent.class)
                .chatModel(preconditionAgentModel.chatModel())
                .systemMessageProvider(_ -> preconditionAgentPrompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(PreconditionActionAgent.RETRY_POLICY, retryState));

        List<Object> tools = new ArrayList<>(List.of(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(), commonTools));
        if (userInteractionTools != null) {
            tools.add(userInteractionTools);
        }
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(tools, EmptyExecutionResult.class));

        return agentBuilder.maxSequentialToolsInvocations(getEffectiveToolCallsBudget()).build();
    }

    private static SystemInfo getSystemInfo() {
        String device = "PC";
        try {
            device = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception _) {
            // If we can't retrieve the device, let's stick to having the default one - PC
        }
        String os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String browser = System.getenv("TEST_BROWSER");
        if (isBlank(browser)) {
            browser = "N/A";
        }
        String environment = System.getenv("TEST_ENVIRONMENT");
        if (isBlank(environment)) {
            environment = "Local";
        }
        return new SystemInfo(device, os, browser, environment);
    }

    @NotNull
    private static TestExecutionResult getFailedTestExecutionResult(TestExecutionContext context,
                                                                    Instant testExecutionStartTimestamp, String errorMessage,
                                                                    SystemInfo systemInfo, String videoPath,
                                                                    List<String> logs) {
        LOG.error(errorMessage);
        return new UiTestExecutionResult(context.getTestCase().name(), FAILED, context.getPreconditionExecutionHistory(),
                context.getTestStepExecutionHistory(), null, systemInfo, videoPath, logs, testExecutionStartTimestamp, now(),
                errorMessage);
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestExecutionContext context,
                                                                       Instant testExecutionStartTimestamp, String errorMessage,
                                                                       BufferedImage screenshot, SystemInfo systemInfo, String videoPath,
                                                                       List<String> logs) {
        LOG.error(errorMessage);
        return new UiTestExecutionResult(context.getTestCase().name(), ERROR, context.getPreconditionExecutionHistory(),
                context.getTestStepExecutionHistory(), screenshot, systemInfo, videoPath, logs, testExecutionStartTimestamp, now(),
                errorMessage);
    }

    private static void addFailedTestStep(
            TestExecutionContext context,
            TestStep testStep,
            String errorMessage,
            String actualResult,
            Instant executionStartTimestamp,
            Instant executionEndTimestamp,
            BufferedImage screenshot,
            TestStepResultStatus status) {
        context.addStepResult(new UiTestStepResult(testStep, status, errorMessage, actualResult, screenshot, executionStartTimestamp,
                executionEndTimestamp));
    }

    private static class UiToolErrorHandler extends DefaultToolErrorHandler {
        private static final List<ErrorCategory> terminalErrors = List.of(NON_RETRYABLE_ERROR, TIMEOUT, TERMINATION_BY_USER);

        public UiToolErrorHandler(RetryPolicy retryPolicy, RetryState retryState) {
            super(retryPolicy, retryState, isFullyUnattended());
        }

        @Override
        protected List<ErrorCategory> getTerminalErrors() {
            return terminalErrors;
        }

        @Override
        public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
            switch (error) {
                case ElementLocationException elementLocationException -> {
                    return handleRetryableToolError(elementLocationException.getMessage());
                }
                case ToolExecutionException toolExecutionException -> {
                    if (getTerminalErrors().contains(toolExecutionException.getErrorCategory())) {
                        throw toolExecutionException;
                    } else {
                        return handleRetryableToolError(toolExecutionException.getMessage());
                    }
                }
                case null, default -> throw new RuntimeException(error);
            }
        }
    }

    private static int getEffectiveToolCallsBudget() {
        return isFullyUnattended() ? getAgentToolCallsBudget() : getAgentToolCallsBudgetAttended();
    }
}
