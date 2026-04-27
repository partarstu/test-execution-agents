package org.tarik.ta.knowledge_graph.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.ExecutionMode;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.service.FailureContextService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.SatisfiesEdgeService;
import org.tarik.ta.knowledge_graph.service.UiElementCache;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NextAtomicPrefetchCoordinatorTest {

    @Mock private KnowledgeService mockKnowledgeService;
    @Mock private SatisfiesEdgeService mockSatisfiesEdgeService;
    @Mock private UiElementCache mockUiElementCache;
    @Mock private UiElementRepository mockUiElementRepository;
    @Mock private ElementLocationHistoryLookup mockElementLocationHistoryLookup;
    @Mock private FailureContextService mockFailureContextService;

    private NextAtomicPrefetchCoordinator coordinatorUnattended;
    private NextAtomicPrefetchCoordinator coordinatorSupervised;

    @BeforeEach
    void setUp() {
        coordinatorUnattended = new NextAtomicPrefetchCoordinator(
                ExecutionMode.UNATTENDED,
                mockKnowledgeService, mockSatisfiesEdgeService, mockUiElementCache, mockUiElementRepository, mockElementLocationHistoryLookup, mockFailureContextService);
        coordinatorSupervised = new NextAtomicPrefetchCoordinator(
                ExecutionMode.SUPERVISED,
                mockKnowledgeService, mockSatisfiesEdgeService, mockUiElementCache, mockUiElementRepository, mockElementLocationHistoryLookup, mockFailureContextService);
    }

    @Test
    @DisplayName("scheduleSuccessPathPrefetch is a no-op in supervised mode")
    void scheduleSuccessPathPrefetch_isNoOpInSupervisedMode() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure next = Procedure.createAtomic("next", List.of(), "", List.of(), List.of(), false);
        ExecutionItem currentItem = new ExecutionItem.TestStepItem(new TestStep("current", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        coordinatorSupervised.scheduleSuccessPathPrefetch(current, currentItem, next, null, snapshot);

        Optional<PrefetchedAtomicContext> result = coordinatorSupervised.takeIfValid(next, currentItem);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Prefetch for next atomic inside same decomposition is reused on successful verification")
    void prefetchedNextAtomicInsideSameComposite_isReusedAfterSuccessfulVerification() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure next = Procedure.createAtomic("next", List.of(), "", List.of(), List.of(), false);
        ExecutionItem item = new ExecutionItem.TestStepItem(new TestStep("step", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        UUID elementId = UUID.randomUUID();
        UiElement element = mock(UiElement.class);
        ElementLocationHistory history = mock(ElementLocationHistory.class);

        when(mockKnowledgeService.findTargetedUiElementId(next.id())).thenReturn(Optional.of(elementId));
        when(mockUiElementCache.get(elementId)).thenReturn(Optional.of(element));
        when(mockElementLocationHistoryLookup.lookup(elementId)).thenReturn(Optional.of(history));
        when(mockFailureContextService.findFailureHints(next.id())).thenReturn(List.of("hint1"));

        coordinatorUnattended.scheduleSuccessPathPrefetch(current, item, next, null, snapshot);

        Optional<PrefetchedAtomicContext> result = coordinatorUnattended.takeIfValid(next, item);

        assertThat(result).isPresent();
        PrefetchedAtomicContext ctx = result.get();
        assertThat(ctx.predictedAtomicProcedure().id()).isEqualTo(next.id());
        assertThat(ctx.targetElementId()).isEqualTo(elementId.toString());
        assertThat(ctx.targetElement()).isEqualTo(element);
        assertThat(ctx.locationHistory()).isEqualTo(history);
        assertThat(ctx.failureHints()).containsExactly("hint1");
        assertThat(ctx.metadata().uiElementFound()).isTrue();
        assertThat(ctx.metadata().locationHistoryFound()).isTrue();
        assertThat(ctx.metadata().failureHintsFound()).isTrue();
    }

    @Test
    @DisplayName("Prefetched context is discarded when invalidated after verification failure")
    void prefetchedQueueItemMatch_isDiscardedOnVerificationFailure() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure next = Procedure.createAtomic("next", List.of(), "", List.of(), List.of(), false);
        ExecutionItem currentItem = new ExecutionItem.TestStepItem(new TestStep("current", List.of(), ""));
        ExecutionItem nextItem = new ExecutionItem.TestStepItem(new TestStep("next", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        coordinatorUnattended.scheduleSuccessPathPrefetch(current, currentItem, next, null, snapshot);
        coordinatorUnattended.invalidate("verification failure");

        Optional<PrefetchedAtomicContext> result = coordinatorUnattended.takeIfValid(next, nextItem);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Prefetched context is discarded on re-decomposition or retry")
    void prefetchedQueueItemMatch_isDiscardedOnReDecompositionOrRetry() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure next = Procedure.createAtomic("next", List.of(), "", List.of(), List.of(), false);
        ExecutionItem currentItem = new ExecutionItem.TestStepItem(new TestStep("current", List.of(), ""));
        ExecutionItem nextItem = new ExecutionItem.TestStepItem(new TestStep("next", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        coordinatorUnattended.scheduleSuccessPathPrefetch(current, currentItem, next, null, snapshot);
        coordinatorUnattended.invalidate("re-decomposition");

        Optional<PrefetchedAtomicContext> result = coordinatorUnattended.takeIfValid(next, nextItem);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Partial prefetch context is still used with null for failed optional sub-tasks")
    void partialPrefetchContext_isStillUsedWithNullForMissingFields() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure next = Procedure.createAtomic("next", List.of(), "", List.of(), List.of(), false);
        ExecutionItem item = new ExecutionItem.TestStepItem(new TestStep("step", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        UUID elementId = UUID.randomUUID();
        when(mockKnowledgeService.findTargetedUiElementId(next.id())).thenReturn(Optional.of(elementId));
        when(mockUiElementCache.get(elementId)).thenReturn(Optional.of(mock(UiElement.class)));
        // location history lookup fails — should degrade gracefully
        when(mockElementLocationHistoryLookup.lookup(elementId)).thenThrow(new RuntimeException("DB timeout"));
        when(mockFailureContextService.findFailureHints(next.id())).thenReturn(List.of());

        coordinatorUnattended.scheduleSuccessPathPrefetch(current, item, next, null, snapshot);

        Optional<PrefetchedAtomicContext> result = coordinatorUnattended.takeIfValid(next, item);

        assertThat(result).isPresent();
        PrefetchedAtomicContext ctx = result.get();
        assertThat(ctx.targetElement()).isNotNull();
        assertThat(ctx.locationHistory()).isNull();
        assertThat(ctx.metadata().uiElementFound()).isTrue();
        assertThat(ctx.metadata().locationHistoryFound()).isFalse();
    }

    @Test
    @DisplayName("UI element is fetched from DB when not in cache, and result is present")
    void buildElementContext_fetchesFromDb_whenNotInCache() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure next = Procedure.createAtomic("next", List.of(), "", List.of(), List.of(), false);
        ExecutionItem item = new ExecutionItem.TestStepItem(new TestStep("step", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        UUID elementId = UUID.randomUUID();
        UiElement element = mock(UiElement.class);

        when(mockKnowledgeService.findTargetedUiElementId(next.id())).thenReturn(Optional.of(elementId));
        when(mockUiElementCache.get(elementId)).thenReturn(Optional.empty());
        when(mockUiElementRepository.findById(elementId)).thenReturn(Optional.of(element));
        when(mockElementLocationHistoryLookup.lookup(elementId)).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(next.id())).thenReturn(List.of());

        coordinatorUnattended.scheduleSuccessPathPrefetch(current, item, next, null, snapshot);

        Optional<PrefetchedAtomicContext> result = coordinatorUnattended.takeIfValid(next, item);

        assertThat(result).isPresent();
        assertThat(result.get().targetElement()).isEqualTo(element);
        assertThat(result.get().metadata().uiElementFound()).isTrue();
    }

    @Test
    @DisplayName("takeIfValid returns empty when predicted atomic does not match actual next")
    void takeIfValid_returnEmpty_whenPredictedAtomicDoesNotMatch() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure next = Procedure.createAtomic("next", List.of(), "", List.of(), List.of(), false);
        Procedure different = Procedure.createAtomic("different", List.of(), "", List.of(), List.of(), false);
        ExecutionItem item = new ExecutionItem.TestStepItem(new TestStep("step", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        when(mockKnowledgeService.findTargetedUiElementId(next.id())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(next.id())).thenReturn(List.of());

        coordinatorUnattended.scheduleSuccessPathPrefetch(current, item, next, null, snapshot);

        Optional<PrefetchedAtomicContext> result = coordinatorUnattended.takeIfValid(different, item);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Semantic-search prefetch resolves first atomic of predicted queue item")
    void semanticSearchPrefetch_resolvesFirstAtomicOfPredictedQueueItem() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure predicted = Procedure.createAtomic("predicted", List.of(), "", List.of(), List.of(), false);
        ExecutionItem currentItem = new ExecutionItem.TestStepItem(new TestStep("current", List.of(), ""));
        ExecutionItem nextQueueItem = new ExecutionItem.TestStepItem(new TestStep("next action", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        var match = new KnowledgeService.MatchResult(predicted, KnowledgeService.MatchConfidence.HIGH,
                List.of(new KnowledgeService.ScoredProcedure(predicted, 0, 0)), false, false, false);
        when(mockKnowledgeService.findBestMatch(eq("next action"), any(), any())).thenReturn(Optional.of(match));
        when(mockKnowledgeService.findTargetedUiElementId(predicted.id())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(predicted.id())).thenReturn(List.of("hint"));

        // nextAtomicInDecomposition = null triggers queue-item search path
        coordinatorUnattended.scheduleSuccessPathPrefetch(current, currentItem, null, nextQueueItem, snapshot);

        Optional<PrefetchedAtomicContext> result = coordinatorUnattended.takeIfValid(predicted, nextQueueItem);

        assertThat(result).isPresent();
        assertThat(result.get().predictedAtomicProcedure().id()).isEqualTo(predicted.id());
        assertThat(result.get().failureHints()).containsExactly("hint");
    }

    @Test
    @DisplayName("Semantic-search prefetch returns empty when confidence is LOW")
    void semanticSearchPrefetch_returnsEmpty_whenConfidenceLow() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure candidate = Procedure.createAtomic("candidate", List.of(), "", List.of(), List.of(), false);
        ExecutionItem currentItem = new ExecutionItem.TestStepItem(new TestStep("current", List.of(), ""));
        ExecutionItem nextQueueItem = new ExecutionItem.TestStepItem(new TestStep("next action", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        var match = new KnowledgeService.MatchResult(candidate, KnowledgeService.MatchConfidence.LOW,
                List.of(new KnowledgeService.ScoredProcedure(candidate, 0, 0)), false, false, false);
        when(mockKnowledgeService.findBestMatch(eq("next action"), any(), any())).thenReturn(Optional.of(match));

        coordinatorUnattended.scheduleSuccessPathPrefetch(current, currentItem, null, nextQueueItem, snapshot);

        Optional<PrefetchedAtomicContext> result = coordinatorUnattended.takeIfValid(candidate, nextQueueItem);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Prefetch context is consumed once and unavailable on second takeIfValid")
    void prefetchContext_isConsumedOnce() {
        Procedure current = Procedure.createAtomic("current", List.of(), "", List.of(), List.of(), false);
        Procedure next = Procedure.createAtomic("next", List.of(), "", List.of(), List.of(), false);
        ExecutionItem item = new ExecutionItem.TestStepItem(new TestStep("step", List.of(), ""));
        ExecutionStateSnapshot snapshot = new ExecutionStateSnapshot(Set.of(), List.of(), List.of());

        when(mockKnowledgeService.findTargetedUiElementId(next.id())).thenReturn(Optional.empty());
        when(mockFailureContextService.findFailureHints(next.id())).thenReturn(List.of());

        coordinatorUnattended.scheduleSuccessPathPrefetch(current, item, next, null, snapshot);

        assertThat(coordinatorUnattended.takeIfValid(next, item)).isPresent();
        // second call — future was nulled after first take
        assertThat(coordinatorUnattended.takeIfValid(next, item)).isEmpty();
    }
}
