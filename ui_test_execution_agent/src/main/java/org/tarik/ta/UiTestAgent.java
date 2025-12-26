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

import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.agents.PreconditionActionAgent;
import org.tarik.ta.core.agents.PreconditionVerificationAgent;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.*;
import org.tarik.ta.core.model.DefaultErrorHandler;
import org.tarik.ta.dto.UiPreconditionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.dto.SystemInfo;
import org.tarik.ta.utils.LogCapture;
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
import org.tarik.ta.manager.VerificationManager;
import org.tarik.ta.tools.*;
import org.tarik.ta.utils.ScreenRecorder;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.service.AiServices.builder;
import static java.lang.String.join;
import static java.time.Instant.now;
import static java.util.Optional.*;
import static org.tarik.ta.UiTestAgentConfig.isElementLocationPrefetchingEnabled;
import static org.tarik.ta.UiTestAgentConfig.isUnattendedMode;
import static org.tarik.ta.core.AgentConfig.*;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.ERROR;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import static org.tarik.ta.core.error.ErrorCategory.*;
import static org.tarik.ta.core.model.ModelFactory.getModel;
import static org.tarik.ta.rag.RetrieverFactory.getUiElementRetriever;
import static org.tarik.ta.core.dto.AgentExecutionResult.ExecutionStatus.VERIFICATION_FAILURE;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.utils.ImageUtils.singleImageContent;

public class UiTestAgent {
    private static final Logger LOG = LoggerFactory.getLogger(UiTestAgent.class);
    protected static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();

    public static TestExecutionResult executeTestCase(String receivedMessage) {
        TestCase testCase = extractTestCase(receivedMessage).orElseThrow();
        LOG.info("Starting execution of the test case '{}'", testCase.name());
        BudgetManager.reset();
        ScreenRecorder screenRecorder = new ScreenRecorder();
        screenRecorder.beginScreenCapture();
        LogCapture logCapture = new LogCapture();
        logCapture.start();
        SystemInfo systemInfo = getSystemInfo();

        try (VerificationManager verificationManager = new VerificationManager()) {
            var testExecutionStartTimestamp = now();
            var context = new UiTestExecutionContext(testCase, new VisualState(captureScreen()));
            var userInteractionTools = new UserInteractionTools(getUiElementRetriever());
            var preconditionCommonTools = new CommonTools();
            var testStepCommonTools = new CommonTools(verificationManager);

            if (testCase.preconditions() != null && !testCase.preconditions().isEmpty()) {
                executePreconditions(context,
                        getPreconditionActionAgent(preconditionCommonTools, userInteractionTools, new RetryState()));
                if (hasPreconditionFailures(context)) {
                    var failedPrecondition = context.getPreconditionExecutionHistory().getLast();
                    return getFailedTestExecutionResult(context, testExecutionStartTimestamp,
                            failedPrecondition.errorMessage(),
                            ((UiPreconditionResult) failedPrecondition).screenshot(), systemInfo,
                            screenRecorder.getCurrentRecordingPath(), logCapture.getLogs());
                }
            }

            var testStepActionAgent = getTestStepActionAgent(testStepCommonTools, userInteractionTools,
                    new RetryState());
            executeTestSteps(context, testStepActionAgent, verificationManager);
            if (hasStepFailures(context)) {
                var lastStep = context.getTestStepExecutionHistory().getLast();
                if (lastStep.executionStatus() == TestStepResultStatus.FAILURE) {
                    return getFailedTestExecutionResult(context, testExecutionStartTimestamp, lastStep.errorMessage(),
                            ((UiTestStepResult) lastStep).screenshot(), systemInfo,
                            screenRecorder.getCurrentRecordingPath(), logCapture.getLogs());
                } else {
                    return getTestExecutionResultWithError(context, testExecutionStartTimestamp,
                            lastStep.errorMessage(),
                            ((UiTestStepResult) lastStep).screenshot(), systemInfo,
                            screenRecorder.getCurrentRecordingPath(), logCapture.getLogs());
                }
            } else {
                return new UiTestExecutionResult(testCase.name(), PASSED, context.getPreconditionExecutionHistory(),
                        context.getTestStepExecutionHistory(), null, systemInfo,
                        screenRecorder.getCurrentRecordingPath(), logCapture.getLogs(), testExecutionStartTimestamp,
                        now(),
                        null);
            }
        } finally {
            LOG.info("Finished execution of the test case '{}'", testCase.name());
            screenRecorder.endScreenCapture();
            logCapture.stop();
        }
    }

    public static Optional<TestCase> extractTestCase(String message) {
        LOG.info("Attempting to extract TestCase instance from user message using AI model.");
        if (isBlank(message)) {
            LOG.error("User message is blank, cannot extract a TestCase.");
            return empty();
        }

        try {
            var agent = getTestCaseExtractionAgent();
            TestCase extractedTestCase = agent.executeAndGetResult(() -> agent.extractTestCase(message))
                    .getResultPayload();
            if (isTestCaseInvalid(extractedTestCase)) {
                LOG.warn(
                        "Model could not extract a valid TestCase from the provided by the user message, original message: {}",
                        message);
                return empty();
            } else {
                LOG.info("Successfully extracted TestCase: '{}'", extractedTestCase.name());
                return of(extractedTestCase);
            }
        } catch (Exception e) {
            LOG.error("Failed to extract test case from message", e);
            return empty();
        }
    }

    private static boolean isTestCaseInvalid(TestCase extractedTestCase) {
        return extractedTestCase == null || isBlank(extractedTestCase.name()) || extractedTestCase.testSteps() == null
                ||
                extractedTestCase.testSteps().isEmpty() ||
                extractedTestCase.testSteps().stream().anyMatch(step -> isBlank(step.stepDescription()));
    }

    private static TestCaseExtractionAgent getTestCaseExtractionAgent() {
        var model = getModel(AgentConfig.getTestCaseExtractionAgentModelName(),
                AgentConfig.getTestCaseExtractionAgentModelProvider());
        var prompt = loadSystemPrompt("test_case_extractor",
                AgentConfig.getTestCaseExtractionAgentPromptVersion(), "test_case_extraction_prompt.txt");
        return builder(TestCaseExtractionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new TestCase("", List.of(), List.of()))
                .build();
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
                var preconditionExecutionResult = preconditionActionAgent.executeWithRetry(
                        () -> preconditionActionAgent.execute(precondition, context.getSharedData().toString()));
                BudgetManager.resetToolCallUsage();

                if (!preconditionExecutionResult.isSuccess()) {
                    var errorMessage = "Failure while executing precondition '%s'. Root cause: %s"
                            .formatted(precondition, preconditionExecutionResult.getMessage());
                    context.addPreconditionResult(
                            new UiPreconditionResult(precondition, false, errorMessage, captureScreen(),
                                    executionStartTimestamp, now()));
                    return;
                }
                LOG.info("Precondition execution complete.");

                var verificationExecutionResult = preconditionVerificationAgent
                        .executeWithRetry(() -> {
                            var screenshot = captureScreen();
                            context.setVisualState(new VisualState(screenshot));
                            return preconditionVerificationAgent.verify(precondition,
                                    context.getSharedData().toString(),
                                    singleImageContent(screenshot));
                        }, r -> r == null || !r.success());
                BudgetManager.resetToolCallUsage();

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

    private static void executeTestSteps(UiTestExecutionContext context,
            UiTestStepActionAgent uiTestStepActionAgent,
            VerificationManager verificationManager) {
        var testStepVerificationAgent = getTestStepVerificationAgent(new RetryState());
        for (TestStep testStep : context.getTestCase().testSteps()) {
            var actionInstruction = testStep.stepDescription();
            var testData = ofNullable(testStep.testData()).map(Object::toString).orElse("");
            var verificationInstruction = testStep.expectedResults();
            try {
                var executionStartTimestamp = now();
                LOG.info("Executing test step: {}", actionInstruction);
                var actionResult = uiTestStepActionAgent.executeWithRetry(() -> {
                    uiTestStepActionAgent.execute(actionInstruction, testData, context.getSharedData().toString(),
                            !isUnattendedMode());
                    return null;
                });
                BudgetManager.resetToolCallUsage();
                if (!actionResult.isSuccess()) {
                    if (actionResult.getExecutionStatus() != VERIFICATION_FAILURE) {
                        // Verification failure happens only if the current action was executed as a UI
                        // element location prefetch part,
                        // it means this test step shouldn't be reported because the execution is to be
                        // halted after verification
                        // failure for the previous step
                        var errorMessage = "Error while executing action '%s'. Root cause: %s"
                                .formatted(actionInstruction, actionResult.getMessage());
                        addFailedTestStep(context, testStep, errorMessage, null, executionStartTimestamp, now(),
                                actionResult.screenshot(), ERROR);
                    }
                    return;
                }
                LOG.info("Action execution complete.");

                if (isNotBlank(verificationInstruction)) {
                    String testDataString = testStep.testData() == null ? null : join(", ", testStep.testData());
                    verificationManager.submitVerification(() -> {
                        try {
                            sleepMillis(ACTION_VERIFICATION_DELAY_MILLIS);
                            LOG.info("Executing verification of: '{}'", verificationInstruction);
                            var verificationExecutionResult = testStepVerificationAgent.executeWithRetry(() -> {
                                var screenshot = captureScreen();
                                context.setVisualState(new VisualState(screenshot));
                                return testStepVerificationAgent.verify(verificationInstruction, actionInstruction,
                                        testDataString, context.getSharedData().toString(),
                                        singleImageContent(screenshot));
                            }, result -> result == null || !result.success());
                            BudgetManager.resetToolCallUsage();

                            if (!verificationExecutionResult.isSuccess()) {
                                var errorMessage = "Failure while verifying test step '%s'. Root cause: %s"
                                        .formatted(actionInstruction, verificationExecutionResult.getMessage());
                                addFailedTestStep(context, testStep, errorMessage, null, executionStartTimestamp, now(),
                                        context.getVisualState().screenshot(), ERROR);
                                return false;
                            } else {
                                VerificationExecutionResult verificationResult = verificationExecutionResult
                                        .getResultPayload();
                                if (verificationResult != null && !verificationResult.success()) {
                                    var errorMessage = "Verification failed. %s"
                                            .formatted(verificationResult.message());
                                    addFailedTestStep(context, testStep, errorMessage, verificationResult.message(),
                                            executionStartTimestamp, now(), context.getVisualState().screenshot(),
                                            TestStepResultStatus.FAILURE);
                                    return false;
                                }
                                LOG.info("Verification execution complete.");
                                var actualResult = verificationResult != null ? verificationResult.message()
                                        : "Verification successful";
                                context.addStepResult(new UiTestStepResult(testStep, SUCCESS, null, actualResult, null,
                                        executionStartTimestamp, now()));
                                return true;
                            }
                        } catch (Exception e) {
                            LOG.error("Unexpected error during async verification", e);
                            addFailedTestStep(context, testStep, e.getMessage(), null, executionStartTimestamp, now(),
                                    captureScreen(),
                                    ERROR);
                            return false;
                        }
                    });

                    if (!isElementLocationPrefetchingEnabled() &&
                            !verificationManager.waitForVerificationToFinish(getVerificationRetryTimeoutMillis())
                                    .success()) {
                        // The test case execution should be interrupted after any verification failure
                        return;
                    }
                } else {
                    context.addStepResult(new UiTestStepResult(testStep, SUCCESS, null, "No verification required",
                            null, executionStartTimestamp, now()));
                }
            } catch (Exception e) {
                LOG.error("Unexpected error while executing the test step: '{}'", testStep.stepDescription(), e);
                addFailedTestStep(context, testStep, e.getMessage(), null, now(), now(), captureScreen(), ERROR);
                return;
            }
        }

        if (isElementLocationPrefetchingEnabled()) {
            verificationManager.waitForVerificationToFinish(Long.MAX_VALUE);
        }
    }

    private static boolean hasPreconditionFailures(TestExecutionContext context) {
        return !context.getPreconditionExecutionHistory().stream().allMatch(PreconditionResult::success);
    }

    private static boolean hasStepFailures(TestExecutionContext context) {
        return context.getTestStepExecutionHistory().stream().map(TestStepResult::executionStatus)
                .anyMatch(s -> s != SUCCESS);
    }

    private static UiTestStepVerificationAgent getTestStepVerificationAgent(RetryState retryState) {
        var testStepVerificationAgentModel = getModel(AgentConfig.getTestStepVerificationAgentModelName(),
                AgentConfig.getTestStepVerificationAgentModelProvider());
        var testStepVerificationAgentPrompt = loadSystemPrompt("test_step/verifier",
                AgentConfig.getTestStepVerificationAgentPromptVersion(), "verification_execution_prompt.txt");
        return builder(UiTestStepVerificationAgent.class)
                .chatModel(testStepVerificationAgentModel.chatModel())
                .systemMessageProvider(_ -> testStepVerificationAgentPrompt)
                .toolExecutionErrorHandler(new UiErrorHandler(UiTestStepVerificationAgent.RETRY_POLICY, retryState))
                .tools(new VerificationExecutionResult(false, ""))
                .build();
    }

    private static UiTestStepActionAgent getTestStepActionAgent(CommonTools commonTools,
            UserInteractionTools userInteractionTools,
            RetryState retryState) {
        var testStepActionAgentModel = getModel(AgentConfig.getTestStepActionAgentModelName(),
                AgentConfig.getTestStepActionAgentModelProvider());
        var testStepActionAgentPrompt = loadSystemPrompt("test_step/executor",
                AgentConfig.getTestStepActionAgentPromptVersion(), "test_step_action_agent_system_prompt.txt");
        var agentBuilder = builder(UiTestStepActionAgent.class)
                .chatModel(testStepActionAgentModel.chatModel())
                .systemMessageProvider(_ -> testStepActionAgentPrompt)
                .toolExecutionErrorHandler(new UiErrorHandler(UiTestStepActionAgent.RETRY_POLICY, retryState));

        if (isUnattendedMode()) {
            agentBuilder.tools(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(), commonTools,
                    new EmptyExecutionResult());
        } else {
            agentBuilder.tools(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(), commonTools,
                    userInteractionTools,
                    new EmptyExecutionResult());
        }

        return agentBuilder.build();
    }

    private static UiPreconditionVerificationAgent getPreconditionVerificationAgent(RetryState retryState) {
        var preconditionVerificationAgentModel = getModel(AgentConfig.getPreconditionVerificationAgentModelName(),
                AgentConfig.getPreconditionVerificationAgentModelProvider());
        var preconditionVerificationAgentPrompt = loadSystemPrompt("precondition/verifier",
                AgentConfig.getPreconditionVerificationAgentPromptVersion(), "precondition_verification_prompt.txt");
        return builder(UiPreconditionVerificationAgent.class)
                .chatModel(preconditionVerificationAgentModel.chatModel())
                .systemMessageProvider(_ -> preconditionVerificationAgentPrompt)
                .toolExecutionErrorHandler(new UiErrorHandler(PreconditionVerificationAgent.RETRY_POLICY, retryState))
                .tools(new VerificationExecutionResult(false, ""))
                .build();
    }

    private static UiPreconditionActionAgent getPreconditionActionAgent(CommonTools commonTools,
            UserInteractionTools userInteractionTools,
            RetryState retryState) {
        var preconditionAgentModel = getModel(AgentConfig.getPreconditionActionAgentModelName(),
                AgentConfig.getPreconditionActionAgentModelProvider());
        var preconditionAgentPrompt = loadSystemPrompt("precondition/executor",
                AgentConfig.getPreconditionAgentPromptVersion(), "precondition_action_agent_system_prompt.txt");
        var agentBuilder = builder(UiPreconditionActionAgent.class)
                .chatModel(preconditionAgentModel.chatModel())
                .systemMessageProvider(_ -> preconditionAgentPrompt)
                .toolExecutionErrorHandler(new UiErrorHandler(PreconditionActionAgent.RETRY_POLICY, retryState));

        if (isUnattendedMode()) {
            agentBuilder.tools(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(), commonTools,
                    new EmptyExecutionResult());
        } else {
            agentBuilder.tools(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(), commonTools,
                    userInteractionTools,
                    new EmptyExecutionResult());
        }

        return agentBuilder.build();
    }

    private static SystemInfo getSystemInfo() {
        String device = "PC";
        try {
            device = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }
        String os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String browser = System.getProperty("test.browser", "N/A");
        String environment = System.getProperty("test.environment", "Local");
        return new SystemInfo(device, os, browser, environment);
    }

    @NotNull
    private static TestExecutionResult getFailedTestExecutionResult(TestExecutionContext context,
            Instant testExecutionStartTimestamp, String errorMessage,
            BufferedImage screenshot, SystemInfo systemInfo, String videoPath, List<String> logs) {
        LOG.error(errorMessage);
        return new UiTestExecutionResult(context.getTestCase().name(), FAILED,
                context.getPreconditionExecutionHistory(),
                context.getTestStepExecutionHistory(),
                screenshot,
                systemInfo,
                videoPath,
                logs,
                testExecutionStartTimestamp, now(), errorMessage);
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestExecutionContext context,
            Instant testExecutionStartTimestamp, String errorMessage,
            BufferedImage screenshot, SystemInfo systemInfo, String videoPath, List<String> logs) {
        LOG.error(errorMessage);
        return new UiTestExecutionResult(context.getTestCase().name(), TestExecutionResult.TestExecutionStatus.ERROR,
                context.getPreconditionExecutionHistory(), context.getTestStepExecutionHistory(), screenshot,
                systemInfo, videoPath, logs,
                testExecutionStartTimestamp,
                now(), errorMessage);
    }

    private static void addFailedTestStep(TestExecutionContext context, TestStep testStep, String errorMessage,
            String actualResult, Instant executionStartTimestamp,
            Instant executionEndTimestamp, BufferedImage screenshot, TestStepResultStatus status) {
        context.addStepResult(new UiTestStepResult(testStep, status, errorMessage, actualResult, screenshot,
                executionStartTimestamp, executionEndTimestamp));
    }

    private static class UiErrorHandler extends DefaultErrorHandler {
        private static final List<ErrorCategory> terminalErrors = List.of(NON_RETRYABLE_ERROR, TIMEOUT,
                TERMINATION_BY_USER, VERIFICATION_FAILED);

        public UiErrorHandler(RetryPolicy retryPolicy, RetryState retryState) {
            super(retryPolicy, retryState, isUnattendedMode());
        }

        @Override
        protected List<ErrorCategory> getTerminalErrors() {
            return terminalErrors;
        }

        @Override
        public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
            if (error instanceof ElementLocationException elementLocationException) {
                return handleRetryableToolError(elementLocationException.getMessage());
            } else if (error instanceof ToolExecutionException toolExecutionException) {
                if (getTerminalErrors().contains(toolExecutionException.getErrorCategory())) {
                    throw toolExecutionException;
                } else {
                    return handleRetryableToolError(toolExecutionException.getMessage());
                }
            } else {
                throw new RuntimeException(error);
            }
        }
    }
}