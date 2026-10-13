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
package org.tarik.ta.panel;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.dto.ExecutionItemContext;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.ProcedureDialogOutcome;
import org.tarik.ta.dto.ProcedureExecutionConfirmationResult;
import org.tarik.ta.dto.RerunOptions;
import org.tarik.ta.dto.UserSelectionResult;
import org.tarik.ta.dto.UserSelectionResult.SelectionAction;
import org.tarik.ta.dto.panel.ProcedureNode;
import org.tarik.ta.dto.panel.ProcedureOutcome;
import org.tarik.ta.dto.panel.RunStatus;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ProcedureSearch;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Acknowledgement;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Confirmation;
import org.tarik.ta.dto.panel.prompt.PromptPayload.FailureContextCapture;
import org.tarik.ta.dto.panel.prompt.PromptPayload.FailureContextCapture.Choice;
import org.tarik.ta.dto.panel.prompt.PromptPayload.ItemView;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Message;
import org.tarik.ta.dto.panel.prompt.PromptPayload.NextAction;
import org.tarik.ta.dto.panel.prompt.PromptPayload.ProcedureEditor;
import org.tarik.ta.dto.panel.prompt.PromptPayload.ProcedureEditor.Outcome;
import org.tarik.ta.dto.panel.prompt.PromptPayload.StartStep;
import org.tarik.ta.dto.panel.prompt.PromptPayload.StartStep.StepRow;
import org.tarik.ta.dto.panel.prompt.PromptPayload.VerificationFailure;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;
import org.tarik.ta.panel.ProcedureBrowser.SearchScope;
import org.tarik.ta.panel.ProcedureEditorPrompt.EditorServices;
import org.tarik.ta.tools.UiElementRefinementHelper;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.tarik.ta.knowledge_graph.ProcedureSelectionRules.defaultDecision;
import static org.tarik.ta.knowledge_graph.ProcedureSelectionRules.offeredActions;
import static org.tarik.ta.knowledge_graph.ProcedureSelectionRules.toSelectionResult;
import static org.tarik.ta.panel.PromptInput.present;
import static org.tarik.ta.panel.PromptInput.require;
import static org.tarik.ta.panel.PromptInput.text;

/**
 * The operator panel as the run sees it: it follows the run for the timeline and asks every operator question as a prompt in the
 * browser. A prompt blocks the run until it is answered or its deadline applies the default; one prompt is open at a time.
 */
@Singleton
final class WebOperatorPanel implements RunObserver, OperatorPrompts {
    private static final Logger LOG = LoggerFactory.getLogger(WebOperatorPanel.class);
    private static final String UNKNOWN_SYMPTOM = "Unknown failure";

    private final ExecutionStateBroadcaster broadcaster;
    private final UiTestAgentConfig config;
    private final EditorServices editorServices;
    private final InstantSource clock;
    private final Duration maxWait;
    private final AtomicReference<PendingPrompt<?>> pending = new AtomicReference<>();
    private final AtomicReference<BroadcastingEventEmitter> runEmitter = new AtomicReference<>();

    @Inject
    WebOperatorPanel(ExecutionStateBroadcaster broadcaster, UiTestAgentConfig config, KnowledgeIngestionService ingestionService,
                     UiElementRepository uiElementRepository, ProcedureUsageByTestCaseTrackingService usageTrackingService,
                     UiElementRefinementHelper refinementHelper) {
        this(broadcaster, config, new EditorServices(ingestionService, uiElementRepository, usageTrackingService, refinementHelper),
                InstantSource.system(), config.getPanelPromptMaxWait());
    }

    WebOperatorPanel(@NotNull ExecutionStateBroadcaster broadcaster, @NotNull UiTestAgentConfig config,
                     @NotNull EditorServices editorServices, @NotNull InstantSource clock, @NotNull Duration maxWait) {
        this.broadcaster = broadcaster;
        this.config = config;
        this.editorServices = editorServices;
        this.clock = clock;
        this.maxWait = maxWait;
    }

    @Override
    public void taskStarted(@NotNull TestCase testCase) {
        broadcaster.taskStarted(testCase);
    }

    @Override
    public void taskFinished(@NotNull TestExecutionResult result) {
        runEmitter.set(null);
        var status = switch (result.getTestExecutionStatus()) {
            case PASSED -> RunStatus.PASSED;
            case FAILED -> RunStatus.FAILED;
            case ERROR -> RunStatus.ERROR;
        };
        broadcaster.taskFinished(status, result.getGeneralErrorMessage());
    }

    @Override
    public void taskAborted() {
        runEmitter.set(null);
        broadcaster.taskFinished(RunStatus.ABORTED, null);
    }

    @Override
    public @NotNull StreamingEventEmitter observe(@NotNull StreamingEventEmitter a2aEmitter) {
        var emitter = new BroadcastingEventEmitter(a2aEmitter, broadcaster);
        runEmitter.set(emitter);
        return emitter;
    }

    @Override
    public void itemOutlined(@NotNull List<String> testData, @NotNull List<ProcedureNode> procedures) {
        broadcaster.itemOutlined(testData, procedures);
    }

    @Override
    public void procedureStarted(@NotNull UUID procedureId) {
        broadcaster.procedureStarted(procedureId);
    }

    @Override
    public void procedureFinished(@NotNull UUID procedureId, @NotNull ProcedureOutcome outcome) {
        broadcaster.procedureFinished(procedureId, outcome);
    }

    @Override
    public void stepResolved(@NotNull StepResolution resolution) {
        broadcaster.stepResolved(resolution);
    }

    @Override
    public int selectStartingStep(@NotNull List<TestStep> testSteps) {
        if (testSteps.isEmpty()) {
            return 0;
        }
        var rows = testSteps.stream()
                .map(step -> new StepRow(step.stepDescription(), requireNonNullElse(step.testData(), List.of()), step.expectedResults()))
                .toList();
        var answer = ask(PromptSpec.of(new StartStep(rows), StartStep.Answer.class, new PromptImages())
                .withCheck(selected -> require(selected.stepIndex() >= StartStep.Answer.ABORT && selected.stepIndex() < rows.size(),
                        "There is no test step %d".formatted(selected.stepIndex() + 1))));
        return answer.stepIndex();
    }

    @Override
    public @NotNull ProcedureExecutionConfirmationResult confirmExecution(@NotNull String procedureDescription,
                                                                          @NotNull List<String> ancestorDescriptions,
                                                                          @NotNull ExecutionItemContext itemContext,
                                                                          boolean beforeExecution) {
        var payload = new Confirmation(procedureDescription, ancestorDescriptions, itemView(itemContext), beforeExecution);
        return ask(PromptSpec.of(payload, ProcedureExecutionConfirmationResult.class, new PromptImages())
                .withDefault(defaultDecision())
                .withCountdown(Duration.ofSeconds(config.getSupervisedCountdownSeconds()))
                .withCheck(decision -> present(decision.decision(), "The decision")));
    }

    @Override
    public @NotNull Optional<UserSelectionResult> chooseProcedure(@NotNull String message, @NotNull String itemDescription,
                                                                  @NotNull KnowledgeService knowledgeService,
                                                                  @NotNull Set<UUID> effectNodeIds, @NotNull Set<UUID> recentParentIds,
                                                                  boolean isPrecondition) {
        var scope = new SearchScope(Set.of(), effectNodeIds, recentParentIds, isPrecondition);
        return chooseAction(new NextAction(message, itemDescription, isPrecondition, offeredActions(null)), knowledgeService, scope);
    }

    @Override
    public @NotNull Optional<UserSelectionResult> chooseNextAction(@NotNull String message, @NotNull String itemDescription,
                                                                   @NotNull KnowledgeService knowledgeService,
                                                                   @NotNull RerunOptions rerunOptions) {
        var precondition = rerunOptions.isPrecondition();
        var scope = new SearchScope(Set.of(), Set.of(), Set.of(), precondition);
        return chooseAction(new NextAction(message, itemDescription, precondition, offeredActions(rerunOptions)), knowledgeService, scope);
    }

    @Override
    public void showMessage(@NotNull String title, @NotNull String message, @Nullable BufferedImage screenshot,
                            @NotNull PopupType type) {
        var images = new PromptImages();
        var payload = new Message(title, message, type, screenshot == null ? null : images.addScreen(screenshot));
        ask(PromptSpec.of(payload, Acknowledgement.class, images).withDefault(new Acknowledgement()));
    }

    @Override
    public void showVerificationFailure(@NotNull String verificationDescription, @NotNull String failureReason,
                                        @Nullable BufferedImage screenshot) {
        var images = new PromptImages();
        var payload = new VerificationFailure(verificationDescription, failureReason,
                screenshot == null ? null : images.addScreen(screenshot));
        ask(PromptSpec.of(payload, Acknowledgement.class, images).withDefault(new Acknowledgement()));
    }

    @Override
    public @NotNull Optional<FailureContext> captureFailureContext(@NotNull ErrorCategory preSelectedCategory,
                                                                   @NotNull String preFilledSymptom) {
        var payload = new FailureContextCapture(preSelectedCategory, preFilledSymptom, List.of(ErrorCategory.values()));
        var answer = ask(PromptSpec.of(payload, FailureContextCapture.Answer.class, new PromptImages())
                .withDefault(new FailureContextCapture.Answer(Choice.DISMISS, null, null, null))
                .withCountdown(Duration.ofSeconds(config.getFailureContextDialogCountdownSeconds()))
                .withCheck(WebOperatorPanel::checkFailureContext));
        if (answer.choice() == Choice.DISMISS) {
            LOG.info("The operator captured no failure context");
            return Optional.empty();
        }
        var symptom = requireNonNullElse(answer.symptom(), "").trim();
        var resolution = requireNonNullElse(answer.resolution(), "").trim();
        return Optional.of(new FailureContext(UUID.randomUUID(), symptom.isEmpty() ? UNKNOWN_SYMPTOM : symptom, answer.category(),
                resolution, 1, clock.instant(), FailureContext.Mode.SUPERVISED));
    }

    @Override
    public @NotNull Optional<IngestionNode> createProcedure(@NotNull ProcedureEditorSession session,
                                                            @NotNull List<String> precedingEffects) {
        var images = new PromptImages();
        var editor = new ProcedureEditorPrompt(session, () -> precedingEffects, null, editorServices, images);
        var node = editor.newNode(initialSuggestions(session));
        var answer = ask(editorSpec(new ProcedureEditor(true, itemView(session.itemContext()), node), editor, images));
        return answer.outcome() == Outcome.SAVE
                ? Optional.of(editor.toIngestionNode(present(answer.node(), "The saved procedure")))
                : Optional.empty();
    }

    @Override
    public @NotNull ProcedureDialogOutcome editProcedure(@NotNull ProcedureEditorSession session, @NotNull Procedure procedure,
                                                         @NotNull Supplier<List<String>> precedingEffects) {
        var images = new PromptImages();
        var editor = new ProcedureEditorPrompt(session, precedingEffects, procedure.id(), editorServices, images);
        var payload = new ProcedureEditor(false, itemView(session.itemContext()), editor.nodeOf(procedure));
        var answer = ask(editorSpec(payload, editor, images));
        return switch (answer.outcome()) {
            case SAVE -> {
                var saved = present(answer.node(), "The saved procedure");
                yield ProcedureDialogOutcome.saved(present(saved.id(), "The saved procedure"), editor.toIngestionNode(saved));
            }
            case DELETE -> {
                var deletedId = present(answer.procedureId(), "The deleted procedure");
                editor.delete(deletedId);
                yield ProcedureDialogOutcome.deletedOutcome(deletedId);
            }
            case CANCEL -> ProcedureDialogOutcome.cancelledOutcome();
        };
    }

    @Override
    public void showStatus(@NotNull String message) {
        broadcaster.status(message);
    }

    @Override
    public void clearStatus() {
        broadcaster.status(null);
    }

    @NotNull Optional<PendingPrompt<?>> pendingPrompt(@NotNull UUID id) {
        PendingPrompt<?> prompt = pending.get();
        return prompt != null && prompt.id().equals(id) ? Optional.of(prompt) : Optional.empty();
    }

    /** Holds the countdown of the open prompt, which then waits until the prompt upper bound; false when there is none to hold. */
    boolean hold(@NotNull PendingPrompt<?> prompt) {
        if (!prompt.hold(() -> publishOpened(prompt))) {
            return false;
        }
        LOG.info("Operator prompt {} ({}) held until {}", prompt.id(), prompt.spec().type(), prompt.deadline());
        return true;
    }

    /** Pauses the running countdown of the open prompt until {@link #resume}; false when there is none to pause. */
    boolean pause(@NotNull PendingPrompt<?> prompt) {
        return prompt.pause(clock.instant(), () -> publishOpened(prompt));
    }

    /** Resumes the paused countdown of the open prompt with the time it had left; false when it is not paused. */
    boolean resume(@NotNull PendingPrompt<?> prompt) {
        return prompt.resume(clock.instant(), () -> publishOpened(prompt));
    }

    /**
     * Shows the prompt and blocks until it is answered or its deadline passes, when the default answer applies or, for a prompt
     * without one, {@link OperatorPromptTimeoutException} fails the run.
     */
    <A> A ask(@NotNull PromptSpec<A> spec) {
        if (config.isFullyUnattended()) {
            throw new IllegalStateException("No operator can answer the %s prompt in unattended mode".formatted(spec.type()));
        }
        var openedAt = clock.instant();
        var prompt = new PendingPrompt<>(UUID.randomUUID(), spec, openedAt, maxWait);
        if (!pending.compareAndSet(null, prompt)) {
            throw new IllegalStateException("The %s prompt cannot open while another prompt is pending".formatted(spec.type()));
        }
        LOG.info("Operator prompt {} ({}) opened", prompt.id(), spec.type());
        try {
            publishOpened(prompt);
            onRunEmitter(emitter -> emitter.emitA2aStatus("Waiting for operator: %s".formatted(spec.type())));
            return await(prompt);
        } finally {
            close(prompt);
            LOG.info("Operator prompt {} ({}) closed after {} ms", prompt.id(), spec.type(),
                    Duration.between(openedAt, clock.instant()).toMillis());
        }
    }

    private <A> A await(PendingPrompt<A> prompt) {
        var answer = prompt.answer();
        while (true) {
            long remainingMillis = Duration.between(clock.instant(), prompt.deadline()).toMillis();
            if (remainingMillis <= 0) {
                return expire(prompt);
            }
            try {
                return answer.get(prompt.waitAt(clock.instant()).toMillis(), MILLISECONDS);
            } catch (TimeoutException _) {
                // The deadline may have moved while waiting, e.g. by a hold or a pause, so it is read again
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                LOG.info("Operator prompt {} ({}) interrupted", prompt.id(), prompt.spec().type());
                throw new OperatorPromptInterruptedException("The run was interrupted while the %s prompt waited for the operator"
                        .formatted(prompt.spec().type()));
            } catch (ExecutionException e) {
                return rethrow(e.getCause());
            }
        }
    }

    // An answer that arrives together with the deadline either wins the future or is rejected, never accepted and ignored
    private <A> A expire(PendingPrompt<A> prompt) {
        var spec = prompt.spec();
        var defaultAnswer = spec.defaultAnswer();
        if (defaultAnswer != null) {
            prompt.answer().complete(defaultAnswer);
        } else {
            prompt.answer().completeExceptionally(new OperatorPromptTimeoutException(
                    "Nobody answered the %s prompt within %d minutes".formatted(spec.type(), maxWait.toMinutes())));
        }
        LOG.info("Operator prompt {} ({}) reached its deadline", prompt.id(), spec.type());
        if (prompt.answer().isCompletedExceptionally()) {
            return rethrow(prompt.answer().exceptionNow());
        }
        return prompt.answer().resultNow();
    }

    private static <A> A rethrow(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException(cause);
    }

    private void close(PendingPrompt<?> prompt) {
        prompt.close(Duration.ofMillis(config.getElementResolutionMaxDurationMillis()));
        pending.compareAndSet(prompt, null);
        broadcaster.promptClosed(prompt.id());
        onRunEmitter(BroadcastingEventEmitter::reemitLastStartedItem);
    }

    private void publishOpened(PendingPrompt<?> prompt) {
        broadcaster.promptOpened(prompt);
    }

    private void onRunEmitter(Consumer<BroadcastingEventEmitter> action) {
        var emitter = runEmitter.get();
        if (emitter != null) {
            action.accept(emitter);
        }
    }

    private Optional<UserSelectionResult> chooseAction(NextAction payload, KnowledgeService knowledgeService, SearchScope scope) {
        var browser = new ProcedureBrowser(knowledgeService);
        var actions = PromptActions.builder()
                .add("search-procedures", ProcedureSearch.class, search -> browser.search(search.query(), search.showAllTypes(), scope))
                .build();
        var answer = ask(PromptSpec.of(payload, NextAction.Answer.class, new PromptImages())
                .withActions(actions)
                .withCheck(choice -> checkNextAction(choice, payload, browser)));
        var procedure = answer.action() == SelectionAction.BROWSE
                ? browser.offered(present(answer.procedureId(), "The procedure")).orElseThrow()
                : null;
        if (answer.action() == SelectionAction.CANCEL) {
            LOG.info("The operator terminated the run at '{}'", payload.itemDescription());
        }
        return toSelectionResult(answer.action(), procedure);
    }

    private static void checkNextAction(NextAction.Answer choice, NextAction payload, ProcedureBrowser browser) {
        var action = present(choice.action(), "The action");
        require(payload.actions().contains(action), "The action %s is not offered".formatted(action));
        if (action == SelectionAction.BROWSE) {
            var procedureId = present(choice.procedureId(), "The procedure");
            require(browser.offered(procedureId).isPresent(), "Pick one of the procedures the search found");
        }
    }

    private static void checkFailureContext(FailureContextCapture.Answer answer) {
        if (present(answer.choice(), "The choice") == Choice.SUBMIT) {
            present(answer.category(), "The category");
            text(requireNonNullElse(answer.symptom(), ""), "The symptom");
            text(requireNonNullElse(answer.resolution(), ""), "The resolution");
        }
    }

    private PromptSpec<ProcedureEditor.Answer> editorSpec(ProcedureEditor payload, ProcedureEditorPrompt editor, PromptImages images) {
        return PromptSpec.of(payload, ProcedureEditor.Answer.class, images)
                .withActions(editor.actions())
                .withCheck(editor::check);
    }

    private KnowledgeSuggestionResult initialSuggestions(ProcedureEditorSession session) {
        var description = session.itemContext().description();
        var suggestionService = session.suggestionService();
        showStatus("Generating AI suggestions for '%s'".formatted(description));
        try {
            return suggestionService.load(suggestionService.prepare(session.suggestionContext(), description, List.of()));
        } finally {
            clearStatus();
        }
    }

    private static ItemView itemView(ExecutionItemContext itemContext) {
        return new ItemView(itemContext.description(), requireNonNullElse(itemContext.testData(), List.of()),
                itemContext.isPrecondition());
    }
}
