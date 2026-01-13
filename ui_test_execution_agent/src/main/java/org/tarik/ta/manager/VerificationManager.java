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
package org.tarik.ta.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.UiTestStepVerificationAgent;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.model.VisualState;

import java.util.Optional;
import java.util.concurrent.*;

import static java.lang.System.currentTimeMillis;
import static java.util.Optional.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.manager.BudgetManager.resetToolCallUsage;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.utils.ImageUtils.singleImageContent;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

public class VerificationManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationManager.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final static RetryPolicy retryPolicy = AgentConfig.getVerificationRetryPolicy();
    private Future<UiOperationExecutionResult<VerificationExecutionResult>> currentVerification;
    private volatile UiOperationExecutionResult<VerificationExecutionResult> lastResult;

    public void submitVerification(UiTestStepVerificationAgent testStepVerificationAgent,
                                   UiTestExecutionContext context,
                                   String verificationInstruction,
                                   String actionInstruction,
                                   String testDataString) {
        lastResult = null;
        currentVerification = CompletableFuture.supplyAsync(() -> {
            int attempts = 0;
            long startTime = currentTimeMillis();
            LOG.info("Starting the retriable verification that: '{}'", verificationInstruction);
            do {
                LOG.info("Attempt: {}", attempts + 1);
                try {
                    lastResult =
                            (UiOperationExecutionResult<VerificationExecutionResult>) testStepVerificationAgent.executeAndGetResult(() -> {
                                var screenshot = captureScreen();
                                context.setVisualState(new VisualState(screenshot));
                                return testStepVerificationAgent.verify(verificationInstruction, actionInstruction, testDataString,
                                        context.getSharedData().toString(), singleImageContent(screenshot));
                            });
                    attempts++;
                    resetToolCallUsage();

                    if (!lastResult.isSuccess() || (lastResult.getResultPayload()!=null && lastResult.getResultPayload().success())) {
                        return lastResult;
                    }

                    sleepMillis(retryPolicy.delayMillis());
                } catch (Exception e) {
                    LOG.error("Unexpected error during verification", e);
                    lastResult = new UiOperationExecutionResult<>(ERROR, e.getMessage(),
                            new VerificationExecutionResult(false, e.getMessage()), captureScreen());
                    return lastResult;
                }
            } while ((currentTimeMillis() - startTime) < retryPolicy.timeoutMillis() || (attempts - 1) <= retryPolicy.maxRetries());
            LOG.warn("Verification timed out after {} attempts and {}ms. Returning the latest recorded verification result",
                    attempts, currentTimeMillis() - startTime);
            return lastResult;
        }, executor);
        LOG.info("Verification submitted.");
    }

    public Optional<VerificationExecutionResult> waitForCurrentVerificationToFinish() {
        if (currentVerification == null) {
            LOG.info("No verification is currently running, nothing to wait for");
            return empty();
        }

        LOG.info("Waiting for verification to finish (timeout: {} ms)...", retryPolicy.timeoutMillis());
        UiOperationExecutionResult<VerificationExecutionResult> result;
        try {
            result = currentVerification.get(retryPolicy.timeoutMillis(), MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn("Timeout while waiting for verification to finish. Cancelling verification task and returning last known result.");
            currentVerification.cancel(true);
            result = lastResult;
        } catch (Exception e) {
            LOG.error("Verification task failed unexpectedly", e);
            result = lastResult;
            if (result == null) {
                return of(new VerificationExecutionResult(false, "Verification error: " + e.getMessage()));
            }
        }

        if (result == null) {
            return empty();
        } else if (!result.isSuccess()) {
            return of(new VerificationExecutionResult(false, result.getMessage()));
        } else {
            return ofNullable(result.getResultPayload());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, SECONDS)) {
                LOG.warn("Executor did not terminate in time");
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}