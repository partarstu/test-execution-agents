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
package org.tarik.ta;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.*;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.core.dto.*;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.security.SecretRegistry;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.model.node.ExecutionRun;
import org.tarik.ta.knowledge_graph.service.ExecutionRunService;
import org.tarik.ta.knowledge_graph.service.ExecutionStateTracker;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.model.VisualStateHolder;
import org.tarik.ta.panel.OperatorPrompts;
import org.tarik.ta.panel.RunObserver;
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
    private final TestExecutionContext context;
    private final ScreenRecorder screenRecorder;
    private final LogCapture logCapture;
    private final ExecutionStateTracker stateTracker;
    private final VisualStateHolder visualState;
    private final RunObserver runObserver;
    private final OperatorPrompts operatorPrompts;
    private final ExecutionRunService executionRunService;

    public UiTestAgent(KnowledgeBasedExecutionOrchestrator knowledgeBasedExecutionOrchestrator,
                       KnowledgeService knowledgeService,
                       UiTestAgentConfig uiTestAgentConfig,
                       TestCaseExtractor testCaseExtractor,
                       BudgetManager budgetManager,
                       TestExecutionContext context,
                       ScreenRecorder screenRecorder,
                       LogCapture logCapture,
                       ExecutionStateTracker stateTracker,
                       VisualStateHolder visualState,
                       RunObserver runObserver,
                       OperatorPrompts operatorPrompts,
                       ExecutionRunService executionRunService) {
        this.knowledgeBasedExecutionOrchestrator = knowledgeBasedExecutionOrchestrator;
        this.knowledgeService = knowledgeService;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.testCaseExtractor = testCaseExtractor;
        this.budgetManager = budgetManager;
        this.context = context;
        this.screenRecorder = screenRecorder;
        this.logCapture = logCapture;
        this.stateTracker = stateTracker;
        this.visualState = visualState;
        this.runObserver = runObserver;
        this.operatorPrompts = operatorPrompts;
        this.executionRunService = executionRunService;
    }

    public TestExecutionResult executeTestCase(String receivedMessage) {
        var testExecutionStartTimestamp = now();
        SystemInfo systemInfo = null;

        try {
            budgetManager.reset();
            SecretRegistry.global().reset(uiTestAgentConfig.getSensitiveDataKeyPattern());
            if (uiTestAgentConfig.isFullyUnattended()) {
                budgetManager.activateTimeBudget();
                budgetManager.activateToolCallBudget();
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
            // Seeded before the first executed item so that the data table is available even when the operator starts
            // the run from a later step.
            context.seedFromDataTable(testCase.testData());
            // After seeding, so that the secrets of the data table are registered before the panel shows the test case
            runObserver.taskStarted(testCase);
            systemInfo = getSystemInfo();
            screenRecorder.beginScreenCapture();
            logCapture.start();

            executionRunService.start(testCase.name());
            try {
                var result = executeFromStartingStep(testCase, testExecutionStartTimestamp, systemInfo);
                executionRunService.finish(runStatusOf(result.getTestExecutionStatus()));
                return result;
            } catch (RuntimeException e) {
                executionRunService.finish(runStatusOf(ERROR));
                throw e;
            } finally {
                LOG.info("Finished execution of the test case '{}'", testCase.name());
            }
        } catch (Exception e) {
            LOG.error("Unexpected error during test case execution", e);
            if (systemInfo == null) {
                systemInfo = getSystemInfo();
            }
            // Whatever was captured before the failure is what makes the failure diagnosable, so the logs and the
            // recording are reported here exactly as they are on every other exit path. Both are empty/null when the
            // failure happened before capturing started.
            return new UiTestExecutionResult("Unknown Test Case", ERROR, List.of(), List.of(), captureScreen(),
                    systemInfo, screenRecorder.getCurrentRecordingPath(), logCapture.getLogs(),
                    testExecutionStartTimestamp, now(),
                    "Unexpected error during test case execution: " + e.getMessage());
        }
    }

    private UiTestExecutionResult executeFromStartingStep(TestCase testCase, Instant testExecutionStartTimestamp, SystemInfo systemInfo) {
        int startingStepIndex = 0;
        if (!uiTestAgentConfig.isFullyUnattended()) {
            startingStepIndex = operatorPrompts.selectStartingStep(testCase.testSteps());
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
    }

    // A cancelled run still returns a result, after the interrupt that cancelled it
    private static ExecutionRun.Status runStatusOf(TestExecutionResult.TestExecutionStatus status) {
        if (Thread.currentThread().isInterrupted()) {
            return ExecutionRun.Status.ABORTED;
        }
        return switch (status) {
            case PASSED -> ExecutionRun.Status.PASSED;
            case FAILED -> ExecutionRun.Status.FAILED;
            case ERROR -> ExecutionRun.Status.ERROR;
        };
    }

    /**
     * Runs the knowledge-based execution flow. The request scope has already
     * been created above.
     */
    private UiTestExecutionResult executeWithKnowledgeFlow(TestExecutionContext context, TestCase testCase,
                                                           int startingStepIndex, Instant testExecutionStartTimestamp,
                                                           SystemInfo systemInfo,
                                                           ScreenRecorder screenRecorder, LogCapture logCapture,
                                                           ExecutionStateTracker stateTracker) {
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
                            visualState.get().screenshot(), systemInfo, screenRecorder.getCurrentRecordingPath(),
                            logCapture.getLogs());
                }
            } else {
                return new UiTestExecutionResult(testCase.name(), PASSED, context.getPreconditionExecutionHistory(),
                        context.getTestStepExecutionHistory(), null, systemInfo, screenRecorder.getCurrentRecordingPath(),
                        logCapture.getLogs(), testExecutionStartTimestamp, now(), null);
            }
        } catch (Exception e) {
            LOG.error("Error during knowledge-based execution", e);
            return new UiTestExecutionResult(testCase.name(), ERROR, context.getPreconditionExecutionHistory(),
                    context.getTestStepExecutionHistory(), captureScreen(), systemInfo, screenRecorder.getCurrentRecordingPath(),
                    logCapture.getLogs(), testExecutionStartTimestamp, now(), "Knowledge-based execution error: " + e.getMessage());
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
