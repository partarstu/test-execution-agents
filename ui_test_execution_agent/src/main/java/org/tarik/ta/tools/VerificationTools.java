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
package org.tarik.ta.tools;

import dev.langchain4j.service.Result;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.BaseUiAgent;
import org.tarik.ta.agents.UiPreconditionVerificationAgent;
import org.tarik.ta.agents.UiTestStepVerificationAgent;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.model.VisualState;

import java.time.Instant;
import java.util.function.Supplier;

import static java.time.Instant.now;
import static org.tarik.ta.core.utils.CommonUtils.getDurationInMillis;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.utils.ImageUtils.singleImageContent;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static java.util.Objects.requireNonNull;

@Singleton
public class VerificationTools {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationTools.class);

    private final BudgetManager budgetManager;
    private final AgentConfig agentConfig;

    @Inject
    public VerificationTools(BudgetManager budgetManager, AgentConfig agentConfig) {
        this.budgetManager = requireNonNull(budgetManager, "budgetManager");
        this.agentConfig = requireNonNull(agentConfig, "agentConfig");
    }

    public VerificationExecutionResult verifyTestStep(String verificationDescription, String actionDescription,
                                                             String actionTestData, UiTestExecutionContext context,
                                                             UiTestStepVerificationAgent verificationAgent) {

        LOG.info("Starting the retriable verification that: '{}'", verificationDescription);
        Supplier<Result<?>> operation = () -> {
            var screenshot = captureScreen();
            context.setVisualState(new VisualState(screenshot));
            return verificationAgent.verify(verificationDescription, actionDescription, actionTestData,
                    context.getSharedData().toString(), singleImageContent(screenshot));
        };
        return executeVerificationWithRetry(verificationAgent, operation, "Verification");
    }

    public VerificationExecutionResult verifyPrecondition(
            String preconditionDescription,
            UiTestExecutionContext context,
            UiPreconditionVerificationAgent preconditionVerificationAgent,
            String relevantData) {

        LOG.info("Verifying if precondition was successfully executed: '{}'", preconditionDescription);
        Supplier<Result<?>> operation = () -> {
            var screenshot = captureScreen();
            context.setVisualState(new VisualState(screenshot));
            var message = buildPreconditionVerificationMessage(preconditionDescription, context.getSharedData().toString(), relevantData);
            return preconditionVerificationAgent.verify(message, singleImageContent(screenshot));
        };
        return executeVerificationWithRetry(preconditionVerificationAgent, operation, "Precondition verification");
    }

    private String buildPreconditionVerificationMessage(String preconditionDescription, String sharedData, String relevantData) {
        var relevantDataSection = relevantData.isBlank() ? ""
                : "\nRelevant data for this precondition: %s\n".formatted(relevantData);
        return """
                The test case precondition is: %s.
                %s
                Test context execution data: %s
                """.formatted(preconditionDescription, relevantDataSection, sharedData);
    }

    private VerificationExecutionResult executeVerificationWithRetry(
            BaseUiAgent<VerificationExecutionResult> agent,
            Supplier<Result<?>> operation,
            String label) {

        RetryPolicy retryPolicy = agentConfig.getVerificationRetryPolicy();
        int attempts = 0;
        Instant start = now();

        UiOperationExecutionResult<VerificationExecutionResult> lastResult = null;
        do {
            LOG.info("Attempt: {}", attempts + 1);
            try {
                lastResult = (UiOperationExecutionResult<VerificationExecutionResult>) agent
                        .executeAndGetResult(operation);
                attempts++;
                budgetManager.resetToolCallUsage();

                if (!lastResult.isSuccess()) {
                    return new VerificationExecutionResult(false, lastResult.getMessage());
                }

                if (lastResult.getResultPayload() != null && lastResult.getResultPayload().success()) {
                    LOG.info("{} complete within {} millis.", label, getDurationInMillis(start));
                    return lastResult.getResultPayload();
                }

                sleepMillis(retryPolicy.delayMillis());
            } catch (Exception e) {
                LOG.error("Unexpected error during {}", label, e);
                if (getDurationInMillis(start) > retryPolicy.timeoutMillis()) {
                    return new VerificationExecutionResult(false,
                            "Error during %s: %s".formatted(label, e.getMessage()));
                }
                sleepMillis(retryPolicy.delayMillis());
            }
        } while (getDurationInMillis(start) < retryPolicy.timeoutMillis());

        LOG.warn("{} timed out after {} attempts. Returning the latest result.", label, attempts);
        if (lastResult != null && lastResult.getResultPayload() != null) {
            return lastResult.getResultPayload();
        }
        return new VerificationExecutionResult(false, "%s timed out.".formatted(label));
    }
}
