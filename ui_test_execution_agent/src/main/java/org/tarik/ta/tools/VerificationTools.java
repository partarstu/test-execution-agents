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
package org.tarik.ta.tools;

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.Result;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.BaseUiAgent;
import org.tarik.ta.agents.UiPreconditionVerificationAgent;
import org.tarik.ta.agents.UiTestStepVerificationAgent;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.model.VisualStateHolder;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static java.time.Instant.now;
import static org.tarik.ta.core.utils.CommonUtils.getDurationInMillis;
import static org.tarik.ta.core.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.core.utils.MarkdownSections.SECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.heading;
import static org.tarik.ta.core.utils.MarkdownSections.joinSections;
import static org.tarik.ta.core.utils.MarkdownSections.section;
import static org.tarik.ta.core.utils.MarkdownSections.userMessageContents;
import static org.tarik.ta.utils.UserMessageSections.ACTION_DATA_LABEL;
import static org.tarik.ta.core.model.TestExecutionContext.EXECUTION_CONTEXT_DATA_LABEL;
import static org.tarik.ta.core.model.TestExecutionContext.PRECONDITION_DATA_LABEL;
import static org.tarik.ta.utils.UserMessageSections.PRECONDITION_LABEL;
import static org.tarik.ta.utils.UserMessageSections.SCREENSHOT_LABEL;
import static java.util.Objects.requireNonNull;

@Singleton
public class VerificationTools {
    private static final Logger LOG = LoggerFactory.getLogger(VerificationTools.class);
    private static final String BEFORE_ACTION_SCREENSHOT_LABEL = "Screenshot before the action";
    private static final String AFTER_ACTION_SCREENSHOT_LABEL = "Screenshot after the action";
    private static final String EXPECTED_RESULTS_LABEL = "Expected results";
    private static final String EXECUTED_ACTION_LABEL = "Action executed before this verification";

    private final BudgetManager budgetManager;
    private final UiTestAgentConfig uiTestAgentConfig;

    @Inject
    public VerificationTools(BudgetManager budgetManager, UiTestAgentConfig uiTestAgentConfig) {
        this.budgetManager = requireNonNull(budgetManager, "budgetManager");
        this.uiTestAgentConfig = requireNonNull(uiTestAgentConfig, "uiTestAgentConfig");
    }

    /**
     * Verifies a test step, where {@code stepHierarchy} is the rendered composite chain plus the step's notes (see {@code
     * AncestorChainRenderer}), empty for a step executed on its own.
     */
    public VerificationExecutionResult verifyTestStep(String verificationDescription, String actionDescription,
                                                      String actionTestData, TestExecutionContext context,
                                                      VisualStateHolder visualState, BufferedImage beforeActionScreenshot,
                                                      UiTestStepVerificationAgent verificationAgent, String stepHierarchy) {

        LOG.info("Starting the retriable verification that: '{}'", verificationDescription);
        Supplier<Result<?>> operation = () -> {
            var screenshot = captureScreen();
            visualState.set(new VisualState(screenshot));
            var message = buildTestStepVerificationMessage(verificationDescription, actionDescription, actionTestData,
                    context.renderSharedData(), stepHierarchy);
            var imageContentCreator = verificationAgent.getImageContentCreator(uiTestAgentConfig);
            return verificationAgent.verify(userMessageContents(message), List.of(
                    TextContent.from(heading(SECTION_LEVEL, BEFORE_ACTION_SCREENSHOT_LABEL)),
                    imageContentCreator.createImageContent(beforeActionScreenshot),
                    TextContent.from(heading(SECTION_LEVEL, AFTER_ACTION_SCREENSHOT_LABEL)),
                    imageContentCreator.createImageContent(screenshot)));
        };
        return executeVerificationWithRetry(verificationAgent, operation, "Verification");
    }

    /**
     * Verifies a precondition, where {@code stepHierarchy} is the rendered composite chain plus the precondition's notes (see
     * {@code AncestorChainRenderer}), empty for one executed on its own.
     */
    public VerificationExecutionResult verifyPrecondition(String preconditionDescription, TestExecutionContext context,
                                                          VisualStateHolder visualState,
                                                          UiPreconditionVerificationAgent preconditionVerificationAgent,
                                                          String relevantData, String stepHierarchy) {

        LOG.info("Verifying if precondition was successfully executed: '{}'", preconditionDescription);
        Supplier<Result<?>> operation = () -> {
            var screenshot = captureScreen();
            visualState.set(new VisualState(screenshot));
            var message = buildPreconditionVerificationMessage(preconditionDescription, context.renderSharedData(), relevantData,
                    stepHierarchy);
            return preconditionVerificationAgent.verify(userMessageContents(message),
                    preconditionVerificationAgent.getImageContentCreator(uiTestAgentConfig).createImageContent(screenshot));
        };
        return executeVerificationWithRetry(preconditionVerificationAgent, operation, "Precondition verification");
    }

    private static String buildTestStepVerificationMessage(String verificationDescription, String actionDescription,
                                                           String actionTestData, String sharedData, String stepHierarchy) {
        return joinSections(
                "Verify that the expected results from the \"%s\" section are met.".formatted(EXPECTED_RESULTS_LABEL),
                section(SECTION_LEVEL, EXPECTED_RESULTS_LABEL, verificationDescription),
                section(SECTION_LEVEL, EXECUTED_ACTION_LABEL, actionDescription),
                section(SECTION_LEVEL, ACTION_DATA_LABEL, actionTestData),
                stepHierarchy,
                section(SECTION_LEVEL, EXECUTION_CONTEXT_DATA_LABEL, sharedData));
    }

    private static String buildPreconditionVerificationMessage(String preconditionDescription, String sharedData, String relevantData,
                                                               String stepHierarchy) {
        return joinSections(
                "Verify that the precondition from the \"%s\" section is met.".formatted(PRECONDITION_LABEL),
                section(SECTION_LEVEL, PRECONDITION_LABEL, preconditionDescription),
                section(SECTION_LEVEL, PRECONDITION_DATA_LABEL, relevantData),
                stepHierarchy,
                section(SECTION_LEVEL, EXECUTION_CONTEXT_DATA_LABEL, sharedData),
                heading(SECTION_LEVEL, SCREENSHOT_LABEL));
    }

    private VerificationExecutionResult executeVerificationWithRetry(
            BaseUiAgent<VerificationExecutionResult> agent,
            Supplier<Result<?>> operation,
            String label) {

        RetryPolicy retryPolicy = uiTestAgentConfig.getVerificationRetryPolicy();
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