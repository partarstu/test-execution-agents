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

import jakarta.inject.Singleton;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.dto.*;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.utils.CommonUtils;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.dto.UiPreconditionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.Procedure.TimingProfile;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
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
import static org.tarik.ta.knowledge_graph.StepExecutionOrchestrator.UserDecisionOutcome.*;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.*;
import static org.tarik.ta.dto.ProcedureExecutionConfirmationResult.Decision.HALTED;
import static org.tarik.ta.utils.ImageUtils.singleImageContent;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.core.utils.CommonUtils.*;

@Singleton
public class StepExecutionOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(StepExecutionOrchestrator.class);

    private final VerificationTools verificationTools;
    private final BudgetManager budgetManager;
    private final UiTestAgentConfig uiTestAgentConfig;
    private final ProcedureKnowledgeCollectionService procedureKnowledgeCollectionService;
    private final KnowledgeService knowledgeService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final UiTestStepActionAgent testStepActionAgent;
    private final UiPreconditionActionAgent preconditionActionAgent;
    private final UiTestStepVerificationAgent testStepVerificationAgent;
    private final UiPreconditionVerificationAgent preconditionVerificationAgent;
    private final long actionVerificationDelayMillis;

    public StepExecutionOrchestrator(VerificationTools verificationTools,
                                     BudgetManager budgetManager,
                                     UiTestAgentConfig uiTestAgentConfig,
                                     ProcedureKnowledgeCollectionService procedureKnowledgeCollectionService,
                                     KnowledgeService knowledgeService,
                                     KnowledgeIngestionService knowledgeIngestionService,
                                     UiTestStepActionAgent testStepActionAgent,
                                     UiPreconditionActionAgent preconditionActionAgent,
                                     UiTestStepVerificationAgent testStepVerificationAgent,
                                     UiPreconditionVerificationAgent preconditionVerificationAgent) {
        this.verificationTools = verificationTools;
        this.budgetManager = budgetManager;
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.procedureKnowledgeCollectionService = procedureKnowledgeCollectionService;
        this.knowledgeService = knowledgeService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.testStepActionAgent = testStepActionAgent;
        this.preconditionActionAgent = preconditionActionAgent;
        this.testStepVerificationAgent = testStepVerificationAgent;
        this.preconditionVerificationAgent = preconditionVerificationAgent;
        this.actionVerificationDelayMillis = uiTestAgentConfig.getActionVerificationDelayMillis();
    }

    AtomicStepResult executeAtomicStep(ExecutionItem item, Procedure atomicStep,
                                       UiTestExecutionContext context,
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
                    var result = executeSinglePrecondition(context, atomicStep, testData, relevantData, execContext);
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
                    var stepResult = executeSingleTestStep(context, testStep, atomicStep, testDataString, execContext);
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
                case PreconditionItem ignored -> preconditionResults.add(
                        new UiPreconditionResult(atomicStep.description(), false, e.getMessage(), errorScreenshot, now(), now()));
                case TestStepItem(TestStep testStep) -> testStepResults.add(
                        new UiTestStepResult(testStep, TestStepResultStatus.ERROR, e.getMessage(), null, errorScreenshot, now(), now()));
            }
            return new AtomicStepResult.ExecutionError(e.getMessage(), e);
        }
    }

    UserDecisionOutcome executeAtomicStepWithRetryLoop(ExecutionItem item, Procedure atomicStep,
                                                       TestCase testCase,
                                                       @Nullable Procedure parentProcedure,
                                                       UiTestExecutionContext context,
                                                       List<UiTestStepResult> testStepResults,
                                                       List<UiPreconditionResult> preconditionResults,
                                                       List<Procedure> executedAtomics,
                                                       AtomicStepExecutionContext execContext) {
        boolean isPreconditionItem = item instanceof PreconditionItem;
        String itemDescription = item.getDescription();
        List<String> itemTestData = item.getTestData();
        String itemExpectedResults = item.getExpectedResults();
        var itemContext = new ExecutionItemContext(itemDescription, itemTestData, isPreconditionItem);

        // Step execution loop, automatically run only once, retry is invoked only if the user chooses to do so.
        while (true) {
            // Pre-execution user notification (supervised mode only)
            if (!uiTestAgentConfig.isFullyUnattended()) {
                var preCheck = checkPreExecutionHalt(atomicStep, parentProcedure, itemContext, itemTestData,
                        itemExpectedResults, itemDescription, isPreconditionItem, testCase, context, executedAtomics);
                switch (preCheck) {
                    case PreExecutionCheckResult.TerminalOutcome r -> {
                        return r.outcome();
                    }
                    case PreExecutionCheckResult.Proceed r -> {
                        atomicStep = r.procedure();
                        context.setVisualState(new VisualState(captureScreen()));
                    }
                }
            }

            // Track result list sizes before execution so a retry can discard the failed attempt's result
            int testResultsSizeBefore = testStepResults.size();
            int preconditionResultsSizeBefore = preconditionResults.size();

            // Execution
            var result = executeAtomicStep(item, atomicStep, context, testStepResults, preconditionResults, execContext);

            // Handling result — short-circuit in unattended mode
            if (uiTestAgentConfig.isFullyUnattended()) {
                return result instanceof AtomicStepResult.Success ? CONTINUE_NEXT_STEP : TERMINATE_EXECUTION;
            } else {
                // Post-execution user notification (supervised mode only)
                var postCheck = handleResultInSupervisedMode(result, atomicStep, parentProcedure, itemContext, itemTestData,
                        itemExpectedResults, itemDescription, isPreconditionItem, testCase, context, executedAtomics);
                switch (postCheck) {
                    case PostExecutionCheckResult.ProceedToNext _ -> {
                        return CONTINUE_NEXT_STEP;
                    }
                    case PostExecutionCheckResult.RetryStep r -> {
                        // Discard this attempt's result so only the last retry is recorded
                        testStepResults.subList(testResultsSizeBefore, testStepResults.size()).clear();
                        preconditionResults.subList(preconditionResultsSizeBefore, preconditionResults.size()).clear();
                        atomicStep = r.procedure();
                        context.setVisualState(new VisualState(captureScreen()));
                    }
                    case PostExecutionCheckResult.TerminalOutcome r -> {
                        return r.outcome();
                    }
                }
            }
        }
    }

    private PreExecutionCheckResult checkPreExecutionHalt(Procedure atomicStep, @Nullable Procedure parentProcedure,
                                                          ExecutionItemContext itemContext, List<String> itemTestData,
                                                          String itemExpectedResults, String itemDescription,
                                                          boolean isPreconditionItem, TestCase testCase,
                                                          UiTestExecutionContext context, List<Procedure> executedAtomics) {
        var decision = ProcedureExecutionConfirmationPopup.displayAndGetUserDecision(
                atomicStep.description(), parentProcedure != null ? parentProcedure.description() : null,
                itemContext, uiTestAgentConfig.getSupervisedCountdownSeconds(), true, uiTestAgentConfig);
        if (decision.decision() != HALTED) {
            return new PreExecutionCheckResult.Proceed(atomicStep);
        }
        LOG.info("User halted execution before step — prompting for next action");
        var haltResult = handleHaltDecision("Execution is about to start but you chose to halt. What would you like to do?",
                atomicStep, itemTestData, itemExpectedResults, itemDescription, isPreconditionItem, testCase,
                context, executedAtomics);
        return switch (haltResult) {
            case HaltHandlerResult.ShouldProceed r -> new PreExecutionCheckResult.TerminalOutcome(r.outcome());
            case HaltHandlerResult.ShouldRetry r -> new PreExecutionCheckResult.Proceed(r.updatedAtomicStep());
        };
    }

    private PostExecutionCheckResult handleResultInSupervisedMode(AtomicStepResult result, Procedure atomicStep,
                                                                  @Nullable Procedure parentProcedure,
                                                                  ExecutionItemContext itemContext, List<String> itemTestData,
                                                                  String itemExpectedResults, String itemDescription,
                                                                  boolean isPreconditionItem, TestCase testCase,
                                                                  UiTestExecutionContext context, List<Procedure> executedAtomics) {
        return switch (result) {
            case AtomicStepResult.Success _ -> handlePostSuccessHalt(atomicStep, parentProcedure, itemContext,
                    itemTestData, itemExpectedResults, itemDescription, isPreconditionItem, testCase, context, executedAtomics);
            case AtomicStepResult.VerificationFailure f -> handleFailureInSupervisedMode(
                    "Verification failed for '%s': %s".formatted(f.description(), f.reason()),
                    atomicStep, itemTestData, itemExpectedResults, itemDescription, isPreconditionItem, testCase, context, executedAtomics);
            case AtomicStepResult.ExecutionError error -> {
                var errorMessage = error.details();
                var errorScreenshot = captureScreen();
                context.setVisualState(new VisualState(errorScreenshot));
                InformationalPopup.display("Error During Execution", errorMessage, errorScreenshot, PopupType.ERROR, uiTestAgentConfig);
                yield handleFailureInSupervisedMode(errorMessage, atomicStep, itemTestData, itemExpectedResults,
                        itemDescription, isPreconditionItem, testCase, context, executedAtomics);
            }
        };
    }

    private PostExecutionCheckResult handlePostSuccessHalt(Procedure atomicStep, @Nullable Procedure parentProcedure,
                                                           ExecutionItemContext itemContext, List<String> itemTestData,
                                                           String itemExpectedResults, String itemDescription,
                                                           boolean isPreconditionItem, TestCase testCase,
                                                           UiTestExecutionContext context, List<Procedure> executedAtomics) {
        var decision = ProcedureExecutionConfirmationPopup.displayAndGetUserDecision(
                atomicStep.description(), parentProcedure != null ? parentProcedure.description() : null,
                itemContext, uiTestAgentConfig.getSupervisedCountdownSeconds(), false, uiTestAgentConfig);
        if (decision.decision() != HALTED) {
            return new PostExecutionCheckResult.ProceedToNext();
        }
        LOG.info("User halted execution after success — prompting for next action");
        var haltResult = handleHaltDecision("Execution succeeded but you chose to halt. What would you like to do?",
                atomicStep, itemTestData, itemExpectedResults, itemDescription, isPreconditionItem, testCase,
                context, executedAtomics);
        return switch (haltResult) {
            case HaltHandlerResult.ShouldProceed r -> new PostExecutionCheckResult.TerminalOutcome(r.outcome());
            case HaltHandlerResult.ShouldRetry r -> new PostExecutionCheckResult.RetryStep(r.updatedAtomicStep());
        };
    }

    private PostExecutionCheckResult handleFailureInSupervisedMode(String message, Procedure atomicStep,
                                                                   List<String> itemTestData, String itemExpectedResults,
                                                                   String itemDescription, boolean isPreconditionItem,
                                                                   TestCase testCase, UiTestExecutionContext context,
                                                                   List<Procedure> executedAtomics) {
        var outcome = promptUserAndDispatch(message, atomicStep, itemTestData, itemExpectedResults,
                itemDescription, isPreconditionItem, testCase, context, executedAtomics);
        if (outcome == TERMINATE_EXECUTION || outcome == RE_DECOMPOSE_AND_RETRY) {
            return new PostExecutionCheckResult.TerminalOutcome(outcome);
        }
        Optional<PostExecutionCheckResult> retryStep = processOutcomeAndRefresh(outcome, atomicStep, context)
                .map(PostExecutionCheckResult.RetryStep::new);
        return retryStep.orElseGet(() -> new PostExecutionCheckResult.TerminalOutcome(RE_DECOMPOSE_AND_RETRY));
    }

    /**
     * Prompts the user for the next action (Edit/Retry/Terminate) and dispatches accordingly.
     */
    private UserDecisionOutcome promptUserAndDispatch(String message, Procedure atomicStep, List<String> testData,
                                                      String expectedResults, String itemDescription,
                                                      boolean isPreconditionItem,
                                                      TestCase testCase,
                                                      UiTestExecutionContext executionContext,
                                                      List<Procedure> executedAtomics) {
        var itemContext = new ExecutionItemContext(itemDescription, testData, isPreconditionItem);
        while (true) {
            var decision = NextActionPopup.displayAndGetUserDecision(null, message, uiTestAgentConfig);
            switch (decision) {
                case EDIT_CURRENT_PROCEDURE -> {
                    LOG.info("User chose to edit procedure '{}'", atomicStep.description());
                    var editResult = procedureKnowledgeCollectionService.triggerEditProcedureFlow(atomicStep, testData,
                            expectedResults, !isPreconditionItem, itemContext, testCase, executionContext, executedAtomics);
                    if (editResult.isSaved()) {
                        knowledgeIngestionService.update(editResult.savedProcedureId().get(), editResult.updatedNode().get());
                        knowledgeService.onKnowledgeIngested();
                        if (editResult.savedProcedureId().filter(id -> id.equals(atomicStep.id())).isPresent()) {
                            return RE_FETCH_AND_RETRY;
                        } else {
                            InformationalPopup.display("Execution Terminated",
                                    "A parent procedure was modified. Execution cannot continue with the current execution graph.",
                                    null, PopupType.INFO, uiTestAgentConfig);
                            return TERMINATE_EXECUTION;
                        }
                    }
                }
                case CREATE_NEW_PROCEDURE -> {
                    LOG.info("User chose to create a new procedure for '{}'", atomicStep.description());
                    var creationResult = procedureKnowledgeCollectionService.triggerNewProcedureFlow(itemDescription,
                            testData, expectedResults, isPreconditionItem, testCase, executionContext, executedAtomics);
                    if (creationResult.isPresent()) {
                        knowledgeIngestionService.ingest(creationResult.get());
                        knowledgeService.onKnowledgeIngested();
                        LOG.info("New procedure created and ingested for '{}'", atomicStep.description());
                        return RE_DECOMPOSE_AND_RETRY;
                    }
                }
                case RETRY -> {
                    LOG.info("User chose to retry procedure '{}'", atomicStep.description());
                    return RE_FETCH_AND_RETRY;
                }
                case TERMINATE -> {
                    LOG.info("User chose to terminate execution");
                    return TERMINATE_EXECUTION;
                }
            }
        }
    }

    UiPreconditionResult executeSinglePrecondition(UiTestExecutionContext context,
                                                   Procedure precondition,
                                                   String testDataString,
                                                   String relevantData,
                                                   AtomicStepExecutionContext execContext) {
        var executionStartTimestamp = now();
        LOG.info("Executing precondition: {}", precondition.description());
        var screenshot = captureScreen();
        context.setVisualState(new VisualState(screenshot));
        var preconditionExecutionResult = preconditionActionAgent.executeAndGetResult(
                () -> {
                    String userMessage = getPreconditionExecutionUserMessage(context, precondition, testDataString, relevantData,
                            execContext.uiElementId(), execContext.failureHints(), execContext.targetElement());
                    return preconditionActionAgent.execute(userMessage, singleImageContent(screenshot));
                });
        budgetManager.resetToolCallUsage();

        if (!preconditionExecutionResult.isSuccess()) {
            var errorMessage = "Failure while executing precondition '%s'. Root cause: %s"
                    .formatted(precondition.description(), preconditionExecutionResult.getMessage());
            return new UiPreconditionResult(precondition.description(), false, errorMessage, captureScreen(),
                    executionStartTimestamp, now());
        }
        LOG.info("Precondition execution complete.");

        long actionDurationMs = java.time.Duration.between(executionStartTimestamp, now()).toMillis();
        LOG.info("Verifying if precondition was successfully executed.");
        var verificationResult = verificationTools.verifyPrecondition(
                precondition.description(), context, preconditionVerificationAgent, execContext.effectiveExpectedResults());
        budgetManager.resetToolCallUsage();

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
     * Returns {@link HaltHandlerResult.ShouldProceed} if execution should terminate or re-decompose,
     * or {@link HaltHandlerResult.ShouldRetry} with the (possibly re-fetched) atomic step if execution should retry.
     */
    private HaltHandlerResult handleHaltDecision(String message, Procedure atomicStep, List<String> itemTestData,
                                                 String itemExpectedResults, String itemDescription,
                                                 boolean isPreconditionItem,
                                                 TestCase testCase,
                                                 UiTestExecutionContext context, List<Procedure> executedAtomics) {
        Procedure current = atomicStep;
        while (true) {
            var outcome = promptUserAndDispatch(message, current, itemTestData, itemExpectedResults, itemDescription,
                    isPreconditionItem, testCase, context, executedAtomics);
            if (outcome == TERMINATE_EXECUTION) {
                return new HaltHandlerResult.ShouldProceed(TERMINATE_EXECUTION);
            }
            if (outcome == RE_DECOMPOSE_AND_RETRY) {
                return new HaltHandlerResult.ShouldProceed(RE_DECOMPOSE_AND_RETRY);
            }
            if (outcome == RE_FETCH_AND_RETRY) {
                current = knowledgeService.findById(current.id()).orElse(current);
                if (!current.isAtomic()) {
                    return new HaltHandlerResult.ShouldProceed(RE_DECOMPOSE_AND_RETRY);
                }
                continue;
            }
            return new HaltHandlerResult.ShouldRetry(current);
        }
    }

    private static String knownExecutionIssues(List<String> failureHints) {
        return (failureHints != null && !failureHints.isEmpty())
                ? "Known issues with this procedure:\n- " + String.join("\n- ", failureHints)
                : "";
    }

    private static String buildExecutionContextString(UiTestExecutionContext context, String elementId, List<String> failureHints,
                                                      String uiElementDetails) {
        String uiElementIdInfo = elementId != null ? "Target UI element ID: %s".formatted(elementId) : "";
        var knownIssues = knownExecutionIssues(failureHints);
        var contextString = "Test execution context data:\n%s\n".formatted(context.getSharedData().toString()).trim();
        return "%s\n%s\n%s\n%s".formatted(contextString, uiElementIdInfo.trim(), uiElementDetails.trim(), knownIssues.trim());
    }

    private static String buildTargetElementDetailBlock(UiElement targetElement) {
        if (targetElement == null) {
            return "";
        }
        return """                
                Target UI element details:
                   - Name: %s
                   - Description: %s
                   - Location details: %s
                   - Parent UI element info: %s
                """.formatted(
                targetElement.name(),
                targetElement.description(),
                targetElement.locationDetails(),
                targetElement.parentElementSummary());
    }

    private static @NonNull String getPreconditionExecutionUserMessage(UiTestExecutionContext context, Procedure precondition,
                                                                       String testDataString, String relevantData,
                                                                       String elementId, List<String> failureHints,
                                                                       UiElement targetElement) {
        String elementDetailBlock = buildTargetElementDetailBlock(targetElement);
        return """
                The precondition you need to execute: %s.
                
                Relevant data for this precondition: %s
                
                %s.
                
                The screenshot follows.
                """.formatted(
                precondition.description(),
                relevantData.isBlank() ? testDataString : relevantData,
                buildExecutionContextString(context, elementId, failureHints, elementDetailBlock).trim());
    }

    UiTestStepResult executeSingleTestStep(UiTestExecutionContext context, TestStep testStep, Procedure atomic,
                                           String testDataString, AtomicStepExecutionContext execContext) {
        var actionInstruction = atomic.description();
        try {
            var executionStartTimestamp = now();
            LOG.info("Executing test step: {}", actionInstruction);
            var screenshot = captureScreen();
            context.setVisualState(new VisualState(screenshot));
            var actionResult = ((UiOperationExecutionResult<EmptyExecutionResult>) testStepActionAgent.executeAndGetResult(() -> {
                String userMessage = getTestStepActionUserMessage(context, atomic, testDataString, execContext.uiElementId(),
                        execContext.failureHints(), execContext.targetElement());
                return testStepActionAgent.execute(userMessage, singleImageContent(screenshot));
            }));
            budgetManager.resetToolCallUsage();

            if (!actionResult.isSuccess()) {
                var message = "There was an error while executing test step action '%s'. Please see agent logs for details"
                        .formatted(actionInstruction);
                LOG.warn("Test step failed: {}", message);
                return new UiTestStepResult(testStep, TestStepResultStatus.ERROR, message, null, captureScreen(),
                        executionStartTimestamp, now());
            }
            LOG.info("Action execution complete.");
            long actionDurationMs = java.time.Duration.between(executionStartTimestamp, now()).toMillis();

            var verificationInstruction =
                    execContext.effectiveExpectedResults() != null ? execContext.effectiveExpectedResults().trim() : "";

            boolean verificationNeeded = isNotBlank(verificationInstruction)
                    && !verificationInstruction.equalsIgnoreCase("null");

            if (verificationNeeded) {
                return verifyTestStep(context, testStep, atomic, actionInstruction, testDataString,
                        executionStartTimestamp, actionDurationMs, execContext);
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
                                                                String elementId, List<String> failureHints,
                                                                UiElement targetElement) {
        String elementDetailBlock = buildTargetElementDetailBlock(targetElement);
        return """
                Execute the following action: %s
                
                Data, related to the action: %s
                
                %s
                
                The screenshot follows.
                """.formatted(
                atomic.description(),
                testDataString,
                buildExecutionContextString(context, elementId, failureHints, elementDetailBlock).trim());
    }

    private UiTestStepResult verifyTestStep(UiTestExecutionContext context, TestStep testStep, Procedure atomic,
                                            String actionInstruction, String testDataString,
                                            Instant executionStartTimestamp, long actionDurationMs,
                                            AtomicStepExecutionContext execContext) {
        String verificationInstruction =
                execContext.effectiveExpectedResults() != null ? execContext.effectiveExpectedResults().trim() : "";
        LOG.info("Verifying that '{}'", verificationInstruction);

        long delayMs = TimingProfile.computeDelay(
                execContext.timingProfile(), uiTestAgentConfig.getTimingVerificationMinDelayMs(), actionVerificationDelayMillis
        );
        LOG.debug("Verification delay for '{}': {}ms ({})", atomic.description(), delayMs,
                execContext.timingProfile() != null ? "profile-driven" : "fallback default");
        sleepMillis(delayMs);

        var verificationResult = verificationTools.verifyTestStep(
                verificationInstruction, actionInstruction, testDataString, context, testStepVerificationAgent);
        budgetManager.resetToolCallUsage();

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
    private Optional<Procedure> processOutcomeAndRefresh(UserDecisionOutcome outcome, Procedure atomicStep,
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

    private void notifyVerificationFailure(String description, String failureMessage, BufferedImage screenshot) {
        if (!uiTestAgentConfig.isFullyUnattended()) {
            VerificationFailurePopup.display(description, failureMessage, screenshot, uiTestAgentConfig);
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
                                                         List<UiPreconditionResult> results) {
        if (results.isEmpty()) {
            var errorMessage = "Execution of precondition '%s' was aborted.".formatted(preconditionDescription);
            LOG.error(errorMessage);
            return new UiPreconditionResult(preconditionDescription, false, errorMessage, captureScreen(), now(), now());
        }
        var start = results.getFirst().getExecutionStartTimestamp();
        var end = results.getLast().getExecutionEndTimestamp();
        var failedResult = results.stream().filter(r -> !r.isSuccess()).findFirst();
        return failedResult.map(r ->
                        new UiPreconditionResult(preconditionDescription, false, r.getErrorMessage(), r.getScreenshot(), start, end))
                .orElseGet(() -> new UiPreconditionResult(preconditionDescription, true, null, null, start, end));
    }

    enum UserDecisionOutcome {
        CONTINUE_NEXT_STEP, TERMINATE_EXECUTION, RE_FETCH_AND_RETRY, RE_DECOMPOSE_AND_RETRY
    }

    // Result of pre-execution confirmation popup: either proceed to execute the step, or a terminal outcome from the halt dialog.
    private sealed interface PreExecutionCheckResult {
        record Proceed(Procedure procedure) implements PreExecutionCheckResult {
        }

        record TerminalOutcome(UserDecisionOutcome outcome) implements PreExecutionCheckResult {
        }
    }

    // Result of post-execution handling in supervised mode: proceed to next step, retry, or a terminal outcome.
    private sealed interface PostExecutionCheckResult {
        record ProceedToNext() implements PostExecutionCheckResult {
        }

        record RetryStep(Procedure procedure) implements PostExecutionCheckResult {
        }

        record TerminalOutcome(UserDecisionOutcome outcome) implements PostExecutionCheckResult {
        }
    }

    private sealed interface HaltHandlerResult {
        record ShouldProceed(UserDecisionOutcome outcome) implements HaltHandlerResult {
        }

        record ShouldRetry(Procedure updatedAtomicStep) implements HaltHandlerResult {
        }
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
