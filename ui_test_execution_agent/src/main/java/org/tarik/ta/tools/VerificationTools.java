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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.ImageVerificationAgent;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.model.VisualState;

import static java.lang.System.currentTimeMillis;
import static org.tarik.ta.core.manager.BudgetManager.resetToolCallUsage;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.utils.ImageUtils.singleImageContent;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

public class VerificationTools extends UiAbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationTools.class);
    private final ImageVerificationAgent imageVerificationAgent;
    private final UiTestExecutionContext context;
    private final RetryPolicy retryPolicy = AgentConfig.getVerificationRetryPolicy();

    public VerificationTools(UiTestExecutionContext context, ImageVerificationAgent imageVerificationAgent) {
        super();
        this.context = context;
        this.imageVerificationAgent = imageVerificationAgent;
    }

    @Tool("Verifies that the test step produced the expected visual result. Returns true if verification passed, false otherwise.")
    public VerificationExecutionResult verifyTestStep(
            @P("The description of the expected visual result to verify") String verificationDescription,
            @P("The description of the action that was just performed") String actionDescription,
            @P("The test data used for the action") String actionTestData,
            @P("Any other relevant context data for the verification") String relatedTestContextData) {
        int attempts = 0;
        long startTime = currentTimeMillis();
        LOG.info("Starting the retriable verification that: '{}'", verificationDescription);

        UiOperationExecutionResult<VerificationExecutionResult> lastResult = null;
        do {
            LOG.info("Attempt: {}", attempts + 1);
            try {
                lastResult = (UiOperationExecutionResult<VerificationExecutionResult>) imageVerificationAgent.executeAndGetResult(() -> {
                    var screenshot = captureScreen();
                    context.setVisualState(new VisualState(screenshot));
                    return imageVerificationAgent.verify(verificationDescription, actionDescription, actionTestData,
                            context.getSharedData().toString(), singleImageContent(screenshot));
                });
                attempts++;
                resetToolCallUsage();

                if (!lastResult.isSuccess()) {
                    return new VerificationExecutionResult(false, lastResult.getMessage());
                }

                if (lastResult.getResultPayload() != null && lastResult.getResultPayload().success()) {
                    return lastResult.getResultPayload();
                }

                sleepMillis(retryPolicy.delayMillis());
            } catch (Exception e) {
                LOG.error("Unexpected error during verification", e);
                if (attempts > retryPolicy.maxRetries()) {
                    return new VerificationExecutionResult(false, "Error during verification: " + e.getMessage());
                }
            }
        } while ((currentTimeMillis() - startTime) < retryPolicy.timeoutMillis() || (attempts - 1) <= retryPolicy.maxRetries());

        LOG.warn("Verification timed out after {} attempts. Returning the latest result.", attempts);
        if (lastResult != null && lastResult.getResultPayload() != null) {
            return lastResult.getResultPayload();
        }
        return new VerificationExecutionResult(false, "Verification timed out.");
    }
}