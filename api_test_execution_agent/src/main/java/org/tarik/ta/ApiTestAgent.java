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

import io.avaje.inject.BeanScope;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.tools.ApiAssertionTools;
import org.tarik.ta.core.tools.TestContextDataTools;
import org.tarik.ta.tools.ApiRequestTools;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScopeModule;

import java.time.Instant;
import java.util.List;

import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.*;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;

@Singleton
public class ApiTestAgent {
    private static final Logger LOG = LoggerFactory.getLogger(ApiTestAgent.class);

    private final ModelFactory modelFactory;
    private final ApiTestAgentConfig apiTestAgentConfig;
    private final TestCaseExtractor testCaseExtractor;
    private final BudgetManager budgetManager;
    private final BeanScope appScope;

    public ApiTestAgent(ModelFactory modelFactory, ApiTestAgentConfig apiTestAgentConfig, TestCaseExtractor testCaseExtractor, BudgetManager budgetManager, BeanScope appScope) {
        this.modelFactory = modelFactory;
        this.apiTestAgentConfig = apiTestAgentConfig;
        this.testCaseExtractor = testCaseExtractor;
        this.budgetManager = budgetManager;
        this.appScope = appScope;
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

        try (BeanScope requestScope = BeanScope.builder()
                .parent(appScope)
                .modules(new BaseAgentRequestScopeModule(testCase), new ApiAgentRequestScopeModule())
                .build()) {
            
            var logCapture = requestScope.get(LogCapture.class);
            logCapture.start();
            
            try {
                var testExecutionStartTimestamp = now();
                var apiContext = requestScope.get(ApiContext.class);
                var executionContext = requestScope.get(TestExecutionContext.class);
                var requestTools = requestScope.get(ApiRequestTools.class);
                var assertionTools = requestScope.get(ApiAssertionTools.class);
                var dataTools = requestScope.get(TestContextDataTools.class);
                
                var preconditionActionAgent = requestScope.get(ApiPreconditionActionAgent.class);
                var testStepActionAgent = requestScope.get(ApiTestStepActionAgent.class);

                if (testCase.preconditions() != null && !testCase.preconditions().isEmpty()) {
                    executePreconditions(executionContext, preconditionActionAgent);
                    if (hasPreconditionFailures(executionContext)) {
                        var failedPrecondition = executionContext.getPreconditionExecutionHistory().getLast();
                        return getFailedTestExecutionResult(executionContext, testExecutionStartTimestamp,
                                failedPrecondition.getErrorMessage(), logCapture.getLogs());
                    }
                }

                executeTestSteps(executionContext, testStepActionAgent);
                if (hasStepFailures(executionContext)) {
                    var lastStep = executionContext.getTestStepExecutionHistory().getLast();
                    if (lastStep.getExecutionStatus() == FAILURE) {
                        return getFailedTestExecutionResult(executionContext, testExecutionStartTimestamp, lastStep.getErrorMessage(), logCapture.getLogs());
                    } else {
                        return getTestExecutionResultWithError(executionContext, testExecutionStartTimestamp, lastStep.getErrorMessage(), logCapture.getLogs());
                    }
                } else {
                    return new TestExecutionResult(testCase.name(), PASSED, executionContext.getPreconditionExecutionHistory(),
                            executionContext.getTestStepExecutionHistory(), testExecutionStartTimestamp, now(), null,
                            null, logCapture.getLogs());
                }
            } finally {
                LOG.info("Finished execution of the test case '{}'", testCase.name());
                logCapture.stop();
            }
        }
    }

    private void executePreconditions(TestExecutionContext executionContext, ApiPreconditionActionAgent preconditionActionAgent) {
        List<String> preconditions = executionContext.getTestCase().preconditions();
        if (preconditions != null && !preconditions.isEmpty()) {
            LOG.info("Executing and verifying preconditions for test case: {}", executionContext.getTestCase().name());
            for (String precondition : preconditions) {
                var executionStartTimestamp = now();
                LOG.info("Executing precondition: {}", precondition);
                var executionResult = preconditionActionAgent.executeWithRetry(
                        () -> preconditionActionAgent.execute(precondition, executionContext.getSharedData().toString()),
                        r -> r == null || !r.success());
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
            LOG.info("All preconditions are met for test case: {}", executionContext.getTestCase().name());
        }
    }

    private void executeTestSteps(TestExecutionContext executionContext, ApiTestStepActionAgent testStepActionAgent) {
        for (TestStep testStep : executionContext.getTestCase().testSteps()) {
            var actionInstruction = testStep.stepDescription();
            var testData = ofNullable(testStep.testData()).map(Object::toString).orElse("");
            var verificationInstruction = testStep.expectedResults();

            try {
                var executionStartTimestamp = now();
                LOG.info("Executing test step: {}", actionInstruction);
                var expectedResults = isNotBlank(verificationInstruction) ? verificationInstruction : "";

                var executionResult = testStepActionAgent.executeWithRetry(
                        () -> testStepActionAgent.execute(actionInstruction, expectedResults, testData,
                                executionContext.getSharedData().toString()),
                        result -> result == null || !result.success());
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
                    var errorMessage = "Verification failed. %s".formatted(verificationResult.message());
                    addFailedTestStep(executionContext, testStep, errorMessage, verificationResult.message(), executionStartTimestamp,
                            now(), FAILURE);
                    return;
                }
                LOG.info("Verification passed.");
                LOG.info("Test step execution and verification complete.");
                var actualResult = verificationResult != null ? verificationResult.message() : "Execution successful";
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
    private TestExecutionResult getFailedTestExecutionResult(TestExecutionContext context,
                                                                    Instant testExecutionStartTimestamp, String errorMessage, List<String> logs) {
        LOG.error(errorMessage);
        return new TestExecutionResult(context.getTestCase().name(), FAILED, context.getPreconditionExecutionHistory(),
                context.getTestStepExecutionHistory(), testExecutionStartTimestamp, now(), errorMessage, null, logs);
    }

    @NotNull
    private TestExecutionResult getTestExecutionResultWithError(TestExecutionContext context,
                                                                       Instant testExecutionStartTimestamp, String errorMessage, List<String> logs) {
        LOG.error(errorMessage);
        return new TestExecutionResult(context.getTestCase().name(), ERROR, context.getPreconditionExecutionHistory(),
                context.getTestStepExecutionHistory(), testExecutionStartTimestamp, now(), errorMessage, null, logs);
    }

    private void addFailedTestStep(TestExecutionContext context, TestStep testStep, String errorMessage,
                                          String actualResult,
                                          Instant executionStartTimestamp, Instant executionEndTimestamp, TestStepResultStatus status) {
        context.addStepResult(
                new TestStepResult(testStep, status, errorMessage, actualResult, executionStartTimestamp, executionEndTimestamp));
    }

}