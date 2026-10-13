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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.dto.ExecutionItemContext;
import org.tarik.ta.dto.IngestionNode;
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
import java.util.UUID;

/**
 * Service for triggering the Human-in-the-Loop knowledge collection flows.
 * Extracted from KnowledgeBasedExecutionOrchestrator to break the circular dependency
 * between StepExecutionOrchestrator and KnowledgeBasedExecutionOrchestrator.
 */
@UiAgentRequestScope
public class ProcedureKnowledgeCollectionService {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureKnowledgeCollectionService.class);

    private final KnowledgeService knowledgeService;
    private final SuggestionService suggestionService;
    private final SimilarChildrenService similarChildrenService;
    private final UiElementResolutionService uiElementResolutionService;
    private final OperatorPrompts operatorPrompts;
    private final ExecutionStateTracker stateTracker;

    public ProcedureKnowledgeCollectionService(KnowledgeService knowledgeService,
                                               SuggestionService suggestionService,
                                               SimilarChildrenService similarChildrenService,
                                               UiElementResolutionService uiElementResolutionService,
                                               OperatorPrompts operatorPrompts,
                                               ExecutionStateTracker stateTracker) {
        this.knowledgeService = knowledgeService;
        this.suggestionService = suggestionService;
        this.similarChildrenService = similarChildrenService;
        this.uiElementResolutionService = uiElementResolutionService;
        this.operatorPrompts = operatorPrompts;
        this.stateTracker = stateTracker;
    }

    /** Lets the operator create a procedure for the item, pre-filled with AI suggestions; ingesting it is the caller's job. */
    public Optional<IngestionNode> triggerNewProcedureFlow(String itemDescription,
                                                           List<String> testData,
                                                           String expectedResults,
                                                           boolean isPrecondition,
                                                           TestCase testCase) {
        LOG.info("Triggering new procedure knowledge collection flow for: '{}'", itemDescription);
        var session = editorSession(new ExecutionItemContext(itemDescription, testData, isPrecondition), testData, expectedResults,
                testCase);
        return operatorPrompts.createProcedure(session, stateTracker.getEffectPhrasesBeforeCurrentItem());
    }

    /**
     * Lets the operator edit the procedure, or one of its parents the operator moves up to, returning the edited node for the caller
     * to ingest.
     */
    public ProcedureEditResult triggerEditProcedureFlow(Procedure startingProcedure, List<String> testData,
                                                        String expectedResults,
                                                        ExecutionItemContext itemContext,
                                                        TestCase testCase) {
        var session = editorSession(itemContext, testData, expectedResults, testCase);
        var outcome = operatorPrompts.editProcedure(session, startingProcedure, stateTracker::getEffectPhrasesInExecutionOrder);
        if (outcome.deleted()) {
            LOG.info("Procedure {} deleted by user", outcome.procedureId());
            return ProcedureEditResult.cancelled();
        }
        if (outcome.procedureId() != null && outcome.result() instanceof IngestionNode.NewProcedure np) {
            LOG.info("Procedure '{}' edited by user", np.procedure().description());
            return ProcedureEditResult.saved(outcome.procedureId(), np);
        }
        return ProcedureEditResult.cancelled();
    }

    private ProcedureEditorSession editorSession(ExecutionItemContext itemContext, List<String> testData, String expectedResults,
                                                 TestCase testCase) {
        var suggestionContext = new SuggestionContext(testCase, stateTracker.getExecutedAtomicProcedures(), testData, expectedResults);
        return new ProcedureEditorSession(itemContext, suggestionContext, knowledgeService, suggestionService, similarChildrenService,
                uiElementResolutionService);
    }

    public record ProcedureEditResult(boolean isSaved, Optional<UUID> savedProcedureId,
                                      Optional<IngestionNode.NewProcedure> updatedNode) {
        public static ProcedureEditResult cancelled() {
            return new ProcedureEditResult(false, Optional.empty(), Optional.empty());
        }

        public static ProcedureEditResult saved(UUID id, IngestionNode.NewProcedure node) {
            return new ProcedureEditResult(true, Optional.of(id), Optional.of(node));
        }
    }
}
