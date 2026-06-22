/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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

import jakarta.inject.Provider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;

import java.time.Instant;
import java.util.List;

import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.*;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;

@ApiAgentRequestScope
public class ApiTestAgent {
    private static final Logger LOG = LoggerFactory.getLogger(ApiTestAgent.class);

    private final ApiTestAgentConfig apiTestAgentConfig;
    private final TestCaseExtractor testCaseExtractor;
    private final BudgetManager budgetManager;
    private final TestExecutionContext executionContext;
    private final LogCapture logCapture;
    private final Provider<ApiPreconditionActionAgent> preconditionActionAgentProvider;
    private final Provider<ApiTestStepActionAgent> testStepActionAgentProvider;

    public ApiTestAgent(ApiTestAgentConfig apiTestAgentConfig, TestCaseExtractor testCaseExtractor, BudgetManager budgetManager,
                        TestExecutionContext executionContext, LogCapture logCapture,
                        Provider<ApiPreconditionActionAgent> preconditionActionAgentProvider,
                        Provider<ApiTestStepActionAgent> testStepActionAgentProvider) {
        this.apiTestAgentConfig = apiTestAgentConfig;
        this.testCaseExtractor = testCaseExtractor;
        this.budgetManager = budgetManager;
        this.executionContext = executionContext;
        this.logCapture = logCapture;
        this.preconditionActionAgentProvider = preconditionActionAgentProvider;
        this.testStepActionAgentProvider = testStepActionAgentProvider;
    }

    public TestExecutionResult executeTestCase(String receivedMessage) {
        budgetManager.reset();
        budgetManager.activateTimeBudget();
        TestCase testCase = testCaseExtractor.extractTestCase(receivedMessage).orElse(null);
        if (testCase == null) {
            return new TestExecutionResult("Unknown", ERROR, List.of(), List.of(), now(), now(),
                    "Could not extract test case", null, List.of());
        }

        LOG.info("Starting execution of the API test case '{}'", testCase.name());
        logCapture.start();

        try {
            var testExecutionStartTimestamp = now();

            if (testCase.preconditions() != null && !testCase.preconditions().isEmpty()) {
                executePreconditions(testCase, executionContext, preconditionActionAgentProvider.get());
                if (hasPreconditionFailures(executionContext)) {
                    var failedPrecondition = executionContext.getPreconditionExecutionHistory().getLast();
                    return getFailedTestExecutionResult(testCase.name(), executionContext, testExecutionStartTimestamp,
                            failedPrecondition.getErrorMessage(), logCapture.getLogs());
                }
            }

            executeTestSteps(testCase, executionContext, testStepActionAgentProvider.get());
            if (hasStepFailures(executionContext)) {
                var lastStep = executionContext.getTestStepExecutionHistory().getLast();
                if (lastStep.getExecutionStatus() == FAILURE) {
                    return getFailedTestExecutionResult(testCase.name(), executionContext, testExecutionStartTimestamp,
                            lastStep.getErrorMessage(),
                            logCapture.getLogs());
                } else {
                    return getTestExecutionResultWithError(testCase.name(), executionContext, testExecutionStartTimestamp,
                            lastStep.getErrorMessage(),
                            logCapture.getLogs());
                }
            }
            return new TestExecutionResult(testCase.name(), PASSED, executionContext.getPreconditionExecutionHistory(),
                    executionContext.getTestStepExecutionHistory(), testExecutionStartTimestamp, now(), null,
                    null, logCapture.getLogs());
        } finally {
            LOG.info("Finished execution of the test case '{}'", testCase.name());
            logCapture.stop();
        }
    }

    private void executePreconditions(TestCase testCase, TestExecutionContext executionContext,
                                      ApiPreconditionActionAgent preconditionActionAgent) {
        List<String> preconditions = testCase.preconditions();
        if (preconditions != null && !preconditions.isEmpty()) {
            LOG.info("Executing and verifying preconditions for test case: {}", testCase.name());
            for (String precondition : preconditions) {
                var executionStartTimestamp = now();
                executionContext.getEventEmitter().emitPreconditionStarted(precondition);
                LOG.info("Executing precondition: {}", precondition);
                var executionResult = preconditionActionAgent.executeWithRetry(
                        () -> preconditionActionAgent.execute(precondition, executionContext.getSharedData().toString()),
                        r -> r == null || !r.success(), apiTestAgentConfig.getActionRetryPolicy());
                budgetManager.resetToolCallUsage();

                if (!executionResult.isSuccess()) {
                    var errorMessage = "Failure while executing precondition '%s'. Root cause: %s".formatted(
                            precondition, executionResult.getMessage());
                    executionContext.addPreconditionResult(
                            new PreconditionResult(precondition, false, errorMessage, executionStartTimestamp,
                                    now()));
                    return;
                }

                var verificationResult = executionResult.getResultPayload();
                if (verificationResult == null) {
                    var errorMessage = "Precondition execution failed. Got no result from the model.";
                    executionContext.addPreconditionResult(
                            new PreconditionResult(precondition, false, errorMessage,
                                    executionStartTimestamp, now()));
                    return;
                }
                if (!verificationResult.success()) {
                    var errorMessage = "Precondition verification failed. %s"
                            .formatted(verificationResult.message());
                    executionContext.addPreconditionResult(
                            new PreconditionResult(precondition, false, errorMessage,
                                    executionStartTimestamp, now()));
                    return;
                }
                executionContext.addPreconditionResult(new PreconditionResult(precondition, true, null,
                        executionStartTimestamp, now()));
                LOG.info("Precondition '{}' is met.", precondition);
            }
            LOG.info("All preconditions are met for test case: {}", testCase.name());
        }
    }

    private void executeTestSteps(TestCase testCase, TestExecutionContext executionContext, ApiTestStepActionAgent testStepActionAgent) {
        for (TestStep testStep : testCase.testSteps()) {
            var actionInstruction = testStep.stepDescription();
            var testData = ofNullable(testStep.testData()).map(Object::toString).orElse("");
            var verificationInstruction = testStep.expectedResults();

            try {
                var executionStartTimestamp = now();
                executionContext.getEventEmitter().emitStepStarted(testStep);
                LOG.info("Executing test step: {}", actionInstruction);
                var expectedResults = isNotBlank(verificationInstruction) ? verificationInstruction : "";

                var executionResult = testStepActionAgent.executeWithRetry(
                        () -> testStepActionAgent.execute(actionInstruction, expectedResults, testData,
                                executionContext.getSharedData().toString()),
                        result -> result == null || !result.success(), apiTestAgentConfig.getActionRetryPolicy());
                budgetManager.resetToolCallUsage();

                if (!executionResult.isSuccess()) {
                    var errorMessage = "Error while executing test step '%s'. Root cause: %s"
                            .formatted(actionInstruction, executionResult.getMessage());
                    addFailedTestStep(executionContext, testStep, errorMessage, null, executionStartTimestamp, now(),
                            TestStepResultStatus.ERROR);
                    return;
                }
                LOG.info("Test step executed successfully.");

                var verificationResult = executionResult.getResultPayload();
                if (verificationResult != null && !verificationResult.success()) {
                    var failureDetails = isNotBlank(verificationResult.message()) ? verificationResult.message() : "No failure details provided";
                    var errorMessage = "Verification failed. %s".formatted(failureDetails);
                    addFailedTestStep(executionContext, testStep, errorMessage, failureDetails, executionStartTimestamp,
                            now(), FAILURE);
                    return;
                }
                LOG.info("Verification passed.");
                LOG.info("Test step execution and verification complete.");
                var message = verificationResult != null ? verificationResult.message() : null;
                var actualResult = isNotBlank(message) ? message : "Execution successful";
                executionContext.addStepResult(new TestStepResult(testStep, SUCCESS, null, actualResult, executionStartTimestamp, now()));
            } catch (Exception e) {
                LOG.error("Unexpected error while executing the test step: '{}'", testStep.stepDescription(), e);
                addFailedTestStep(executionContext, testStep, e.getMessage(), null, now(), now(), TestStepResultStatus.ERROR);
                return;
            }
        }
    }


    private boolean hasPreconditionFailures(TestExecutionContext context) {
        return !context.getPreconditionExecutionHistory().stream().allMatch(PreconditionResult::isSuccess);
    }

    private boolean hasStepFailures(TestExecutionContext context) {
        return context.getTestStepExecutionHistory().stream().map(TestStepResult::getExecutionStatus)
                .anyMatch(s -> s != SUCCESS);
    }

    @NotNull
    private TestExecutionResult getFailedTestExecutionResult(String testCaseName, TestExecutionContext context,
                                                             Instant testExecutionStartTimestamp, String errorMessage, List<String> logs) {
        LOG.error(errorMessage);
        return new TestExecutionResult(testCaseName, FAILED, context.getPreconditionExecutionHistory(),
                context.getTestStepExecutionHistory(), testExecutionStartTimestamp, now(), errorMessage, null, logs);
    }

    @NotNull
    private TestExecutionResult getTestExecutionResultWithError(String testCaseName, TestExecutionContext context,
                                                                Instant testExecutionStartTimestamp, String errorMessage,
                                                                List<String> logs) {
        LOG.error(errorMessage);
        return new TestExecutionResult(testCaseName, ERROR, context.getPreconditionExecutionHistory(),
                context.getTestStepExecutionHistory(), testExecutionStartTimestamp, now(), errorMessage, null, logs);
    }

    private void addFailedTestStep(TestExecutionContext context, TestStep testStep, String errorMessage,
                                   String actualResult,
                                   Instant executionStartTimestamp, Instant executionEndTimestamp, TestStepResultStatus status) {
        context.addStepResult(
                new TestStepResult(testStep, status, errorMessage, actualResult, executionStartTimestamp, executionEndTimestamp));
    }

}
