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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.dto.ExecutionItemContext;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.dto.KnowledgeSuggestionResult.SuggestedStep;
import org.tarik.ta.dto.UiElementInfo;
import org.tarik.ta.dto.panel.prompt.EditorFields;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ChildRef;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.EffectsBeforeChild;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.Effects;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementInfoUpdate;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementLocation;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementQuery;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementRef;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementResolution;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.NoRequest;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.Parents;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ProcedureHits;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ProcedureRef;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ProcedureSearch;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ScreenshotCrop;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.SimilarChildren;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.CapturedScreen;
import org.tarik.ta.dto.panel.prompt.PromptPayload.ProcedureEditor.Answer;
import org.tarik.ta.dto.panel.prompt.PromptPayload.ProcedureEditor.Outcome;
import org.tarik.ta.dto.panel.prompt.SavedNode;
import org.tarik.ta.dto.panel.prompt.EditorNode.ChildRow;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding.PhraseType;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.ScoredProcedure;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;
import org.tarik.ta.knowledge_graph.service.SimilarChildrenService;
import org.tarik.ta.knowledge_graph.service.SuggestionService;
import org.tarik.ta.knowledge_graph.service.SuggestionService.SuggestionContext;
import org.tarik.ta.knowledge_graph.service.UiElementResolutionService;
import org.tarik.ta.knowledge_graph.service.UiElementResolutionService.ResolutionOutcome;
import org.tarik.ta.panel.ProcedureEditorPrompt.EditorServices;
import org.tarik.ta.tools.SpinnerTools;
import org.tarik.ta.tools.UiElementRefinementHelper;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcedureEditorPromptTest {
    private static final ExecutionItemContext ITEM = new ExecutionItemContext("Open the cart", List.of("user=anna"), false);
    private static final Procedure CHILD = Procedure.createAtomic("Click the cart icon", List.of(), "", List.of(), List.of("Cart is open"),
            false).withNegatedEffects(List.of("Shop page is shown"));
    private static final Procedure EDITED = Procedure.createComposite("Open the cart", List.of("user=anna"), "Cart shown", List.of(),
            List.of("Cart is open"), false);
    private static final UiElement ELEMENT = new UiElement(UUID.randomUUID(), "Cart icon", "A cart", "top right", "Shop", null, false);
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Mock
    private KnowledgeService knowledgeService;
    @Mock
    private SuggestionService suggestionService;
    @Mock
    private SimilarChildrenService similarChildrenService;
    @Mock
    private UiElementResolutionService resolutionService;
    @Mock
    private KnowledgeIngestionService ingestionService;
    @Mock
    private UiElementRepository uiElementRepository;
    @Mock
    private ProcedureUsageByTestCaseTrackingService usageTrackingService;
    @Mock
    private UiElementRefinementHelper refinementHelper;

    private final PromptImages images = new PromptImages();
    private ProcedureEditorSession session;
    private EditorServices services;

    @BeforeEach
    void setUp() {
        var suggestionContext = new SuggestionContext(new TestCase("Shop", List.of(), List.of(), List.of()), List.of(), List.of(), "");
        session = new ProcedureEditorSession(ITEM, suggestionContext, knowledgeService, suggestionService, similarChildrenService,
                resolutionService);
        services = new EditorServices(ingestionService, uiElementRepository, usageTrackingService, refinementHelper);
        when(knowledgeService.findById(EDITED.id())).thenReturn(Optional.of(EDITED));
        when(knowledgeService.findById(CHILD.id())).thenReturn(Optional.of(CHILD));
        when(uiElementRepository.findById(ELEMENT.id())).thenReturn(Optional.of(ELEMENT));
    }

    @Test
    void nodeOf_shouldListTheChildrenParentsAndTestCasesOfAPersistedProcedure() {
        var parent = Procedure.createComposite("Buy an item", List.of(), "", List.of(), List.of(), false);
        when(knowledgeService.getChildren(EDITED.id())).thenReturn(List.of(CHILD));
        when(knowledgeService.findParents(EDITED.id())).thenReturn(List.of(parent));
        when(usageTrackingService.findTestCasesUsingProcedure(EDITED.id())).thenReturn(List.of("Checkout"));

        var node = editor(EDITED.id()).nodeOf(EDITED);

        assertThat(node.id()).isEqualTo(EDITED.id());
        assertThat(node.fields().atomic()).isFalse();
        assertThat(node.fields().testData()).containsExactly("user=anna");
        assertThat(node.children()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(CHILD.id());
            assertThat(row.effects()).containsExactly("Cart is open");
            assertThat(row.negatedEffects()).containsExactly("Shop page is shown");
        });
        assertThat(node.parents()).containsExactly("Buy an item");
        assertThat(node.testCases()).containsExactly("Checkout");
    }

    @Test
    void newNode_shouldPrefillTheSuggestions_andListTheSuggestedChildrenAsUnsaved() {
        var suggestions = new KnowledgeSuggestionResult(List.of("Shop is open"), List.of("Cart is open"), List.of(), List.of("Cart shown"),
                List.of(new SuggestedStep("Click the cart icon")), false);

        var node = editor(null).newNode(suggestions);

        assertThat(node.id()).isNull();
        assertThat(node.fields().description()).isEqualTo("Open the cart");
        assertThat(node.fields().atomic()).isFalse();
        assertThat(node.fields().prerequisites()).containsExactly("Shop is open");
        assertThat(node.fields().expectedResults()).isEqualTo("Cart shown");
        assertThat(node.children()).extracting(ChildRow::id, ChildRow::description)
                .containsExactly(tuple(null, "Click the cart icon"));
    }

    @Test
    void searchProcedures_shouldExcludeTheEditedProcedureAndItsChildren_andScorePrerequisitesByTheChildrenEffects() {
        var effect = new PhraseEmbedding(UUID.randomUUID(), "Cart is open", new float[0], PhraseType.EFFECT);
        var other = Procedure.createAtomic("Click checkout", List.of(), "", List.of(), List.of(), false);
        when(knowledgeService.findEffectsForProcedure(CHILD.id())).thenReturn(List.of(effect));
        when(knowledgeService.findTopRankedWithScores(eq("click"), eq(Set.of(effect.id())), eq(Set.of(EDITED.id()))))
                .thenReturn(List.of(new ScoredProcedure(CHILD, 0, 0), new ScoredProcedure(other, 1, 1)));

        var hits = (ProcedureHits) run(editor(EDITED.id()), "search-procedures",
                new ProcedureSearch("click", false, EDITED.id(), false, List.of(CHILD.id())));

        assertThat(hits.hits()).singleElement().satisfies(hit -> assertThat(hit.id()).isEqualTo(other.id()));
    }

    @Test
    void effectsBeforeChild_shouldFoldThePrecedingSiblingsOfEveryLevel_newestFirst() {
        var editor = new ProcedureEditorPrompt(session, () -> List.of("User is logged in", "Shop page is shown"), EDITED.id(), services,
                images);

        var effects = (Effects) run(editor, "effects-before-child", new EffectsBeforeChild(List.of(
                List.of(new ChildRef(CHILD.id(), CHILD.description())),
                List.of(new ChildRef(null, "Suggested step")))));

        assertThat(effects.effects()).containsExactly("Cart is open", "User is logged in");
    }

    @Test
    void actions_shouldRejectAProcedureIdUnknownToTheGraph() {
        var unknown = UUID.randomUUID();
        var editor = editor(EDITED.id());

        assertThatThrownBy(() -> run(editor, "open-procedure", new ProcedureRef(unknown))).isInstanceOf(PromptInputException.class);
        assertThatThrownBy(() -> run(editor, "effects-before-child", new EffectsBeforeChild(List.of(List.of(new ChildRef(unknown, "x"))))))
                .isInstanceOf(PromptInputException.class);
        assertThatThrownBy(() -> run(editor, "search-procedures", new ProcedureSearch("click", false, unknown, false, List.of())))
                .isInstanceOf(PromptInputException.class);
        assertThatThrownBy(() -> run(editor, "search-procedures", new ProcedureSearch("click", false, null, false, List.of(unknown))))
                .isInstanceOf(PromptInputException.class);
        assertThatThrownBy(() -> run(editor, "similar-children", new SimilarChildren(unknown, List.of())))
                .isInstanceOf(PromptInputException.class);
    }

    @Test
    void replaceElementScreenshot_shouldRejectARectangleOutsideTheCapture_andCropAnInsideOne() {
        var editor = editor(EDITED.id());
        showElement(editor);
        CapturedScreen capture;
        try (var screen = mockStatic(UiCommonUtils.class)) {
            screen.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB));
            capture = (CapturedScreen) run(editor, "capture-screen", new NoRequest());
        }
        assertThat(capture.width()).isEqualTo(100);

        assertThatThrownBy(() -> run(editor, "replace-element-screenshot",
                new ScreenshotCrop(ELEMENT.id(), capture.screenshotId(), 90, 10, 20, 10)))
                .isInstanceOf(PromptInputException.class).hasMessageContaining("outside");
        when(refinementHelper.replaceElementScreenshot(eq(uiElementRepository), eq(ELEMENT.id()), any())).thenReturn(Optional.of(ELEMENT));
        run(editor, "replace-element-screenshot", new ScreenshotCrop(ELEMENT.id(), capture.screenshotId(), 10, 10, 20, 10));
        verify(refinementHelper).replaceElementScreenshot(eq(uiElementRepository), eq(ELEMENT.id()),
                argThat(image -> image.getWidth() == 20 && image.getHeight() == 10));
    }

    @Test
    void replaceElementScreenshot_shouldCropOnlyTheLatestCapture_evenAfterManyElementScreenshotsWereAdded() {
        var editor = editor(EDITED.id());
        showElement(editor);
        CapturedScreen older;
        CapturedScreen latest;
        try (var screen = mockStatic(UiCommonUtils.class)) {
            screen.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB));
            older = (CapturedScreen) run(editor, "capture-screen", new NoRequest());
            latest = (CapturedScreen) run(editor, "capture-screen", new NoRequest());
        }
        var thumbnail = UiElement.Screenshot.fromBufferedImage(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png");
        for (int added = 0; added <= PromptImages.MAX_IMAGES; added++) {
            images.add(thumbnail);
        }
        when(refinementHelper.replaceElementScreenshot(eq(uiElementRepository), eq(ELEMENT.id()), any())).thenReturn(Optional.of(ELEMENT));

        assertThatThrownBy(() -> run(editor, "replace-element-screenshot",
                new ScreenshotCrop(ELEMENT.id(), older.screenshotId(), 10, 10, 20, 10)))
                .isInstanceOf(PromptInputException.class).hasMessageContaining("latest screen capture");
        run(editor, "replace-element-screenshot", new ScreenshotCrop(ELEMENT.id(), latest.screenshotId(), 10, 10, 20, 10));
        verify(refinementHelper).replaceElementScreenshot(eq(uiElementRepository), eq(ELEMENT.id()), any());
    }

    @Test
    void actions_shouldRejectAnExistingProcedureTheEditorNeverShowed_andAcceptTheShownOnes() {
        var other = Procedure.createAtomic("Click checkout", List.of(), "", List.of(), List.of(), false);
        when(knowledgeService.findById(other.id())).thenReturn(Optional.of(other));
        when(knowledgeService.findTopRankedWithScores(eq("checkout"), any(), any())).thenReturn(List.of(new ScoredProcedure(other, 0, 0)));
        var editor = editor(EDITED.id());

        assertThatThrownBy(() -> run(editor, "open-procedure", new ProcedureRef(CHILD.id()))).hasMessageContaining("not shown");
        assertThatThrownBy(() -> run(editor, "edit-parent", new ProcedureRef(CHILD.id()))).hasMessageContaining("not shown");
        assertThatThrownBy(() -> run(editor, "save-child", new SavedNode(CHILD.id(), false, fields("Click", true), null, List.of())))
                .hasMessageContaining("not shown");
        assertThatThrownBy(() -> run(editor, "delete-procedure", new ProcedureRef(other.id()))).hasMessageContaining("not shown");
        verify(ingestionService, never()).update(any(), any());
        verify(ingestionService, never()).deleteProcedure(any());

        when(knowledgeService.getChildren(EDITED.id())).thenReturn(List.of(CHILD));
        editor.nodeOf(EDITED);
        run(editor, "open-procedure", new ProcedureRef(CHILD.id()));
        run(editor, "search-procedures", new ProcedureSearch("checkout", true, EDITED.id(), false, List.of()));
        run(editor, "delete-procedure", new ProcedureRef(other.id()));

        verify(ingestionService).deleteProcedure(other.id());
    }

    @Test
    void elementActions_shouldRejectAnElementTheEditorNeverShowed_andAcceptAShownOne() {
        var editor = editor(EDITED.id());
        var update = new ElementInfoUpdate(ELEMENT.id(), "Cart icon", "A cart", "top right", "Shop", false);

        assertThatThrownBy(() -> run(editor, "update-element-info", update)).hasMessageContaining("not shown");
        assertThatThrownBy(() -> run(editor, "replace-element-screenshot", new ScreenshotCrop(ELEMENT.id(), "capture", 0, 0, 1, 1)))
                .hasMessageContaining("not shown");
        assertThatThrownBy(() -> run(editor, "delete-element", new ElementRef(ELEMENT.id()))).hasMessageContaining("not shown");
        verify(refinementHelper, never()).updateElementInfo(any(), any(), any());
        verify(resolutionService, never()).deleteElement(any());

        showElement(editor);
        when(refinementHelper.updateElementInfo(eq(uiElementRepository), eq(ELEMENT.id()), any())).thenReturn(Optional.of(ELEMENT));
        run(editor, "update-element-info", update);
        run(editor, "delete-element", new ElementRef(ELEMENT.id()));

        verify(refinementHelper).updateElementInfo(eq(uiElementRepository), eq(ELEMENT.id()), any());
        verify(resolutionService).deleteElement(ELEMENT);
    }

    @Test
    void locateElement_shouldUseTheTestDataOfTheItem_andReturnTheFailureReason_whenTheElementIsNotFound() {
        when(resolutionService.locate("Click the cart icon", "[user=anna]")).thenReturn(new ResolutionOutcome.Failed("Not on screen"));

        var resolution = (ElementResolution) run(editor(EDITED.id()), "locate-element",
                new ElementLocation("Click the cart icon", List.of("ignored")));

        assertThat(resolution.element()).isNull();
        assertThat(resolution.failureReason()).isEqualTo("Not on screen");
    }

    @Test
    void check_shouldRejectASaveThatReferencesAChildOrElementUnknownToTheGraph() {
        var editor = editor(EDITED.id());
        var unknownChild = savedComposite(EDITED.id(), List.of(UUID.randomUUID()));
        var unknownElement = new SavedNode(CHILD.id(), false, fields("Click", true), UUID.randomUUID(), List.of());

        assertThatThrownBy(() -> editor.check(new Answer(Outcome.SAVE, unknownChild, null))).isInstanceOf(PromptInputException.class);
        assertThatThrownBy(() -> editor.check(new Answer(Outcome.SAVE, unknownElement, null))).isInstanceOf(PromptInputException.class);
    }

    @Test
    void check_shouldAcceptOnlyTheEditedProcedureOrAParentTheOperatorMovedUpTo() {
        var parent = Procedure.createComposite("Buy an item", List.of(), "", List.of(), List.of(), false);
        when(knowledgeService.findById(parent.id())).thenReturn(Optional.of(parent));
        when(knowledgeService.findParents(EDITED.id())).thenReturn(List.of(parent));
        var editor = editor(EDITED.id());
        var savedParent = savedComposite(parent.id(), List.of(EDITED.id()));

        assertThatThrownBy(() -> editor.check(new Answer(Outcome.SAVE, savedParent, null))).isInstanceOf(PromptInputException.class);
        editor.nodeOf(EDITED);
        var parents = (Parents) run(editor, "edit-parent", new ProcedureRef(EDITED.id()));

        assertThat(parents.parents()).singleElement().satisfies(summary -> assertThat(summary.id()).isEqualTo(parent.id()));
        editor.check(new Answer(Outcome.SAVE, savedParent, null));
        editor.check(new Answer(Outcome.DELETE, null, parent.id()));
        assertThatThrownBy(() -> editor.check(new Answer(Outcome.DELETE, null, UUID.randomUUID()))).isInstanceOf(PromptInputException.class);
    }

    @Test
    void check_shouldRejectASaveOfANewProcedure_thatReplacesAnExistingOneOrChangesTheType() {
        var editor = editor(null);

        assertThatThrownBy(() -> editor.check(new Answer(Outcome.SAVE, new SavedNode(EDITED.id(), false, fields("Open", true), null,
                List.of()), null))).isInstanceOf(PromptInputException.class);
        assertThatThrownBy(() -> editor.check(new Answer(Outcome.SAVE, new SavedNode(null, true, fields("Open", true), null, List.of()),
                null))).isInstanceOf(PromptInputException.class);
        assertThatThrownBy(() -> editor.check(new Answer(Outcome.DELETE, null, EDITED.id()))).isInstanceOf(PromptInputException.class);
        editor.check(new Answer(Outcome.SAVE, new SavedNode(null, false, fields("Open", true), null, List.of()), null));
    }

    @Test
    void check_shouldRejectACompositeWithoutChildren_andABlankDescription() {
        var editor = editor(EDITED.id());

        assertThatThrownBy(() -> editor.check(new Answer(Outcome.SAVE, savedComposite(EDITED.id(), List.of()), null)))
                .hasMessageContaining("at least one child step");
        assertThatThrownBy(() -> editor.check(new Answer(Outcome.SAVE, new SavedNode(EDITED.id(), false, fields(" ", true), null,
                List.of()), null))).hasMessageContaining("description");
    }

    @Test
    void toIngestionNode_shouldReferenceTheChildren_keepTheLoadedTypeAndTrimTheTexts() {
        var precondition = Procedure.createComposite("Shop is open", List.of("store=EU"), "", List.of(), List.of(), true);
        when(knowledgeService.findById(precondition.id())).thenReturn(Optional.of(precondition));
        var fields = new EditorFields(" Shop is open ", " ", false, true, List.of(), List.of("Shop is shown"), List.of(), List.of(),
                " shown ");

        var node = editor(precondition.id()).toIngestionNode(new SavedNode(precondition.id(), false, fields, ELEMENT.id(),
                List.of(CHILD.id())));

        assertThat(node.procedure().description()).isEqualTo("Shop is open");
        assertThat(node.procedure().isPrecondition()).isTrue();
        assertThat(node.procedure().testData()).containsExactly("store=EU");
        assertThat(node.procedure().optional()).isTrue();
        assertThat(node.procedure().additionalInfo()).isNull();
        assertThat(node.procedure().expectedResults()).isEqualTo("shown");
        assertThat(node.targetUiElementId()).isNull();
        assertThat(node.children()).containsExactly(new IngestionNode.ExistingReference(CHILD.id()));
    }

    @Test
    void saveChild_shouldIngestANewChild_andUpdateALinkedOne() {
        var editor = editor(EDITED.id());
        var newId = UUID.randomUUID();
        var created = Procedure.createAtomic("Click the cart icon", List.of(), "", List.of(), List.of(), false);
        when(ingestionService.ingest(any())).thenReturn(newId);
        when(knowledgeService.findById(newId)).thenReturn(Optional.of(created));
        when(knowledgeService.getChildren(EDITED.id())).thenReturn(List.of(CHILD));
        editor.nodeOf(EDITED);

        var row = (ChildRow) run(editor, "save-child", new SavedNode(null, false, fields("Click the cart icon", true), ELEMENT.id(),
                List.of()));
        run(editor, "save-child", new SavedNode(CHILD.id(), false, fields("Click the cart", true), null, List.of()));

        assertThat(row.description()).isEqualTo("Click the cart icon");
        verify(ingestionService).update(eq(CHILD.id()), any());
        verify(knowledgeService, times(2)).onKnowledgeIngested();
    }

    @Test
    void editProcedure_shouldDeleteTheProcedure_whenTheOperatorDeletesIt() throws Exception {
        try (var executor = newVirtualThreadPerTaskExecutor(); var broadcaster = new ExecutionStateBroadcaster(mock(PanelAuth.class))) {
            var panel = new WebOperatorPanel(broadcaster, mock(UiTestAgentConfig.class), services, InstantSource.system(), WAIT);
            var outcome = executor.submit(() -> panel.editProcedure(session, EDITED, List::of));
            var prompt = awaitPrompt(broadcaster, panel);

            answer(prompt, new Answer(Outcome.DELETE, null, EDITED.id()));

            assertThat(outcome.get(WAIT.toSeconds(), SECONDS).deleted()).isTrue();
            verify(ingestionService).deleteProcedure(EDITED.id());
        }
    }

    @Test
    void locateElement_shouldPublishTheResolutionAgentsStatus_andLeaveTheEditorOpen() throws Exception {
        try (var executor = newVirtualThreadPerTaskExecutor(); var broadcaster = new ExecutionStateBroadcaster(mock(PanelAuth.class))) {
            var panel = new WebOperatorPanel(broadcaster, mock(UiTestAgentConfig.class), services, InstantSource.system(), WAIT);
            var spinnerTools = new SpinnerTools(panel);
            when(resolutionService.locate(any(), any())).thenAnswer(_ -> {
                spinnerTools.showSpinner("Looking for the cart icon");
                assertThat(broadcaster.snapshot().statusMessage()).isEqualTo("Looking for the cart icon");
                return new ResolutionOutcome.Located(ELEMENT);
            });
            var outcome = executor.submit(() -> panel.editProcedure(session, EDITED, List::of));
            var prompt = awaitPrompt(broadcaster, panel);
            var action = prompt.spec().actions().find("locate-element").orElseThrow();

            var located = (ElementResolution) prompt.runAction(action, new ElementLocation("Click the cart icon", List.of())).orElseThrow();

            assertThat(located.element().id()).isEqualTo(ELEMENT.id());
            assertThat(panel.pendingPrompt(prompt.id())).isPresent();
            answer(prompt, new Answer(Outcome.CANCEL, null, null));
            assertThat(outcome.get(WAIT.toSeconds(), SECONDS).cancelled()).isTrue();
            verify(ingestionService, never()).deleteProcedure(any());
        }
    }

    private void showElement(ProcedureEditorPrompt editor) {
        when(resolutionService.searchElements("cart")).thenReturn(List.of(ELEMENT));
        run(editor, "search-elements", new ElementQuery("cart"));
    }

    private ProcedureEditorPrompt editor(UUID startingProcedureId) {
        return new ProcedureEditorPrompt(session, List::of, startingProcedureId, services, images);
    }

    private static Object run(ProcedureEditorPrompt editor, String name, Object request) {
        return editor.actions().find(name).orElseThrow().run(request);
    }

    private static SavedNode savedComposite(UUID id, List<UUID> childIds) {
        return new SavedNode(id, false, fields("Open the cart", false), null, childIds);
    }

    private static EditorFields fields(String description, boolean atomic) {
        return new EditorFields(description, null, atomic, false, List.of(), List.of(), List.of(), List.of(), "");
    }

    @SuppressWarnings("unchecked")
    private static void answer(PendingPrompt<?> prompt, Answer answer) {
        ((PendingPrompt<Answer>) prompt).answer().complete(answer);
    }

    private static PendingPrompt<?> awaitPrompt(ExecutionStateBroadcaster broadcaster, WebOperatorPanel panel) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            var view = broadcaster.snapshot().prompt();
            if (view != null) {
                return panel.pendingPrompt(view.id()).orElseThrow();
            }
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
        }
        throw new AssertionError(new TimeoutException("No prompt opened"));
    }
}
