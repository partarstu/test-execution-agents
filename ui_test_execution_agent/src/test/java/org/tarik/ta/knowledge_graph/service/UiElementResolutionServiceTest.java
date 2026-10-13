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
package org.tarik.ta.knowledge_graph.service;

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.UiElementDescriptionExtractionAgent;
import org.tarik.ta.agents.UiElementResolutionAgent;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.dto.ScreenRegion;
import org.tarik.ta.dto.UiElementDescriptionResult;
import org.tarik.ta.dto.UiElementLocationResult;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository.UiElementMatch;
import org.tarik.ta.knowledge_graph.service.UiElementResolutionService.ResolutionOutcome;
import org.tarik.ta.panel.OperatorPrompts;
import org.tarik.ta.tools.UiElementRefinementHelper;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.agents.UiElementResolutionAgent.CREATE_WORKFLOW;
import static org.tarik.ta.agents.UiElementResolutionAgent.RESOLVE_WORKFLOW;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;

@ExtendWith(MockitoExtension.class)
class UiElementResolutionServiceTest {
    @Mock
    private UiElementResolutionAgent resolutionAgent;
    @Mock
    private UiElementDescriptionExtractionAgent descriptionExtractionAgent;
    @Mock
    private UiTestAgentConfig config;
    @Mock
    private UiElementRepository elementRepository;
    @Mock
    private UiElementRefinementHelper refinementHelper;
    @Mock
    private OperatorPrompts operatorPrompts;

    private UiElementAliasRegistry aliasRegistry;
    private UiElementResolutionService service;

    @BeforeEach
    void setUp() {
        aliasRegistry = new UiElementAliasRegistry();
        service = new UiElementResolutionService(resolutionAgent, descriptionExtractionAgent, config, elementRepository,
                refinementHelper, aliasRegistry, operatorPrompts);
        lenient().when(config.getElementResolutionMaxDurationMillis()).thenReturn(5_000);
    }

    @Test
    void locate_shouldReturnTheElementAndClearTheStatus_whenAgentReturnsAlias() {
        var element = element("Search field", screenshot());
        returnLocation(new UiElementLocationResult(true, aliasRegistry.aliasFor(element.id()), element.name(), null, "Located"));
        when(elementRepository.findById(element.id())).thenReturn(Optional.of(element));

        var outcome = service.locate("Click search", "dashboard name");

        assertThat(outcome).isEqualTo(new ResolutionOutcome.Located(element));
        verify(operatorPrompts).clearStatus();
    }

    @Test
    void locate_shouldSendTheActionDescriptionAndTheRelatedDataAsSections() {
        when(resolutionAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<Result<?>>>getArgument(0).get();
            return new OperationExecutionResult<>(SUCCESS, "success", null);
        });

        service.locate("Click search", "dashboard name {{name}}");

        verify(resolutionAgent).resolveForAction(List.of(TextContent.from("""
                ## Action description
                Click search

                ## Data related to the element
                dashboard name {{name}}""")), RESOLVE_WORKFLOW);
    }

    @Test
    void createAndLocate_shouldSendTheElementDescriptionToTheCreateWorkflow() {
        when(resolutionAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<Result<?>>>getArgument(0).get();
            return new OperationExecutionResult<>(SUCCESS, "success", null);
        });

        service.createAndLocate("Save button", "");

        verify(resolutionAgent).resolveForAction(List.of(TextContent.from("## Element description\nSave button")), CREATE_WORKFLOW);
    }

    @Test
    void locate_shouldFailWithTheAgentsReason_whenTheAgentReportsFailure() {
        returnLocation(new UiElementLocationResult(false, null, null, null, "Multiple instances of target element were found."));

        var outcome = service.locate("Click search", "");

        assertThat(outcome).isEqualTo(new ResolutionOutcome.Failed("Multiple instances of target element were found."));
    }

    @Test
    void locate_shouldFailWithTheDefaultReason_whenTheAgentReturnsNoResult() {
        returnLocation(null);

        var outcome = service.locate("Click search", "");

        assertThat(outcome).isEqualTo(new ResolutionOutcome.Failed("The element could not be located on the screen or in the database."));
    }

    @Test
    void locate_shouldFail_whenTheAgentReportsSuccessWithoutAnElementId() {
        returnLocation(new UiElementLocationResult(true, " ", null, null, "Located"));

        var outcome = service.locate("Click search", "");

        assertThat(outcome).isEqualTo(new ResolutionOutcome.Failed("Agent reported success but failed to provide an element ID."));
    }

    @Test
    void locate_shouldFail_whenTheAliasIsUnknown() {
        returnLocation(new UiElementLocationResult(true, "E99", null, null, "Located"));

        var outcome = service.locate("Click search", "");

        assertThat(outcome).isEqualTo(new ResolutionOutcome.Failed("Agent resolved unknown element ID 'E99'."));
    }

    @Test
    void locate_shouldFail_whenTheResolvedElementIsNotInTheDatabase() {
        var elementId = UUID.randomUUID();
        returnLocation(new UiElementLocationResult(true, aliasRegistry.aliasFor(elementId), null, null, "Located"));
        when(elementRepository.findById(elementId)).thenReturn(Optional.empty());

        var outcome = service.locate("Click search", "");

        assertThat(outcome).isEqualTo(new ResolutionOutcome.Failed(
                "Agent resolved element ID '%s' but no matching element was found in the database.".formatted(elementId)));
    }

    @Test
    void locate_shouldStoreTheCapturedScreenshot_whenTheLocatedElementHasNone() {
        var element = element("Search field", null);
        var region = new ScreenRegion(1, 2, 3, 4);
        returnLocation(new UiElementLocationResult(true, aliasRegistry.aliasFor(element.id()), element.name(), region, "Located"));
        when(elementRepository.findById(element.id())).thenReturn(Optional.of(element));

        try (var screen = mockStatic(UiCommonUtils.class)) {
            screen.when(() -> UiCommonUtils.captureElementScreenshot(region)).thenReturn(new BufferedImage(3, 4, BufferedImage.TYPE_INT_RGB));

            var outcome = service.locate("Click search", "");

            assertThat(outcome).isInstanceOfSatisfying(ResolutionOutcome.Located.class,
                    located -> assertThat(located.element().screenshot()).isNotNull());
            verify(elementRepository).update(any(UiElement.class));
        }
    }

    @Test
    void locate_shouldReportAnUnexpectedFailure_whenTheResultCannotBeProcessed() {
        var elementId = UUID.randomUUID();
        returnLocation(new UiElementLocationResult(true, aliasRegistry.aliasFor(elementId), "Search field", null, "Located"));
        when(elementRepository.findById(elementId)).thenThrow(new IllegalStateException("the graph is unreachable"));

        var outcome = service.locate("Click search", "");

        assertThat(outcome).isEqualTo(new ResolutionOutcome.Failed(
                "An unexpected error occurred while locating the element: the graph is unreachable"));
    }

    @Test
    void createAndLocate_shouldReportTheAgentFailure_whenTheCreationThrows() {
        when(resolutionAgent.executeAndGetResult(any())).thenThrow(new IllegalStateException("the model refused"));

        var outcome = service.createAndLocate("Search field", "");

        assertThat(outcome).isEqualTo(new ResolutionOutcome.Failed("Element location failed: the model refused"));
        verify(operatorPrompts).clearStatus();
    }

    @Test
    void locate_shouldReportTimeoutAndAbandonTheWorker_whenResolutionExceedsItsBudget() throws Exception {
        when(config.getElementResolutionMaxDurationMillis()).thenReturn(50);
        var workerInterrupted = new CountDownLatch(1);
        when(resolutionAgent.executeAndGetResult(any())).thenAnswer(_ -> {
            try {
                // Far beyond the budget: the runner has to cut the call short instead of waiting for it.
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                workerInterrupted.countDown();
                throw new IllegalStateException("resolution interrupted", e);
            }
            return new OperationExecutionResult<>(SUCCESS, "success", null);
        });

        var outcome = service.locate("Click search", "");

        assertThat(outcome).isEqualTo(new ResolutionOutcome.Failed("Element location timed out after 50 ms."));
        assertThat(workerInterrupted.await(10, SECONDS)).isTrue();
    }

    @Test
    void locate_shouldReturnOnlyAfterTheWorkerEnded_andKeepTheInterrupt_whenTheCallerIsInterrupted() throws Exception {
        var workerStarted = new CountDownLatch(1);
        var workerEnded = new AtomicBoolean();
        when(resolutionAgent.executeAndGetResult(any())).thenAnswer(_ -> {
            workerStarted.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                // The agent finishes its current step before it notices the interrupt
                Thread.sleep(200);
                workerEnded.set(true);
                throw new IllegalStateException("resolution interrupted", e);
            }
            return new OperationExecutionResult<>(SUCCESS, "success", null);
        });
        var outcome = new AtomicReference<ResolutionOutcome>();
        var workerEndedOnReturn = new AtomicBoolean();
        var interruptKept = new AtomicBoolean();

        var caller = Thread.ofVirtual().start(() -> {
            outcome.set(service.locate("Click search", ""));
            workerEndedOnReturn.set(workerEnded.get());
            interruptKept.set(Thread.currentThread().isInterrupted());
        });
        assertThat(workerStarted.await(10, SECONDS)).isTrue();
        caller.interrupt();

        assertThat(caller.join(Duration.ofSeconds(10))).isTrue();
        assertThat(outcome.get()).isEqualTo(new ResolutionOutcome.Failed("Element location was interrupted."));
        assertThat(workerEndedOnReturn).isTrue();
        assertThat(interruptKept).isTrue();
        verify(operatorPrompts).clearStatus();
    }

    @Test
    void extractElementDescription_shouldSendTheProcedureDescriptionAsASectionAndReturnTheExtractedDescription() {
        when(descriptionExtractionAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<Result<?>>>getArgument(0).get();
            return new OperationExecutionResult<>(SUCCESS, "success", new UiElementDescriptionResult("Search icon"));
        });

        var description = service.extractElementDescription("Click search");

        assertThat(description).contains("Search icon");
        verify(descriptionExtractionAgent).extract(List.of(TextContent.from("## Procedure description\nClick search")));
    }

    @Test
    void extractElementDescription_shouldReturnTheProcedureDescription_whenTheAgentFindsNone() {
        when(descriptionExtractionAgent.executeAndGetResult(any()))
                .thenReturn(new OperationExecutionResult<>(SUCCESS, "success", new UiElementDescriptionResult(" ")));

        assertThat(service.extractElementDescription("Click search")).contains("Click search");
    }

    @Test
    void extractElementDescription_shouldReturnEmpty_whenTheExtractionFails() {
        when(descriptionExtractionAgent.executeAndGetResult(any())).thenThrow(new IllegalStateException("no extraction"));

        assertThat(service.extractElementDescription("Click search")).isEmpty();
    }

    @Test
    void searchElements_shouldReturnTheElementsOfTheSemanticMatches() {
        var element = element("Search field", null);
        when(refinementHelper.retrieveUiElementsWithMinimumSimilarity(elementRepository, "search"))
                .thenReturn(List.of(new UiElementMatch(element, 0.9)));

        assertThat(service.searchElements("search")).containsExactly(element);
    }

    @Test
    void deleteElement_shouldRemoveTheElementFromTheRepository() {
        var element = element("Search field", null);

        service.deleteElement(element);

        verify(elementRepository).remove(element);
    }

    private void returnLocation(UiElementLocationResult payload) {
        when(resolutionAgent.executeAndGetResult(any())).thenReturn(new OperationExecutionResult<>(SUCCESS, "success", payload));
    }

    private static UiElement element(String name, UiElement.Screenshot screenshot) {
        return new UiElement(UUID.randomUUID(), name, "description", "location", "navigation", screenshot, false);
    }

    private static UiElement.Screenshot screenshot() {
        return new UiElement.Screenshot("png", "image/png", "aW1hZ2U=");
    }
}
