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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.dto.ExecutionItemContext;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.ProcedureDialogOutcome;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.ExecutionStateTracker;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.SimilarChildrenService;
import org.tarik.ta.knowledge_graph.service.SuggestionService;
import org.tarik.ta.knowledge_graph.service.SuggestionService.SuggestionContext;
import org.tarik.ta.knowledge_graph.service.UiElementResolutionService;
import org.tarik.ta.panel.OperatorPrompts;
import org.tarik.ta.panel.ProcedureEditorSession;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers what each knowledge collection flow hands to the procedure editor and how it reads the editor's outcome. */
@ExtendWith(MockitoExtension.class)
class ProcedureKnowledgeCollectionServiceTest {
    private static final ExecutionItemContext ITEM = new ExecutionItemContext("click the search icon", List.of(), false);

    @Mock
    private KnowledgeService knowledgeService;
    @Mock
    private SuggestionService suggestionService;
    @Mock
    private SimilarChildrenService similarChildrenService;
    @Mock
    private UiElementResolutionService uiElementResolutionService;
    @Mock
    private OperatorPrompts operatorPrompts;
    @Mock
    private ExecutionStateTracker stateTracker;

    @Test
    void triggerNewProcedureFlow_shouldOfferTheEffectsBeforeTheCurrentItem_whenTheNewProcedureReplacesIt() {
        var effectsBeforeItem = List.of("User is logged in");
        when(stateTracker.getEffectPhrasesBeforeCurrentItem()).thenReturn(effectsBeforeItem);

        buildService().triggerNewProcedureFlow("open the panel editor", List.of(), "", false,
                new TestCase("Panels", List.of(), List.of(), List.of()));

        var session = ArgumentCaptor.forClass(ProcedureEditorSession.class);
        verify(operatorPrompts).createProcedure(session.capture(), eq(effectsBeforeItem));
        assertThat(session.getValue().itemContext()).isEqualTo(new ExecutionItemContext("open the panel editor", List.of(), false));
    }

    @Test
    void triggerNewProcedureFlow_shouldBuildTheSuggestionContextFromTheAtomicsExecutedSoFar() {
        var executedAtomic = Procedure.createAtomic("open the dashboard", List.of(), "", List.of(), List.of(), false);
        when(stateTracker.getExecutedAtomicProcedures()).thenReturn(List.of(executedAtomic));
        var testCase = new TestCase("Panels", List.of(), List.of(), List.of());

        buildService().triggerNewProcedureFlow("open the panel editor", List.of("user=admin"), "The editor is shown", false, testCase);

        var session = ArgumentCaptor.forClass(ProcedureEditorSession.class);
        verify(operatorPrompts).createProcedure(session.capture(), any());
        assertThat(session.getValue().suggestionContext())
                .isEqualTo(new SuggestionContext(testCase, List.of(executedAtomic), List.of("user=admin"), "The editor is shown"));
    }

    @Test
    void triggerNewProcedureFlow_shouldReturnTheCreatedProcedure_whenTheOperatorSavesIt() {
        var created = new IngestionNode.ExistingReference(Procedure.createAtomic("open", List.of(), "", List.of(), List.of(), false).id());
        when(operatorPrompts.createProcedure(any(), any())).thenReturn(Optional.of(created));

        var result = buildService().triggerNewProcedureFlow("open the panel editor", List.of(), "", false,
                new TestCase("Panels", List.of(), List.of(), List.of()));

        assertThat(result).contains(created);
    }

    @Test
    void triggerEditProcedureFlow_shouldOfferTheCurrentlyVisibleEffects_whenAProcedureIsEdited() {
        var procedure = procedure("click the search icon");
        when(stateTracker.getEffectPhrasesInExecutionOrder()).thenReturn(List.of("Panel edit view is opened"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<List<String>>> precedingEffects = ArgumentCaptor.forClass(Supplier.class);
        when(operatorPrompts.editProcedure(any(), eq(procedure), precedingEffects.capture()))
                .thenReturn(ProcedureDialogOutcome.cancelledOutcome());

        buildService().triggerEditProcedureFlow(procedure, List.of(), "", ITEM, null);

        assertThat(precedingEffects.getValue().get()).containsExactly("Panel edit view is opened");
    }

    @Test
    void triggerEditProcedureFlow_shouldReturnTheEditedNode_whenTheOperatorSaves() {
        var procedure = procedure("click the search icon");
        var edited = new IngestionNode.NewProcedure(procedure, null, List.of());
        when(operatorPrompts.editProcedure(any(), eq(procedure), any())).thenReturn(ProcedureDialogOutcome.saved(procedure.id(), edited));

        var result = buildService().triggerEditProcedureFlow(procedure, List.of(), "", ITEM, null);

        assertThat(result.isSaved()).isTrue();
        assertThat(result.savedProcedureId()).contains(procedure.id());
        assertThat(result.updatedNode()).contains(edited);
    }

    @Test
    @DisplayName("A parent the operator moved up to and saved is returned under its own id")
    void triggerEditProcedureFlow_shouldReturnTheSavedParent_whenTheOperatorMovedUpToIt() {
        var child = procedure("open the order list");
        var parent = Procedure.createComposite("review the orders", List.of(), "", List.of(), List.of(), false);
        var edited = new IngestionNode.NewProcedure(parent, null, List.of(new IngestionNode.ExistingReference(child.id())));
        when(operatorPrompts.editProcedure(any(), eq(child), any())).thenReturn(ProcedureDialogOutcome.saved(parent.id(), edited));

        var result = buildService().triggerEditProcedureFlow(child, List.of(), "", ITEM, null);

        assertThat(result.savedProcedureId()).contains(parent.id());
        assertThat(result.updatedNode()).contains(edited);
    }

    @Test
    void triggerEditProcedureFlow_shouldReturnNothingToIngest_whenTheOperatorDeletesTheProcedure() {
        var procedure = procedure("click the search icon");
        when(operatorPrompts.editProcedure(any(), eq(procedure), any())).thenReturn(ProcedureDialogOutcome.deletedOutcome(procedure.id()));

        var result = buildService().triggerEditProcedureFlow(procedure, List.of(), "", ITEM, null);

        assertThat(result.isSaved()).isFalse();
        assertThat(result.updatedNode()).isEmpty();
    }

    @Test
    void triggerEditProcedureFlow_shouldReturnNothingToIngest_whenTheOperatorCancels() {
        var procedure = procedure("click the search icon");
        when(operatorPrompts.editProcedure(any(), eq(procedure), any())).thenReturn(ProcedureDialogOutcome.cancelledOutcome());

        assertThat(buildService().triggerEditProcedureFlow(procedure, List.of(), "", ITEM, null).isSaved()).isFalse();
    }

    private static Procedure procedure(String description) {
        return Procedure.createAtomic(description, List.of(), "", List.of(), List.of(), false);
    }

    private ProcedureKnowledgeCollectionService buildService() {
        return new ProcedureKnowledgeCollectionService(knowledgeService, suggestionService, similarChildrenService,
                uiElementResolutionService, operatorPrompts, stateTracker);
    }
}
