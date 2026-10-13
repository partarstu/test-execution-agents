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
package org.tarik.ta.knowledge_graph;

import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.dto.*;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.dto.ExecutionItemContext;
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.RerunOptions;
import org.tarik.ta.dto.RerunOptions.Halt;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.dto.UiPreconditionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.knowledge_graph.execution.AncestorChainRenderer;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.PreconditionItem;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem.TestStepItem;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.Procedure.TimingProfile;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.UiElementAliasRegistry;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.model.VisualStateHolder;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.panel.OperatorPrompts;
import org.tarik.ta.tools.VerificationTools;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static java.time.Duration.between;
import static java.time.Instant.now;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.*;
import static org.tarik.ta.dto.ProcedureExecutionConfirmationResult.Decision.HALTED;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.core.utils.CommonUtils.*;
import static org.tarik.ta.core.utils.MarkdownSections.*;
import static org.tarik.ta.core.model.TestExecutionContext.EXECUTION_CONTEXT_DATA_LABEL;
import static org.tarik.ta.core.model.TestExecutionContext.PRECONDITION_DATA_LABEL;
import static org.tarik.ta.utils.UserMessageSections.*;

@UiAgentRequestScope
public class StepExecutionOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(StepExecutionOrchestrator.class);
    static final String NO_VERIFICATION_REQUIRED = "No verification required";

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
    private final UiElementAliasRegistry uiElementAliasRegistry;
    private final VisualStateHolder visualState;
    private final OperatorPrompts operatorPrompts;
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
                                     UiPreconditionVerificationAgent preconditionVerificationAgent,
                                     UiElementAliasRegistry uiElementAliasRegistry,
                                     VisualStateHolder visualState,
                                     OperatorPrompts operatorPrompts) {
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
        this.uiElementAliasRegistry = uiElementAliasRegistry;
        this.visualState = visualState;
        this.operatorPrompts = operatorPrompts;
        this.actionVerificationDelayMillis = uiTestAgentConfig.getActionVerificationDelayMillis();
    }

    AtomicStepResult executeAtomicStep(ExecutionItem item, Procedure atomicStep,
                                       TestExecutionContext context,
                                       List<UiTestStepResult> testStepResults,
                                       List<UiPreconditionResult> preconditionResults,
                                       AtomicStepExecutionContext stepExecutionContext) {
        var relevantData = AncestorChainRenderer.resolveRelevantData(item, atomicStep, stepExecutionContext.ancestorChain());
        try {
            return switch (item) {
                case PreconditionItem preconditionItem -> {
                    var result = executeSinglePrecondition(context, preconditionItem, atomicStep, relevantData, stepExecutionContext);
                    preconditionResults.add(result);
                    yield result.isSuccess()
                            ? new AtomicStepResult.Success()
                            : new AtomicStepResult.VerificationFailure(atomicStep.description(),
                            result.getErrorMessage(), result.getScreenshot());
                }
                case TestStepItem testStepItem -> {
                    var testStep = testStepItem.testStep();
                    var stepResult = executeSingleTestStep(context, testStepItem, atomicStep, relevantData, stepExecutionContext);
                    testStepResults.add(stepResult);
                    yield switch (stepResult.getExecutionStatus()) {
                        case SUCCESS -> new AtomicStepResult.Success();
                        case FAILURE -> new AtomicStepResult.VerificationFailure(testStep.stepDescription(),
                                stepResult.getActualResult(), stepResult.getScreenshot());
                        case ERROR -> new AtomicStepResult.ExecutionError(stepResult.getErrorMessage(), null);
                    };
                }
            };
        } catch (RuntimeException e) {
            LOG.error("Unhandled exception during atomic step '{}': {}", atomicStep.description(), e.getMessage(), e);
            var errorScreenshot = captureScreen();
            visualState.set(new VisualState(errorScreenshot));
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
                                                       TestExecutionContext context,
                                                       List<UiTestStepResult> testStepResults,
                                                       List<UiPreconditionResult> preconditionResults,
                                                       AtomicStepExecutionContext stepExecutionContext,
                                                       Function<Procedure, AtomicStepExecutionContext> contextFactory) {
        boolean isPreconditionItem = item instanceof PreconditionItem;
        String itemDescription = item.getDescription();
        List<String> itemTestData = item.getTestData();
        String itemExpectedResults = item.getExpectedResults();
        var itemContext = new ExecutionItemContext(itemDescription, itemTestData, isPreconditionItem);
        // currentExecContext is rebuilt whenever atomicStep changes (e.g. after a supervised-mode atomic rerun),
        // ensuring the agent always sees the latest TARGETS relationship and element details.
        var currentExecContext = stepExecutionContext;
        var currentAtomicStep = atomicStep;

        // Step execution loop, automatically run only once; a rerun happens only if the user chooses to do so.
        while (true) {
            // Pre-execution user notification (supervised mode only)
            if (!uiTestAgentConfig.isFullyUnattended()) {
                var decision = operatorPrompts.confirmExecution(
                        currentAtomicStep.description(), ancestorDescriptions(currentExecContext), itemContext, true);
                if (decision.decision() == HALTED) {
                    LOG.info("User halted execution before step — prompting for next action");
                    var outcome = askUserForNextAction(
                            "Execution is about to start but you chose to halt. What would you like to do?",
                            currentAtomicStep, itemTestData, itemExpectedResults, itemDescription, isPreconditionItem,
                            false, Halt.BEFORE_EXECUTION, testCase);
                    switch (outcome) {
                        case UserDecisionOutcome.RerunAtomicProcedure(var procedure) -> {
                            currentAtomicStep = procedure;
                            currentExecContext = contextFactory.apply(procedure);
                            visualState.set(new VisualState(captureScreen()));
                        }
                        case UserDecisionOutcome.ContinueNextStep _, UserDecisionOutcome.TerminateExecution _,
                             UserDecisionOutcome.RerunTestStep _ -> {
                            return outcome;
                        }
                    }
                } else {
                    currentExecContext = contextFactory.apply(currentAtomicStep);
                    visualState.set(new VisualState(captureScreen()));
                }
            }

            // Track result list sizes before execution so a rerun can discard the failed attempt's result
            int testResultsSizeBefore = testStepResults.size();
            int preconditionResultsSizeBefore = preconditionResults.size();

            // Execution
            var result = executeAtomicStep(item, currentAtomicStep, context, testStepResults, preconditionResults, currentExecContext);

            // Handling result — short-circuit in unattended mode
            if (uiTestAgentConfig.isFullyUnattended()) {
                return result instanceof AtomicStepResult.Success ? new UserDecisionOutcome.ContinueNextStep()
                        : new UserDecisionOutcome.TerminateExecution();
            }

            // Post-execution user notification (supervised mode only)
            var outcome = handleResultInSupervisedMode(result, currentAtomicStep, currentExecContext, itemContext,
                    itemTestData, itemExpectedResults, itemDescription, isPreconditionItem, testCase);
            switch (outcome) {
                case UserDecisionOutcome.RerunAtomicProcedure(var procedure) -> {
                    // Discard this attempt's result so only the last rerun is recorded
                    testStepResults.subList(testResultsSizeBefore, testStepResults.size()).clear();
                    preconditionResults.subList(preconditionResultsSizeBefore, preconditionResults.size()).clear();
                    currentAtomicStep = procedure;
                    currentExecContext = contextFactory.apply(procedure);
                    visualState.set(new VisualState(captureScreen()));
                }
                case UserDecisionOutcome.ContinueNextStep _, UserDecisionOutcome.TerminateExecution _,
                     UserDecisionOutcome.RerunTestStep _ -> {
                    return outcome;
                }
            }
        }
    }

    /**
     * Post-execution handling in supervised mode: shows the post-execution confirmation for a successful step
     * (Proceed continues to the next step) or asks for the next action on failures and halts.
     */
    private UserDecisionOutcome handleResultInSupervisedMode(AtomicStepResult result, Procedure atomicStep,
                                                             AtomicStepExecutionContext stepExecutionContext,
                                                             ExecutionItemContext itemContext, List<String> itemTestData,
                                                             String itemExpectedResults, String itemDescription,
                                                             boolean isPreconditionItem, TestCase testCase) {
        return switch (result) {
            case AtomicStepResult.Success _ -> {
                var decision = operatorPrompts.confirmExecution(
                        atomicStep.description(), ancestorDescriptions(stepExecutionContext), itemContext, false);
                if (decision.decision() != HALTED) {
                    yield new UserDecisionOutcome.ContinueNextStep();
                }
                LOG.info("User halted execution after success — prompting for next action");
                yield askUserForNextAction("Execution succeeded but you chose to halt. What would you like to do?",
                        atomicStep, itemTestData, itemExpectedResults, itemDescription, isPreconditionItem,
                        true, Halt.AFTER_SUCCESS, testCase);
            }
            case AtomicStepResult.VerificationFailure f -> askUserForNextAction(
                    "Verification failed for '%s': %s".formatted(f.description(), f.reason()),
                    atomicStep, itemTestData, itemExpectedResults, itemDescription, isPreconditionItem,
                    true, Halt.NONE, testCase);
            case AtomicStepResult.ExecutionError error -> {
                var errorMessage = error.details();
                var errorScreenshot = captureScreen();
                visualState.set(new VisualState(errorScreenshot));
                operatorPrompts.showMessage("Error During Execution", errorMessage, errorScreenshot, PopupType.ERROR);
                yield askUserForNextAction(errorMessage, atomicStep, itemTestData, itemExpectedResults,
                        itemDescription, isPreconditionItem, true, Halt.NONE, testCase);
            }
        };
    }

    /**
     * Asks the user for the next action after a failure or halt in rerun mode, persisting dialog edits and reopening the dialog
     * until an explicit rerun or proceed, which refetches the procedure first, a continue after a halt, or terminate.
     */
    private UserDecisionOutcome askUserForNextAction(String message, Procedure atomicStep, List<String> testData,
                                                     String expectedResults, String itemDescription,
                                                     boolean isPreconditionItem, boolean atomicRerunAvailable, Halt halt,
                                                     TestCase testCase) {
        var itemContext = new ExecutionItemContext(itemDescription, testData, isPreconditionItem);
        // A procedure created in this dialog session replaces the current root when the whole test step is rerun
        @Nullable UUID createdProcedureId = null;
        while (true) {
            var selectionOpt = operatorPrompts.chooseNextAction(message, atomicStep.description(), knowledgeService,
                    new RerunOptions(isPreconditionItem, atomicRerunAvailable, halt));

            if (selectionOpt.isEmpty()) {
                LOG.info("User closed the dialog — terminating execution");
                return new UserDecisionOutcome.TerminateExecution();
            }

            var selection = selectionOpt.get();
            switch (selection.action()) {
                case CONTINUE -> {
                    LOG.info("User chose to continue with the next step after '{}'", atomicStep.description());
                    return new UserDecisionOutcome.ContinueNextStep();
                }
                case RERUN_ATOMIC_PROCEDURE, PROCEED -> {
                    LOG.info("User chose {} for the atomic procedure '{}'", selection.action(), atomicStep.description());
                    var refetched = knowledgeService.findById(atomicStep.id()).filter(Procedure::isAtomic);
                    if (refetched.isPresent()) {
                        return new UserDecisionOutcome.RerunAtomicProcedure(refetched.get());
                    }
                    operatorPrompts.showMessage("Atomic Procedure Not Available",
                            "Procedure '%s' is no longer atomic or has been deleted. The whole test step will be rerun instead."
                                    .formatted(atomicStep.description()), null, PopupType.WARNING);
                    return new UserDecisionOutcome.RerunTestStep(createdProcedureId);
                }
                case RERUN_TEST_STEP -> {
                    LOG.info("User chose to rerun the whole test step for '{}'", atomicStep.description());
                    return new UserDecisionOutcome.RerunTestStep(createdProcedureId);
                }
                case BROWSE -> {
                    LOG.info("User chose to edit procedure '{}'", atomicStep.description());
                    var editResult = procedureKnowledgeCollectionService.triggerEditProcedureFlow(
                            selection.selectedProcedure(), testData, expectedResults,
                            itemContext, testCase);
                    if (editResult.isSaved()) {
                        knowledgeIngestionService.update(editResult.savedProcedureId().get(), editResult.updatedNode().get());
                        knowledgeService.onKnowledgeIngested();
                    }
                    // Saved or cancelled — the dialog is reopened for an explicit next action
                }
                case CREATE -> {
                    LOG.info("User chose to create a new procedure for '{}'", atomicStep.description());
                    var creationResult = procedureKnowledgeCollectionService.triggerNewProcedureFlow(itemDescription,
                            testData, expectedResults, isPreconditionItem, testCase);
                    if (creationResult.isPresent()) {
                        createdProcedureId = knowledgeIngestionService.ingest(creationResult.get());
                        knowledgeService.onKnowledgeIngested();
                        LOG.info("New procedure created and ingested for '{}'", atomicStep.description());
                    }
                    // Created or cancelled — the dialog is reopened for an explicit next action
                }
                // RETRY is never offered in rerun mode; CANCEL means the dialog was closed
                case RETRY, CANCEL -> {
                    return new UserDecisionOutcome.TerminateExecution();
                }
            }
        }
    }

    UiPreconditionResult executeSinglePrecondition(TestExecutionContext context,
                                                   PreconditionItem item,
                                                   Procedure precondition,
                                                   String relevantData,
                                                   AtomicStepExecutionContext stepExecutionContext) {
        var executionStartTimestamp = now();
        LOG.info("Executing precondition: {}", precondition.description());
        var screenshot = captureScreen();
        visualState.set(new VisualState(screenshot));
        var preconditionOperationResult = preconditionActionAgent.executeAndGetResult(
                () -> {
                    String userMessage = getPreconditionExecutionUserMessage(context, item, precondition, relevantData,
                            uiElementAliasRegistry.aliasFor(stepExecutionContext.uiElementId()), stepExecutionContext);
                    return preconditionActionAgent.execute(userMessageContents(userMessage),
                            preconditionActionAgent.getImageContentCreator(uiTestAgentConfig).createImageContent(screenshot));
                });
        budgetManager.resetToolCallUsage();

        if (!preconditionOperationResult.isSuccess()) {
            var errorMessage = "Failure while executing precondition '%s'. Root cause: %s"
                    .formatted(precondition.description(), preconditionOperationResult.getMessage());
            return new UiPreconditionResult(precondition.description(), false, errorMessage, captureScreen(),
                    executionStartTimestamp, now());
        }
        var preconditionPayload = preconditionOperationResult.getResultPayload();
        if (preconditionPayload != null && !preconditionPayload.executionSuccess()) {
            var errorMessage = "Precondition '%s' execution reported failure. Root cause: %s"
                    .formatted(precondition.description(), preconditionPayload.message());
            LOG.warn("{}", errorMessage);
            return new UiPreconditionResult(precondition.description(), false, errorMessage, captureScreen(),
                    executionStartTimestamp, now());
        }
        if (preconditionPayload != null && isNotBlank(preconditionPayload.message())) {
            LOG.info("Precondition agent note: {}", preconditionPayload.message());
        }
        LOG.info("Precondition execution complete.");

        long actionDurationMs = between(executionStartTimestamp, now()).toMillis();
        LOG.info("Verifying if precondition was successfully executed.");
        var verificationResult = verificationTools.verifyPrecondition(
                precondition.description(), context, visualState, preconditionVerificationAgent, relevantData,
                AncestorChainRenderer.renderForVerification(stepExecutionContext.ancestorChain(), item, precondition.additionalInfo()));
        budgetManager.resetToolCallUsage();

        if (verificationResult == null) {
            var errorMessage = "Precondition verification failed. Got no verification result from the model.";
            return new UiPreconditionResult(precondition.description(), false, errorMessage,
                    visualState.get().screenshot(), executionStartTimestamp, now());
        }
        if (!verificationResult.success()) {
            var errorMessage = "Precondition verification failed. %s".formatted(verificationResult.message());
            notifyVerificationFailure(precondition.description(), verificationResult.message(), visualState.get().screenshot());
            return new UiPreconditionResult(precondition.description(), false, errorMessage,
                    visualState.get().screenshot(), executionStartTimestamp, now());
        }
        LOG.info("Precondition '{}' is met.", precondition.description());
        stepExecutionContext.timingRecorder().record(precondition.id(), actionDurationMs, 0);
        return new UiPreconditionResult(precondition.description(), true, null, null, executionStartTimestamp, now());
    }

    private static List<String> ancestorDescriptions(AtomicStepExecutionContext stepExecutionContext) {
        return stepExecutionContext.ancestorChain().stream().map(Procedure::description).toList();
    }

    private static String knownExecutionIssues(@Nullable List<String> failureHints) {
        return section(SECTION_LEVEL, "Known issues with this procedure", failureHints == null ? "" : bulletList(failureHints));
    }

    private static String buildExecutionContextString(TestExecutionContext context, @Nullable String elementAlias,
                                                      AtomicStepExecutionContext stepExecutionContext) {
        return joinSections(section(SECTION_LEVEL, EXECUTION_CONTEXT_DATA_LABEL, context.renderSharedData()),
                section(SECTION_LEVEL, TARGET_ELEMENT_ID_LABEL, elementAlias),
                buildTargetElementDetailBlock(stepExecutionContext.targetElement()),
                buildLocationHistoryBlock(stepExecutionContext.locationHistory()),
                knownExecutionIssues(stepExecutionContext.failureHints()));
    }

    private static String buildLocationHistoryBlock(@Nullable ElementLocationHistory history) {
        if (history == null) {
            return "";
        }
        return section(SECTION_LEVEL, "Target UI element location history", joinLines(
                listItem("Stability score", history.stabilityScore()),
                listItem("Average location time", "%s ms".formatted(history.avgLocationTimeMs())),
                listItem("Average retries count", history.locationRetriesCount())));
    }

    private static String buildTargetElementDetailBlock(@Nullable UiElement targetElement) {
        if (targetElement == null) {
            return "";
        }
        return section(SECTION_LEVEL, "Target UI element details", joinLines(
                listItem("Name", targetElement.name()),
                listItem("Description", targetElement.description()),
                listItem("Location details", targetElement.locationDetails()),
                listItem("Enclosing component (the component containing this UI element, e.g. a page, dialog, form, panel or pop-up)",
                        targetElement.parentElementSummary()),
                listItem("Depends on data", targetElement.isDataDependent() ? "yes" : "no")));
    }

    private static @NonNull String getPreconditionExecutionUserMessage(TestExecutionContext context, PreconditionItem item,
                                                                       Procedure precondition, String relevantData,
                                                                       String elementAlias,
                                                                       AtomicStepExecutionContext stepExecutionContext) {
        return joinSections(
                "Execute the precondition from the \"%s\" section.".formatted(PRECONDITION_LABEL),
                section(SECTION_LEVEL, PRECONDITION_LABEL, precondition.description()),
                section(SECTION_LEVEL, PRECONDITION_DATA_LABEL, relevantData),
                section(SECTION_LEVEL, ADDITIONAL_INFO_LABEL, precondition.additionalInfo()),
                AncestorChainRenderer.renderChain(stepExecutionContext.ancestorChain(), item),
                buildExecutionContextString(context, elementAlias, stepExecutionContext),
                heading(SECTION_LEVEL, SCREENSHOT_LABEL));
    }

    UiTestStepResult executeSingleTestStep(TestExecutionContext context, TestStepItem item, Procedure atomic,
                                           String testDataString, AtomicStepExecutionContext stepExecutionContext) {
        var testStep = item.testStep();
        var actionInstruction = atomic.description();
        try {
            var executionStartTimestamp = now();
            LOG.info("Executing test step: {}", actionInstruction);
            var screenshot = captureScreen();
            visualState.set(new VisualState(screenshot));
            var actionResult = ((UiOperationExecutionResult<EmptyExecutionResult>) testStepActionAgent.executeAndGetResult(() -> {
                String userMessage = getTestStepActionUserMessage(context, item, atomic, testDataString,
                        uiElementAliasRegistry.aliasFor(stepExecutionContext.uiElementId()), stepExecutionContext);
                return testStepActionAgent.execute(userMessageContents(userMessage),
                        testStepActionAgent.getImageContentCreator(uiTestAgentConfig).createImageContent(screenshot));
            }));
            budgetManager.resetToolCallUsage();

            if (!actionResult.isSuccess()) {
                var message = "There was an error while executing test step action '%s': %s"
                        .formatted(actionInstruction, actionResult.getMessage());
                LOG.warn("Test step failed: {}", message);
                return new UiTestStepResult(testStep, TestStepResultStatus.ERROR, message, null, captureScreen(),
                        executionStartTimestamp, now());
            }
            var actionPayload = actionResult.getResultPayload();
            if (actionPayload != null && !actionPayload.executionSuccess()) {
                var failureMessage = "Test step action '%s' reported failure. Root cause: %s"
                        .formatted(actionInstruction, actionPayload.message());
                LOG.warn("{}", failureMessage);
                return new UiTestStepResult(testStep, TestStepResultStatus.ERROR, failureMessage, null, captureScreen(),
                        executionStartTimestamp, now());
            }
            if (actionPayload != null && isNotBlank(actionPayload.message())) {
                LOG.info("Action agent note: {}", actionPayload.message());
            }
            LOG.info("Action execution complete.");
            long actionDurationMs = between(executionStartTimestamp, now()).toMillis();

            var verificationInstruction =
                    stepExecutionContext.effectiveExpectedResults() != null ? stepExecutionContext.effectiveExpectedResults().trim() : "";

            boolean verificationNeeded = isNotBlank(verificationInstruction)
                    && !verificationInstruction.equalsIgnoreCase("null");

            if (verificationNeeded) {
                return verifyTestStep(context, item, atomic, actionInstruction, testDataString, screenshot,
                        executionStartTimestamp, actionDurationMs, stepExecutionContext);
            } else {
                stepExecutionContext.timingRecorder().record(atomic.id(), actionDurationMs, 0);
                return new UiTestStepResult(testStep, SUCCESS, null, NO_VERIFICATION_REQUIRED, null,
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

    private static @NonNull String getTestStepActionUserMessage(TestExecutionContext context, TestStepItem item, Procedure atomic,
                                                                String testDataString, String elementAlias,
                                                                AtomicStepExecutionContext stepExecutionContext) {
        return joinSections(
                "Execute the action from the \"%s\" section.".formatted(ACTION_LABEL),
                section(SECTION_LEVEL, ACTION_LABEL, atomic.description()),
                section(SECTION_LEVEL, ACTION_DATA_LABEL, testDataString),
                section(SECTION_LEVEL, ADDITIONAL_INFO_LABEL, atomic.additionalInfo()),
                AncestorChainRenderer.renderChain(stepExecutionContext.ancestorChain(), item),
                buildExecutionContextString(context, elementAlias, stepExecutionContext),
                heading(SECTION_LEVEL, SCREENSHOT_LABEL));
    }

    private UiTestStepResult verifyTestStep(TestExecutionContext context, TestStepItem item, Procedure atomic,
                                            String actionInstruction, String testDataString,
                                            BufferedImage beforeActionScreenshot,
                                            Instant executionStartTimestamp, long actionDurationMs,
                                            AtomicStepExecutionContext stepExecutionContext) {
        String verificationInstruction =
                stepExecutionContext.effectiveExpectedResults() != null ? stepExecutionContext.effectiveExpectedResults().trim() : "";
        LOG.info("Verifying that '{}'", verificationInstruction);

        long delayMs = TimingProfile.computeDelay(
                stepExecutionContext.timingProfile(), uiTestAgentConfig.getTimingVerificationMinDelayMs(), actionVerificationDelayMillis
        );
        LOG.debug("Verification delay for '{}': {}ms ({})", atomic.description(), delayMs,
                stepExecutionContext.timingProfile() != null ? "profile-driven" : "fallback default");
        sleepMillis(delayMs);

        var verificationResult = verificationTools.verifyTestStep(
                verificationInstruction, actionInstruction, testDataString, context, visualState, beforeActionScreenshot,
                testStepVerificationAgent,
                AncestorChainRenderer.renderForVerification(stepExecutionContext.ancestorChain(), item, atomic.additionalInfo()));
        budgetManager.resetToolCallUsage();

        var testStep = item.testStep();
        if (verificationResult == null) {
            var message = "No verification result returned.";
            LOG.warn("Something went wrong. {}", message);
            return new UiTestStepResult(testStep, ERROR, message, null, visualState.get().screenshot(), executionStartTimestamp,
                    now());
        }

        if (!verificationResult.success()) {
            var generalMessage = ("Verification failed. %s".formatted(verificationResult.message())).trim();
            LOG.warn("Interrupting test case execution because the verification failed. {}", verificationResult.message());
            notifyVerificationFailure(verificationInstruction, verificationResult.message(), visualState.get().screenshot());
            return new UiTestStepResult(testStep, TestStepResultStatus.FAILURE, generalMessage,
                    verificationResult.message(), visualState.get().screenshot(), executionStartTimestamp, now());
        } else {
            LOG.info("Verification succeeded.");
            stepExecutionContext.timingRecorder().record(atomic.id(), actionDurationMs, delayMs);
            return new UiTestStepResult(testStep, SUCCESS, null, verificationResult.message(), null,
                    executionStartTimestamp, now());
        }
    }

    /**
     * Surfaces the standard supervised-mode failure dialog for a decomposition error (composite procedure with no children).
     * Maps the user's choice to a {@link UserDecisionOutcome} so the caller can decide the next {@code ExecutionFlow}.
     */
    UserDecisionOutcome handleDecompositionFailureInSupervisedMode(String message, Procedure procedure,
                                                                   ExecutionItem item, TestCase testCase) {
        var outcome = askUserForNextAction(message, procedure,
                item.getTestData(), item.getExpectedResults(), item.getDescription(),
                item instanceof PreconditionItem, false, Halt.NONE, testCase);
        // An atomic rerun is never offered for a decomposition failure; rerunning the whole step is the safe mapping
        return outcome instanceof UserDecisionOutcome.RerunAtomicProcedure
                ? new UserDecisionOutcome.RerunTestStep(null)
                : outcome;
    }

    UserDecisionOutcome handleUnsatisfiedPrerequisitesInSupervisedMode(String message, Procedure atomicStep,
                                                                       ExecutionItem item, TestCase testCase) {
        return askUserForNextAction(message, atomicStep, item.getTestData(), item.getExpectedResults(),
                item.getDescription(), item instanceof PreconditionItem, true, Halt.NONE, testCase);
    }

    private void notifyVerificationFailure(String description, String failureMessage, BufferedImage screenshot) {
        if (!uiTestAgentConfig.isFullyUnattended()) {
            operatorPrompts.showVerificationFailure(description, failureMessage, screenshot);
        } else {
            LOG.warn("Verification failed: {} — {}", description, failureMessage);
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