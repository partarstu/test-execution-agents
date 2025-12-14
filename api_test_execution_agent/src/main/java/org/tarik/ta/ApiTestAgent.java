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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiPreconditionVerificationAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.agents.ApiTestStepVerificationAgent;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryState;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.DefaultErrorHandler;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.tools.ApiAssertionTools;
import org.tarik.ta.core.tools.TestContextDataTools;
import org.tarik.ta.tools.ApiRequestTools;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.service.AiServices.builder;
import static java.time.Instant.now;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.core.AgentConfig.*;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;
import static org.tarik.ta.core.manager.BudgetManager.resetToolCallUsage;
import static org.tarik.ta.core.model.ModelFactory.getModel;
import static org.tarik.ta.core.utils.CoreUtils.isBlank;
import static org.tarik.ta.core.utils.CoreUtils.isNotBlank;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

public class ApiTestAgent {
        private static final Logger LOG = LoggerFactory.getLogger(ApiTestAgent.class);

        public static TestExecutionResult executeTestCase(String receivedMessage) {
                TestCase testCase = extractTestCase(receivedMessage).orElse(null);
                if (testCase == null) {
                        return new TestExecutionResult("Unknown", TestExecutionResult.TestExecutionStatus.ERROR,
                                        List.of(), List.of(), now(), now(),
                                        "Could not extract test case");
                }

                LOG.info("Starting execution of the API test case '{}'", testCase.name());
                BudgetManager.reset();

                try {
                        var testExecutionStartTimestamp = now();
                        var apiContext = ApiContext.createFromConfig();
                        var executionContext = new TestExecutionContext(testCase);
                        var requestTools = new ApiRequestTools(apiContext, executionContext);
                        var assertionTools = new ApiAssertionTools(apiContext);
                        var dataTools = new TestContextDataTools(executionContext);

                        if (testCase.preconditions() != null && !testCase.preconditions().isEmpty()) {
                                executePreconditions(executionContext, apiContext, requestTools, assertionTools,
                                                dataTools);
                                if (hasPreconditionFailures(executionContext)) {
                                        var failedPrecondition = executionContext.getPreconditionExecutionHistory()
                                                        .getLast();
                                        return getFailedTestExecutionResult(executionContext,
                                                        testExecutionStartTimestamp, failedPrecondition.errorMessage());
                                }
                        }

                        executeTestSteps(executionContext, apiContext, requestTools, assertionTools, dataTools);
                        if (hasStepFailures(executionContext)) {
                                var lastStep = executionContext.getTestStepExecutionHistory().getLast();
                                if (lastStep.executionStatus() == FAILURE) {
                                        return getFailedTestExecutionResult(executionContext,
                                                        testExecutionStartTimestamp, lastStep.errorMessage());
                                } else {
                                        return getTestExecutionResultWithError(executionContext,
                                                        testExecutionStartTimestamp, lastStep.errorMessage());
                                }
                        } else {
                                return new TestExecutionResult(testCase.name(), PASSED,
                                                executionContext.getPreconditionExecutionHistory(),
                                                executionContext.getTestStepExecutionHistory(),
                                                testExecutionStartTimestamp, now(), null);
                        }
                } finally {
                        LOG.info("Finished execution of the test case '{}'", testCase.name());
                }
        }

        private static void executePreconditions(TestExecutionContext executionContext, ApiContext apiContext,
                        ApiRequestTools requestTools, ApiAssertionTools assertionTools,
                        TestContextDataTools dataTools) {
                List<String> preconditions = executionContext.getTestCase().preconditions();
                if (preconditions != null && !preconditions.isEmpty()) {
                        var preconditionActionAgent = getApiPreconditionActionAgent(requestTools, assertionTools,
                                        dataTools, new RetryState());
                        var preconditionVerificationAgent = getApiPreconditionVerificationAgent(apiContext,
                                        new RetryState());
                        LOG.info("Executing and verifying preconditions for test case: {}",
                                        executionContext.getTestCase().name());
                        for (String precondition : preconditions) {
                                var executionStartTimestamp = now();
                                LOG.info("Executing precondition: {}", precondition);
                                var preconditionExecutionResult = preconditionActionAgent
                                                .executeWithRetry(() -> preconditionActionAgent.execute(precondition,
                                                                executionContext.getSharedData().toString(),
                                                                apiContext.toString()));
                                resetToolCallUsage();

                                if (!preconditionExecutionResult.isSuccess()) {
                                        var errorMessage = "Failure while executing precondition '%s'. Root cause: %s"
                                                        .formatted(precondition,
                                                                        preconditionExecutionResult.getMessage());
                                        executionContext.addPreconditionResult(
                                                        new PreconditionResult(precondition, false, errorMessage,
                                                                        executionStartTimestamp, now()));
                                        return;
                                }
                                LOG.info("Precondition execution complete.");

                                var lastResponseStatus = apiContext.getLastResponse()
                                                .map(r -> String.valueOf(r.getStatusCode()))
                                                .orElse("N/A");
                                var lastResponseBody = apiContext.getLastResponse()
                                                .map(r -> r.getBody().asString())
                                                .orElse("");
                                var verificationExecutionResult = preconditionVerificationAgent
                                                .executeWithRetry(
                                                                () -> preconditionVerificationAgent.verify(precondition,
                                                                                preconditionExecutionResult
                                                                                                .getMessage(),
                                                                                lastResponseStatus, lastResponseBody,
                                                                                executionContext.getSharedData()
                                                                                                .toString(),
                                                                                apiContext.getVariables().toString()),
                                                                r -> r == null || !r.success());
                                resetToolCallUsage();

                                if (!verificationExecutionResult.isSuccess()) {
                                        var errorMessage = "Error while verifying precondition '%s'. Root cause: %s"
                                                        .formatted(precondition,
                                                                        verificationExecutionResult.getMessage());
                                        executionContext.addPreconditionResult(
                                                        new PreconditionResult(precondition, false, errorMessage,
                                                                        executionStartTimestamp, now()));
                                        return;
                                }

                                var verificationResult = verificationExecutionResult.getResultPayload();
                                if (verificationResult == null) {
                                        var errorMessage = "Precondition verification failed. Got no verification result from the model.";
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

        private static void executeTestSteps(TestExecutionContext executionContext, ApiContext apiContext,
                        ApiRequestTools requestTools,
                        ApiAssertionTools assertionTools, TestContextDataTools dataTools) {
                var testStepActionAgent = getApiTestStepActionAgent(requestTools, assertionTools, dataTools,
                                new RetryState());
                var testStepVerificationAgent = getApiTestStepVerificationAgent(apiContext, new RetryState());
                for (TestStep testStep : executionContext.getTestCase().testSteps()) {
                        var actionInstruction = testStep.stepDescription();
                        var testData = ofNullable(testStep.testData()).map(Object::toString).orElse("");
                        var verificationInstruction = testStep.expectedResults();

                        try {
                                var executionStartTimestamp = now();
                                LOG.info("Executing test step: {}", actionInstruction);

                                var actionResult = testStepActionAgent.executeWithRetry(() -> {
                                        testStepActionAgent.execute(actionInstruction, testData,
                                                        executionContext.getSharedData().toString(),
                                                        !isUnattendedMode());
                                        return null;
                                });
                                resetToolCallUsage();

                                if (!actionResult.isSuccess()) {
                                        var errorMessage = "Error while executing action '%s'. Root cause: %s"
                                                        .formatted(actionInstruction, actionResult.getMessage());
                                        addFailedTestStep(executionContext, testStep, errorMessage, null,
                                                        executionStartTimestamp, now(),
                                                        TestStepResultStatus.ERROR);
                                        return;
                                }
                                LOG.info("Action execution complete.");

                                if (isNotBlank(verificationInstruction)) {
                                        var lastResponseStatus = apiContext.getLastResponse()
                                                        .map(r -> String.valueOf(r.getStatusCode()))
                                                        .orElse("N/A");
                                        var lastResponseBody = apiContext.getLastResponse()
                                                        .map(r -> r.getBody().asString())
                                                        .orElse("");
                                        var lastResponseHeaders = apiContext.getLastResponse()
                                                        .map(r -> r.getHeaders().toString())
                                                        .orElse("");

                                        LOG.info("Executing verification of: '{}'", verificationInstruction);
                                        var verificationExecutionResult = testStepVerificationAgent.executeWithRetry(
                                                        () -> testStepVerificationAgent.verify(verificationInstruction,
                                                                        actionInstruction, testData, lastResponseStatus,
                                                                        lastResponseBody, lastResponseHeaders,
                                                                        executionContext.getSharedData().toString(),
                                                                        apiContext.getVariables().toString()),
                                                        result -> result == null || !result.success());
                                        resetToolCallUsage();

                                        if (!verificationExecutionResult.isSuccess()) {
                                                var errorMessage = "Failure while verifying test step '%s'. Root cause: %s"
                                                                .formatted(actionInstruction,
                                                                                verificationExecutionResult
                                                                                                .getMessage());
                                                addFailedTestStep(executionContext, testStep, errorMessage, null,
                                                                executionStartTimestamp, now(),
                                                                TestStepResultStatus.ERROR);
                                                return;
                                        }

                                        var verificationResult = verificationExecutionResult.getResultPayload();
                                        if (verificationResult != null && !verificationResult.success()) {
                                                var errorMessage = "Verification failed. %s"
                                                                .formatted(verificationResult.message());
                                                addFailedTestStep(executionContext, testStep, errorMessage,
                                                                verificationResult.message(), executionStartTimestamp,
                                                                now(), FAILURE);
                                                return;
                                        }
                                        LOG.info("Verification execution complete.");
                                        var actualResult = verificationResult != null ? verificationResult.message()
                                                        : "Verification successful";
                                        executionContext.addStepResult(
                                                        new TestStepResult(testStep, SUCCESS, null, actualResult,
                                                                        executionStartTimestamp, now()));
                                } else {
                                        executionContext.addStepResult(
                                                        new TestStepResult(testStep, SUCCESS, null,
                                                                        "No verification required",
                                                                        executionStartTimestamp, now()));
                                }
                        } catch (Exception e) {
                                LOG.error("Unexpected error while executing the test step: '{}'",
                                                testStep.stepDescription(), e);
                                addFailedTestStep(executionContext, testStep, e.getMessage(), null, now(), now(),
                                                TestStepResultStatus.ERROR);
                                return;
                        }
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
                                LOG.warn("Model could not extract a valid TestCase from the provided user message, original message: {}",
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
                return extractedTestCase == null
                                || isBlank(extractedTestCase.name())
                                || extractedTestCase.testSteps() == null
                                || extractedTestCase.testSteps().isEmpty()
                                || extractedTestCase.testSteps().stream()
                                                .anyMatch(step -> isBlank(step.stepDescription()));
        }

        private static boolean hasPreconditionFailures(TestExecutionContext context) {
                return !context.getPreconditionExecutionHistory().stream().allMatch(PreconditionResult::success);
        }

        private static boolean hasStepFailures(TestExecutionContext context) {
                return context.getTestStepExecutionHistory().stream().map(TestStepResult::executionStatus)
                                .anyMatch(s -> s != SUCCESS);
        }

        @NotNull
        private static TestExecutionResult getFailedTestExecutionResult(TestExecutionContext context,
                        Instant testExecutionStartTimestamp, String errorMessage) {
                LOG.error(errorMessage);
                return new TestExecutionResult(context.getTestCase().name(), FAILED,
                                context.getPreconditionExecutionHistory(),
                                context.getTestStepExecutionHistory(), testExecutionStartTimestamp, now(),
                                errorMessage);
        }

        @NotNull
        private static TestExecutionResult getTestExecutionResultWithError(TestExecutionContext context,
                        Instant testExecutionStartTimestamp, String errorMessage) {
                LOG.error(errorMessage);
                return new TestExecutionResult(context.getTestCase().name(),
                                TestExecutionResult.TestExecutionStatus.ERROR,
                                context.getPreconditionExecutionHistory(),
                                context.getTestStepExecutionHistory(), testExecutionStartTimestamp, now(),
                                errorMessage);
        }

        private static void addFailedTestStep(TestExecutionContext context, TestStep testStep, String errorMessage,
                        String actualResult,
                        Instant executionStartTimestamp, Instant executionEndTimestamp, TestStepResultStatus status) {
                context.addStepResult(
                                new TestStepResult(testStep, status, errorMessage, actualResult,
                                                executionStartTimestamp, executionEndTimestamp));
        }

        private static TestCaseExtractionAgent getTestCaseExtractionAgent() {
                var model = getModel(getTestCaseExtractionAgentModelName(), getTestCaseExtractionAgentModelProvider());
                var prompt = loadSystemPrompt("test_case_extractor", getTestCaseExtractionAgentPromptVersion(),
                                "test_case_extraction_prompt.txt");
                return builder(TestCaseExtractionAgent.class)
                                .chatModel(model.chatModel())
                                .systemMessageProvider(_ -> prompt)
                                .tools(new TestCase("", List.of(), List.of()))
                                .build();
        }

        private static ApiTestStepActionAgent getApiTestStepActionAgent(ApiRequestTools requestTools,
                        ApiAssertionTools assertionTools,
                        TestContextDataTools dataTools, RetryState retryState) {
                var model = getModel(getTestStepActionAgentModelName(), getTestStepActionAgentModelProvider());
                var prompt = loadSystemPrompt("api_test", getTestStepActionAgentPromptVersion(),
                                "api_test_step_agent_prompt.txt");

                return builder(ApiTestStepActionAgent.class)
                                .chatModel(model.chatModel())
                                .systemMessageProvider(_ -> prompt)
                                .tools(requestTools, assertionTools, dataTools, new EmptyExecutionResult())
                                .toolExecutionErrorHandler(new DefaultErrorHandler(ApiTestStepActionAgent.RETRY_POLICY,
                                                retryState))
                                .build();
        }

        private static ApiPreconditionActionAgent getApiPreconditionActionAgent(ApiRequestTools requestTools,
                        ApiAssertionTools assertionTools,
                        TestContextDataTools dataTools,
                        RetryState retryState) {
                var model = getModel(getPreconditionActionAgentModelName(), getPreconditionActionAgentModelProvider());
                var prompt = loadSystemPrompt("api_precondition_action", getPreconditionAgentPromptVersion(),
                                "api_precondition_action_prompt.txt");

                return builder(ApiPreconditionActionAgent.class)
                                .chatModel(model.chatModel())
                                .systemMessageProvider(_ -> prompt)
                                .tools(requestTools, assertionTools, dataTools, new EmptyExecutionResult())
                                .toolExecutionErrorHandler(new DefaultErrorHandler(
                                                ApiPreconditionActionAgent.RETRY_POLICY, retryState))
                                .build();
        }

        private static ApiPreconditionVerificationAgent getApiPreconditionVerificationAgent(ApiContext apiContext,
                        RetryState retryState) {
                var model = getModel(getPreconditionVerificationAgentModelName(),
                                getPreconditionVerificationAgentModelProvider());
                var prompt = loadSystemPrompt("api_precondition_verification",
                                getPreconditionVerificationAgentPromptVersion(),
                                "api_precondition_verification_prompt.txt");

                return builder(ApiPreconditionVerificationAgent.class)
                                .chatModel(model.chatModel())
                                .systemMessageProvider(_ -> prompt)
                                .tools(new ApiAssertionTools(apiContext), new VerificationExecutionResult(false, ""))
                                .toolExecutionErrorHandler(new DefaultErrorHandler(
                                                ApiPreconditionVerificationAgent.RETRY_POLICY, retryState))
                                .build();
        }

        private static ApiTestStepVerificationAgent getApiTestStepVerificationAgent(ApiContext apiContext,
                        RetryState retryState) {
                var model = getModel(getTestStepVerificationAgentModelName(),
                                getTestStepVerificationAgentModelProvider());
                var prompt = loadSystemPrompt("api_test_step_verification", getTestStepVerificationAgentPromptVersion(),
                                "api_test_step_verification_prompt.txt");

                return builder(ApiTestStepVerificationAgent.class)
                                .chatModel(model.chatModel())
                                .systemMessageProvider(_ -> prompt)
                                .tools(new ApiAssertionTools(apiContext), new VerificationExecutionResult(false, ""))
                                .toolExecutionErrorHandler(new DefaultErrorHandler(
                                                ApiTestStepVerificationAgent.RETRY_POLICY, retryState))
                                .build();
        }
}