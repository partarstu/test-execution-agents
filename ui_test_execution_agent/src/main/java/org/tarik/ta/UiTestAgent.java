/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.*;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.core.dto.*;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.service.ExecutionStateTracker;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.user_dialogs.knowledge.TestStepSelectionPopup;
import org.tarik.ta.utils.ScreenRecorder;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;

import static java.time.Instant.now;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.*;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

import java.net.InetAddress;

@UiAgentRequestScope
public class UiTestAgent {
    private static final Logger LOG = LoggerFactory.getLogger(UiTestAgent.class);

    private final KnowledgeBasedExecutionOrchestrator knowledgeBasedExecutionOrchestrator;
    private final KnowledgeService knowledgeService;
    private final UiTestAgentConfig uiTestAgentConfig;
    private final TestCaseExtractor testCaseExtractor;
    private final BudgetManager budgetManager;
    private final UiTestExecutionContext context;
    private final ScreenRecorder screenRecorder;
    private final LogCapture logCapture;
    private final ExecutionStateTracker stateTracker;

    public UiTestAgent(KnowledgeBasedExecutionOrchestrator knowledgeBasedExecutionOrchestrator,
                       KnowledgeService knowledgeService,
                       UiTestAgentConfig uiTestAgentConfig,
                       TestCaseExtractor testCaseExtractor,
                       BudgetManager budgetManager,
                       UiTestExecutionContext context,
                       ScreenRecorder screenRecorder,
                       LogCapture logCapture,
                       ExecutionStateTracker stateTracker) {
        this.knowledgeBasedExecutionOrchestrator = knowledgeBasedExecutionOrchestrator;
        this.knowledgeService = knowledgeService;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.testCaseExtractor = testCaseExtractor;
        this.budgetManager = budgetManager;
        this.context = context;
        this.screenRecorder = screenRecorder;
        this.logCapture = logCapture;
        this.stateTracker = stateTracker;
    }

    public TestExecutionResult executeTestCase(String receivedMessage) {
        var testExecutionStartTimestamp = now();
        SystemInfo systemInfo = null;

        try {
            budgetManager.reset();
            if (uiTestAgentConfig.isFullyUnattended()) {
                budgetManager.activateTimeBudget();
            }
            var extractedTestCase = testCaseExtractor.extractTestCase(receivedMessage);
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
            systemInfo = getSystemInfo();
            screenRecorder.beginScreenCapture();
            logCapture.start();

            try {
                int startingStepIndex = 0;
                if (!uiTestAgentConfig.isFullyUnattended()) {
                    startingStepIndex = TestStepSelectionPopup.displayAndGetSelection(testCase.testSteps(), uiTestAgentConfig);
                    if (startingStepIndex < 0) {
                        LOG.info("Operator selected to abort execution from the step selection dialog.");
                        return new UiTestExecutionResult(testCase.name(), ERROR, List.of(), List.of(), captureScreen(),
                                systemInfo, screenRecorder.getCurrentRecordingPath(), logCapture.getLogs(),
                                testExecutionStartTimestamp, now(),
                                "Execution was cancelled by the operator in the step selection dialog.");
                    } else if (startingStepIndex > 0) {
                        LOG.info("Operator selected to start from step #{} (0-indexed)", startingStepIndex);
                    }
                }

                return executeWithKnowledgeFlow(context, testCase, startingStepIndex, testExecutionStartTimestamp,
                        systemInfo, screenRecorder, logCapture, stateTracker);
            } finally {
                LOG.info("Finished execution of the test case '{}'", testCase.name());
            }
        } catch (Exception e) {
            LOG.error("Unexpected error during test case execution", e);
            if (systemInfo == null) {
                systemInfo = getSystemInfo();
            }
            return new UiTestExecutionResult("Unknown Test Case", ERROR, List.of(), List.of(), captureScreen(),
                    systemInfo, null, List.of(),
                    testExecutionStartTimestamp, now(),
                    "Unexpected error during test case execution: " + e.getMessage());
        }
    }

    /**
     * Runs the knowledge-based execution flow. The request scope has already
     * been created above.
     */
    private UiTestExecutionResult executeWithKnowledgeFlow(UiTestExecutionContext context, TestCase testCase,
            int startingStepIndex, Instant testExecutionStartTimestamp,
            SystemInfo systemInfo,
            ScreenRecorder screenRecorder, LogCapture logCapture, ExecutionStateTracker stateTracker) {
        try {
            knowledgeBasedExecutionOrchestrator.executeBasedOnKnowledge(context, testCase, startingStepIndex, stateTracker);

            if (hasStepFailures(context) || hasPreconditionFailures(context)) {
                var lastStep = context.getTestStepExecutionHistory().isEmpty() ? null
                        : context.getTestStepExecutionHistory().getLast();
                if (lastStep != null && lastStep.getExecutionStatus() == FAILURE) {
                    return buildErrorResult(testCase.name(), context, FAILED, testExecutionStartTimestamp, lastStep.getErrorMessage(),
                            null, systemInfo, screenRecorder.getCurrentRecordingPath(), logCapture.getLogs());
                } else if (lastStep != null) {
                    return buildErrorResult(testCase.name(), context, ERROR, testExecutionStartTimestamp, lastStep.getErrorMessage(),
                            captureScreen(), systemInfo, screenRecorder.getCurrentRecordingPath(), logCapture.getLogs());
                } else {
                    var failedPrecondition = context.getPreconditionExecutionHistory().stream()
                            .filter(p -> !p.isSuccess())
                            .findFirst();
                    String errorMessage = failedPrecondition.map(PreconditionResult::getErrorMessage)
                            .orElse("Unknown error during knowledge-based execution");
                    return buildErrorResult(testCase.name(), context, ERROR, testExecutionStartTimestamp, errorMessage,
                            context.getVisualState().screenshot(), systemInfo, screenRecorder.getCurrentRecordingPath(),
                            logCapture.getLogs());
                }
            } else {
                return new UiTestExecutionResult(testCase.name(), PASSED, context.getPreconditionExecutionHistory(),
                        context.getTestStepExecutionHistory(), null, systemInfo,
                        screenRecorder.getCurrentRecordingPath(),
                        logCapture.getLogs(), testExecutionStartTimestamp, now(), null);
            }
        } catch (Exception e) {
            LOG.error("Error during knowledge-based execution", e);
            return new UiTestExecutionResult(testCase.name(), ERROR, context.getPreconditionExecutionHistory(),
                    context.getTestStepExecutionHistory(), captureScreen(), systemInfo,
                    screenRecorder.getCurrentRecordingPath(),
                    logCapture.getLogs(), testExecutionStartTimestamp, now(),
                    "Knowledge-based execution error: " + e.getMessage());
        } finally {
            knowledgeService.onSessionEnd();
        }
    }

    private static SystemInfo getSystemInfo() {
        String device = "PC";
        try {
            device = InetAddress.getLocalHost().getHostName();
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

    static boolean hasPreconditionFailures(TestExecutionContext context) {
        return !context.getPreconditionExecutionHistory().stream().allMatch(PreconditionResult::isSuccess);
    }

    static boolean hasStepFailures(TestExecutionContext context) {
        return context.getTestStepExecutionHistory().stream().map(TestStepResult::getExecutionStatus)
                .anyMatch(s -> s != SUCCESS);
    }

    @NotNull
    static UiTestExecutionResult buildErrorResult(String testCaseName, TestExecutionContext context,
                                                  TestExecutionResult.TestExecutionStatus status,
                                                  Instant testExecutionStartTimestamp, String errorMessage,
                                                  BufferedImage screenshot, SystemInfo systemInfo,
                                                  String videoPath, List<String> logs) {
        LOG.error(errorMessage);
        return new UiTestExecutionResult(testCaseName, status,
                context.getPreconditionExecutionHistory(), context.getTestStepExecutionHistory(),
                screenshot, systemInfo, videoPath, logs, testExecutionStartTimestamp, now(), errorMessage);
    }
}
