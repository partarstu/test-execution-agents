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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.UiTestAgent;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.UiPreconditionActionAgent;
import org.tarik.ta.agents.UiPreconditionVerificationAgent;
import org.tarik.ta.agents.UiTestStepActionAgent;
import org.tarik.ta.agents.UiTestStepVerificationAgent;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.knowledge_graph.KnowledgeBasedExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.ProcedureKnowledgeCollectionService;
import org.tarik.ta.knowledge_graph.StepExecutionOrchestrator;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.AsyncExecutionPersistenceService;
import org.tarik.ta.knowledge_graph.service.ExecutionStateTracker;
import org.tarik.ta.knowledge_graph.service.FailureContextService;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;
import org.tarik.ta.knowledge_graph.service.SatisfiesEdgeService;
import org.tarik.ta.knowledge_graph.service.UiElementCache;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.tools.VerificationTools;
import org.tarik.ta.utils.ScreenRecorder;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.FAILURE;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.SUCCESS;

/**
 * Hermetic end-to-end smoke test for the UI test execution agent. The whole knowledge-based execution flow runs for
 * real ({@link UiTestAgent} -> {@link KnowledgeBasedExecutionOrchestrator} -> {@link StepExecutionOrchestrator} ->
 * {@link VerificationTools}); only the external boundaries are mocked. Unlike the API agent (which drives a scripted
 * {@code ChatModel} against real tools), the UI plan mocks the LLM agent interfaces themselves, because the UI tools
 * would otherwise drive a real screen. The mocked seams are:
 * <ul>
 *     <li>the four LLM agents (step/precondition action + verification), stubbed at {@code executeAndGetResult};</li>
 *     <li>{@link KnowledgeService} — the Neo4j boundary — returning in-memory {@link Procedure} matches;</li>
 *     <li>the screen, via a {@link MockedStatic} of {@link UiCommonUtils#captureScreen()} that sits above every AWT
 *     {@code Robot} call, keeping the suite xvfb/headless-safe.</li>
 * </ul>
 * Everything is wired by hand (no Avaje DI bootstrap) so the run stays fully in-process and deterministic. The agent
 * runs in fully-unattended mode so no supervised-mode Swing dialogs are shown.
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
    private FailureContextService failureContextService;
    @Mock
    private UiElementCache uiElementCache;
    @Mock
    private UiElementRepository uiElementRepository;
    @Mock
    private ElementLocationHistoryLookup elementLocationHistoryLookup;
    @Mock
    private AsyncExecutionPersistenceService asyncExecutionPersistenceService;

    @Mock
    private UiTestStepActionAgent stepActionAgent;
    @Mock
    private UiPreconditionActionAgent preconditionActionAgent;
    @Mock
    private UiTestStepVerificationAgent stepVerificationAgent;
    @Mock
    private UiPreconditionVerificationAgent preconditionVerificationAgent;

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

        lenient().when(screenRecorder.getCurrentRecordingPath()).thenReturn("videos/smoke.mp4");
        lenient().when(logCapture.getLogs()).thenReturn(List.of("execution started", "execution finished"));

        // Happy-path defaults for the test-step agents; the precondition agents are stubbed only where used.
        lenient().when(stepActionAgent.executeAndGetResult(any())).thenReturn(actionSuccess());
        lenient().when(stepVerificationAgent.executeAndGetResult(any())).thenReturn(verification(true, "state matches"));
    }

    @AfterEach
    void tearDown() {
        uiCommonUtilsMockedStatic.close();
    }

    @Test
    @DisplayName("Single step passes and the result carries SystemInfo, logs and the recording path")
    void singleStepPasses() {
        var step = new TestStep("Click the login button", List.of(), "The dashboard is shown");
        var testCase = new TestCase("Single step", List.of(), List.of(step));
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
        var testCase = new TestCase("Multi step", List.of(), List.of(first, second));
        matchAtomicProcedure(first.stepDescription());
        matchAtomicProcedure(second.stepDescription());

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getStepResults()).hasSize(2)
                .allSatisfy(stepResult -> assertThat(stepResult.getExecutionStatus()).isEqualTo(SUCCESS));
    }

    @Test
    @DisplayName("A failing precondition action yields ERROR before any step runs")
    void preconditionFailureYieldsError() {
        var precondition = "The user is logged in";
        var step = new TestStep("Open the profile page", List.of(), "The profile page is shown");
        var testCase = new TestCase("Precondition failure", List.of(precondition), List.of(step));
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
        var testCase = new TestCase("Verification failure", List.of(), List.of(step));
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
        var testCase = new TestCase("Missing procedure", List.of(), List.of(step));
        when(knowledgeService.findBestMatch(eq(step.stepDescription()), any(), any())).thenReturn(Optional.empty());

        TestExecutionResult result = execute(testCase);

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getGeneralErrorMessage()).contains("Knowledge-based execution error");
    }

    @Test
    @DisplayName("Streaming: started + result events are emitted for the precondition and the step in order")
    void streamingEventsEmittedThroughEmitter() {
        var precondition = "The app is open";
        var step = new TestStep("Search for an item", List.of(), "Results are shown");
        var testCase = new TestCase("Streaming", List.of(precondition), List.of(step));
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

    private TestExecutionResult execute(@NotNull TestCase testCase) {
        return execute(testCase, StreamingEventEmitter.NOOP);
    }

    private TestExecutionResult execute(@NotNull TestCase testCase, @NotNull StreamingEventEmitter eventEmitter) {
        when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.of(testCase));
        return buildAgent(eventEmitter).executeTestCase("run");
    }

    /** Wires the whole real execution graph, injecting the mocked seams and the given streaming emitter. */
    private UiTestAgent buildAgent(@NotNull StreamingEventEmitter eventEmitter) {
        var context = new UiTestExecutionContext(new VisualState(screenshot), eventEmitter);
        var verificationTools = new VerificationTools(budgetManager, config);
        var stepOrchestrator = new StepExecutionOrchestrator(verificationTools, budgetManager, config,
                procedureKnowledgeCollectionService, knowledgeService, knowledgeIngestionService,
                stepActionAgent, preconditionActionAgent, stepVerificationAgent, preconditionVerificationAgent);
        var orchestrator = new KnowledgeBasedExecutionOrchestrator(knowledgeService, knowledgeIngestionService,
                stepOrchestrator, procedureKnowledgeCollectionService, satisfiesEdgeService,
                procedureUsageByTestCaseTrackingService, failureContextService, config, uiElementCache,
                uiElementRepository, elementLocationHistoryLookup, asyncExecutionPersistenceService);
        var stateTracker = new ExecutionStateTracker(config);
        return new UiTestAgent(orchestrator, knowledgeService, config, testCaseExtractor, budgetManager, context,
                screenRecorder, logCapture, stateTracker);
    }

    /** Makes {@link KnowledgeService#findBestMatch} return a direct, high-confidence atomic procedure for a description. */
    private void matchAtomicProcedure(@NotNull String description) {
        var procedure = Procedure.createAtomic(description, List.of(), "", List.of(), List.of(), false);
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
    }
}
