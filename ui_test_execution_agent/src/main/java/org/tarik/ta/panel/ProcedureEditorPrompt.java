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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.dto.EditorChild;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.dto.KnowledgeSuggestionResult.SuggestedStep;
import org.tarik.ta.dto.UiElementInfo;
import org.tarik.ta.dto.panel.prompt.EditorFields;
import org.tarik.ta.dto.panel.prompt.EditorNode;
import org.tarik.ta.dto.panel.prompt.EditorNode.ChildRow;
import org.tarik.ta.dto.panel.prompt.ElementCard;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.CapturedScreen;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ChildRef;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.DescriptionText;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.Done;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.EffectsBeforeChild;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.Effects;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementCards;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementInfoUpdate;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementLocation;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementQuery;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementRef;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ElementResolution;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.NoRequest;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ParentSummary;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.Parents;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ProcedureHits;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ProcedureRef;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ProcedureSearch;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.ScreenshotCrop;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.SimilarChildren;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.SimilarRows;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.SuggestionRequest;
import org.tarik.ta.dto.panel.prompt.PromptActionMessages.Suggestions;
import org.tarik.ta.dto.panel.prompt.PromptPayload.ProcedureEditor.Answer;
import org.tarik.ta.dto.panel.prompt.SavedNode;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.KnowledgeIngestionService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.ProcedureUsageByTestCaseTrackingService;
import org.tarik.ta.knowledge_graph.service.UiElementResolutionService.ResolutionOutcome;
import org.tarik.ta.panel.ProcedureBrowser.SearchScope;
import org.tarik.ta.tools.UiElementRefinementHelper;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElse;
import static org.tarik.ta.knowledge_graph.ProcedureEditorRules.buildTransientProcedure;
import static org.tarik.ta.knowledge_graph.ProcedureEditorRules.distinctNewestFirst;
import static org.tarik.ta.knowledge_graph.ProcedureEditorRules.effectsBeforeChild;
import static org.tarik.ta.knowledge_graph.ProcedureEditorRules.resolvePreservedFields;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.createAtomic;
import static org.tarik.ta.knowledge_graph.model.node.Procedure.createComposite;
import static org.tarik.ta.panel.PromptInput.MAX_QUERY_LENGTH;
import static org.tarik.ta.panel.PromptInput.list;
import static org.tarik.ta.panel.PromptInput.present;
import static org.tarik.ta.panel.PromptInput.require;
import static org.tarik.ta.panel.PromptInput.text;
import static org.tarik.ta.panel.PromptInput.texts;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

/**
 * The server side of one procedure editor prompt: the nodes it shows, the actions behind its views and the checks and mapping of
 * what it saves. Children are persisted when the operator saves them inside the editor, as the parent's save references them.
 */
final class ProcedureEditorPrompt {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureEditorPrompt.class);

    private final ProcedureEditorSession session;
    private final Supplier<List<String>> precedingEffects;
    private final EditorServices services;
    private final PromptImages images;
    private final ProcedureBrowser browser;
    private final Set<UUID> editableRootIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> shownIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> shownElementIds = ConcurrentHashMap.newKeySet();
    private final @Nullable UUID startingProcedureId;

    /** The services every editor prompt shares, which the singleton panel injects. */
    record EditorServices(KnowledgeIngestionService ingestionService, UiElementRepository uiElementRepository,
                          ProcedureUsageByTestCaseTrackingService usageTrackingService, UiElementRefinementHelper refinementHelper) {
    }

    ProcedureEditorPrompt(@NotNull ProcedureEditorSession session, @NotNull Supplier<List<String>> precedingEffects,
                          @Nullable UUID startingProcedureId, @NotNull EditorServices services, @NotNull PromptImages images) {
        this.session = session;
        this.precedingEffects = precedingEffects;
        this.startingProcedureId = startingProcedureId;
        this.services = services;
        this.images = images;
        this.browser = new ProcedureBrowser(session.knowledgeService());
        if (startingProcedureId != null) {
            editableRootIds.add(startingProcedureId);
        }
    }

    @NotNull EditorNode nodeOf(@NotNull Procedure procedure) {
        shownIds.add(procedure.id());
        var knowledge = knowledge();
        var element = procedure.isAtomic() ? targetElementOf(procedure).map(this::cardOf).orElse(null) : null;
        var children = knowledge.getChildren(procedure.id()).stream().map(this::rowOf).toList();
        var parents = knowledge.findParents(procedure.id()).stream().map(Procedure::description).toList();
        var testCases = services.usageTrackingService().findTestCasesUsingProcedure(procedure.id());
        return new EditorNode(procedure.id(), procedure.isPrecondition(), fieldsOf(procedure), element, children, parents, testCases);
    }

    /** A new procedure for the execution item, pre-filled with the suggestions, whose suggested children still have to be saved. */
    @NotNull EditorNode newNode(@NotNull KnowledgeSuggestionResult suggestions) {
        var item = session.itemContext();
        var procedure = Objects.requireNonNull(buildTransientProcedure(item.description(), suggestions, !item.isPrecondition()));
        var children = Stream.ofNullable(suggestions.suggestedChildSteps()).flatMap(List::stream)
                .map(step -> suggestedRow(step, item.isPrecondition()))
                .toList();
        return new EditorNode(null, item.isPrecondition(), fieldsOf(procedure), null, children, List.of(), List.of());
    }

    @NotNull PromptActions actions() {
        return PromptActions.builder()
                .add("search-procedures", ProcedureSearch.class, this::searchProcedures)
                .add("effects-before-child", EffectsBeforeChild.class, this::effectsBefore)
                .add("load-suggestions", SuggestionRequest.class, this::loadSuggestions)
                .add("similar-children", SimilarChildren.class, this::similarChildren)
                .addDrivingDisplay("locate-element", ElementLocation.class, request -> resolveElement(request, false))
                .addDrivingDisplay("create-and-locate", ElementLocation.class, request -> resolveElement(request, true))
                .add("select-element", DescriptionText.class, this::elementQuery)
                .add("search-elements", ElementQuery.class, this::searchElements)
                .add("update-element-info", ElementInfoUpdate.class, this::updateElementInfo)
                .addDrivingDisplay("capture-screen", NoRequest.class, _ -> captureScreenImage())
                .add("replace-element-screenshot", ScreenshotCrop.class, this::replaceElementScreenshot)
                .add("delete-element", ElementRef.class, this::deleteElement)
                .add("open-procedure", ProcedureRef.class, request -> nodeOf(shownProcedure(request.procedureId())))
                .add("edit-parent", ProcedureRef.class, this::parents)
                .add("save-child", SavedNode.class, this::saveChild)
                .add("delete-procedure", ProcedureRef.class, this::deleteProcedure)
                .build();
    }

    /** Checks the editor's answer: a create saves a new procedure, an edit saves or deletes the edited one or a parent moved up to. */
    void check(@NotNull Answer answer) {
        boolean create = startingProcedureId == null;
        switch (present(answer.outcome(), "The outcome")) {
            case SAVE -> {
                var node = present(answer.node(), "The saved procedure");
                checkSaved(node);
                if (create) {
                    require(node.id() == null, "A new procedure cannot replace an existing one");
                    require(node.precondition() == session.itemContext().isPrecondition(), "The procedure type differs from the item");
                } else {
                    require(node.id() != null && editableRootIds.contains(node.id()), "Only the edited procedure or a parent can be saved");
                }
            }
            case DELETE -> require(!create && editableRootIds.contains(present(answer.procedureId(), "The deleted procedure")),
                    "Only the edited procedure or a parent can be deleted");
            case CANCEL -> {
            }
        }
    }

    @NotNull IngestionNode.NewProcedure toIngestionNode(@NotNull SavedNode node) {
        var loaded = node.id() == null ? null : existingProcedure(node.id());
        var fields = node.fields();
        boolean showTestData = loaded == null ? !node.precondition() : !loaded.isPrecondition();
        var preserved = resolvePreservedFields(loaded, showTestData, fields.testData());
        var description = fields.description().trim();
        var expectedResults = fields.expectedResults().trim();
        var procedure = fields.atomic()
                ? createAtomic(description, preserved.testData(), expectedResults, fields.prerequisites(), fields.effects(),
                preserved.isPrecondition())
                : createComposite(description, preserved.testData(), expectedResults, fields.prerequisites(), fields.effects(),
                preserved.isPrecondition());
        var additionalInfo = fields.additionalInfo() == null ? "" : fields.additionalInfo().trim();
        var completed = procedure.withNegatedEffects(fields.negatedEffects())
                .withOptional(fields.optional())
                .withAdditionalInfo(additionalInfo.isEmpty() ? null : additionalInfo);
        List<IngestionNode> children = fields.atomic() ? List.of()
                : node.childIds().stream().<IngestionNode>map(IngestionNode.ExistingReference::new).toList();
        return new IngestionNode.NewProcedure(completed, fields.atomic() ? node.targetElementId() : null, children);
    }

    void delete(@NotNull UUID procedureId) {
        services.ingestionService().deleteProcedure(procedureId);
        knowledge().onKnowledgeIngested();
        LOG.info("Procedure {} deleted by the operator", procedureId);
    }

    private ProcedureHits searchProcedures(ProcedureSearch request) {
        var childIds = Set.copyOf(list(requireNonNullElse(request.childIds(), List.of()), "The child steps"));
        childIds.forEach(this::existingProcedure);
        var excluded = new HashSet<>(childIds);
        if (request.procedureId() != null) {
            excluded.add(existingProcedure(request.procedureId()).id());
        }
        var effectNodeIds = new HashSet<UUID>();
        childIds.forEach(childId -> knowledge().findEffectsForProcedure(childId).forEach(effect -> effectNodeIds.add(effect.id())));
        var recentParentIds = request.procedureId() == null ? Set.<UUID>of() : Set.of(request.procedureId());
        return browser.search(request.query(), request.showAllTypes(),
                new SearchScope(excluded, effectNodeIds, recentParentIds, request.precondition()));
    }

    private Effects effectsBefore(EffectsBeforeChild request) {
        var effects = precedingEffects.get();
        for (var level : list(request.levels(), "The nesting levels")) {
            effects = effectsBeforeChild(effects, editorChildren(level));
        }
        return new Effects(distinctNewestFirst(effects));
    }

    private Suggestions loadSuggestions(SuggestionRequest request) {
        var description = text(request.description(), "The description").trim();
        require(!description.isEmpty(), "Enter a description to get suggestions for");
        var precedingAtomics = list(request.precedingSteps(), "The preceding steps").stream()
                .flatMap(step -> atomicsOf(step).stream())
                .toList();
        var suggestionService = session.suggestionService();
        var suggestions = suggestionService.load(suggestionService.prepare(session.suggestionContext(), description, precedingAtomics));
        return new Suggestions(suggestions.isAtomicSuggestion(), Stream.ofNullable(suggestions.suggestedChildSteps())
                .flatMap(List::stream).map(SuggestedStep::description).toList(),
                requireNonNullElse(suggestions.suggestedPreconditions(), List.of()),
                requireNonNullElse(suggestions.suggestedEffects(), List.of()),
                requireNonNullElse(suggestions.suggestedNegatedEffects(), List.of()),
                suggestions.suggestedExpectedResultsText());
    }

    private SimilarRows similarChildren(SimilarChildren request) {
        var rows = editorChildren(request.rows());
        var excluded = new HashSet<UUID>();
        if (request.procedureId() != null) {
            excluded.add(existingProcedure(request.procedureId()).id());
        }
        rows.forEach(row -> {
            if (row instanceof EditorChild.Linked linked) {
                excluded.add(linked.procedure().id());
            }
        });
        var indices = session.similarChildrenService().findStepsWithSimilarProcedures(rows, excluded);
        return new SimilarRows(indices.stream().sorted().toList());
    }

    private ElementResolution resolveElement(ElementLocation request, boolean create) {
        var description = text(request.description(), "The description").trim();
        require(!description.isEmpty(), "Enter a description of the element");
        var item = session.itemContext();
        var relatedData = (item.hasTestData() ? item.testData() : texts(request.testData(), "The test data")).toString();
        var resolution = session.elementResolutionService();
        var outcome = create ? resolution.createAndLocate(description, relatedData) : resolution.locate(description, relatedData);
        return switch (outcome) {
            case ResolutionOutcome.Located(var element) -> new ElementResolution(cardOf(element), null);
            case ResolutionOutcome.Failed(var reason) -> new ElementResolution(null, reason);
        };
    }

    private ElementQuery elementQuery(DescriptionText request) {
        var description = text(request.description(), "The description").trim();
        require(!description.isEmpty(), "Enter a description of the procedure first");
        return new ElementQuery(session.elementResolutionService().extractElementDescription(description).orElse(description));
    }

    private ElementCards searchElements(ElementQuery request) {
        var query = text(request.query(), MAX_QUERY_LENGTH, "The search text").trim();
        if (query.isEmpty()) {
            return new ElementCards(List.of());
        }
        return new ElementCards(session.elementResolutionService().searchElements(query).stream().map(this::cardOf).toList());
    }

    private ElementCard updateElementInfo(ElementInfoUpdate request) {
        var info = new UiElementInfo(text(request.name(), "The name").trim(), text(request.description(), "The description").trim(),
                text(request.locationDetails(), "The location details").trim(), text(request.pageSummary(), "The page").trim(),
                request.dataDependent());
        require(!info.name().isEmpty(), "Enter a name of the element");
        return services.refinementHelper().updateElementInfo(services.uiElementRepository(), shownElementId(request.elementId()), info)
                .map(this::cardOf)
                .orElseThrow(() -> new PromptInputException("The element no longer exists"));
    }

    private CapturedScreen captureScreenImage() {
        var screen = captureScreen();
        return new CapturedScreen(images.addCapture(screen), screen.getWidth(), screen.getHeight());
    }

    private ElementCard replaceElementScreenshot(ScreenshotCrop request) {
        var elementId = shownElementId(request.elementId());
        var screen = images.latestCapture(present(request.screenshotId(), "The screenshot"))
                .orElseThrow(() -> new PromptInputException("Only the latest screen capture can be cropped; capture the screen again"));
        require(isInside(request, screen), "The selected area lies outside the screen capture");
        var crop = screen.getSubimage(request.x(), request.y(), request.width(), request.height());
        return services.refinementHelper().replaceElementScreenshot(services.uiElementRepository(), elementId, crop)
                .map(this::cardOf)
                .orElseThrow(() -> new PromptInputException("The element no longer exists"));
    }

    private Done deleteElement(ElementRef request) {
        var element = services.uiElementRepository().findById(shownElementId(request.elementId()))
                .orElseThrow(() -> new PromptInputException("The element no longer exists"));
        session.elementResolutionService().deleteElement(element);
        LOG.info("UI element '{}' ({}) deleted by the operator", element.name(), element.id());
        return new Done();
    }

    private Parents parents(ProcedureRef request) {
        var parents = knowledge().findParents(shownProcedure(request.procedureId()).id());
        parents.forEach(parent -> {
            editableRootIds.add(parent.id());
            shownIds.add(parent.id());
        });
        return new Parents(parents.stream().map(parent -> new ParentSummary(parent.id(), parent.description())).toList());
    }

    private ChildRow saveChild(SavedNode node) {
        checkSaved(node);
        if (node.id() != null) {
            shownProcedure(node.id());
        }
        var ingestionNode = toIngestionNode(node);
        UUID savedId;
        if (node.id() == null) {
            savedId = services.ingestionService().ingest(ingestionNode);
        } else {
            services.ingestionService().update(node.id(), ingestionNode);
            savedId = node.id();
        }
        knowledge().onKnowledgeIngested();
        LOG.info("Child procedure '{}' ({}) saved by the operator", ingestionNode.procedure().description(), savedId);
        return rowOf(existingProcedure(savedId));
    }

    private Done deleteProcedure(ProcedureRef request) {
        delete(shownProcedure(request.procedureId()).id());
        return new Done();
    }

    private void checkSaved(SavedNode node) {
        var fields = present(node.fields(), "The fields");
        require(!text(fields.description(), "The description").isBlank(), "Enter a description for the procedure");
        if (fields.additionalInfo() != null) {
            text(fields.additionalInfo(), "The additional info");
        }
        texts(fields.prerequisites(), "The prerequisites");
        texts(fields.effects(), "The effects");
        texts(fields.negatedEffects(), "The negated effects");
        texts(fields.testData(), "The test data");
        text(fields.expectedResults(), "The expected results");
        if (node.id() != null) {
            existingProcedure(node.id());
        }
        var childIds = list(node.childIds(), "The child steps");
        if (!fields.atomic()) {
            require(!childIds.isEmpty(), "A composite procedure needs at least one child step");
            require(node.id() == null || !childIds.contains(node.id()), "A procedure cannot be its own child step");
            childIds.forEach(this::existingProcedure);
        }
        if (fields.atomic() && node.targetElementId() != null) {
            require(services.uiElementRepository().findById(node.targetElementId()).isPresent(), "The target element no longer exists");
        }
    }

    private List<EditorChild> editorChildren(List<ChildRef> refs) {
        return list(refs, "The child steps").stream().map(this::editorChildOf).toList();
    }

    private EditorChild editorChildOf(ChildRef ref) {
        if (ref.id() != null) {
            return new EditorChild.Linked(existingProcedure(ref.id()), null);
        }
        return new EditorChild.New(suggestedStep(ref), null, null, true, List.of());
    }

    private List<Procedure> atomicsOf(ChildRef ref) {
        if (ref.id() != null) {
            return knowledge().resolveToAtomicSteps(existingProcedure(ref.id()).id());
        }
        return List.of(suggestedStep(ref));
    }

    private static Procedure suggestedStep(ChildRef ref) {
        return createAtomic(text(ref.description(), "The child step"), List.of(), "", List.of(), List.of(), false);
    }

    private Procedure existingProcedure(@Nullable UUID procedureId) {
        return knowledge().findById(present(procedureId, "The procedure"))
                .orElseThrow(() -> new PromptInputException("The procedure no longer exists in the knowledge graph"));
    }

    // Like the answer, which accepts only the editor's roots, the actions that open, change or delete a procedure stay within the
    // procedures this editor has shown: nodes, child rows, parents and search hits
    private Procedure shownProcedure(@Nullable UUID procedureId) {
        var id = present(procedureId, "The procedure");
        require(shownIds.contains(id) || browser.offered(id).isPresent(), "The procedure was not shown in this editor");
        return existingProcedure(id);
    }

    private UUID shownElementId(@Nullable UUID elementId) {
        var id = present(elementId, "The element");
        require(shownElementIds.contains(id), "The element was not shown in this editor");
        return id;
    }

    private Optional<UiElement> targetElementOf(Procedure procedure) {
        return knowledge().findTargetedUiElementId(procedure.id()).flatMap(services.uiElementRepository()::findById);
    }

    private ChildRow rowOf(Procedure procedure) {
        shownIds.add(procedure.id());
        var screenshotId = procedure.isAtomic()
                ? targetElementOf(procedure).flatMap(this::screenshotIdOf).orElse(null)
                : null;
        return new ChildRow(procedure.id(), procedure.description(), procedure.isAtomic(), procedure.isPrecondition(),
                procedure.effects(), procedure.negatedEffects(), screenshotId);
    }

    private static ChildRow suggestedRow(SuggestedStep step, boolean precondition) {
        return new ChildRow(null, step.description(), true, precondition, List.of(), List.of(), null);
    }

    private ElementCard cardOf(UiElement element) {
        shownElementIds.add(element.id());
        return new ElementCard(element.id(), element.name(), element.description(), element.locationDetails(),
                element.parentElementSummary(), element.isDataDependent(), screenshotIdOf(element).orElse(null));
    }

    private Optional<String> screenshotIdOf(UiElement element) {
        return Optional.ofNullable(element.screenshot()).flatMap(images::add);
    }

    private static EditorFields fieldsOf(Procedure procedure) {
        return new EditorFields(procedure.description(), procedure.additionalInfo(), procedure.isAtomic(), procedure.optional(),
                procedure.prerequisites(), procedure.effects(), procedure.negatedEffects(), procedure.testData(),
                procedure.expectedResults());
    }

    private static boolean isInside(ScreenshotCrop crop, BufferedImage screen) {
        return crop.x() >= 0 && crop.y() >= 0 && crop.width() > 0 && crop.height() > 0
                && (long) crop.x() + crop.width() <= screen.getWidth() && (long) crop.y() + crop.height() <= screen.getHeight();
    }

    private KnowledgeService knowledge() {
        return session.knowledgeService();
    }
}
