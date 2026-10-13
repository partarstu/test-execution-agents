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
package org.tarik.ta.smoke;

import org.bytedeco.javacpp.Loader;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.tool.ToolErrorContext;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgent;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.UiToolErrorHandler;
import org.tarik.ta.agents.BestUiElementMatchSelectionAgent;
import org.tarik.ta.agents.UiElementGrounder;
import org.tarik.ta.agents.UiPreconditionActionAgent;
import org.tarik.ta.agents.UiPreconditionVerificationAgent;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.agents.UiTestStepActionAgent;
import org.tarik.ta.agents.UiTestStepVerificationAgent;
import org.tarik.ta.core.AgentConfig.ModelProvider;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.dto.LocatedElementInfo;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.dto.UiStateCheckResult;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.ProcedureKnowledgeCollectionService;
import org.tarik.ta.knowledge_graph.StepExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly;
import org.tarik.ta.knowledge_graph.model.node.ExecutionAnomaly.Kind;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.repository.ExecutionAnomalyRepository;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.panel.ProcedureNode;
import org.tarik.ta.dto.panel.ProcedureOutcome;
import org.tarik.ta.knowledge_graph.model.node.ExecutionRun;
import org.tarik.ta.knowledge_graph.model.node.StepResolution;
import org.tarik.ta.knowledge_graph.repository.ExecutionRunRepository;
import org.tarik.ta.knowledge_graph.service.ExecutionAnomalyService;
import org.tarik.ta.knowledge_graph.service.ExecutionRunService;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElementAppearance;
import org.tarik.ta.knowledge_graph.repository.FailureContextRepository;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.knowledge_graph.repository.PhraseEmbeddingRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureMatch;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.SatisfiesEdgeRepository.UnsatisfiedPrerequisite;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.AsyncExecutionPersistenceService;
import org.tarik.ta.knowledge_graph.service.DecompositionService;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;
import org.tarik.ta.knowledge_graph.service.ExecutionStateTracker;
import org.tarik.ta.knowledge_graph.service.FailureContextService;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;
import org.tarik.ta.knowledge_graph.service.SatisfiesEdgeService;
import org.tarik.ta.knowledge_graph.service.UiElementAliasRegistry;
import org.tarik.ta.knowledge_graph.service.UiElementCache;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.model.ImageContentCreator;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.model.VisualStateHolder;
import org.tarik.ta.panel.OperatorPrompts;
import org.tarik.ta.panel.PanelTestFixture;
import org.tarik.ta.panel.RunObserver;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.tools.ElementLocatorTools;
import org.tarik.ta.tools.MouseTools;
import org.tarik.ta.tools.MouseTools.ScrollDirection;
import org.tarik.ta.tools.VerificationTools;
import org.tarik.ta.utils.ImageMatchingUtil;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;

/**
 * Hermetic end-to-end smoke test running the whole knowledge-based UI execution flow for real, with the four LLM agents mocked at
 * their interfaces (the UI tools would otherwise drive a real screen), the {@link KnowledgeService} Neo4j boundary and the screen
 * via {@link UiCommonUtils#captureScreen()}. Everything is wired by hand and stays in-process and headless-safe; the supervised runs
 * are answered by a scripted operator through the HTTP API of a running operator panel.
 */
@Tag("smoke")
@ExtendWith(MockitoExtension.class)
@DisplayName("UI agent - hermetic end-to-end smoke")
class UiAgentSmokeTest {

    @Mock
    private KnowledgeService knowledgeService;
    @Mock
    private KnowledgeIngestionService knowledgeIngestionService;
    @Mock
    private ProcedureKnowledgeCollectionService procedureKnowledgeCollectionService;
    @Mock
    private SatisfiesEdgeService satisfiesEdgeService;
    @Mock
    private ProcedureUsageByTestCaseTrackingService procedureUsageByTestCaseTrackingService;
    @Mock
    private ExecutionRunRepository executionRunRepository;
    @Mock
    private FailureContextService failureContextService;
    @Mock
    private UiElementCache uiElementCache;
    @Mock
    private UiElementRepository uiElementRepository;
    @Mock
    private ElementLocationHistoryLookup elementLocationHistoryLookup;
    @Mock
    private AsyncExecutionPersistenceService asyncExecutionPersistenceService;

    // Seams of the real KnowledgeService used by the match-confirmation gate tests.
    @Mock
    private ProcedureRepository procedureRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private DecompositionService decompositionService;
    @Mock
    private PhraseEmbeddingRepository phraseEmbeddingRepository;

    @Mock
    private UiTestStepActionAgent stepActionAgent;
    @Mock
    private UiPreconditionActionAgent preconditionActionAgent;
    @Mock
    private UiTestStepVerificationAgent stepVerificationAgent;
    @Mock
    private UiPreconditionVerificationAgent preconditionVerificationAgent;
    @Mock
    private UiStateCheckAgent stateCheckAgent;
    @Mock
    private UiElementGrounder uiElementGrounder;
    @Mock
    private BestUiElementMatchSelectionAgent bestUiElementMatchSelectionAgent;
    @Mock
    private Robot robot;

    @Mock
    private UiTestAgentConfig config;
    @Mock
    private TestCaseExtractor testCaseExtractor;
    @Mock
    private BudgetManager budgetManager;
    @Mock
    private ScreenRecorder screenRecorder;
    @Mock
    private LogCapture logCapture;
    @Mock
    private OperatorPrompts operatorPrompts;
    @Mock
    private RunObserver runObserver;

    private BufferedImage screenshot;
    private MockedStatic<UiCommonUtils> uiCommonUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        screenshot = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        uiCommonUtilsMockedStatic = mockStatic(UiCommonUtils.class);
        uiCommonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(screenshot);

        lenient().when(config.isFullyUnattended()).thenReturn(true);
        lenient().when(config.getExecutionMode()).thenReturn(ExecutionMode.UNATTENDED);
        lenient().when(config.isAtomicVerificationEnabled()).thenReturn(true);
        lenient().when(config.getAncestryWindowSize()).thenReturn(5);
        // Zero delays/timeouts keep the verification retry loop from actually sleeping.
        lenient().when(config.getActionVerificationDelayMillis()).thenReturn(0);
        lenient().when(config.getTimingVerificationMinDelayMs()).thenReturn(0);
        lenient().when(config.getVerificationRetryPolicy()).thenReturn(new RetryPolicy(1, 0, 0));
        lenient().when(config.getActionRetryPolicy()).thenReturn(new RetryPolicy(1, 0, 0));
        lenient().when(config.getSensitiveDataKeyPattern()).thenReturn(Pattern.compile("(?!)"));

        lenient().when(screenRecorder.getCurrentRecordingPath()).thenReturn("videos/smoke.mp4");
        lenient().when(logCapture.getLogs()).thenReturn(List.of("execution started", "execution finished"));

        // Happy-path defaults for the test-step agents; the precondition agents are stubbed only where used.
        lenient().when(stepActionAgent.executeAndGetResult(any())).thenReturn(actionSuccess());
        lenient().when(stepVerificationAgent.executeAndGetResult(any())).thenReturn(verification(true, "state matches"));
        // The run history reaches its mocked repository as the agent writes it: off the thread or not, in order, before the run ends
        lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(asyncExecutionPersistenceService).persistRunHistory(any(), any());
    }

    @Test
    @DisplayName("The run reports every procedure node in nesting order and records how each item was resolved, ending as passed")
    void runReportsProcedureNodesAndRecordsItsResolutions() {
        var step = new TestStep("Log in as admin", List.of(), "The dashboard is shown");
        var testCase = new TestCase("Run history", List.of(), List.of(), List.of(step));
        var typeName = Procedure.createAtomic("Type the admin name", List.of(), "", List.of(), List.of(), false);
        var pressLogin = Procedure.createAtomic("Press the login button", List.of(), "", List.of(), List.of(), false);
        var enterCredentials = Procedure.createComposite("Enter the admin credentials", List.of(), "", List.of(), List.of(), false);
        var root = Procedure.createComposite(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        var realKnowledgeService = knowledgeServiceMatching(root, 0.98);
        when(decompositionService.decompose(root.id())).thenReturn(List.of(typeName, pressLogin));
        var childrenById = Map.of(root.id(), List.of(enterCredentials, pressLogin), enterCredentials.id(), List.of(typeName));
        when(decompositionService.getChildrenOrdered(any()))
                .thenAnswer(invocation -> childrenById.getOrDefault(invocation.<UUID>getArgument(0), List.of()));

        TestExecutionResult result = execute(testCase, StreamingEventEmitter.NOOP, realKnowledgeService);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        var order = inOrder(runObserver);
        order.verify(runObserver).itemOutlined(List.of(), List.of(
                ProcedureNode.pending(root.id(), root.description(), "", false, false, 0),
                ProcedureNode.pending(enterCredentials.id(), enterCredentials.description(), "", false, false, 1),
                ProcedureNode.pending(typeName.id(), typeName.description(), "", true, false, 2),
                ProcedureNode.pending(pressLogin.id(), pressLogin.description(), "", true, false, 1)));
        order.verify(runObserver).procedureStarted(root.id());
        order.verify(runObserver).procedureStarted(enterCredentials.id());
        order.verify(runObserver).procedureStarted(typeName.id());
        order.verify(runObserver).procedureFinished(typeName.id(), ProcedureOutcome.COMPLETED);
        order.verify(runObserver).procedureFinished(enterCredentials.id(), ProcedureOutcome.COMPLETED);
        order.verify(runObserver).procedureStarted(pressLogin.id());
        order.verify(runObserver).procedureFinished(pressLogin.id(), ProcedureOutcome.COMPLETED);
        order.verify(runObserver).procedureFinished(root.id(), ProcedureOutcome.COMPLETED);
        var run = ArgumentCaptor.forClass(ExecutionRun.class);
        verify(executionRunRepository).createRun(run.capture());
        assertThat(run.getValue().testCaseName()).isEqualTo("Run history");
        var resolution = ArgumentCaptor.forClass(StepResolution.class);
        verify(executionRunRepository).appendResolution(eq(run.getValue().id()), resolution.capture());
        assertThat(resolution.getValue().source()).isEqualTo(StepResolution.Source.KNOWLEDGE);
        assertThat(resolution.getValue().chosenProcedureId()).isEqualTo(root.id());
        assertThat(resolution.getValue().outcome()).isEqualTo(StepResolution.Outcome.SUCCEEDED);
        assertThat(resolution.getValue().candidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.semanticScore()).isEqualTo(0.98));
        verify(executionRunRepository).finishRun(eq(run.getValue().id()), any(), eq(ExecutionRun.Status.PASSED));
    }

    @AfterEach
    void tearDown() {
        uiCommonUtilsMockedStatic.close();
    }

    @Test
    @DisplayName("Single step passes and the result carries SystemInfo, logs and the recording path")
    void singleStepPasses() {
        var step = new TestStep("Click the login button", List.of(), "The dashboard is shown");
        var testCase = new TestCase("Single step", List.of(), List.of(), List.of(step));
        matchAtomicProcedure(step.stepDescription());

        UiTestExecutionResult result = (UiTestExecutionResult) execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getTestCaseName()).isEqualTo("Single step");
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult ->
                assertThat(stepResult.getExecutionStatus()).isEqualTo(SUCCESS));
        assertThat(result.getSystemInfo()).isNotNull();
        assertThat(result.getLogs()).containsExactly("execution started", "execution finished");
        assertThat(result.getVideoPath()).isEqualTo("videos/smoke.mp4");
        assertThat(result.getGeneralErrorMessage()).isNull();
        // The screen seam was exercised for real (through the mocked capture) rather than hitting AWT.
        uiCommonUtilsMockedStatic.verify(UiCommonUtils::captureScreen, atLeastOnce());
    }

    @Test
    @DisplayName("Multiple steps all pass")
    void multiStepPasses() {
        var first = new TestStep("Open the settings page", List.of(), "The settings page is shown");
        var second = new TestStep("Toggle dark mode", List.of(), "Dark mode is enabled");
        var testCase = new TestCase("Multi step", List.of(), List.of(), List.of(first, second));
        matchAtomicProcedure(first.stepDescription());
        matchAtomicProcedure(second.stepDescription());

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getStepResults()).hasSize(2)
                .allSatisfy(stepResult -> assertThat(stepResult.getExecutionStatus()).isEqualTo(SUCCESS));
    }

    @Test
    @DisplayName("A composite procedure is decomposed and both atomic children execute")
    @SuppressWarnings("unchecked")
    void compositeProcedureDecomposesAndPasses() {
        var step = new TestStep("Log in as admin", List.of(), "The dashboard is shown");
        var testCase = new TestCase("Composite step", List.of(), List.of(), List.of(step));
        var enterCredentials = Procedure.createAtomic("Enter the admin credentials", List.of(), "", List.of(), List.of(), false);
        var pressLogin = Procedure.createAtomic("Press the login button", List.of(), "The login form is submitted", List.of(),
                List.of(), false);
        var composite = Procedure.createComposite(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        matchProcedure(step.stepDescription(), composite);
        when(knowledgeService.resolveToAtomicSteps(composite.id())).thenReturn(List.of(enterCredentials, pressLogin));
        when(knowledgeService.getChildrenForExecution(composite.id())).thenReturn(List.of(enterCredentials, pressLogin));
        captureVerificationPrompt();

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult ->
                assertThat(stepResult.getExecutionStatus()).isEqualTo(SUCCESS));
        // Both atomic children of the decomposed composite were actually executed.
        verify(stepActionAgent, times(2)).executeAndGetResult(any());
        ArgumentCaptor<List<Content>> verificationPrompt = ArgumentCaptor.forClass(List.class);
        verify(stepVerificationAgent).verify(verificationPrompt.capture(), any());
        assertThat(singleText(verificationPrompt.getValue())).startsWith("""
                Verify that the expected results from the "Expected results" section are met.

                ## Expected results
                The login form is submitted
                The dashboard is shown

                ## Action executed before this verification
                """);
    }

    @Test
    @DisplayName("The prerequisite of the next atomic is satisfied by the effect of the composite that closes before it")
    void nextAtomicPrerequisiteIsSatisfiedByTheEffectOfTheClosingComposite() {
        var step = new TestStep("Activate wrapping the header text", List.of(), "The header text is wrapped");
        var testCase = new TestCase("Composite effect", List.of(), List.of(), List.of(step));
        var scroll = Procedure.createAtomic("Scroll the options pane down", List.of(), "", List.of(), List.of(), false);
        var clickToggle = Procedure.createAtomic("Click the 'Wrap header text' toggle", List.of(), "", List.of(), List.of(), false);
        var scrollUntilVisible = Procedure.createComposite("Scroll until the 'Wrap header text' toggle is visible", List.of(), "",
                List.of(), List.of(), false);
        var root = Procedure.createComposite(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        var toggleVisible = new PhraseEmbedding(UUID.randomUUID(), "The 'Wrap header text' toggle is visible", new float[0],
                PhraseType.EFFECT);
        matchProcedure(step.stepDescription(), root);
        when(knowledgeService.resolveToAtomicSteps(root.id())).thenReturn(List.of(scroll, clickToggle));
        when(knowledgeService.getChildrenForExecution(root.id())).thenReturn(List.of(scrollUntilVisible, clickToggle));
        when(knowledgeService.getChildrenForExecution(scrollUntilVisible.id())).thenReturn(List.of(scroll));
        when(knowledgeService.findEffectsForProcedure(any())).thenAnswer(invocation ->
                scrollUntilVisible.id().equals(invocation.getArgument(0)) ? List.of(toggleVisible) : List.of());
        when(satisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any())).thenAnswer(invocation ->
                !clickToggle.id().equals(invocation.getArgument(0)) || invocation.<Set<UUID>>getArgument(1).contains(toggleVisible.id())
                        ? List.of()
                        : List.of(new UnsatisfiedPrerequisite("The 'Wrap header text' toggle is visible", 0.0, null)));

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        verify(stepActionAgent, times(2)).executeAndGetResult(any());
    }

    @Test
    @DisplayName("An effect negated by an atomic is no longer part of the state the next atomic's prerequisites are checked against")
    void negatedEffectIsRemovedFromTheStateBeforeTheNextAtomicIsChecked() {
        var step = new TestStep("Submit the order form", List.of(), "The order is submitted");
        var testCase = new TestCase("Negated effect", List.of(), List.of(), List.of(step));
        var openDialog = Procedure.createAtomic("Open the order dialog", List.of(), "", List.of(), List.of(), false);
        var closeDialog = Procedure.createAtomic("Close the order dialog", List.of(), "", List.of(), List.of(), false)
                .withNegatedEffects(List.of("The order dialog is open"));
        var submitForm = Procedure.createAtomic("Press the submit button", List.of(), "", List.of(), List.of(), false);
        var root = Procedure.createComposite(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        var dialogOpen = PhraseEmbedding.createEffect("The order dialog is open", new float[0]);
        var formShown = PhraseEmbedding.createEffect("The order form is shown", new float[0]);
        matchProcedure(step.stepDescription(), root);
        when(knowledgeService.resolveToAtomicSteps(root.id())).thenReturn(List.of(openDialog, closeDialog, submitForm));
        when(knowledgeService.getChildrenForExecution(root.id())).thenReturn(List.of(openDialog, closeDialog, submitForm));
        when(knowledgeService.findEffectsForProcedure(any())).thenAnswer(invocation -> {
            UUID procedureId = invocation.getArgument(0);
            if (openDialog.id().equals(procedureId)) {
                return List.of(dialogOpen);
            }
            return closeDialog.id().equals(procedureId) ? List.of(formShown) : List.of();
        });
        when(satisfiesEdgeService.findNegatedEffectNodeIds(any(), any())).thenAnswer(invocation ->
                closeDialog.equals(invocation.getArgument(0)) ? Set.of(dialogOpen.id()) : Set.of());
        var effectsCheckedForSubmit = new CopyOnWriteArrayList<Set<UUID>>();
        when(satisfiesEdgeService.findUnsatisfiedPrerequisites(any(), any())).thenAnswer(invocation -> {
            if (submitForm.id().equals(invocation.getArgument(0))) {
                effectsCheckedForSubmit.add(Set.copyOf(invocation.<Set<UUID>>getArgument(1)));
            }
            return List.of();
        });

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        verify(stepActionAgent, times(3)).executeAndGetResult(any());
        verify(satisfiesEdgeService).findNegatedEffectNodeIds(closeDialog, Set.of(dialogOpen.id()));
        assertThat(effectsCheckedForSubmit).isNotEmpty().allSatisfy(effectIds -> assertThat(effectIds).containsExactly(formShown.id()));
    }

    @Test
    @DisplayName("A decomposed atomic step sees the test step's data only on the outermost level of its composite chain")
    @SuppressWarnings("unchecked")
    void decomposedStepCarriesTheAncestorChainIntoTheVerificationPrompt() {
        var step = new TestStep("Log in as admin", List.of("Dashboard name is 'Sales'"), "The dashboard is shown");
        var testCase = new TestCase("Composite hierarchy", List.of(), List.of(), List.of(step));
        var pressLogin = Procedure.createAtomic("Press the login button", List.of(), "", List.of(), List.of(), false);
        var composite = Procedure.createComposite(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        matchProcedure(step.stepDescription(), composite);
        when(knowledgeService.resolveToAtomicSteps(composite.id())).thenReturn(List.of(pressLogin));
        when(knowledgeService.getChildrenForExecution(composite.id())).thenReturn(List.of(pressLogin));
        captureVerificationPrompt();

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        ArgumentCaptor<List<Content>> verificationPrompt = ArgumentCaptor.forClass(List.class);
        verify(stepVerificationAgent).verify(verificationPrompt.capture(), any());
        assertThat(singleText(verificationPrompt.getValue()))
                .contains("""
                        ## Procedure hierarchy
                        Innermost first; every level is the parent of the previous one.

                        ### Level 1: Direct parent (the procedure this step is a part of)
                        Log in as admin

                        ### Data of the whole test step
                        - Dashboard name is 'Sales'""")
                .doesNotContain("Data relevant to this action")
                .doesNotContain("none");
    }

    @Test
    @DisplayName("A later step's action and verification see the data of an earlier step attributed to it and marked as not current")
    @SuppressWarnings("unchecked")
    void laterStepActionAndVerificationSeeEarlierStepDataAttributedToTheEarlierStep() {
        var first = new TestStep("Open the edit view of the specified panel", List.of("Panel = 'Bar gauge'"), "The edit view is shown");
        var second = new TestStep("Open the dashboard", List.of("Dashboard name is 'State timeline'"), "The dashboard is opened");
        var testCase = new TestCase("Step attribution", List.of(), List.of(), List.of(first, second));
        matchAtomicProcedure(first.stepDescription());
        matchAtomicProcedure(second.stepDescription());
        captureVerificationPrompt();
        lenient().when(stepActionAgent.getImageContentCreator(any()))
                .thenReturn(new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000));
        when(stepActionAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<?>>getArgument(0).get();
            return actionSuccess();
        });

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        ArgumentCaptor<List<Content>> actionPrompts = ArgumentCaptor.forClass(List.class);
        verify(stepActionAgent, times(2)).execute(actionPrompts.capture(), any());
        ArgumentCaptor<List<Content>> verificationPrompts = ArgumentCaptor.forClass(List.class);
        verify(stepVerificationAgent, times(2)).verify(verificationPrompts.capture(), any());
        var attributedContext = """
                ## Test execution context data
                [{"step":{"number":1,"type":"test step","description":"Open the edit view of the specified panel"},\
                "belongsToCurrentStep":false,"entries":[{"name":"Panel","value":"'Bar gauge'","source":"test step data"}]},\
                {"step":{"number":2,"type":"test step","description":"Open the dashboard"},"belongsToCurrentStep":true,\
                "entries":[{"value":"Dashboard name is 'State timeline'","source":"test step data"}]}]""";
        assertThat(singleText(actionPrompts.getAllValues().getLast())).contains(attributedContext);
        assertThat(singleText(verificationPrompts.getAllValues().getLast())).endsWith(attributedContext);
    }

    @Test
    @DisplayName("The verification compares the screen captured before the action with the screen captured after it")
    @SuppressWarnings("unchecked")
    void verificationReceivesTheScreenshotsBeforeAndAfterTheAction() {
        var step = new TestStep("Zoom in the map", List.of(), "The map is zoomed in");
        var testCase = new TestCase("Before and after", List.of(), List.of(), List.of(step));
        matchAtomicProcedure(step.stepDescription());
        captureVerificationPrompt();
        var afterActionScreenshot = new BufferedImage(1, 1, TYPE_INT_RGB);
        afterActionScreenshot.setRGB(0, 0, 0xFFFFFF);
        when(stepActionAgent.executeAndGetResult(any())).thenAnswer(_ -> {
            uiCommonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(afterActionScreenshot);
            return actionSuccess();
        });

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        ArgumentCaptor<List<Content>> screenshots = ArgumentCaptor.forClass(List.class);
        verify(stepVerificationAgent).verify(any(), screenshots.capture());
        var imageContentCreator = stepVerificationAgent.getImageContentCreator(config);
        assertThat(screenshots.getValue()).containsExactly(
                TextContent.from("## Screenshot before the action"), imageContentCreator.createImageContent(screenshot),
                TextContent.from("## Screenshot after the action"), imageContentCreator.createImageContent(afterActionScreenshot));
    }

    /**
     * Makes the mocked verification agent actually run the prompt-building supplier, so that the user message the real
     * {@link VerificationTools} assembles reaches {@link UiTestStepVerificationAgent#verify} and can be asserted on.
     */
    private void captureVerificationPrompt() {
        var imageContentCreator = new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000);
        lenient().when(stepVerificationAgent.getImageContentCreator(any())).thenReturn(imageContentCreator);
        when(stepVerificationAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<?>>getArgument(0).get();
            return verification(true, "state matches");
        });
    }

    @Test
    @DisplayName("A scroll step drives the real scroll tool, which turns the mouse wheel until the state check passes")
    void scrollStepTurnsTheMouseWheelUntilTheStateIsReached() {
        var step = new TestStep("Scroll the options pane down until the 'Wrap header text' toggle is visible", List.of(),
                "The 'Wrap header text' toggle is visible");
        var testCase = new TestCase("Scroll step", List.of(), List.of(), List.of(step));
        matchAtomicProcedure(step.stepDescription());
        uiCommonUtilsMockedStatic.when(UiCommonUtils::getRobot).thenReturn(robot);
        when(config.getMouseScrollNotchesPerAttempt()).thenReturn(3);
        when(config.getMaxActionExecutionDurationMillis()).thenReturn(10_000);
        when(stateCheckAgent.getImageContentCreator(config))
                .thenReturn(new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000));
        when(stateCheckAgent.executeAndGetResult(any())).thenReturn(stateCheck(false), stateCheck(false), stateCheck(true));
        var mouseTools = new MouseTools(stateCheckAgent, config);
        var scrollToolResult = new AtomicReference<String>();
        // The mocked action model picks the scroll tool, as the LLM would for this step
        when(stepActionAgent.executeAndGetResult(any())).thenAnswer(_ -> {
            scrollToolResult.set(mouseTools.scrollUntilStateAchieved(ScrollDirection.DOWN, "The 'Wrap header text' toggle is visible"));
            return actionSuccess();
        });

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(scrollToolResult.get())
                .isEqualTo("The expected state 'The 'Wrap header text' toggle is visible' has been reached, the action is complete.");
        verify(robot, times(2)).mouseWheel(3);
        verify(robot, never()).mousePress(anyInt());
        verify(stateCheckAgent, times(3)).executeAndGetResult(any());
    }

    @Test
    @DisplayName("An invented element ID is handed back to the action model as guidance instead of failing the step")
    void inventedElementIdIsFedBackToTheActionModel() {
        var step = new TestStep("Switch on the 'Wrap header text' toggle", List.of(), "The toggle is on");
        var testCase = new TestCase("Invented element ID", List.of(), List.of(), List.of(step));
        matchAtomicProcedure(step.stepDescription());
        var elementLocatorTools = new ElementLocatorTools(uiElementCache, uiElementRepository, stateCheckAgent, null,
                uiElementGrounder, bestUiElementMatchSelectionAgent, new UiElementAliasRegistry(), config);
        var toolErrorHandler = new UiToolErrorHandler(config.getActionRetryPolicy(), config);
        var guidanceFedBack = new AtomicReference<String>();
        // The mocked action model calls the locate tool with a made-up ID, as the LLM did, and the real error handler
        // decides what it gets back; the model then finishes the step as its second attempt.
        when(stepActionAgent.executeAndGetResult(any())).thenAnswer(_ -> {
            try {
                elementLocatorTools.locateKnownElementById("wrapHeaderTextToggle", null);
            } catch (ElementLocationException e) {
                guidanceFedBack.set(toolErrorHandler.handle(e, mock(ToolErrorContext.class)).text());
            }
            return actionSuccess();
        });

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(guidanceFedBack.get()).isEqualTo("No UI element with ID 'wrapHeaderTextToggle' exists. Use only the target UI " +
                "element ID given in the task; if none was given, this tool must not be called.");
        verify(uiElementRepository, never()).findById(any());
    }

    @Test
    @DisplayName("A click step locates the element and clicks its normalized centre at the matching logical screen position")
    @SuppressWarnings("unchecked")
    void clickStepClicksTheLocatedElementAtItsLogicalScreenPosition() {
        var step = new TestStep("Select the last day of the displayed month in the calendar", List.of(), "The day is selected");
        var testCase = new TestCase("Click step", List.of(), List.of(), List.of(step));
        matchAtomicProcedure(step.stepDescription());
        // A 4K screen at 150% scaling, grounded by a model which answers in pixels of a screenshot downscaled by half
        uiCommonUtilsMockedStatic.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(3840, 2160, TYPE_INT_RGB));
        uiCommonUtilsMockedStatic.when(UiCommonUtils::getScreenSize).thenReturn(new Dimension(2560, 1440));
        uiCommonUtilsMockedStatic.when(UiCommonUtils::getRobot).thenReturn(robot);
        when(config.getElementLocatorVisualGroundingVoteCount()).thenReturn(1);
        when(config.getBboxScreenshotLongestAllowedDimensionPixels()).thenReturn(1920);
        when(config.getBboxScreenshotMaxSizeMegapixels()).thenReturn(10.0);
        when(config.skipBestUiElementMatchSelection()).thenReturn(true);
        var dayCell = new UiElement(UUID.randomUUID(), "calendar day cell", "A day cell of the calendar grid",
                "Among the other day cells", "Calendar popup", null, true);
        when(uiElementCache.get(dayCell.id())).thenReturn(Optional.of(dayCell));
        when(knowledgeService.findTargetedUiElementId(any())).thenReturn(Optional.of(dayCell.id()));
        var groundedScreenshot = ArgumentCaptor.forClass(BufferedImage.class);
        when(uiElementGrounder.locate(anyString(), groundedScreenshot.capture())).thenReturn(List.of(new Rectangle(1127, 372, 40, 40)));
        var aliasRegistry = new UiElementAliasRegistry();
        var elementLocatorTools = new ElementLocatorTools(uiElementCache, uiElementRepository, stateCheckAgent,
                mock(LocationHistoryRecorder.class), uiElementGrounder, bestUiElementMatchSelectionAgent, aliasRegistry, config);
        var mouseTools = new MouseTools(stateCheckAgent, config);
        var locatedElement = new AtomicReference<LocatedElementInfo>();
        lenient().when(stepActionAgent.getImageContentCreator(any()))
                .thenReturn(new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000));
        // The mocked action model locates the cell with the resolved day and clicks the centre the tool returned, as the LLM would
        when(stepActionAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<?>>getArgument(0).get();
            locatedElement.set(elementLocatorTools.locateKnownElementById(aliasRegistry.aliasFor(dayCell.id()), "30"));
            mouseTools.leftMouseClick(locatedElement.get().centerXCoordinate(), locatedElement.get().centerYCoordinate());
            return actionSuccess();
        });

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        ArgumentCaptor<List<Content>> actionPrompt = ArgumentCaptor.forClass(List.class);
        verify(stepActionAgent).execute(actionPrompt.capture(), any());
        assertThat(singleText(actionPrompt.getValue()))
                .startsWith("""
                        Execute the action from the "Action" section.

                        ## Action
                        Select the last day of the displayed month in the calendar

                        ## Target UI element ID
                        """)
                .contains("""
                        ## Target UI element details
                        - Name: calendar day cell
                        - Description: A day cell of the calendar grid
                        - Location details: Among the other day cells
                        - Enclosing component (the component containing this UI element, e.g. a page, dialog, form, panel or pop-up): \
                        Calendar popup
                        - Depends on data: yes""")
                .endsWith("\n\n## Screenshot");
        verify(uiElementGrounder).locate(contains("available specific data for this element: 30"), any(BufferedImage.class));
        assertThat(groundedScreenshot.getValue().getWidth()).isEqualTo(1920);
        // The centre of the grounded box is (2294, 784) on the 4K screenshot, (597, 363) normalized and (1528, 523) logical
        assertThat(locatedElement.get().centerXCoordinate()).isEqualTo(597);
        assertThat(locatedElement.get().centerYCoordinate()).isEqualTo(363);
        verify(robot).mouseMove(1528, 523);
        verify(robot).mousePress(BUTTON1_DOWN_MASK);
        verify(robot).mouseRelease(BUTTON1_DOWN_MASK);
    }

    @Test
    @DisplayName("When the current appearance of the target element finds nothing, its expired appearances are consulted")
    void expiredAppearancesAreConsultedWhenTheCurrentAppearanceFindsNothing() {
        var step = new TestStep("Click the Save button", List.of(), "The form is saved");
        var testCase = new TestCase("Expired appearance", List.of(), List.of(), List.of(step));
        var saveButton = new UiElement(UUID.randomUUID(), "Save button", "The save button", "Bottom right", "Order form", null, false);
        matchAtomicProcedure(step.stepDescription());
        when(config.isAlgorithmicSearchEnabled()).thenReturn(true);
        when(config.getElementBoundingBoxColorName()).thenReturn("green");
        stubVisualGroundingScreenshotLimits();
        when(uiElementCache.get(saveButton.id())).thenReturn(Optional.of(saveButton));
        when(uiElementGrounder.locate(any(), any())).thenReturn(List.of());
        var expiredScreenshot = UiElement.Screenshot.fromBufferedImage(new BufferedImage(4, 4, TYPE_INT_RGB), "png");
        when(uiElementRepository.findExpiredAppearances(saveButton.id())).thenReturn(List.of(new UiElementAppearance(UUID.randomUUID(),
                "The old save button", "Bottom right", "Order form", expiredScreenshot, null, Instant.parse("2026-09-01T10:00:00Z"))));
        var aliasRegistry = new UiElementAliasRegistry();
        var elementLocatorTools = new ElementLocatorTools(uiElementCache, uiElementRepository, stateCheckAgent,
                mock(LocationHistoryRecorder.class), uiElementGrounder, bestUiElementMatchSelectionAgent, aliasRegistry, config);
        when(stepActionAgent.executeAndGetResult(any())).thenAnswer(_ -> {
            try {
                elementLocatorTools.locateKnownElementById(aliasRegistry.aliasFor(saveButton.id()), null);
                return actionSuccess();
            } catch (ElementLocationException e) {
                return actionReportedFailure(e.getMessage());
            }
        });

        TestExecutionResult result;
        try (var imageMatching = mockStatic(ImageMatchingUtil.class);
             var _ = mockStatic(Loader.class)) {
            imageMatching.when(() -> ImageMatchingUtil.findMatchingRegionsWithORB(any(), any(), any()))
                    .thenReturn(List.of(new Rectangle(10, 10, 10, 10)));
            imageMatching.when(() -> ImageMatchingUtil.findMatchingRegionsWithTemplateMatching(any(), any(), any())).thenReturn(List.of());
            uiCommonUtilsMockedStatic.when(() -> UiCommonUtils.getColorName(any())).thenReturn("green");

            result = execute(testCase);

            imageMatching.verify(() -> ImageMatchingUtil.findMatchingRegionsWithORB(any(),
                    argThat((BufferedImage image) -> image.getWidth() == 4), any()));
        }

        verify(uiElementRepository).findExpiredAppearances(saveButton.id());
        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> assertThat(stepResult.getErrorMessage())
                // The expired appearance yielded the only candidate, which the validation model then rejected
                .contains("Only algorithmic matching provided results, but the validation model decided that none of them are valid"));
    }

    @Test
    @DisplayName("The resolved failure hints of a procedure reach the action prompt ranked by the repository and capped by the limit")
    @SuppressWarnings("unchecked")
    void rankedAndCappedFailureHintsReachTheActionPrompt() {
        var step = new TestStep("Click the Save button", List.of(), "The form is saved");
        var testCase = new TestCase("Failure hints", List.of(), List.of(), List.of(step));
        var procedure = Procedure.createAtomic(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        matchProcedure(step.stepDescription(), procedure);
        var failureContextRepository = mock(FailureContextRepository.class);
        when(config.getFailureHintsMaxCount()).thenReturn(2);
        when(failureContextRepository.findFailureContexts(procedure.id(), 2)).thenReturn(List.of(
                new FailureContext(UUID.randomUUID(), "Save button hidden", ErrorCategory.TRANSIENT_TOOL_ERROR, "scroll down first", 7,
                        Instant.now(), FailureContext.Mode.UNATTENDED),
                new FailureContext(UUID.randomUUID(), "Slow form", ErrorCategory.TIMEOUT, "wait for the spinner", 2, Instant.now(),
                        FailureContext.Mode.SUPERVISED)));
        lenient().when(stepActionAgent.getImageContentCreator(any()))
                .thenReturn(new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000));
        when(stepActionAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<?>>getArgument(0).get();
            return actionSuccess();
        });

        TestExecutionResult result = execute(testCase, new FailureContextService(failureContextRepository, config));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        verify(failureContextRepository).findFailureContexts(procedure.id(), 2);
        ArgumentCaptor<List<Content>> actionPrompt = ArgumentCaptor.forClass(List.class);
        verify(stepActionAgent).execute(actionPrompt.capture(), any());
        assertThat(singleText(actionPrompt.getValue())).contains("""
                ## Known issues with this procedure
                - [TRANSIENT_TOOL_ERROR] Save button hidden -> scroll down first
                - [TIMEOUT] Slow form -> wait for the spinner""");
    }

    @Test
    @DisplayName("A failed element location and the failed step it causes are recorded as anomalies of the running test case")
    void failedLocationAndFailedStepAreRecordedAsAnomalies() {
        var step = new TestStep("Click the Save button", List.of(), "The form is saved");
        var testCase = new TestCase("Anomaly recording", List.of(), List.of(), List.of(step));
        var procedure = Procedure.createAtomic(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        var saveButton = new UiElement(UUID.randomUUID(), "Save button", "The save button", "Bottom right", "Order form", null, false);
        matchProcedure(step.stepDescription(), procedure);
        when(config.isAnomalyRecordingEnabled()).thenReturn(true);
        when(config.getElementBoundingBoxColorName()).thenReturn("green");
        stubVisualGroundingScreenshotLimits();
        when(uiElementCache.get(saveButton.id())).thenReturn(Optional.of(saveButton));
        when(uiElementGrounder.locate(any(), any())).thenReturn(List.of());
        var anomalyRepository = mock(ExecutionAnomalyRepository.class);
        var persistenceService = new AsyncExecutionPersistenceService(config, satisfiesEdgeService, knowledgeService, procedureRepository,
                failureContextService, procedureUsageByTestCaseTrackingService, new ExecutionAnomalyService(anomalyRepository, config));
        var aliasRegistry = new UiElementAliasRegistry();
        var elementLocatorTools = new ElementLocatorTools(uiElementCache, uiElementRepository, stateCheckAgent,
                persistenceService::updateElementStability, uiElementGrounder, bestUiElementMatchSelectionAgent, aliasRegistry, config);
        when(stepActionAgent.executeAndGetResult(any())).thenAnswer(_ -> {
            try {
                elementLocatorTools.locateKnownElementById(aliasRegistry.aliasFor(saveButton.id()), null);
                return actionSuccess();
            } catch (ElementLocationException e) {
                return actionReportedFailure(e.getMessage());
            }
        });

        TestExecutionResult result = execute(testCase, persistenceService);

        assertThat(result.getTestExecutionStatus()).isNotEqualTo(PASSED);
        var anomalies = ArgumentCaptor.forClass(ExecutionAnomaly.class);
        verify(anomalyRepository, timeout(5_000).atLeast(2)).persist(anomalies.capture());
        assertThat(anomalies.getAllValues()).extracting(ExecutionAnomaly::kind, ExecutionAnomaly::subjectId)
                .contains(tuple(Kind.ELEMENT_LOCATION_FAILED, saveButton.id()), tuple(Kind.STEP_FAILED, procedure.id()));
        assertThat(anomalies.getAllValues()).allSatisfy(anomaly -> assertThat(anomaly.testCaseName()).isEqualTo("Anomaly recording"));
    }

    private void stubVisualGroundingScreenshotLimits() {
        when(config.getElementLocatorVisualGroundingVoteCount()).thenReturn(1);
        when(config.getBboxScreenshotLongestAllowedDimensionPixels()).thenReturn(1920);
        when(config.getBboxScreenshotMaxSizeMegapixels()).thenReturn(10.0);
    }

    private static String singleText(List<Content> userMessage) {
        assertThat(userMessage).singleElement().isInstanceOf(TextContent.class);
        return ((TextContent) userMessage.getFirst()).text();
    }

    private static OperationExecutionResult<UiStateCheckResult> stateCheck(boolean reached) {
        return new OperationExecutionResult<>(ExecutionStatus.SUCCESS, "ok", new UiStateCheckResult(reached, "checked"));
    }

    @Test
    @DisplayName("A verification that fails once succeeds on the retry attempt")
    void verificationRetryRecoversOnSecondAttempt() {
        var step = new TestStep("Refresh the dashboard", List.of(), "The widget is updated");
        var testCase = new TestCase("Verification retry", List.of(), List.of(), List.of(step));
        matchAtomicProcedure(step.stepDescription());
        // A timeout > 0 lets the loop attempt again; the zero delay keeps the retry instantaneous.
        when(config.getVerificationRetryPolicy()).thenReturn(new RetryPolicy(2, 0, 5000));
        when(stepVerificationAgent.executeAndGetResult(any()))
                .thenReturn(verification(false, "the widget is stale"), verification(true, "the widget is updated"));

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult ->
                assertThat(stepResult.getExecutionStatus()).isEqualTo(SUCCESS));
        verify(stepVerificationAgent, times(2)).executeAndGetResult(any());
    }

    @Test
    @DisplayName("An unexpected exception from the action agent yields ERROR with the cause recorded")
    void unexpectedActionExceptionFailsTestCase() {
        var step = new TestStep("Click the flaky control", List.of(), "Nothing crashes");
        var testCase = new TestCase("Unexpected exception", List.of(), List.of(), List.of(step));
        matchAtomicProcedure(step.stepDescription());
        when(stepActionAgent.executeAndGetResult(any())).thenThrow(new RuntimeException("boom from the action agent"));

        TestExecutionResult result = execute(testCase);

        // Errors are not failed verifications: the atomic ERROR survives mergeAtomicResults and maps to a top-level
        // ERROR, matching the API agent's status contract.
        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR);
            assertThat(stepResult.getErrorMessage()).contains("boom from the action agent");
            assertThat(((UiTestStepResult) stepResult).getScreenshot()).isNotNull();
        });
        assertThat(result.getGeneralErrorMessage()).contains("boom from the action agent");
    }

    @Test
    @DisplayName("A failing precondition verification yields ERROR before any step runs")
    void preconditionVerificationFailureYieldsError() {
        var precondition = "The user is logged in";
        var step = new TestStep("Open the profile page", List.of(), "The profile page is shown");
        var testCase = new TestCase("Precondition verification failure", List.of(new Precondition(precondition, List.of())), List.of(), List.of(step));
        matchAtomicProcedure(precondition);
        when(preconditionActionAgent.executeAndGetResult(any())).thenReturn(actionSuccess());
        when(preconditionVerificationAgent.executeAndGetResult(any()))
                .thenReturn(verification(false, "the login screen is still visible"));

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).isEmpty();
        assertThat(result.getPreconditionResults()).singleElement().satisfies(preconditionResult -> {
            assertThat(preconditionResult.isSuccess()).isFalse();
            assertThat(preconditionResult.getErrorMessage()).contains("the login screen is still visible");
        });
    }

    @Test
    @DisplayName("A composite whose child verification fails yields FAILED")
    void compositeChildVerificationFailureYieldsFailed() {
        var step = new TestStep("Log in as admin", List.of(), "The dashboard is shown");
        var testCase = new TestCase("Composite child verification failure", List.of(), List.of(), List.of(step));
        var enterCredentials = Procedure.createAtomic("Enter the admin credentials", List.of(), "", List.of(), List.of(), false);
        var pressLogin = Procedure.createAtomic("Press the login button", List.of(), "", List.of(), List.of(), false);
        var composite = Procedure.createComposite(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        matchProcedure(step.stepDescription(), composite);
        when(knowledgeService.resolveToAtomicSteps(composite.id())).thenReturn(List.of(enterCredentials, pressLogin));
        lenient().when(knowledgeService.getChildrenForExecution(composite.id())).thenReturn(List.of(enterCredentials, pressLogin));
        when(stepVerificationAgent.executeAndGetResult(any()))
                .thenReturn(verification(false, "the credentials were not accepted"));

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(FAILED);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(FAILURE);
            assertThat(stepResult.getErrorMessage()).contains("the credentials were not accepted");
        });
    }

    @Test
    @DisplayName("A composite whose child action throws yields ERROR")
    void compositeChildActionExceptionYieldsError() {
        var step = new TestStep("Log in as admin", List.of(), "The dashboard is shown");
        var testCase = new TestCase("Composite child action error", List.of(), List.of(), List.of(step));
        var enterCredentials = Procedure.createAtomic("Enter the admin credentials", List.of(), "", List.of(), List.of(), false);
        var pressLogin = Procedure.createAtomic("Press the login button", List.of(), "", List.of(), List.of(), false);
        var composite = Procedure.createComposite(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        matchProcedure(step.stepDescription(), composite);
        when(knowledgeService.resolveToAtomicSteps(composite.id())).thenReturn(List.of(enterCredentials, pressLogin));
        lenient().when(knowledgeService.getChildrenForExecution(composite.id())).thenReturn(List.of(enterCredentials, pressLogin));
        when(stepActionAgent.executeAndGetResult(any())).thenThrow(new RuntimeException("boom in the first child"));

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR);
            assertThat(stepResult.getErrorMessage()).contains("boom in the first child");
        });
    }

    @Test
    @DisplayName("A budget-exhaustion exception propagates to a top-level ERROR preserving the budget text")
    void budgetExhaustionPropagatesAsError() {
        var step = new TestStep("Click the slow control", List.of(), "Nothing crashes");
        var testCase = new TestCase("Budget propagation", List.of(), List.of(), List.of(step));
        matchAtomicProcedure(step.stepDescription());
        when(stepActionAgent.executeAndGetResult(any()))
                .thenThrow(new RuntimeException("Execution time budget exceeded: the run took too long"));

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getGeneralErrorMessage()).contains("Execution time budget exceeded");
    }

    @Test
    @DisplayName("A failing precondition action yields ERROR before any step runs")
    void preconditionFailureYieldsError() {
        var precondition = "The user is logged in";
        var step = new TestStep("Open the profile page", List.of(), "The profile page is shown");
        var testCase = new TestCase("Precondition failure", List.of(new Precondition(precondition, List.of())), List.of(), List.of(step));
        matchAtomicProcedure(precondition);
        when(preconditionActionAgent.executeAndGetResult(any()))
                .thenReturn(actionReportedFailure("the login screen is still visible"));

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).isEmpty();
        assertThat(result.getPreconditionResults()).singleElement().satisfies(preconditionResult -> {
            assertThat(preconditionResult.isSuccess()).isFalse();
            assertThat(preconditionResult.getErrorMessage()).contains("the login screen is still visible");
        });
        assertThat(result.getGeneralErrorMessage()).contains("the login screen is still visible");
    }

    @Test
    @DisplayName("A failed step verification yields FAILED and the step result carries a screenshot")
    void verificationFailureYieldsFailed() {
        var step = new TestStep("Submit the form", List.of(), "A success banner is shown");
        var testCase = new TestCase("Verification failure", List.of(), List.of(), List.of(step));
        matchAtomicProcedure(step.stepDescription());
        when(stepVerificationAgent.executeAndGetResult(any()))
                .thenReturn(verification(false, "no success banner is present"));

        UiTestExecutionResult result = (UiTestExecutionResult) execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(FAILED);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(FAILURE);
            assertThat(stepResult.getErrorMessage()).contains("no success banner is present");
            assertThat(((UiTestStepResult) stepResult).getScreenshot()).isNotNull();
        });
    }

    @Test
    @DisplayName("A missing procedure in unattended mode yields ERROR")
    void missingProcedureYieldsError() {
        var step = new TestStep("Perform an unknown action", List.of(), "Something happens");
        var testCase = new TestCase("Missing procedure", List.of(), List.of(), List.of(step));
        when(knowledgeService.findBestMatch(eq(step.stepDescription()), any(), any())).thenReturn(Optional.empty());

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getGeneralErrorMessage()).contains("No matching procedure found for 'Perform an unknown action'");
        assertThat(result.getStepResults()).singleElement()
                .satisfies(stepResult -> assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR));
    }

    @Test
    @DisplayName("A low-confidence match in unattended mode records the failed step, a STEP_FAILED anomaly and the usage edge")
    void lowConfidenceMatchRecordsTheFailedStepAnomalyAndUsage() {
        var step = new TestStep("Archive the order", List.of(), "The order is archived");
        var testCase = new TestCase("Selection failure", List.of(), List.of(), List.of(step));
        var procedure = Procedure.createAtomic("Delete the order", List.of(), "", List.of(), List.of(), false);
        when(knowledgeService.findBestMatch(eq(step.stepDescription()), any(), any())).thenReturn(Optional.of(
                new KnowledgeService.MatchResult(procedure, KnowledgeService.MatchConfidence.LOW,
                        List.of(new KnowledgeService.ScoredProcedure(procedure, 0, 0)), false, false, false)));
        when(config.isAnomalyRecordingEnabled()).thenReturn(true);
        var anomalyRepository = mock(ExecutionAnomalyRepository.class);
        var persistenceService = new AsyncExecutionPersistenceService(config, satisfiesEdgeService, knowledgeService, procedureRepository,
                failureContextService, procedureUsageByTestCaseTrackingService, new ExecutionAnomalyService(anomalyRepository, config));

        TestExecutionResult result = execute(testCase, persistenceService);

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getGeneralErrorMessage()).isEqualTo("No high-confidence match found for the test step 'Archive the order'.");
        assertThat(result.getStepResults()).singleElement()
                .satisfies(stepResult -> assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR));
        var anomaly = ArgumentCaptor.forClass(ExecutionAnomaly.class);
        verify(anomalyRepository, timeout(5_000)).persist(anomaly.capture());
        assertThat(anomaly.getValue()).extracting(ExecutionAnomaly::kind, ExecutionAnomaly::subjectId, ExecutionAnomaly::testCaseName)
                .containsExactly(Kind.STEP_FAILED, procedure.id(), "Selection failure");
        verify(procedureUsageByTestCaseTrackingService, timeout(5_000)).mergeUsesProcedure("Selection failure", procedure.id());
        verifyNoInteractions(stepActionAgent);
    }

    @Test
    @DisplayName("Streaming: started + result events are emitted for the precondition and the step in order")
    void streamingEventsEmittedThroughEmitter() {
        var precondition = "The app is open";
        var step = new TestStep("Search for an item", List.of(), "Results are shown");
        var testCase = new TestCase("Streaming", List.of(new Precondition(precondition, List.of())), List.of(), List.of(step));
        matchAtomicProcedure(precondition);
        matchAtomicProcedure(step.stepDescription());
        when(preconditionActionAgent.executeAndGetResult(any())).thenReturn(actionSuccess());
        when(preconditionVerificationAgent.executeAndGetResult(any())).thenReturn(verification(true, "the app is open"));

        var recordingEmitter = new RecordingEventEmitter();
        TestExecutionResult result = execute(testCase, recordingEmitter);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(recordingEmitter.events)
                .containsExactly("precondition-started", "precondition-result", "step-started", "step-result");
    }

    @Test
    @DisplayName("Starting from a later step seeds the skipped step's effects, so the selected step's prerequisites are satisfied")
    void startingFromLaterStepSeedsEffectsOfSkippedSteps() throws Exception {
        when(config.isFullyUnattended()).thenReturn(false);
        when(config.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        var skippedStep = new TestStep("Open the dashboard", List.of(), "The dashboard is shown");
        var selectedStep = new TestStep("Click the time range picker", List.of(), "The time range picker is open");
        var testCase = new TestCase("Start from step", List.of(), List.of(), List.of(skippedStep, selectedStep));
        var skippedProcedure = Procedure.createAtomic(skippedStep.stepDescription(), List.of(), "", List.of(), List.of(), false);
        var selectedProcedure = Procedure.createAtomic(selectedStep.stepDescription(), List.of(), "", List.of(), List.of(), false);
        var dashboardOpened = new PhraseEmbedding(UUID.randomUUID(), "Dashboard page is opened", new float[0], PhraseType.EFFECT);
        matchProcedure(skippedStep.stepDescription(), skippedProcedure);
        matchProcedure(selectedStep.stepDescription(), selectedProcedure);
        when(knowledgeService.findEffectsForProcedure(skippedProcedure.id())).thenReturn(List.of(dashboardOpened));
        when(satisfiesEdgeService.findUnsatisfiedPrerequisites(eq(selectedProcedure.id()), any())).thenAnswer(invocation ->
                invocation.<Set<UUID>>getArgument(1).contains(dashboardOpened.id())
                        ? List.of()
                        : List.of(new UnsatisfiedPrerequisite("Dashboard page is opened", 0.0, null)));

        try (var panel = PanelTestFixture.builder().start();
             var operator = ScriptedOperator.start(panel.baseUrl(), prompt -> switch (prompt.type()) {
                 case "start-step" -> Optional.of("{\"stepIndex\":1}");
                 case "confirmation" -> Optional.of("{\"decision\":\"PROCEED\"}");
                 default -> Optional.of("{}");
             })) {
            var result = executeSupervised(testCase, panel);

            assertThat(operator.seen()).extracting(ScriptedOperator.SeenPrompt::type).contains("start-step", "confirmation")
                    .doesNotContain("message");
            assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
            assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
                assertThat(stepResult.getTestStep()).isEqualTo(selectedStep);
                assertThat(stepResult.getExecutionStatus()).isEqualTo(SUCCESS);
            });
        }
    }

    @Test
    @DisplayName("A paused confirmation countdown keeps the run waiting past its end, and once resumed it proceeds with the step")
    void pausedConfirmationCountdownWaitsUntilResumed() throws Exception {
        when(config.isFullyUnattended()).thenReturn(false);
        when(config.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        var step = new TestStep("Click the time range picker", List.of(), "The time range picker is open");
        var testCase = new TestCase("Paused countdown", List.of(), List.of(), List.of(step));
        matchProcedure(step.stepDescription(), Procedure.createAtomic(step.stepDescription(), List.of(), "", List.of(), List.of(), false));
        var confirmation = new CompletableFuture<String>();

        try (var panel = PanelTestFixture.builder().countdownSeconds(2).start();
             var operator = ScriptedOperator.start(panel.baseUrl(), prompt -> switch (prompt.type()) {
                 case "start-step" -> Optional.of("{\"stepIndex\":0}");
                 case "confirmation" -> {
                     confirmation.complete(prompt.id());
                     yield Optional.empty();
                 }
                 default -> Optional.of("{}");
             })) {
            var result = new AtomicReference<TestExecutionResult>();
            var run = Thread.ofVirtual().start(() -> result.set(executeSupervised(testCase, panel)));
            var promptId = confirmation.get(10, TimeUnit.SECONDS);
            assertThat(run.join(Duration.ofMillis(300))).isFalse();

            assertThat(operator.changeCountdown(promptId, "pause")).isEqualTo(204);
            assertThat(run.join(Duration.ofMillis(3_000))).as("the paused countdown outlives its 2 s").isFalse();
            var paused = operator.snapshot().get("prompt");
            assertThat(paused.get("id").asText()).isEqualTo(promptId);
            assertThat(paused.get("paused").asBoolean()).isTrue();
            assertThat(paused.get("remainingMillis").asLong()).as("the time left at the pause, not the whole countdown")
                    .isBetween(1L, 1_700L);
            assertThat(operator.changeCountdown(promptId, "resume")).isEqualTo(204);

            assertThat(run.join(Duration.ofSeconds(20))).isTrue();
            assertThat(result.get().getTestExecutionStatus()).isEqualTo(PASSED);
        }
    }

    @Test
    @DisplayName("Unsatisfied prerequisites in supervised mode open the next-action prompt, and a test step rerun continues the execution")
    void unsatisfiedPrerequisitesOpenNextActionPromptInSupervisedMode() throws Exception {
        when(config.isFullyUnattended()).thenReturn(false);
        when(config.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        var step = new TestStep("Click the time range picker", List.of(), "The time range picker is open");
        var testCase = new TestCase("Prerequisites", List.of(), List.of(), List.of(step));
        var procedure = Procedure.createAtomic(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        matchProcedure(step.stepDescription(), procedure);
        when(knowledgeService.findById(procedure.id())).thenReturn(Optional.of(procedure));
        when(satisfiesEdgeService.findUnsatisfiedPrerequisites(eq(procedure.id()), any()))
                .thenReturn(List.of(new UnsatisfiedPrerequisite("Dashboard page is opened", 0.0, null)))
                .thenReturn(List.of());

        try (var panel = PanelTestFixture.builder().start();
             var operator = ScriptedOperator.start(panel.baseUrl(), prompt -> switch (prompt.type()) {
                 case "start-step" -> Optional.of("{\"stepIndex\":0}");
                 case "confirmation" -> Optional.of("{\"decision\":\"PROCEED\"}");
                 case "next-action" -> Optional.of("{\"action\":\"RERUN_TEST_STEP\"}");
                 default -> Optional.of("{}");
             })) {
            var result = executeSupervised(testCase, panel);

            var message = operator.seen().stream().filter(prompt -> prompt.type().equals("message")).findFirst().orElseThrow();
            assertThat(message.payload().get("title").asText()).isEqualTo("Prerequisites Not Satisfied");
            var nextAction = operator.seen().stream().filter(prompt -> prompt.type().equals("next-action")).findFirst().orElseThrow();
            assertThat(nextAction.payload().get("itemDescription").asText()).isEqualTo(procedure.description());
            assertThat(nextAction.payload().get("actions").toString())
                    .isEqualTo("[\"RERUN_TEST_STEP\",\"RERUN_ATOMIC_PROCEDURE\",\"CREATE\",\"BROWSE\",\"CANCEL\"]");
            assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
            assertThat(result.getStepResults()).singleElement()
                    .satisfies(stepResult -> assertThat(stepResult.getExecutionStatus()).isEqualTo(SUCCESS));
        }
    }

    @Test
    @DisplayName("After a halt before and after a step, the next-action prompt offers to proceed and then to continue, and the run passes")
    void haltedStepProceedsAndContinuesFromTheNextActionPrompt() throws Exception {
        when(config.isFullyUnattended()).thenReturn(false);
        when(config.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        var step = new TestStep("Click the time range picker", List.of(), "The time range picker is open");
        var testCase = new TestCase("Halted step", List.of(), List.of(), List.of(step));
        var procedure = Procedure.createAtomic(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        matchProcedure(step.stepDescription(), procedure);
        when(knowledgeService.findById(procedure.id())).thenReturn(Optional.of(procedure));

        try (var panel = PanelTestFixture.builder().start();
             var operator = ScriptedOperator.start(panel.baseUrl(), prompt -> switch (prompt.type()) {
                 case "start-step" -> Optional.of("{\"stepIndex\":0}");
                 case "confirmation" -> Optional.of("{\"decision\":\"HALTED\"}");
                 case "next-action" -> Optional.of("{\"action\":\"%s\"}".formatted(prompt.payload().get("actions").get(0).asText()));
                 default -> Optional.of("{}");
             })) {
            var result = executeSupervised(testCase, panel);

            assertThat(operator.seen()).filteredOn(prompt -> prompt.type().equals("next-action"))
                    .extracting(prompt -> prompt.payload().get("actions").toString())
                    .containsExactly("[\"PROCEED\",\"RERUN_TEST_STEP\",\"CREATE\",\"BROWSE\",\"CANCEL\"]",
                            "[\"CONTINUE\",\"RERUN_TEST_STEP\",\"RERUN_ATOMIC_PROCEDURE\",\"CREATE\",\"BROWSE\",\"CANCEL\"]");
            assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
            assertThat(result.getStepResults()).singleElement()
                    .satisfies(stepResult -> assertThat(stepResult.getExecutionStatus()).isEqualTo(SUCCESS));
        }
    }

    @Test
    @DisplayName("A procedure the operator creates from the next-action prompt is recorded as created by the operator")
    void procedureCreatedByTheOperatorIsRecordedAsOperatorCreated() throws Exception {
        when(config.isFullyUnattended()).thenReturn(false);
        when(config.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        var step = new TestStep("Open the reports page", List.of(), "The reports page is shown");
        var testCase = new TestCase("Created procedure", List.of(), List.of(), List.of(step));
        var procedure = Procedure.createAtomic(step.stepDescription(), List.of(), "", List.of(), List.of(), false);
        var match = new KnowledgeService.MatchResult(procedure, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(procedure, 0, 0)), false, false, false);
        when(knowledgeService.findBestMatch(eq(step.stepDescription()), any(), any())).thenReturn(Optional.empty(), Optional.of(match));
        when(procedureKnowledgeCollectionService.triggerNewProcedureFlow(anyString(), any(), any(), anyBoolean(), any()))
                .thenReturn(Optional.of(new IngestionNode.NewProcedure(procedure, null, List.of())));

        try (var panel = PanelTestFixture.builder().start();
             var operator = ScriptedOperator.start(panel.baseUrl(), prompt -> switch (prompt.type()) {
                 case "start-step" -> Optional.of("{\"stepIndex\":0}");
                 case "confirmation" -> Optional.of("{\"decision\":\"PROCEED\"}");
                 case "next-action" -> Optional.of("{\"action\":\"CREATE\"}");
                 default -> Optional.of("{}");
             })) {
            var result = executeSupervised(testCase, panel);

            assertThat(operator.seen()).extracting(ScriptedOperator.SeenPrompt::type).contains("next-action");
            assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
            var resolution = ArgumentCaptor.forClass(StepResolution.class);
            verify(executionRunRepository).appendResolution(any(), resolution.capture());
            assertThat(resolution.getValue().source()).isEqualTo(StepResolution.Source.OPERATOR_CREATED);
            assertThat(resolution.getValue().chosenProcedureId()).isEqualTo(procedure.id());
        }
    }

    @Test
    @DisplayName("Cancelling a supervised run while a prompt is open ends it with no open prompt, and the panel shows it aborted")
    void cancellingWhileAPromptIsOpenAbortsTheRunAndClosesThePrompt() throws Exception {
        when(config.isFullyUnattended()).thenReturn(false);
        when(config.getExecutionMode()).thenReturn(ExecutionMode.SUPERVISED);
        var step = new TestStep("Click the login button", List.of(), "The dashboard is shown");
        var testCase = new TestCase("Cancelled", List.of(), List.of(), List.of(step, step));
        var promptOpened = new CountDownLatch(1);
        try (var panel = PanelTestFixture.builder().start();
             var operator = ScriptedOperator.start(panel.baseUrl(), _ -> {
                 promptOpened.countDown();
                 return Optional.empty();
             })) {
            when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.of(testCase));
            var observer = panel.runObserver();
            var agent = buildAgent(observer.observe(StreamingEventEmitter.NOOP), knowledgeService, failureContextService,
                    asyncExecutionPersistenceService, panel.operatorPrompts(), observer);
            var result = new AtomicReference<TestExecutionResult>();
            var run = Thread.ofVirtual().start(() -> result.set(agent.executeTestCase("run")));
            assertThat(promptOpened.await(10, TimeUnit.SECONDS)).isTrue();

            // What AbstractAgentExecutor.cancel does to the execution, after which UiAgentExecutor reports the run aborted
            run.interrupt();
            assertThat(run.join(Duration.ofSeconds(10))).isTrue();
            observer.taskAborted();

            assertThat(result.get().getTestExecutionStatus()).isEqualTo(ERROR);
            var state = operator.snapshot();
            assertThat(state.get("prompt").isNull()).isTrue();
            assertThat(state.get("runStatus").asText()).isEqualTo("ABORTED");
            assertThat(operator.seen()).extracting(ScriptedOperator.SeenPrompt::type).containsExactly("start-step");
        }
    }

    /** Runs the test case answered through the panel, observed by it as the A2A executor does. */
    private TestExecutionResult executeSupervised(@NotNull TestCase testCase, @NotNull PanelTestFixture panel) {
        when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.of(testCase));
        var observer = panel.runObserver();
        var emitter = observer.observe(StreamingEventEmitter.NOOP);
        var result = buildAgent(emitter, knowledgeService, failureContextService, asyncExecutionPersistenceService,
                panel.operatorPrompts(), observer).executeTestCase("run");
        observer.taskFinished(result);
        return result;
    }

    private TestExecutionResult execute(@NotNull TestCase testCase) {
        return execute(testCase, StreamingEventEmitter.NOOP);
    }

    @Test
    @DisplayName("An unexpected failure outside the execution flow still reports the captured logs and the recording")
    void unexpectedFailureStillReportsLogsAndRecording() {
        var step = new TestStep("Click the login button", List.of(), "The dashboard is shown");
        var testCase = new TestCase("Unexpected failure", List.of(), List.of(), List.of(step));
        doThrow(new IllegalStateException("the screen recorder could not start")).when(screenRecorder).beginScreenCapture();

        UiTestExecutionResult result = (UiTestExecutionResult) execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getGeneralErrorMessage()).contains("the screen recorder could not start");
        // Without these the caller would receive a bare failure and no trace of what the run did before it broke.
        assertThat(result.getLogs()).containsExactly("execution started", "execution finished");
        assertThat(result.getVideoPath()).isEqualTo("videos/smoke.mp4");
    }

    @Test
    @DisplayName("Procedure matching: a match at the high threshold is executed directly")
    void matchAtHighThresholdIsExecuted() {
        var step = new TestStep("Select 'Germany' in the country dropdown", List.of(), "Germany is selected");
        var testCase = new TestCase("Direct match", List.of(), List.of(), List.of(step));
        var candidate = Procedure.createAtomic("Select 'Germany' in the country dropdown", List.of(), "", List.of(), List.of(), false);
        var realKnowledgeService = knowledgeServiceMatching(candidate, 0.98);

        TestExecutionResult result = execute(testCase, StreamingEventEmitter.NOOP, realKnowledgeService);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult ->
                assertThat(stepResult.getExecutionStatus()).isEqualTo(SUCCESS));
    }

    @Test
    @DisplayName("Procedure matching: a match below the high threshold is not executed")
    void matchBelowHighThresholdIsNotExecuted() {
        var step = new TestStep("Select 'Germany' in the country dropdown", List.of(), "Germany is selected");
        var testCase = new TestCase("Low confidence match", List.of(), List.of(), List.of(step));
        var candidate = Procedure.createAtomic("Select 'France' in the country dropdown", List.of(), "", List.of(), List.of(), false);
        var realKnowledgeService = knowledgeServiceMatching(candidate, 0.96);

        TestExecutionResult result = execute(testCase, StreamingEventEmitter.NOOP, realKnowledgeService);

        // Unattended mode has no operator to resolve a low-confidence match, so the run ends as ERROR instead of
        // executing the procedure of the other country.
        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        verify(stepActionAgent, never()).executeAndGetResult(any());
    }

    /**
     * Builds a real {@link KnowledgeService} over the mocked Neo4j boundary, returning {@code candidate} at
     * {@code score}.
     */
    private KnowledgeService knowledgeServiceMatching(@NotNull Procedure candidate, double score) {
        when(config.getKnowledgeMatchConfidenceLow()).thenReturn(0.88);
        when(config.getKnowledgeMatchConfidenceHigh()).thenReturn(0.97);
        when(config.getKnowledgeMatchTopN()).thenReturn(5);
        when(config.getStabilityPenaltyThreshold()).thenReturn(0.5);
        when(embeddingService.embed(any())).thenReturn(new Embedding(new float[]{0.1f}));
        when(procedureRepository.findBySemanticSearch(any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(new ProcedureMatch(candidate, score)));
        when(procedureRepository.findCandidateContextBatch(any(), any()))
                .thenReturn(List.of(new ProcedureRepository.CandidateContext(candidate.id(), Optional.empty(), Optional.empty(), 0)));
        when(phraseEmbeddingRepository.findPrerequisitesSatisfactionBatch(any(), any(), anyDouble())).thenReturn(List.of());
        return new KnowledgeService(procedureRepository, embeddingService, decompositionService, phraseEmbeddingRepository, config);
    }

    private TestExecutionResult execute(@NotNull TestCase testCase, @NotNull StreamingEventEmitter eventEmitter) {
        return execute(testCase, eventEmitter, knowledgeService);
    }

    private TestExecutionResult execute(@NotNull TestCase testCase, @NotNull StreamingEventEmitter eventEmitter,
                                        @NotNull KnowledgeService knowledgeServiceToUse) {
        when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.of(testCase));
        return buildAgent(eventEmitter, knowledgeServiceToUse, failureContextService, asyncExecutionPersistenceService)
                .executeTestCase("run");
    }

    private TestExecutionResult execute(@NotNull TestCase testCase, @NotNull FailureContextService failureContextServiceToUse) {
        when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.of(testCase));
        return buildAgent(StreamingEventEmitter.NOOP, knowledgeService, failureContextServiceToUse, asyncExecutionPersistenceService)
                .executeTestCase("run");
    }

    private TestExecutionResult execute(@NotNull TestCase testCase, @NotNull AsyncExecutionPersistenceService persistenceServiceToUse) {
        when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.of(testCase));
        return buildAgent(StreamingEventEmitter.NOOP, knowledgeService, failureContextService, persistenceServiceToUse)
                .executeTestCase("run");
    }

    /** Wires the whole real execution graph, injecting the mocked seams and the given streaming emitter. */
    private UiTestAgent buildAgent(@NotNull StreamingEventEmitter eventEmitter, @NotNull KnowledgeService knowledgeService,
                                   @NotNull FailureContextService failureContextService,
                                   @NotNull AsyncExecutionPersistenceService asyncExecutionPersistenceService) {
        return buildAgent(eventEmitter, knowledgeService, failureContextService, asyncExecutionPersistenceService, operatorPrompts,
                runObserver);
    }

    /** Wires the whole real execution graph with the given operator prompts and run observer, e.g. those of a running panel. */
    private UiTestAgent buildAgent(@NotNull StreamingEventEmitter eventEmitter, @NotNull KnowledgeService knowledgeService,
                                   @NotNull FailureContextService failureContextService,
                                   @NotNull AsyncExecutionPersistenceService asyncExecutionPersistenceService,
                                   @NotNull OperatorPrompts operatorPrompts, @NotNull RunObserver runObserver) {
        var context = new TestExecutionContext(eventEmitter);
        var visualState = new VisualStateHolder(new VisualState(screenshot));
        var verificationTools = new VerificationTools(budgetManager, config);
        var stepOrchestrator = new StepExecutionOrchestrator(verificationTools, budgetManager, config,
                procedureKnowledgeCollectionService, knowledgeService, knowledgeIngestionService,
                stepActionAgent, preconditionActionAgent, stepVerificationAgent, preconditionVerificationAgent,
                new UiElementAliasRegistry(), visualState, operatorPrompts);
        // One run service per request, as in the agent's request scope
        var executionRunService = new ExecutionRunService(executionRunRepository, asyncExecutionPersistenceService, config);
        var orchestrator = new KnowledgeBasedExecutionOrchestrator(knowledgeService, knowledgeIngestionService,
                stepOrchestrator, procedureKnowledgeCollectionService, satisfiesEdgeService,
                procedureUsageByTestCaseTrackingService, failureContextService, config, uiElementCache,
                uiElementRepository, elementLocationHistoryLookup, asyncExecutionPersistenceService, operatorPrompts, runObserver,
                executionRunService);
        var stateTracker = new ExecutionStateTracker(config);
        return new UiTestAgent(orchestrator, knowledgeService, config, testCaseExtractor, budgetManager, context,
                screenRecorder, logCapture, stateTracker, visualState, runObserver, operatorPrompts,
                executionRunService);
    }

    /** Makes {@link KnowledgeService#findBestMatch} return a direct, high-confidence atomic procedure for a description. */
    private void matchAtomicProcedure(@NotNull String description) {
        matchProcedure(description, Procedure.createAtomic(description, List.of(), "", List.of(), List.of(), false));
    }

    /** Makes {@link KnowledgeService#findBestMatch} return the given procedure as a direct, high-confidence match. */
    private void matchProcedure(@NotNull String description, @NotNull Procedure procedure) {
        var match = new KnowledgeService.MatchResult(procedure, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(procedure, 0, 0)), false, false, false);
        when(knowledgeService.findBestMatch(eq(description), any(), any())).thenReturn(Optional.of(match));
    }

    private static UiOperationExecutionResult<EmptyExecutionResult> actionSuccess() {
        return new UiOperationExecutionResult<>(ExecutionStatus.SUCCESS, "ok", new EmptyExecutionResult(true, "done"), null);
    }

    private UiOperationExecutionResult<EmptyExecutionResult> actionReportedFailure(@NotNull String message) {
        return new UiOperationExecutionResult<>(ExecutionStatus.SUCCESS, "ok", new EmptyExecutionResult(false, message), screenshot);
    }

    private static UiOperationExecutionResult<VerificationExecutionResult> verification(boolean success, @NotNull String message) {
        return new UiOperationExecutionResult<>(ExecutionStatus.SUCCESS, "ok", new VerificationExecutionResult(success, message), null);
    }

    /**
     * Captures the ordered kinds of streaming events the execution flow publishes, so a test can assert what a live
     * client would observe without standing up the A2A transport (already covered by the {@code agent_core} suite).
     */
    private static final class RecordingEventEmitter implements StreamingEventEmitter {
        private final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void emitStepStarted(@NotNull TestStep step) {
            events.add("step-started");
        }

        @Override
        public void emitPreconditionStarted(@NotNull String precondition) {
            events.add("precondition-started");
        }

        @Override
        public void emitStepResult(@NotNull TestStepResult stepResult) {
            events.add("step-result");
        }

        @Override
        public void emitPreconditionResult(@NotNull PreconditionResult preconditionResult) {
            events.add("precondition-result");
        }

        @Override
        public void emitLog(@NotNull String logLine) {
            events.add("log");
        }

        @Override
        public void emitStatus(@NotNull String message) {
            events.add("status");
        }
    }
}
