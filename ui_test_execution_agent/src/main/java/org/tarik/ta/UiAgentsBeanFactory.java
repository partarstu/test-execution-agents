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

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Singleton;
import org.tarik.ta.agents.*;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.dto.*;
import org.tarik.ta.tools.*;

import java.util.List;

import static dev.langchain4j.service.AiServices.builder;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

/**
 * Avaje DI factory that produces all UI agent beans.
 * The class itself is NOT a bean - only its @Bean methods produce beans.
 */
@Factory
class UiAgentsBeanFactory {
    private final ModelFactory modelFactory;
    private final UiTestAgentConfig uiTestAgentConfig;

    UiAgentsBeanFactory(ModelFactory modelFactory, UiTestAgentConfig uiTestAgentConfig) {
        this.modelFactory = modelFactory;
        this.uiTestAgentConfig = uiTestAgentConfig;
    }

    // Knowledge Collection - used by UiElementDialogHelper
    @Bean
    @Singleton
    UiElementResolutionAgent getKnowledgeCollectionElementResolutionAgent(
            ElementLocatorTools elementLocatorTools,
            UiElementDbTools uiElementDbTools,
            SpinnerTools spinnerTools) {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getKnowledgeCollectionElementResolutionAgentModelName(),
                uiTestAgentConfig.getKnowledgeCollectionElementResolutionAgentModelProvider());
        var prompt = loadSystemPrompt("knowledge/knowledge_collection_element_resolution",
                uiTestAgentConfig.getKnowledgeCollectionElementResolutionAgentPromptVersion(), "ui_element_resolution_prompt.txt");
        var agentBuilder = builder(UiElementResolutionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getActionRetryPolicy(), uiTestAgentConfig))
                .maxSequentialToolsInvocations(uiTestAgentConfig.getAgentToolCallsBudget());
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(elementLocatorTools, uiElementDbTools, spinnerTools),
                UiElementLocationResult.class));
        return agentBuilder.build();
    }

    @Bean
    @Singleton
    KnowledgeSuggestionAgent getKnowledgeSuggestionAgent() {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getKnowledgeSuggestionAgentModelName(),
                uiTestAgentConfig.getKnowledgeSuggestionAgentModelProvider());
        var prompt = loadSystemPrompt("knowledge/suggestion", uiTestAgentConfig.getKnowledgeSuggestionAgentPromptVersion(),
                "knowledge_suggestion_prompt.txt");
        return builder(KnowledgeSuggestionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(KnowledgeSuggestionResult.empty())
                .build();
    }

    @Bean
    @Singleton
    UiStateCheckAgent getUiStateCheckAgent() {
        var prompt = loadSystemPrompt("common/ui_state_checker", uiTestAgentConfig.getUiStateCheckAgentPromptVersion(),
                "ui_state_checker_prompt.txt");
        return builder(UiStateCheckAgent.class)
                .chatModel(modelFactory.getModel(uiTestAgentConfig.getUiStateCheckAgentModelName(),
                        uiTestAgentConfig.getUiStateCheckAgentModelProvider()).chatModel())
                .systemMessageProvider(_ -> prompt)
                .maxSequentialToolsInvocations(uiTestAgentConfig.getAgentToolCallsBudget())
                .tools(new UiStateCheckResult(false, ""))
                .build();
    }

    @Bean
    @Singleton
    UiTestStepVerificationAgent getTestStepVerificationAgent() {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getTestStepVerificationAgentModelName(),
                uiTestAgentConfig.getTestStepVerificationAgentModelProvider(),
                uiTestAgentConfig.getVerificationModelMaxRetries());
        var prompt = loadSystemPrompt("test_step/verifier", uiTestAgentConfig.getTestStepVerificationAgentPromptVersion(),
                "main_verification_prompt.txt");
        var agentBuilder = builder(UiTestStepVerificationAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .maxSequentialToolsInvocations(uiTestAgentConfig.getAgentToolCallsBudget())
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getVerificationRetryPolicy(), uiTestAgentConfig));
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(List.of(), VerificationExecutionResult.class));
        return agentBuilder.build();
    }

    @Bean
    @Singleton
    UiPreconditionVerificationAgent getPreconditionVerificationAgent() {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getPreconditionVerificationAgentModelName(),
                uiTestAgentConfig.getPreconditionVerificationAgentModelProvider(),
                uiTestAgentConfig.getVerificationModelMaxRetries());
        var prompt = loadSystemPrompt("precondition/verifier", uiTestAgentConfig.getPreconditionVerificationAgentPromptVersion(),
                "precondition_verification_prompt.txt");
        return builder(UiPreconditionVerificationAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getVerificationRetryPolicy(), uiTestAgentConfig))
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(), VerificationExecutionResult.class))
                .maxSequentialToolsInvocations(uiTestAgentConfig.getAgentToolCallsBudget())
                .build();
    }

    @Bean
    @Singleton
    UiTestStepActionAgent getUiTestStepActionAgent(
            CommonTools commonTools,
            MouseTools mouseTools,
            KeyboardTools keyboardTools,
            ElementLocatorTools elementLocatorTools) {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getTestStepActionAgentModelName(),
                uiTestAgentConfig.getTestStepActionAgentModelProvider());
        var prompt = loadSystemPrompt("test_step/executor", uiTestAgentConfig.getTestStepActionAgentPromptVersion(),
                "test_step_action_agent_system_prompt.txt");
        var agentBuilder = builder(UiTestStepActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getActionRetryPolicy(), uiTestAgentConfig))
                .maxSequentialToolsInvocations(uiTestAgentConfig.getAgentToolCallsBudget());
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(mouseTools, keyboardTools, elementLocatorTools, commonTools),
                EmptyExecutionResult.class));
        return agentBuilder.build();
    }

    @Bean
    @Singleton
    UiPreconditionActionAgent getPreconditionActionAgent(
            CommonTools commonTools,
            MouseTools mouseTools,
            KeyboardTools keyboardTools,
            ElementLocatorTools elementLocatorTools) {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getPreconditionActionAgentModelName(),
                uiTestAgentConfig.getPreconditionActionAgentModelProvider());
        var prompt = loadSystemPrompt("precondition/executor", uiTestAgentConfig.getPreconditionAgentPromptVersion(),
                "precondition_action_agent_system_prompt.txt");
        var agentBuilder = builder(UiPreconditionActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getActionRetryPolicy(), uiTestAgentConfig));
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(mouseTools, keyboardTools, elementLocatorTools, commonTools),
                EmptyExecutionResult.class));
        return agentBuilder.maxSequentialToolsInvocations(uiTestAgentConfig.getAgentToolCallsBudget()).build();
    }

    @Bean
    @Singleton
    UiElementBoundingBoxAgent getUiElementBoundingBoxAgent() {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getElementBoundingBoxAgentModelName(),
                uiTestAgentConfig.getElementBoundingBoxAgentModelProvider());
        var prompt = loadSystemPrompt("element_locator/bounding_box", uiTestAgentConfig.getElementBoundingBoxAgentPromptVersion(),
                "element_bounding_box_prompt.txt");
        return builder(UiElementBoundingBoxAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new BoundingBoxes(List.of()))
                .build();
    }

    @Bean
    @Singleton
    BestUiElementMatchSelectionAgent getBestUiElementMatchSelectionAgent() {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getUiElementVisualMatchAgentModelName(),
                uiTestAgentConfig.getUiElementVisualMatchAgentModelProvider());
        var prompt = loadSystemPrompt("element_locator/best_ui_match_selection", uiTestAgentConfig.getElementSelectionAgentPromptVersion(),
                "find_best_matching_ui_element_id.txt");
        return builder(BestUiElementMatchSelectionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new BestUiElementVisualMatchResult(false, "", ""))
                .build();
    }

    @Bean
    @Singleton
    UiElementDescriptionExtractionAgent getUiElementDescriptionExtractionAgent() {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getUiElementDescriptionExtractionAgentModelName(),
                uiTestAgentConfig.getUiElementDescriptionExtractionAgentModelProvider());
        var prompt = loadSystemPrompt("element_describer",
                uiTestAgentConfig.getUiElementDescriptionExtractionAgentPromptVersion(),
                "ui_element_description_extraction_prompt.txt");
        return builder(UiElementDescriptionExtractionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(UiElementDescriptionResult.empty())
                .build();
    }

    @Bean
    @Singleton
    UiElementExtendedDescriptionAgent getUiElementExtendedDescriptionAgent() {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getUiElementDescriptionMatcherAgentModelName(),
                uiTestAgentConfig.getUiElementDescriptionMatcherAgentModelProvider());
        var prompt = loadSystemPrompt("element_describer", uiTestAgentConfig.getUiElementDescriptionMatcherAgentPromptVersion(),
                "description_matcher_prompt.txt");
        return builder(UiElementExtendedDescriptionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new UiElementIdentificationResult(false, false,
                        new UiElementDescription("", "", "", "", false)))
                .build();
    }

    @Bean
    @Singleton
    DbUiElementSelectionAgent getDbUiElementSelectionAgent() {
        var model = modelFactory.getModel(
                uiTestAgentConfig.getDbElementCandidateSelectionAgentModelName(),
                uiTestAgentConfig.getDbElementCandidateSelectionAgentModelProvider());
        var prompt = loadSystemPrompt("db_element_selector", uiTestAgentConfig.getDbElementCandidateSelectionAgentPromptVersion(),
                "select_best_db_search_result_prompt.txt");
        return builder(DbUiElementSelectionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new DbUiElementSelectionResult(false, false, "", ""))
                .build();
    }
}
