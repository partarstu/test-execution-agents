/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.dto.*;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.tools.*;

import java.util.List;

import static dev.langchain4j.service.AiServices.builder;
import static org.tarik.ta.UiTestAgentConfig.*;
import static org.tarik.ta.core.agents.PreconditionVerificationAgent.RETRY_POLICY;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

/**
 * DI-managed factory that creates request-scoped AI agent instances.
 * Each create* method produces a fresh agent instance intended for use within a single request.
 */
@Singleton
class UiAgentsBeanFactory {
    // Separate stability tracking for the knowledge-collection flow, which runs outside the main execution graph.
    private static final ProcedureRepository KNOWLEDGE_COLLECTION_STABILITY_REPO = new ProcedureRepository();

    // Static accessor for non-injectable contexts (e.g. Swing dialogs).
    private static volatile UiAgentsBeanFactory instance;

    private final ModelFactory modelFactory;
    // App-level singleton: one agent shared across all knowledge-collection dialogs.
    private final UiElementResolutionAgent knowledgeCollectionAgent;

    @Inject
    UiAgentsBeanFactory(ModelFactory modelFactory) {
        this.modelFactory = modelFactory;
        this.knowledgeCollectionAgent = createKnowledgeCollectionElementResolutionAgent();
        instance = this;
    }

    static UiAgentsBeanFactory getInstance() {
        return instance;
    }

    UiElementResolutionAgent getKnowledgeCollectionAgent() {
        return knowledgeCollectionAgent;
    }

    KnowledgeSuggestionAgent createKnowledgeSuggestionAgent() {
        var model = modelFactory.getModel(getKnowledgeSuggestionAgentModelName(), getKnowledgeSuggestionAgentModelProvider());
        var prompt = loadSystemPrompt("knowledge/suggestion", getKnowledgeSuggestionAgentPromptVersion(), "knowledge_suggestion_prompt.txt");
        return builder(KnowledgeSuggestionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(KnowledgeSuggestionResult.empty())
                .build();
    }

    UiTestStepVerificationAgent createTestStepVerificationAgent() {
        var model = modelFactory.getModel(getTestStepVerificationAgentModelName(), getTestStepVerificationAgentModelProvider(),
                getVerificationModelMaxRetries());
        var prompt = loadSystemPrompt("test_step/verifier", getTestStepVerificationAgentPromptVersion(), "main_verification_prompt.txt");
        var agentBuilder = builder(UiTestStepVerificationAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .maxSequentialToolsInvocations(getAgentToolCallsBudget())
                .toolExecutionErrorHandler(new UiToolErrorHandler(UiTestStepVerificationAgent.RETRY_POLICY));
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(List.of(), VerificationExecutionResult.class));
        return agentBuilder.build();
    }

    UiPreconditionVerificationAgent createPreconditionVerificationAgent() {
        var model = modelFactory.getModel(getPreconditionVerificationAgentModelName(),
                getPreconditionVerificationAgentModelProvider(), getVerificationModelMaxRetries());
        var prompt = loadSystemPrompt("precondition/verifier", getPreconditionVerificationAgentPromptVersion(),
                "precondition_verification_prompt.txt");
        return builder(UiPreconditionVerificationAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(RETRY_POLICY))
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(), VerificationExecutionResult.class))
                .maxSequentialToolsInvocations(getAgentToolCallsBudget())
                .build();
    }

    UiTestStepActionAgent createTestStepActionAgent(CommonTools commonTools,
                                                    LocationHistoryRecorder locationHistoryRecorder,
                                                    ElementLocationHistoryLookup stabilityLookup) {
        var model = modelFactory.getModel(getTestStepActionAgentModelName(), getTestStepActionAgentModelProvider());
        var prompt = loadSystemPrompt("test_step/executor", getTestStepActionAgentPromptVersion(),
                "test_step_action_agent_system_prompt.txt");
        var agentBuilder = builder(UiTestStepActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(UiTestStepActionAgent.RETRY_POLICY))
                .maxSequentialToolsInvocations(getAgentToolCallsBudget());
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(new MouseTools(createUiStateCheckAgent()), new KeyboardTools(createUiStateCheckAgent()),
                        new ElementLocatorTools(createUiElementBoundingBoxAgent(), createBestUiElementMatchSelectionAgent(),
                                createUiStateCheckAgent(), locationHistoryRecorder, stabilityLookup::lookup),
                        commonTools),
                EmptyExecutionResult.class));
        return agentBuilder.build();
    }

    UiPreconditionActionAgent createPreconditionActionAgent(CommonTools commonTools,
                                                            LocationHistoryRecorder locationHistoryRecorder,
                                                            ElementLocationHistoryLookup stabilityLookup) {
        var model = modelFactory.getModel(getPreconditionActionAgentModelName(), getPreconditionActionAgentModelProvider());
        var prompt = loadSystemPrompt("precondition/executor", getPreconditionAgentPromptVersion(),
                "precondition_action_agent_system_prompt.txt");
        var agentBuilder = builder(UiPreconditionActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(PreconditionActionAgent.RETRY_POLICY));
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(new MouseTools(createUiStateCheckAgent()), new KeyboardTools(createUiStateCheckAgent()),
                        new ElementLocatorTools(createUiElementBoundingBoxAgent(), createBestUiElementMatchSelectionAgent(),
                                createUiStateCheckAgent(), locationHistoryRecorder, stabilityLookup::lookup),
                        commonTools),
                EmptyExecutionResult.class));
        return agentBuilder.maxSequentialToolsInvocations(getAgentToolCallsBudget()).build();
    }

    UiStateCheckAgent createUiStateCheckAgent() {
        var prompt = loadSystemPrompt("common/ui_state_checker", getUiStateCheckAgentPromptVersion(), "ui_state_checker_prompt.txt");
        return builder(UiStateCheckAgent.class)
                .chatModel(modelFactory.getModel(getUiStateCheckAgentModelName(), getUiStateCheckAgentModelProvider()).chatModel())
                .systemMessageProvider(_ -> prompt)
                .maxSequentialToolsInvocations(getAgentToolCallsBudget())
                .tools(new UiStateCheckResult(false, ""))
                .build();
    }

    UiElementBoundingBoxAgent createUiElementBoundingBoxAgent() {
        var model = modelFactory.getModel(getElementBoundingBoxAgentModelName(), getElementBoundingBoxAgentModelProvider());
        var prompt = loadSystemPrompt("element_locator/bounding_box", getElementBoundingBoxAgentPromptVersion(),
                "element_bounding_box_prompt.txt");
        return builder(UiElementBoundingBoxAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new BoundingBoxes(List.of()))
                .build();
    }

    BestUiElementMatchSelectionAgent createBestUiElementMatchSelectionAgent() {
        var model = modelFactory.getModel(getUiElementVisualMatchAgentModelName(), getUiElementVisualMatchAgentModelProvider());
        var prompt = loadSystemPrompt("element_locator/best_ui_match_selection", getElementSelectionAgentPromptVersion(),
                "find_best_matching_ui_element_id.txt");
        return builder(BestUiElementMatchSelectionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new BestUiElementVisualMatchResult(false, "", ""))
                .build();
    }

    UiElementExtendedDescriptionAgent createUiElementExtendedDescriptionAgent() {
        var model = modelFactory.getModel(getUiElementDescriptionMatcherAgentModelName(),
                getUiElementDescriptionMatcherAgentModelProvider());
        var prompt = loadSystemPrompt("element_describer", getUiElementDescriptionMatcherAgentPromptVersion(),
                "description_matcher_prompt.txt");
        return builder(UiElementExtendedDescriptionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new UiElementIdentificationResult(false, false,
                        new UiElementDescription("", "", "", "", false)))
                .build();
    }

    DbUiElementSelectionAgent createDbUiElementSelectionAgent() {
        var model = modelFactory.getModel(getDbElementCandidateSelectionAgentModelName(),
                getDbElementCandidateSelectionAgentModelProvider());
        var prompt = loadSystemPrompt("db_element_selector", getDbElementCandidateSelectionAgentPromptVersion(),
                "select_best_db_search_result_prompt.txt");
        return builder(DbUiElementSelectionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new DbUiElementSelectionResult(false, false, "", ""))
                .build();
    }

    UiElementResolutionAgent createKnowledgeCollectionElementResolutionAgent() {
        var model = modelFactory.getModel(getKnowledgeCollectionElementResolutionAgentModelName(),
                getKnowledgeCollectionElementResolutionAgentModelProvider());
        var prompt = loadSystemPrompt("knowledge/knowledge_collection_element_resolution",
                getKnowledgeCollectionElementResolutionAgentPromptVersion(), "ui_element_resolution_prompt.txt");
        var locatorTools = new ElementLocatorTools(createUiElementBoundingBoxAgent(), createBestUiElementMatchSelectionAgent(),
                createUiStateCheckAgent(), KNOWLEDGE_COLLECTION_STABILITY_REPO::updateElementStability,
                KNOWLEDGE_COLLECTION_STABILITY_REPO::getElementStability);
        var dbTools = new UiElementDbTools(createUiElementExtendedDescriptionAgent(), createDbUiElementSelectionAgent(),
                createUiStateCheckAgent());
        var agentBuilder = builder(UiElementResolutionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(UiElementResolutionAgent.RETRY_POLICY))
                .maxSequentialToolsInvocations(getAgentToolCallsBudget());
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(locatorTools, dbTools, new SpinnerTools()),
                UiElementLocationResult.class));
        return agentBuilder.build();
    }
}
