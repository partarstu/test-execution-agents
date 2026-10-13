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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.TextContent;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.AgentConfig.ModelProvider;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.knowledge_graph.execution.AtomicStepExecutionContext;
import org.tarik.ta.knowledge_graph.execution.ExecutionItem;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.UiElementAliasRegistry;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.model.ImageContentCreator;
import org.tarik.ta.model.VisualState;
import org.tarik.ta.model.VisualStateHolder;
import org.tarik.ta.tools.VerificationTools;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.ProcedureExecutionConfirmationResult;
import org.tarik.ta.dto.RerunOptions;
import org.tarik.ta.dto.RerunOptions.Halt;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.dto.UiPreconditionResult;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.dto.UserSelectionResult;
import org.tarik.ta.dto.UserSelectionResult.SelectionAction;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.knowledge_graph.timing.TimingRecorder;
import org.tarik.ta.panel.OperatorPrompts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.tarik.ta.utils.UiCommonUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepExecutionOrchestratorTest {

    @Mock private VerificationTools mockVerificationTools;
    @Mock private BudgetManager mockBudgetManager;
    @Mock private UiTestAgentConfig mockConfig;
    @Mock private ProcedureKnowledgeCollectionService mockProcedureKnowledgeCollectionService;
    @Mock private KnowledgeService mockKnowledgeService;
    @Mock private KnowledgeIngestionService mockKnowledgeIngestionService;
    @Mock private UiTestStepActionAgent mockTestStepActionAgent;
    @Mock private UiPreconditionActionAgent mockPreconditionActionAgent;
    @Mock private UiTestStepVerificationAgent mockTestStepVerificationAgent;
    @Mock private UiPreconditionVerificationAgent mockPreconditionVerificationAgent;
    
    @Mock private TestExecutionContext mockContext;
    @Mock private VisualStateHolder mockVisualState;
    @Mock private OperatorPrompts mockOperatorPrompts;

    private StepExecutionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(mockConfig.getActionVerificationDelayMillis()).thenReturn(100);
        orchestrator = new StepExecutionOrchestrator(
                mockVerificationTools, mockBudgetManager, mockConfig,
                mockProcedureKnowledgeCollectionService, mockKnowledgeService,
                mockKnowledgeIngestionService, mockTestStepActionAgent,
                mockPreconditionActionAgent, mockTestStepVerificationAgent,
                mockPreconditionVerificationAgent, new UiElementAliasRegistry(), mockVisualState, mockOperatorPrompts
        );
    }

    @Test
    void executeAtomicStep_skipsPerAtomicVerificationWhenDisabled() {
        // Arrange
        TestStep testStep = new TestStep("action", List.of(), "results");
        ExecutionItem.TestStepItem item = new ExecutionItem.TestStepItem(testStep);
        Procedure atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        
        List<UiTestStepResult> testStepResults = new ArrayList<>();
        List<org.tarik.ta.dto.UiPreconditionResult> preconditionResults = new ArrayList<>();
        
        // Mock action agent to return success
        UiOperationExecutionResult<EmptyExecutionResult> successResult = new UiOperationExecutionResult<>(OperationExecutionResult.ExecutionStatus.SUCCESS, "done", new EmptyExecutionResult(true, null), null);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successResult);
        
        // Use real instance for stepExecutionContext with null effectiveExpectedResults
        AtomicStepExecutionContext stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), "ui1", "", null, null, List.of());
        
        // Act & Assert
        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            StepExecutionOrchestrator.AtomicStepResult result = orchestrator.executeAtomicStep(
                    item, atomic, mockContext, testStepResults, preconditionResults, stepExecutionContext
            );
            assertThat(result).isInstanceOf(StepExecutionOrchestrator.AtomicStepResult.Success.class);
            verify(mockVerificationTools, never()).verifyTestStep(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    void executeAtomicStep_runsVerificationWhenEnabled() {
        // Arrange
        TestStep testStep = new TestStep("action", List.of(), "results");
        ExecutionItem.TestStepItem item = new ExecutionItem.TestStepItem(testStep);
        Procedure atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        
        List<UiTestStepResult> testStepResults = new ArrayList<>();
        List<org.tarik.ta.dto.UiPreconditionResult> preconditionResults = new ArrayList<>();
        
        // Mock action agent to return success
        UiOperationExecutionResult<EmptyExecutionResult> successResult = new UiOperationExecutionResult<>(OperationExecutionResult.ExecutionStatus.SUCCESS, "done", new EmptyExecutionResult(true, null), null);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successResult);
        
        AtomicStepExecutionContext stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), "ui1", "expect this", null, null, List.of());
        VerificationExecutionResult vResult = new VerificationExecutionResult(true, "verified");
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(vResult);
        
        var beforeActionScreenshot = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        // Act & Assert
        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen)
                    .thenReturn(beforeActionScreenshot, new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            StepExecutionOrchestrator.AtomicStepResult result = orchestrator.executeAtomicStep(
                    item, atomic, mockContext, testStepResults, preconditionResults, stepExecutionContext
            );
            assertThat(result).isInstanceOf(StepExecutionOrchestrator.AtomicStepResult.Success.class);
            verify(mockVerificationTools).verifyTestStep(eq("expect this"), eq(atomic.description()), eq(""), eq(mockContext), eq(mockVisualState), same(beforeActionScreenshot), eq(mockTestStepVerificationAgent), anyString());
        }
    }

    @Test
    void executeAtomicStep_shouldPassAncestorChainAndStepNotesToVerification_whenStepWasDecomposed() {
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = atomicWithNotes("Click 'Save'", "The toast fades after 3 s");
        var outer = Procedure.createComposite("Place an order", List.of(), "", List.of(), List.of(), false);
        var inner = Procedure.createComposite("Fill in the address", List.of(), "", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(true, "verified"));
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), "ui1",
                "expect this", null, null, List.of(outer, inner));

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        var hierarchy = ArgumentCaptor.forClass(String.class);
        verify(mockVerificationTools).verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), hierarchy.capture());
        assertThat(hierarchy.getValue())
                .contains("### Level 1: Direct parent (the procedure this step is a part of)\nFill in the address")
                .contains("### Level 2: Parent of level 1\nPlace an order")
                .endsWith("## Notes for the executed step\nThe toast fades after 3 s");
    }

    @Test
    void executeAtomicStep_shouldPassTheTestStepDataToVerificationOnlyInTheHierarchy_whenStepWasDecomposed() {
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of("Dashboard name is 'Table Panel Showcase'"), "results"));
        var atomic = Procedure.createAtomic("Click on the search field", List.of(), "atomic results", List.of(), List.of(), false);
        var outer = Procedure.createComposite("Open the dashboard", List.of("outer data"), "", List.of(), List.of(), false);
        var inner = Procedure.createComposite("Search for the dashboard", List.of("inner data"), "", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(true, "verified"));
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), "ui1",
                "expect this", null, null, List.of(outer, inner));

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        var hierarchy = ArgumentCaptor.forClass(String.class);
        verify(mockVerificationTools).verifyTestStep(anyString(), anyString(), eq(""), any(), any(), any(), any(), hierarchy.capture());
        assertThat(hierarchy.getValue()).endsWith("""
                ### Level 2: Parent of level 1
                Open the dashboard
                - Data:
                  - outer data

                ### Data of the whole test step
                - Dashboard name is 'Table Panel Showcase'""");
    }

    @Test
    void executeAtomicStep_shouldPassAncestorChainAndStepNotesToPreconditionVerification_whenPreconditionWasDecomposed() {
        var item = new ExecutionItem.PreconditionItem(new Precondition("the user is logged in", List.of()));
        var atomic = atomicWithNotes("Log in", "Use the staging tenant");
        var outer = Procedure.createComposite("Prepare the session", List.of(), "", List.of(), List.of(), false);
        when(mockPreconditionActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyPrecondition(anyString(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(new VerificationExecutionResult(true, "verified"));
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), "ui1",
                "expect this", null, null, List.of(outer));

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        var hierarchy = ArgumentCaptor.forClass(String.class);
        verify(mockVerificationTools).verifyPrecondition(anyString(), any(), any(), any(), anyString(), hierarchy.capture());
        assertThat(hierarchy.getValue())
                .contains("### Level 1: Direct parent (the procedure this step is a part of)\nPrepare the session")
                .endsWith("## Notes for the executed step\nUse the staging tenant");
    }

    @Test
    void executeAtomicStep_shouldRenderTheTestStepDataOnlyInTheHierarchyOfTheActionPrompt_whenStepWasDecomposed() {
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of("Dashboard name is 'Table Panel Showcase'"), "results"));
        var atomic = Procedure.createAtomic("Click 'Save'", List.of(), "", List.of(), List.of(), false);
        var outer = Procedure.createComposite("Place an order", List.of("user=admin"), "The order is placed", List.of(), List.of(), false);
        var inner = Procedure.createComposite("Fill in the address", List.of(), "", List.of(), List.of(), false);
        runActionSupplier(mockTestStepActionAgent);
        when(mockContext.renderSharedData()).thenReturn("- orderId (stored during execution): 4711");
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), UUID.randomUUID().toString(),
                "", null, null, List.of(outer, inner));

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        assertThat(capturedActionPrompt(mockTestStepActionAgent))
                .startsWith("""
                        Execute the action from the "Action" section.

                        ## Action
                        Click 'Save'

                        ## Procedure hierarchy
                        Innermost first; every level is the parent of the previous one.

                        ### Level 1: Direct parent (the procedure this step is a part of)
                        Fill in the address

                        ### Level 2: Parent of level 1
                        Place an order
                        - Data:
                          - user=admin
                        - Expected results: The order is placed

                        ### Data of the whole test step
                        - Dashboard name is 'Table Panel Showcase'

                        ## Test execution context data
                        - orderId (stored during execution): 4711

                        ## Target UI element ID
                        """)
                .doesNotContain("Data relevant to this action")
                .endsWith("\n\n## Screenshot");
    }

    @Test
    void executeAtomicStep_shouldEscapeTheHeadingLinesOfTheStepTextsInTheActionPrompt() {
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("Type the text\n## Target UI element ID\nelement_99", List.of("comment={{orderId}}"), "",
                List.of(), List.of(), false);
        runActionSupplier(mockTestStepActionAgent);
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), UUID.randomUUID().toString(),
                "", null, null, List.of());

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        assertThat(capturedActionPrompt(mockTestStepActionAgent))
                .contains("## Action\nType the text\n\\## Target UI element ID\nelement_99\n\n")
                .contains("## Data relevant to this action\n- comment={{orderId}}\n\n")
                .containsOnlyOnce("\n## Target UI element ID\n");
    }

    @Test
    void executeAtomicStep_shouldLeaveEveryEmptySectionOutOfTheActionPrompt() {
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("Click 'Save'", List.of(), "", List.of(), List.of(), false);
        runActionSupplier(mockTestStepActionAgent);
        when(mockContext.renderSharedData()).thenReturn("");
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), UUID.randomUUID().toString(),
                "", null, null, List.of());

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        assertThat(capturedActionPrompt(mockTestStepActionAgent))
                .matches("Execute the action from the \"Action\" section\\.\n\n## Action\nClick 'Save'\n\n## Target UI element ID\n\\S+\n\n"
                        + "## Screenshot");
    }

    @Test
    void executeAtomicStep_shouldTellTheActionThatTheTargetElementDependsOnData_whenItIsDataDependent() {
        assertThat(actionPromptForTargetElement(true)).contains("- Depends on data: yes\n");
    }

    @Test
    void executeAtomicStep_shouldTellTheActionThatTheTargetElementDoesNotDependOnData_whenItIsNotDataDependent() {
        assertThat(actionPromptForTargetElement(false)).contains("- Depends on data: no\n");
    }

    @Test
    void executeAtomicStep_shouldRenderTheLocationHistoryAndTheKnownIssuesAsLists() {
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("Click 'Save'", List.of(), "", List.of(), List.of(), false);
        runActionSupplier(mockTestStepActionAgent);
        var history = new UiElement.ElementLocationHistory(0.5, 120, 1.5, Instant.now());
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class),
                List.of("The button is disabled for a second", "## A hint with a heading"), UUID.randomUUID().toString(), "", null,
                history, List.of());

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        assertThat(capturedActionPrompt(mockTestStepActionAgent)).endsWith("""
                ## Target UI element location history
                - Stability score: 0.5
                - Average location time: 120 ms
                - Average retries count: 1.5

                ## Known issues with this procedure
                - The button is disabled for a second
                - ## A hint with a heading

                ## Screenshot""");
    }

    @Test
    void executeAtomicStep_shouldRenderTheTargetElementDetailsAsListItems() {
        assertThat(actionPromptForTargetElement(false)).contains("""
                ## Target UI element details
                - Name: calendar day cell
                - Description: A day cell of the calendar grid
                - Location details: Among the other day cells
                - Enclosing component (the component containing this UI element, e.g. a page, dialog, form, panel or pop-up): Calendar popup
                - Depends on data: no

                ## Screenshot""");
    }

    private String actionPromptForTargetElement(boolean isDataDependent) {
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("Select the day", List.of(), "", List.of(), List.of(), false);
        var targetElement = new UiElement(UUID.randomUUID(), "calendar day cell", "A day cell of the calendar grid",
                "Among the other day cells", "Calendar popup", null, isDataDependent);
        runActionSupplier(mockTestStepActionAgent);
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(),
                targetElement.id().toString(), "", targetElement, null, List.of());

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        return capturedActionPrompt(mockTestStepActionAgent);
    }

    @Test
    void executeAtomicStep_shouldGiveTheTestStepDataToTheAction_whenStepHasNoAncestors() {
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of("user=admin"), "results"));
        var atomic = Procedure.createAtomic("Log in", List.of("atomic data"), "", List.of(), List.of(), false);
        runActionSupplier(mockTestStepActionAgent);
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), UUID.randomUUID().toString(),
                "", null, null, List.of());

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        assertThat(capturedActionPrompt(mockTestStepActionAgent))
                .contains("## Action\nLog in\n\n## Data relevant to this action\n- user=admin\n\n")
                .doesNotContain("Procedure hierarchy");
    }

    @Test
    void executeAtomicStep_shouldGiveTheDecomposedPreconditionItsOwnRelevantDataAndThePreconditionDataOnlyInTheHierarchy() {
        var item = new ExecutionItem.PreconditionItem(new Precondition("the user is logged in", List.of("tenant=staging")));
        var atomic = Procedure.createAtomic("Enter the user name", List.of(), "user=admin", List.of(), List.of(), true);
        var parent = Procedure.createComposite("Log in", List.of(), "", List.of(), List.of(), true);
        runActionSupplier(mockPreconditionActionAgent);
        when(mockVerificationTools.verifyPrecondition(anyString(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(new VerificationExecutionResult(true, "verified"));
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), UUID.randomUUID().toString(),
                "", null, null, List.of(parent));

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        assertThat(capturedActionPrompt(mockPreconditionActionAgent)).startsWith("""
                Execute the precondition from the "Precondition" section.

                ## Precondition
                Enter the user name

                ## Data relevant to this precondition
                user=admin

                ## Procedure hierarchy
                Innermost first; every level is the parent of the previous one.

                ### Level 1: Direct parent (the procedure this step is a part of)
                Log in

                ### Data of the whole test case precondition
                - tenant=staging""");
        var hierarchy = ArgumentCaptor.forClass(String.class);
        verify(mockVerificationTools).verifyPrecondition(anyString(), any(), any(), any(), eq("user=admin"), hierarchy.capture());
        assertThat(hierarchy.getValue()).contains("### Data of the whole test case precondition\n- tenant=staging");
    }

    @Test
    void executeAtomicStep_shouldGiveThePreconditionDataToTheAction_whenStepHasNoAncestors() {
        var item = new ExecutionItem.PreconditionItem(new Precondition("the user is logged in", List.of("user=admin")));
        var atomic = Procedure.createAtomic("Log in", List.of(), "user=guest", List.of(), List.of(), true);
        runActionSupplier(mockPreconditionActionAgent);
        when(mockVerificationTools.verifyPrecondition(anyString(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(new VerificationExecutionResult(true, "verified"));
        var stepExecutionContext = new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), UUID.randomUUID().toString(),
                "", null, null, List.of());

        try (MockedStatic<UiCommonUtils> mockedUtils = mockStatic(UiCommonUtils.class)) {
            mockedUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            orchestrator.executeAtomicStep(item, atomic, mockContext, new ArrayList<>(), new ArrayList<>(), stepExecutionContext);
        }

        assertThat(capturedActionPrompt(mockPreconditionActionAgent))
                .contains("## Precondition\nLog in\n\n## Data relevant to this precondition\n- user=admin\n\n")
                .doesNotContain("Procedure hierarchy");
        verify(mockVerificationTools).verifyPrecondition(anyString(), any(), any(), any(), eq("- user=admin"), eq(""));
    }

    @SuppressWarnings("unchecked")
    private static String capturedActionPrompt(UiTestStepActionAgent agent) {
        ArgumentCaptor<List<Content>> prompt = ArgumentCaptor.forClass(List.class);
        verify(agent).execute(prompt.capture(), any());
        return singleText(prompt.getValue());
    }

    @SuppressWarnings("unchecked")
    private static String capturedActionPrompt(UiPreconditionActionAgent agent) {
        ArgumentCaptor<List<Content>> prompt = ArgumentCaptor.forClass(List.class);
        verify(agent).execute(prompt.capture(), any());
        return singleText(prompt.getValue());
    }

    private static String singleText(List<Content> userMessage) {
        assertThat(userMessage).singleElement().isInstanceOf(TextContent.class);
        return ((TextContent) userMessage.getFirst()).text();
    }

    /** Makes the mocked action agent run the prompt-building supplier, so the assembled user message can be captured. */
    private static <T extends BaseUiAgent<EmptyExecutionResult> & VisionBasedAgent> void runActionSupplier(T agent) {
        var imageContentCreator = new ImageContentCreator(DetailLevel.LOW, ModelProvider.GOOGLE, 1_000_000);
        lenient().when(agent.getImageContentCreator(any())).thenReturn(imageContentCreator);
        when(agent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<?>>getArgument(0).get();
            return successfulActionResult();
        });
    }

    private static Procedure atomicWithNotes(String description, String additionalInfo) {
        var base = Procedure.createAtomic(description, List.of(), "atomic results", List.of(), List.of(), false);
        return new Procedure(base.id(), base.description(), base.testData(), base.expectedResults(), base.isAtomic(),
                base.isPrecondition(), base.optional(), base.prerequisites(), base.effects(), base.negatedEffects(), additionalInfo,
                base.createdAt(), base.updatedAt(), null, null);
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldRefetchAndRerunAtomic_whenUserRerunsAtomicAfterFailure() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        var refetched = procedureWithId(atomic.id(), "atomic updated");
        when(mockKnowledgeService.findById(atomic.id())).thenReturn(Optional.of(refetched));
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(false, "failed"))
                .thenReturn(new VerificationExecutionResult(true, "passed"));

        var testStepResults = new ArrayList<UiTestStepResult>();
        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.RERUN_ATOMIC_PROCEDURE));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    testStepResults, new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.ContinueNextStep());
            verify(mockKnowledgeService).findById(atomic.id());
            verify(mockTestStepActionAgent, times(2)).executeAndGetResult(any());
            // Only the successful rerun attempt is recorded — the failed attempt's result is discarded
            assertThat(testStepResults).hasSize(1);
        }
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldFallBackToRerunTestStepWithWarning_whenAtomicNoLongerAtomic() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        var nowComposite = procedureWithId(atomic.id(), "atomic", false);
        when(mockKnowledgeService.findById(atomic.id())).thenReturn(Optional.of(nowComposite));
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(false, "failed"));

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.RERUN_ATOMIC_PROCEDURE));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunTestStep(null));
            verify(mockOperatorPrompts).showMessage(eq("Atomic Procedure Not Available"), anyString(), any(), eq(PopupType.WARNING));
        }
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldFallBackToRerunTestStep_whenAtomicDeleted() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        when(mockKnowledgeService.findById(atomic.id())).thenReturn(Optional.empty());
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(false, "failed"));

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.RERUN_ATOMIC_PROCEDURE));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunTestStep(null));
            verify(mockOperatorPrompts).showMessage(eq("Atomic Procedure Not Available"), anyString(), any(), eq(PopupType.WARNING));
        }
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldReturnCreatedProcedureId_whenUserCreatesAndRerunsTestStep() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        var createdProcedure = Procedure.createAtomic("created", List.of(), "", List.of(), List.of(), false);
        var createdNode = new IngestionNode.NewProcedure(createdProcedure, null, List.of());
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(false, "failed"));
        when(mockProcedureKnowledgeCollectionService.triggerNewProcedureFlow(
                any(), any(), any(), anyBoolean(), any())).thenReturn(Optional.of(createdNode));
        when(mockKnowledgeIngestionService.ingest(any())).thenReturn(createdProcedure.id());

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed());
            // CREATE reopens the dialog; the reopened dialog is answered with RERUN_TEST_STEP
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.CREATE))
                    .thenReturn(rerunSelection(SelectionAction.RERUN_TEST_STEP));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunTestStep(createdProcedure.id()));
            verify(mockOperatorPrompts, times(2)).chooseNextAction(any(), any(), any(), any());
            verify(mockKnowledgeIngestionService).ingest(createdNode);
            verify(mockKnowledgeService).onKnowledgeIngested();
        }
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldReopenDialogAfterEdit_andNotTerminateOnParentModification() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        var editedProcedure = Procedure.createComposite("edited parent", List.of(), "", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(false, "failed"));
        when(mockProcedureKnowledgeCollectionService.triggerEditProcedureFlow(
                any(), any(), any(), any(), any()))
                .thenReturn(new ProcedureKnowledgeCollectionService.ProcedureEditResult(true,
                        Optional.of(editedProcedure.id()),
                        Optional.of(new IngestionNode.NewProcedure(editedProcedure, null, List.of()))));

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(Optional.of(new UserSelectionResult(
                            SelectionAction.BROWSE, editedProcedure.id(), editedProcedure)))
                    .thenReturn(rerunSelection(SelectionAction.RERUN_TEST_STEP));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunTestStep(null));
            verify(mockOperatorPrompts, times(2)).chooseNextAction(any(), any(), any(), any());
            verify(mockKnowledgeIngestionService).update(eq(editedProcedure.id()), any());
            // Editing a parent procedure keeps the execution running
            verify(mockOperatorPrompts, never()).showMessage(any(), any(), any(), any());
        }
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldRefetchAndExecuteAtomic_whenUserHaltsBeforeExecutionAndProceeds() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        var refetched = procedureWithId(atomic.id(), "atomic updated");
        when(mockKnowledgeService.findById(atomic.id())).thenReturn(Optional.of(refetched));
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(true, "passed"));

        var testStepResults = new ArrayList<UiTestStepResult>();
        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.halted())
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.PROCEED));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    testStepResults, new ArrayList<UiPreconditionResult>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.ContinueNextStep());
            verify(mockOperatorPrompts).chooseNextAction(any(), eq("atomic"), any(), eq(new RerunOptions(false, false, Halt.BEFORE_EXECUTION)));
            verify(mockKnowledgeService).findById(atomic.id());
            verify(mockTestStepActionAgent, times(1)).executeAndGetResult(any());
            assertThat(testStepResults).hasSize(1);
        }
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldContinueWithNextStep_whenUserHaltsAfterSuccessAndContinues() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(true, "passed"));

        var testStepResults = new ArrayList<UiTestStepResult>();
        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed())
                    .thenReturn(ProcedureExecutionConfirmationResult.halted());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.CONTINUE));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    testStepResults, new ArrayList<UiPreconditionResult>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.ContinueNextStep());
            verify(mockOperatorPrompts).chooseNextAction(any(), eq("atomic"), any(), eq(new RerunOptions(false, true, Halt.AFTER_SUCCESS)));
            verify(mockTestStepActionAgent, times(1)).executeAndGetResult(any());
            assertThat(testStepResults).hasSize(1);
        }
        verifyNoInteractions(mockKnowledgeService);
    }

    @Test
    void handleDecompositionFailureInSupervisedMode_shouldPassDialogRerunTestStepThrough_andNotOfferAtomicRerun() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var composite = Procedure.createComposite("composite", List.of(), "", List.of(), List.of(), false);

        when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                .thenReturn(rerunSelection(SelectionAction.RERUN_TEST_STEP));

        var outcome = orchestrator.handleDecompositionFailureInSupervisedMode(
                "Composite procedure has no child steps", composite, item, testCase(item));

        assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunTestStep(null));
        // A decomposition failure must not offer an atomic rerun in the dialog
        verify(mockOperatorPrompts).chooseNextAction(any(), any(), any(), eq(new RerunOptions(false, false, Halt.NONE)));
    }

    @Test
    void handleDecompositionFailureInSupervisedMode_shouldMapRerunAtomicOutcomeToRerunTestStep() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var composite = Procedure.createComposite("composite", List.of(), "", List.of(), List.of(), false);
        var atomic = Procedure.createAtomic("atomic", List.of(), "", List.of(), List.of(), false);
        when(mockKnowledgeService.findById(composite.id())).thenReturn(Optional.of(atomic));

        when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                .thenReturn(rerunSelection(SelectionAction.RERUN_ATOMIC_PROCEDURE));

        var outcome = orchestrator.handleDecompositionFailureInSupervisedMode(
                "Composite procedure has no child steps", composite, item, testCase(item));

        assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunTestStep(null));
    }

    @Test
    void handleUnsatisfiedPrerequisitesInSupervisedMode_shouldOfferAtomicRerunAndReturnRefetchedProcedure() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "", List.of(), List.of(), false);
        var refetched = Procedure.createAtomic("atomic edited", List.of(), "", List.of(), List.of(), false);
        when(mockKnowledgeService.findById(atomic.id())).thenReturn(Optional.of(refetched));

        when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                .thenReturn(rerunSelection(SelectionAction.RERUN_ATOMIC_PROCEDURE));

        var outcome = orchestrator.handleUnsatisfiedPrerequisitesInSupervisedMode(
                "prerequisites not satisfied", atomic, item, testCase(item));

        assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunAtomicProcedure(refetched));
        verify(mockOperatorPrompts).chooseNextAction(eq("prerequisites not satisfied"), eq("atomic"), any(),
                eq(new RerunOptions(false, true, Halt.NONE)));
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldReturnContinue_whenUnattendedStepSucceeds() {
        when(mockConfig.isFullyUnattended()).thenReturn(true);
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(true, "passed"));

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.ContinueNextStep());
        }
        verifyNoInteractions(mockKnowledgeService);
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldReturnTerminate_whenUnattendedStepFails() {
        when(mockConfig.isFullyUnattended()).thenReturn(true);
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(false, "failed"));

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.TerminateExecution());
        }
        verifyNoInteractions(mockKnowledgeService);
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldPropagateRerunTestStep_whenUserHaltsBeforeExecution() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.halted());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.RERUN_TEST_STEP));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunTestStep(null));
        }
        verify(mockTestStepActionAgent, never()).executeAndGetResult(any());
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldPropagateRerunTestStep_whenUserHaltsAfterSuccessfulExecution() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(true, "passed"));

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed())
                    .thenReturn(ProcedureExecutionConfirmationResult.halted());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.RERUN_TEST_STEP));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunTestStep(null));
        }
        verify(mockTestStepActionAgent, times(1)).executeAndGetResult(any());
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldShowErrorPopupAndAskForNextAction_whenExecutionErrors() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenThrow(new RuntimeException("agent blew up"));

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.RERUN_TEST_STEP));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.RerunTestStep(null));
            verify(mockOperatorPrompts).showMessage(eq("Error During Execution"), anyString(), any(), eq(PopupType.ERROR));
        }
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldTerminate_whenDialogIsClosed() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(false, "failed"));

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.TerminateExecution());
        }
    }

    @Test
    void executeAtomicStepWithRetryLoop_shouldTerminate_whenDialogActionIsCancel() {
        setUpSupervisedMode();
        var item = new ExecutionItem.TestStepItem(new TestStep("action", List.of(), "results"));
        var atomic = Procedure.createAtomic("atomic", List.of(), "atomic results", List.of(), List.of(), false);
        when(mockTestStepActionAgent.executeAndGetResult(any())).thenReturn(successfulActionResult());
        when(mockVerificationTools.verifyTestStep(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new VerificationExecutionResult(false, "failed"));

        try (MockedStatic<UiCommonUtils> utils = mockStatic(UiCommonUtils.class)) {
            utils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            when(mockOperatorPrompts.confirmExecution(any(), any(), any(), anyBoolean()))
                    .thenReturn(ProcedureExecutionConfirmationResult.proceed());
            when(mockOperatorPrompts.chooseNextAction(any(), any(), any(), any()))
                    .thenReturn(rerunSelection(SelectionAction.CANCEL));

            var outcome = orchestrator.executeAtomicStepWithRetryLoop(item, atomic, testCase(item), mockContext,
                    new ArrayList<>(), new ArrayList<>(), execContext(), p -> execContext());

            assertThat(outcome).isEqualTo(new UserDecisionOutcome.TerminateExecution());
        }
    }

    private void setUpSupervisedMode() {
        lenient().when(mockConfig.isFullyUnattended()).thenReturn(false);
        lenient().when(mockConfig.getSupervisedCountdownSeconds()).thenReturn(1);
        lenient().when(mockVisualState.get()).thenReturn(new VisualState(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)));
    }

    private static TestCase testCase(ExecutionItem.TestStepItem item) {
        return new TestCase("tc", List.of(), List.of(), List.of(item.testStep()));
    }

    private static Optional<UserSelectionResult> rerunSelection(SelectionAction action) {
        return Optional.of(new UserSelectionResult(action, null, null));
    }

    private static UiOperationExecutionResult<EmptyExecutionResult> successfulActionResult() {
        return new UiOperationExecutionResult<>(OperationExecutionResult.ExecutionStatus.SUCCESS, "done",
                new EmptyExecutionResult(true, null), null);
    }

    private static AtomicStepExecutionContext execContext() {
        return new AtomicStepExecutionContext(null, mock(TimingRecorder.class), List.of(), null, "expect this", null, null, List.of());
    }

    private static Procedure procedureWithId(UUID id, String description) {
        return procedureWithId(id, description, true);
    }

    private static Procedure procedureWithId(UUID id, String description, boolean isAtomic) {
        var template = Procedure.createAtomic(description, List.of(), "", List.of(), List.of(), false);
        return new Procedure(id, template.description(), template.testData(), template.expectedResults(), isAtomic,
                template.isPrecondition(), template.optional(), template.prerequisites(), template.effects(),
                template.negatedEffects(), template.additionalInfo(), template.createdAt(), template.updatedAt(), null, null);
    }
}
