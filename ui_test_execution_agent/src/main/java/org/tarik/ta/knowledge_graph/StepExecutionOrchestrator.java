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
package org.tarik.ta.knowledge_graph;

import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.dto.*;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.dto.UiPreconditionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.model.node.Procedure.TimingProfile;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.tools.VerificationTools;
import org.tarik.ta.user_dialogs.*;
import org.tarik.ta.user_dialogs.knowledge.ProcedureExecutionConfirmationPopup;
import org.tarik.ta.user_dialogs.knowledge.ExecutionItemContext;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.time.Instant.now;
import static java.util.stream.Collectors.joining;
import org.tarik.ta.agents.KnowledgeSuggestionAgent;
import static org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator.triggerEditProcedureFlow;
import static org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator.triggerNewProcedureFlow;
import static org.tarik.ta.knowledge_graph.StepExecutionOrchestrator.RetryLoopOutcome.*;
import static org.tarik.ta.UiTestAgentConfig.*;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.*;
import static org.tarik.ta.core.manager.BudgetManager.resetToolCallUsage;
import static org.tarik.ta.dto.ProcedureExecutionConfirmationResult.Decision.HALTED;
import static org.tarik.ta.utils.ImageUtils.getInstance;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.core.utils.CommonUtils.*;

import java.util.function.Supplier;

class StepExecutionOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(StepExecutionOrchestrator.class);
    static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();

    private StepExecutionOrchestrator() {
    }

    static AtomicStepResult executeAtomicStep(ExecutionItem item, Procedure atomicStep,
                                              UiTestExecutionContext context, UiPreconditionActionAgent preconditionActionAgent,
                                              UiPreconditionVerificationAgent preconditionVerificationAgent,
                                              UiTestStepActionAgent actionAgent,
                                              UiTestStepVerificationAgent testStepVerificationAgent,
                                              List<UiTestStepResult> testStepResults,
                                              List<UiPreconditionResult> preconditionResults,
                                              AtomicStepExecutionContext execContext) {
        try {
            return switch (item) {
                case PreconditionItem ignored -> {
                    var testData = atomicStep.testData() != null
                            ? atomicStep.testData().stream().map(Object::toString).collect(joining(", "))
                            : "";
                    var relevantData = atomicStep.expectedResults() != null ? atomicStep.expectedResults() : "";
                    var result = executeSinglePrecondition(context, atomicStep, testData, relevantData,
                            preconditionActionAgent, preconditionVerificationAgent, execContext);
                    preconditionResults.add(result);
                    yield result.isSuccess()
                            ? new AtomicStepResult.Success()
                            : new AtomicStepResult.VerificationFailure(atomicStep.description(),
                            result.getErrorMessage(), result.getScreenshot());
                }
                case TestStepItem(TestStep testStep) -> {
                    boolean hasTestStepData = testStep.testData() != null && !testStep.testData().isEmpty()
                            && testStep.testData().stream().anyMatch(CommonUtils::isNotBlank);
                    List<String> effectiveTestData = hasTestStepData ? testStep.testData()
                            : (atomicStep.testData() != null ? atomicStep.testData() : List.of());
                    var testDataString = effectiveTestData.stream().map(Object::toString).collect(joining(", "));
                    var stepResult = executeSingleTestStep(context, testStep, atomicStep, testDataString,
                            actionAgent, testStepVerificationAgent, execContext);
                    testStepResults.add(stepResult);
                    yield switch (stepResult.getExecutionStatus()) {
                        case SUCCESS -> new AtomicStepResult.Success();
                        case FAILURE -> new AtomicStepResult.VerificationFailure(testStep.stepDescription(),
                                stepResult.getErrorMessage(), stepResult.getScreenshot());
                        case ERROR -> new AtomicStepResult.ExecutionError(stepResult.getErrorMessage(), null);
                    };
                }
            };
        } catch (RuntimeException e) {
            LOG.error("Unhandled exception during atomic step '{}': {}", atomicStep.description(), e.getMessage(), e);
            var errorScreenshot = captureScreen();
            context.setVisualState(new VisualState(errorScreenshot));
            switch (item) {
                case PreconditionItem ignored ->
                        preconditionResults.add(new UiPreconditionResult(atomicStep.description(), false, e.getMessage(), errorScreenshot, now(), now()));
                case TestStepItem(TestStep testStep) ->
                        testStepResults.add(new UiTestStepResult(testStep, TestStepResultStatus.ERROR, e.getMessage(), null, errorScreenshot, now(), now()));
            }
            return new AtomicStepResult.ExecutionError(e.getMessage(), e);
        }
    }

    static RetryLoopOutcome executeAtomicStepWithRetryLoop(ExecutionItem item, Procedure atomicStep,
                                                           @Nullable Procedure parentProcedure, UiTestExecutionContext context,
                                                           Supplier<UiPreconditionActionAgent> preconditionActionAgentFactory,
                                                           UiPreconditionVerificationAgent preconditionVerificationAgent,
                                                           Supplier<UiTestStepActionAgent> actionAgentFactory,
                                                           UiTestStepVerificationAgent testStepVerificationAgent,
                                                           List<UiTestStepResult> testStepResults,
                                                           List<UiPreconditionResult> preconditionResults,
                                                           KnowledgeServices knowledgeServices,
                                                           List<Procedure> executedAtomics,
                                                           AtomicStepExecutionContext execContext,
                                                           KnowledgeSuggestionAgent knowledgeSuggestionAgent) {
        boolean isPreconditionItem = item instanceof PreconditionItem;
        var actionAgent = actionAgentFactory.get();
        var preconditionActionAgent = preconditionActionAgentFactory.get();
        var knowledgeService = knowledgeServices.mainKnowledgeService();
        var ingestionService = knowledgeServices.ingestionService();

        String itemDescription = item.getDescription();
        List<String> itemTestData = item.getTestData();
        String itemExpectedResults = item.getExpectedResults();

        while (true) {
            if (!isFullyUnattended()) {
                var itemContext = new ExecutionItemContext(itemDescription, itemTestData, isPreconditionItem);
                var decision = ProcedureExecutionConfirmationPopup.displayAndGetUserDecision(
                        atomicStep.description(),
                        parentProcedure != null ? parentProcedure.description() : null, itemContext, getSupervisedCountdownSeconds(), true);
                if (decision.decision() == HALTED) {
                    LOG.info("User halted execution before step — prompting for next action");
                    var haltResult = handleHaltDecision(
                            "Execution is about to start but you chose to halt. What would you like to do?",
                            atomicStep, itemTestData, itemExpectedResults, itemDescription, isPreconditionItem,
                            knowledgeService, ingestionService, context, executedAtomics, knowledgeSuggestionAgent);
                    switch (haltResult) {
                        case HaltHandlerResult.ShouldReturn(var outcome) -> { return outcome; }
                        case HaltHandlerResult.ShouldRetry(var updated) -> {
                            atomicStep = updated;
                            context.setVisualState(new VisualState(captureScreen()));
                            continue;
                        }
                    }
                }
            }

            var result = executeAtomicStep(item, atomicStep, context, preconditionActionAgent,
                    preconditionVerificationAgent, actionAgent,
                    testStepVerificationAgent, testStepResults, preconditionResults, execContext);
            switch (result) {
                case AtomicStepResult.Success _ -> {
                    if (isFullyUnattended()) {
                        return CONTINUE_NEXT_STEP;
                    }
                    var itemContext = new ExecutionItemContext(itemDescription, itemTestData, isPreconditionItem);
                    var decision = ProcedureExecutionConfirmationPopup.displayAndGetUserDecision(
                            atomicStep.description(),
                            parentProcedure != null ? parentProcedure.description() : null, itemContext, getSupervisedCountdownSeconds(),
                            false);
                    if (decision.decision() == HALTED) {
                        LOG.info("User halted execution after success — prompting for next action");
                        var haltResult = handleHaltDecision(
                                "Execution succeeded but you chose to halt. What would you like to do?",
                                atomicStep, itemTestData, itemExpectedResults, itemDescription, isPreconditionItem,
                                knowledgeService, ingestionService, context, executedAtomics, knowledgeSuggestionAgent);
                        switch (haltResult) {
                            case HaltHandlerResult.ShouldReturn(var outcome) -> { return outcome; }
                            case HaltHandlerResult.ShouldRetry(var updated) -> {
                                atomicStep = updated;
                                context.setVisualState(new VisualState(captureScreen()));
                                continue;
                            }
                        }
                    }
                    return CONTINUE_NEXT_STEP;
                }
                case AtomicStepResult.VerificationFailure failure -> {
                    if (isFullyUnattended()) {
                        return TERMINATE_EXECUTION;
                    }
                    var outcome = promptUserAndDispatch("Verification failed for '%s': %s"
                                    .formatted(failure.description(), failure.reason()), atomicStep, itemTestData,
                            itemExpectedResults, itemDescription, isPreconditionItem, knowledgeService, ingestionService,
                            context, executedAtomics, knowledgeSuggestionAgent);
                    if (outcome == TERMINATE_EXECUTION) {
                        return TERMINATE_EXECUTION;
                    }
                    var updated = processOutcomeAndRefresh(outcome, atomicStep, knowledgeService, context);
                    if (updated.isEmpty()) return RE_DECOMPOSE_AND_RETRY;
                    atomicStep = updated.get();
                }
                case AtomicStepResult.ExecutionError error -> {
                    if (isFullyUnattended()) {
                        return TERMINATE_EXECUTION;
                    }
                    var errorMessage = error.details();
                    var errorScreenshot = captureScreen();
                    context.setVisualState(new VisualState(errorScreenshot));
                    InformationalPopup.display("Error During Execution", errorMessage, errorScreenshot,
                            PopupType.ERROR);
                    var outcome = promptUserAndDispatch(errorMessage, atomicStep, itemTestData, itemExpectedResults,
                            itemDescription, isPreconditionItem, knowledgeService, ingestionService, context, executedAtomics, knowledgeSuggestionAgent);
                    if (outcome == TERMINATE_EXECUTION) {
                        return TERMINATE_EXECUTION;
                    }
                    var updated = processOutcomeAndRefresh(outcome, atomicStep, knowledgeService, context);
                    if (updated.isEmpty()) return RE_DECOMPOSE_AND_RETRY;
                    atomicStep = updated.get();
                }
            }
        }
    }

    /**
     * Prompts the user for the next action (Edit/Retry/Terminate) and dispatches accordingly.
     * Returns {@code TERMINATE_EXECUTION} if the user chose Terminate or edited a parent procedure,
     * {@code RE_FETCH_AND_RETRY} if the current atomic step was edited (caller must re-fetch before retrying),
     * or {@code CONTINUE_NEXT_STEP} for a plain retry.
     */
    private static RetryLoopOutcome promptUserAndDispatch(String message, Procedure atomicStep, List<String> testData,
                                                          String expectedResults, String itemDescription,
                                                          boolean isPreconditionItem, KnowledgeService knowledgeService,
                                                          KnowledgeIngestionService ingestionService,
                                                          UiTestExecutionContext executionContext,
                                                          List<Procedure> executedAtomics,
                                                          KnowledgeSuggestionAgent knowledgeSuggestionAgent) {
        var itemContext = new ExecutionItemContext(itemDescription, testData, isPreconditionItem);
        while (true) {
            var decision = NextActionPopup.displayAndGetUserDecision(null, message);
            switch (decision) {
                case EDIT_CURRENT_PROCEDURE -> {
                    LOG.info("User chose to edit procedure '{}'", atomicStep.description());
                    var editResult = triggerEditProcedureFlow(atomicStep, testData, expectedResults,
                            knowledgeService, ingestionService, !isPreconditionItem, itemContext, executionContext,
                            executedAtomics, knowledgeSuggestionAgent);
                    if (editResult.isSaved()) {
                        ingestionService.update(editResult.savedProcedureId().get(), editResult.updatedNode().get());
                        knowledgeService.onKnowledgeIngested();
                        if (editResult.savedProcedureId().filter(id -> id.equals(atomicStep.id())).isPresent()) {
                            return RE_FETCH_AND_RETRY;
                        }
                        // A parent was saved — the execution graph has changed, continuing is unsafe
                        InformationalPopup.display("Execution Terminated",
                                "A parent procedure was modified. Execution cannot continue with the current execution graph.",
                                null, PopupType.INFO);
                        return TERMINATE_EXECUTION;
                    }
                }
                case CREATE_NEW_PROCEDURE -> {
                    LOG.info("User chose to create a new procedure for '{}'", atomicStep.description());
                    var creationResult = triggerNewProcedureFlow(itemDescription, testData,
                            expectedResults, knowledgeService, ingestionService, isPreconditionItem, executionContext,
                            executedAtomics, knowledgeSuggestionAgent);
                    if (creationResult.isPresent()) {
                        ingestionService.ingest(creationResult.get());
                        knowledgeService.onKnowledgeIngested();
                        LOG.info("New procedure created and ingested for '{}'", atomicStep.description());
                        return RE_FETCH_AND_RETRY;
                    }
                }
                case RETRY -> {
                    LOG.info("User chose to retry procedure '{}'", atomicStep.description());
                    return CONTINUE_NEXT_STEP;
                }
                case TERMINATE -> {
                    LOG.info("User chose to terminate execution");
                    return TERMINATE_EXECUTION;
                }
            }
        }
    }

    static UiPreconditionResult executeSinglePrecondition(UiTestExecutionContext context,
                                                          Procedure precondition,
                                                          String testDataString,
                                                          String relevantData,
                                                          UiPreconditionActionAgent preconditionActionAgent,
                                                          UiPreconditionVerificationAgent preconditionVerificationAgent,
                                                          AtomicStepExecutionContext execContext) {
        var executionStartTimestamp = now();
        LOG.info("Executing precondition: {}", precondition.description());
        var actionScreenshot = captureScreen();
        context.setVisualState(new VisualState(actionScreenshot));
        var preconditionExecutionResult = preconditionActionAgent.executeAndGetResult(
                () -> {
                    String userMessage = getPreconditionExecutionUserMessage(context, precondition, testDataString, relevantData,
                            execContext.uiElementId(), execContext.failureHints());
                    return preconditionActionAgent.execute(userMessage, getInstance().singleImageContent(actionScreenshot));
                });
        resetToolCallUsage();

        if (!preconditionExecutionResult.isSuccess()) {
            var errorMessage = "Failure while executing precondition '%s'. Root cause: %s"
                    .formatted(precondition.description(), preconditionExecutionResult.getMessage());
            return new UiPreconditionResult(precondition.description(), false, errorMessage, captureScreen(),
                    executionStartTimestamp, now());
        }
        LOG.info("Precondition execution complete.");

        long actionDurationMs = java.time.Duration.between(executionStartTimestamp, now()).toMillis();
        LOG.info("Verifying if precondition was successfully executed.");
        var verificationResult = VerificationTools.verifyPrecondition(
                precondition.description(), context, preconditionVerificationAgent, execContext.effectiveExpectedResults());
        resetToolCallUsage();

        if (verificationResult == null) {
            var errorMessage = "Precondition verification failed. Got no verification result from the model.";
            return new UiPreconditionResult(precondition.description(), false, errorMessage,
                    context.getVisualState().screenshot(), executionStartTimestamp, now());
        }
        if (!verificationResult.success()) {
            var errorMessage = "Precondition verification failed. %s".formatted(verificationResult.message());
            notifyVerificationFailure(precondition.description(), verificationResult.message(), context.getVisualState().screenshot());
            return new UiPreconditionResult(precondition.description(), false, errorMessage,
                    context.getVisualState().screenshot(), executionStartTimestamp, now());
        }
        LOG.info("Precondition '{}' is met.", precondition.description());
        execContext.timingRecorder().record(precondition.id(), actionDurationMs, 0);
        return new UiPreconditionResult(precondition.description(), true, null, null, executionStartTimestamp, now());
    }

    /**
     * Handles a user halt decision by looping until the user chooses to retry or triggers a terminal outcome.
     * Returns {@link HaltHandlerResult.ShouldReturn} if execution should terminate or re-decompose,
     * or {@link HaltHandlerResult.ShouldRetry} with the (possibly re-fetched) atomic step if execution should retry.
     */
    private static HaltHandlerResult handleHaltDecision(String message, Procedure atomicStep, List<String> itemTestData,
                                                        String itemExpectedResults, String itemDescription,
                                                        boolean isPreconditionItem, KnowledgeService knowledgeService,
                                                        KnowledgeIngestionService ingestionService,
                                                        UiTestExecutionContext context, List<Procedure> executedAtomics,
                                                        KnowledgeSuggestionAgent knowledgeSuggestionAgent) {
        Procedure current = atomicStep;
        while (true) {
            var outcome = promptUserAndDispatch(message, current, itemTestData, itemExpectedResults, itemDescription,
                    isPreconditionItem, knowledgeService, ingestionService, context, executedAtomics, knowledgeSuggestionAgent);
            if (outcome == TERMINATE_EXECUTION) {
                return new HaltHandlerResult.ShouldReturn(TERMINATE_EXECUTION);
            }
            if (outcome == RE_FETCH_AND_RETRY) {
                current = knowledgeService.findById(current.id()).orElse(current);
                if (!current.isAtomic()) {
                    return new HaltHandlerResult.ShouldReturn(RE_DECOMPOSE_AND_RETRY);
                }
                continue;
            }
            return new HaltHandlerResult.ShouldRetry(current);
        }
    }

    private static String formatHintsSection(List<String> failureHints) {
        return (failureHints != null && !failureHints.isEmpty())
                ? "\nKnown issues with this procedure:\n- " + String.join("\n- ", failureHints) + "\n"
                : "";
    }

    private static String buildExecutionContextSuffix(String elementId, List<String> failureHints) {
        String elementHint = elementId != null
                ? "\nTarget UI element ID: %s — locate this element on screen before interacting with it.\n".formatted(elementId)
                : "";
        return elementHint + formatHintsSection(failureHints);
    }

    private static @NonNull String getPreconditionExecutionUserMessage(UiTestExecutionContext context, Procedure precondition,
                                                                       String testDataString, String relevantData, String elementId, List<String> failureHints) {
        return """
                The precondition you need to execute: %s.

                Relevant data for this precondition: %s

                Test context data: %s.
                %s
                The screenshot follows.
                """.formatted(
                precondition.description(),
                relevantData.isBlank() ? testDataString : relevantData,
                context.getSharedData().toString(),
                buildExecutionContextSuffix(elementId, failureHints));
    }

    static UiTestStepResult executeSingleTestStep(UiTestExecutionContext context, TestStep testStep, Procedure atomic,
                                                  String testDataString, UiTestStepActionAgent actionAgent,
                                                  UiTestStepVerificationAgent testStepVerificationAgent,
                                                  AtomicStepExecutionContext execContext) {
        var actionInstruction = atomic.description();
        try {
            var executionStartTimestamp = now();
            LOG.info("Executing test step: {}", actionInstruction);
            var screenshot = captureScreen();
            context.setVisualState(new VisualState(screenshot));
            var actionResult = ((UiOperationExecutionResult<EmptyExecutionResult>) actionAgent.executeAndGetResult(() -> {
                String userMessage = getTestStepActionUserMessage(context, atomic, testDataString, execContext.uiElementId(), execContext.failureHints());
                actionAgent.execute(userMessage, getInstance().singleImageContent(screenshot));
                return null;
            }));
            resetToolCallUsage();
            if (!actionResult.isSuccess()) {
                var message = "There was an error while executing test step action '%s'. Please see agent logs for details"
                        .formatted(actionInstruction);
                LOG.warn("Test step failed: {}", message);
                return new UiTestStepResult(testStep, TestStepResultStatus.ERROR, message, null, captureScreen(),
                        executionStartTimestamp, now());
            }
            LOG.info("Action execution complete.");
            long actionDurationMs = java.time.Duration.between(executionStartTimestamp, now()).toMillis();

            var verificationInstruction = execContext.effectiveExpectedResults() != null ? execContext.effectiveExpectedResults().trim() : "";

            boolean verificationNeeded = isNotBlank(verificationInstruction)
                    && !verificationInstruction.equalsIgnoreCase("null");

            if (verificationNeeded) {
                return verifyTestStep(context, testStep, atomic, testStepVerificationAgent,
                        actionInstruction, testDataString, executionStartTimestamp, actionDurationMs, execContext);
            } else {
                execContext.timingRecorder().record(atomic.id(), actionDurationMs, 0);
                return new UiTestStepResult(testStep, SUCCESS, null, "No verification required", null,
                        executionStartTimestamp, now());
            }
        } catch (ElementLocationException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Unexpected error while executing the test step: '{}'", testStep.stepDescription(), e);
            var message = e.getMessage();
            LOG.warn("Got error while executing test step: {}", message);
            return new UiTestStepResult(testStep, TestStepResultStatus.ERROR, message, null, captureScreen(), now(),
                    now());
        }
    }

    private static @NonNull String getTestStepActionUserMessage(UiTestExecutionContext context, Procedure atomic, String testDataString,
                                                                String elementId, List<String> failureHints) {
        return """
                Execute the following action: %s

                Data, related to the action: %s

                Test context execution data: %s
                %s
                The screenshot follows.
                """.formatted(
                atomic.description(),
                testDataString,
                context.getSharedData().toString(),
                buildExecutionContextSuffix(elementId, failureHints));
    }

    private static UiTestStepResult verifyTestStep(UiTestExecutionContext context, TestStep testStep, Procedure atomic,
                                                   UiTestStepVerificationAgent testStepVerificationAgent,
                                                   String actionInstruction, String testDataString,
                                                   Instant executionStartTimestamp, long actionDurationMs,
                                                   AtomicStepExecutionContext execContext) {
        String verificationInstruction = execContext.effectiveExpectedResults() != null ? execContext.effectiveExpectedResults().trim() : "";
        LOG.info("Verifying that '{}'", verificationInstruction);

        long delayMs = TimingProfile.computeDelay(
                execContext.timingProfile(), getTimingVerificationMinDelayMs(), getActionVerificationDelayMillis()
        );
        LOG.debug("Verification delay for '{}': {}ms ({})", atomic.description(), delayMs,
                execContext.timingProfile() != null ? "profile-driven" : "fallback default");
        sleepMillis(delayMs);

        VerificationExecutionResult verificationResult = VerificationTools.verifyTestStep(
                verificationInstruction, actionInstruction, testDataString, context, testStepVerificationAgent);
        resetToolCallUsage();

        if (verificationResult == null) {
            var message = "No verification result returned.";
            LOG.warn("Something went wrong. {}", message);
            return new UiTestStepResult(testStep, ERROR, message, null, context.getVisualState().screenshot(), executionStartTimestamp,
                    now());
        }

        if (!verificationResult.success()) {
            var generalMessage = ("Verification failed. %s".formatted(verificationResult.message())).trim();
            LOG.warn("Interrupting test case execution because the verification failed. {}", verificationResult.message());
            notifyVerificationFailure(verificationInstruction, verificationResult.message(), context.getVisualState().screenshot());
            return new UiTestStepResult(testStep, TestStepResultStatus.FAILURE, generalMessage,
                    verificationResult.message(), context.getVisualState().screenshot(), executionStartTimestamp, now());
        } else {
            LOG.info("Verification succeeded.");
            execContext.timingRecorder().record(atomic.id(), actionDurationMs, delayMs);
            return new UiTestStepResult(testStep, SUCCESS, null, verificationResult.message(), null,
                    executionStartTimestamp, now());
        }
    }

    /**
     * Applies the retry outcome: re-fetches the procedure if needed and refreshes the visual state.
     * Returns empty if the procedure is no longer atomic and needs re-decomposition.
     */
    private static Optional<Procedure> processOutcomeAndRefresh(RetryLoopOutcome outcome, Procedure atomicStep,
                                                                 KnowledgeService knowledgeService,
                                                                 UiTestExecutionContext context) {
        if (outcome != RE_FETCH_AND_RETRY) {
            context.setVisualState(new VisualState(captureScreen()));
            return Optional.of(atomicStep);
        }
        var updated = knowledgeService.findById(atomicStep.id()).orElse(atomicStep);
        if (!updated.isAtomic()) {
            return Optional.empty();
        }
        context.setVisualState(new VisualState(captureScreen()));
        return Optional.of(updated);
    }

    private static void notifyVerificationFailure(String description, String failureMessage, BufferedImage screenshot) {
        if (!isFullyUnattended()) {
            VerificationFailurePopup.display(description, failureMessage, screenshot);
        } else {
            LOG.warn("Verification failed: {} — {}", description, failureMessage);
        }
    }

    static UiTestStepResult mergeAtomicResults(TestStep testStep, List<UiTestStepResult> results) {
        if (results.isEmpty()) {
            var errorMessage = "Execution of test step '%s' was aborted.".formatted(testStep.stepDescription());
            LOG.error(errorMessage);
            return new UiTestStepResult(testStep, TestStepResultStatus.FAILURE, errorMessage, "No atomic steps executed", null, now(),
                    now());
        }

        boolean allSuccess = results.stream().allMatch(r -> r.getExecutionStatus() == SUCCESS);
        var finalStatus = allSuccess ? TestStepResultStatus.SUCCESS : results.getLast().getExecutionStatus();
        var finalError = results.stream()
                .map(TestStepResult::getErrorMessage)
                .filter(CommonUtils::isNotBlank)
                .collect(joining("\n"))
                .trim();
        if (isBlank(finalError)) {
            finalError = "Execution of test step '%s' was aborted.".formatted(testStep.stepDescription());
        }
        var finalActualResult = results.stream()
                .map(TestStepResult::getActualResult)
                .filter(Objects::nonNull)
                .collect(joining("\n"));
        Instant start = results.getFirst().getExecutionStartTimestamp();
        Instant end = results.getLast().getExecutionEndTimestamp();
        BufferedImage screenshot = results.getLast().getScreenshot();
        return new UiTestStepResult(testStep, finalStatus, finalError, finalActualResult, screenshot, start, end);
    }

    static UiPreconditionResult mergePreconditionResults(String preconditionDescription,
                                                         List<UiPreconditionResult> results, boolean allAtomicsSuccess) {
        if (results.isEmpty()) {
            var errorMessage = "Execution of precondition '%s' was aborted.".formatted(preconditionDescription);
            LOG.error(errorMessage);
            return new UiPreconditionResult(preconditionDescription, false, errorMessage, captureScreen(), now(), now());
        }
        if (!allAtomicsSuccess) {
            var last = results.getLast();
            return new UiPreconditionResult(preconditionDescription, false, last.getErrorMessage(),
                    last.getScreenshot(), results.getFirst().getExecutionStartTimestamp(), last.getExecutionEndTimestamp());
        }
        return new UiPreconditionResult(preconditionDescription, true, null, null,
                results.getFirst().getExecutionStartTimestamp(), results.getLast().getExecutionEndTimestamp());
    }


    enum RetryLoopOutcome {
        CONTINUE_NEXT_STEP, TERMINATE_EXECUTION, RE_FETCH_AND_RETRY, RE_DECOMPOSE_AND_RETRY
    }

    private sealed interface HaltHandlerResult {
        record ShouldReturn(RetryLoopOutcome outcome) implements HaltHandlerResult {}
        record ShouldRetry(Procedure updatedAtomicStep) implements HaltHandlerResult {}
    }

    sealed interface AtomicStepResult {
        record Success() implements AtomicStepResult {
        }

        record VerificationFailure(String description, String reason, BufferedImage screenshot)
                implements AtomicStepResult {
        }

        record ExecutionError(String details, Throwable cause) implements AtomicStepResult {
        }
    }
}