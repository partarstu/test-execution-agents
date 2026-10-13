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
package org.tarik.ta;

import dev.langchain4j.model.chat.ChatModel;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.tarik.ta.agents.*;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.core.tools.TestContextDataTools;
import org.tarik.ta.dto.*;
import org.tarik.ta.tools.*;
import org.tarik.ta.utils.UiCommonUtils;

import java.util.List;

import static dev.langchain4j.service.AiServices.builder;
import static org.tarik.ta.GroundingOutputFormat.CENTER_AND_SIZE;
import static org.tarik.ta.agents.GroundingProtocol.HOLO_CLICK_POSITION;
import static org.tarik.ta.agents.GroundingProtocol.QWEN_CLICK_POINT;
import static org.tarik.ta.core.model.DefaultToolErrorHandler.forToolArguments;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

/**
 * Avaje DI factory that produces all UI agent beans.
 * The class itself is NOT a bean - only its @Bean methods produce beans.
 */
@UiAgentRequestScope
@Factory
class UiAgentsBeanFactory {
    /**
     * LangChain4j's own default for {@code maxToolCallingRoundTrips}, applied whenever an operator can
     * intervene and the unattended tool-call budget must not cap the agent's round trips.
     */
    private static final int SUPERVISED_MAX_TOOL_CALLING_ROUND_TRIPS = 100;

    private final ModelFactory modelFactory;
    private final UiTestAgentConfig uiTestAgentConfig;

    UiAgentsBeanFactory(ModelFactory modelFactory, UiTestAgentConfig uiTestAgentConfig) {
        this.modelFactory = modelFactory;
        this.uiTestAgentConfig = uiTestAgentConfig;
        UiCommonUtils.setHdrCorrectionEnabled(uiTestAgentConfig.isHdrCorrectionEnabled());
    }

    /**
     * The unattended tool-call budget exists to stop runaway automation; in supervised mode an operator can
     * always intervene, so the framework default applies instead of the budget.
     */
    int resolveMaxToolCallingRoundTrips() {
        return uiTestAgentConfig.isFullyUnattended()
                ? uiTestAgentConfig.getAgentToolCallsBudget()
                : SUPERVISED_MAX_TOOL_CALLING_ROUND_TRIPS;
    }

    // Knowledge Collection - used by UiElementResolutionService
    @Bean
    UiElementResolutionAgent getKnowledgeCollectionElementResolutionAgent(
            ElementLocatorTools elementLocatorTools,
            UiElementDbTools uiElementDbTools,
            SpinnerTools spinnerTools) {
        var model = modelFactory.getModel("knowledge_collection_element_resolution",
                uiTestAgentConfig.getKnowledgeCollectionElementResolutionAgentModelName(),
                uiTestAgentConfig.getKnowledgeCollectionElementResolutionAgentModelProvider());
        var prompt = loadSystemPrompt("knowledge/knowledge_collection_element_resolution",
                uiTestAgentConfig.getKnowledgeCollectionElementResolutionAgentPromptVersion(), "ui_element_resolution_prompt.md");
        var agentBuilder = builder(UiElementResolutionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getActionRetryPolicy(), uiTestAgentConfig))
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()))
                .maxToolCallingRoundTrips(resolveMaxToolCallingRoundTrips());
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(elementLocatorTools, uiElementDbTools, spinnerTools),
                UiElementLocationResult.class));
        return agentBuilder.build();
    }

    @Bean
    KnowledgeSuggestionAgent getKnowledgeSuggestionAgent() {
        var model = modelFactory.getModel("knowledge_suggestion",
                uiTestAgentConfig.getKnowledgeSuggestionAgentModelName(),
                uiTestAgentConfig.getKnowledgeSuggestionAgentModelProvider());
        var prompt = loadSystemPrompt("knowledge/suggestion", uiTestAgentConfig.getKnowledgeSuggestionAgentPromptVersion(),
                "knowledge_suggestion_prompt.md");
        return builder(KnowledgeSuggestionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(KnowledgeSuggestionResult.empty())
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()))
                .build();
    }

    @Bean
    UiStateCheckAgent getUiStateCheckAgent() {
        var prompt = loadSystemPrompt("common/ui_state_checker", uiTestAgentConfig.getUiStateCheckAgentPromptVersion(),
                "ui_state_checker_prompt.md");
        return builder(UiStateCheckAgent.class)
                .chatModel(modelFactory.getModel("ui_state_check", uiTestAgentConfig.getUiStateCheckAgentModelName(),
                        uiTestAgentConfig.getUiStateCheckAgentModelProvider()).chatModel())
                .systemMessageProvider(_ -> prompt)
                .maxToolCallingRoundTrips(resolveMaxToolCallingRoundTrips())
                .tools(new UiStateCheckResult(false, ""))
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()))
                .build();
    }

    @Bean
    UiTestStepVerificationAgent getTestStepVerificationAgent() {
        var model = modelFactory.getModel("test_step_verification",
                uiTestAgentConfig.getTestStepVerificationAgentModelName(),
                uiTestAgentConfig.getTestStepVerificationAgentModelProvider(),
                uiTestAgentConfig.getVerificationModelMaxRetries());
        var prompt = loadSystemPrompt("test_step/verifier", uiTestAgentConfig.getTestStepVerificationAgentPromptVersion(),
                "verification_execution_prompt.md");
        var agentBuilder = builder(UiTestStepVerificationAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .maxToolCallingRoundTrips(resolveMaxToolCallingRoundTrips())
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getVerificationRetryPolicy(), uiTestAgentConfig))
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getVerificationRetryPolicy()));
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(List.of(), VerificationExecutionResult.class));
        return agentBuilder.build();
    }

    @Bean
    UiPreconditionVerificationAgent getPreconditionVerificationAgent() {
        var model = modelFactory.getModel("precondition_verification",
                uiTestAgentConfig.getPreconditionVerificationAgentModelName(),
                uiTestAgentConfig.getPreconditionVerificationAgentModelProvider(),
                uiTestAgentConfig.getVerificationModelMaxRetries());
        var prompt = loadSystemPrompt("precondition/verifier", uiTestAgentConfig.getPreconditionVerificationAgentPromptVersion(),
                "precondition_verification_prompt.md");
        return builder(UiPreconditionVerificationAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getVerificationRetryPolicy(), uiTestAgentConfig))
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getVerificationRetryPolicy()))
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(), VerificationExecutionResult.class))
                .maxToolCallingRoundTrips(resolveMaxToolCallingRoundTrips())
                .build();
    }

    @Bean
    UiTestStepActionAgent getUiTestStepActionAgent(
            CommonTools commonTools,
            MouseTools mouseTools,
            KeyboardTools keyboardTools,
            ElementLocatorTools elementLocatorTools,
            TestContextDataTools testContextDataTools) {
        var model = modelFactory.getModel("test_step_action",
                uiTestAgentConfig.getTestStepActionAgentModelName(),
                uiTestAgentConfig.getTestStepActionAgentModelProvider());
        var prompt = loadSystemPrompt("test_step/executor", uiTestAgentConfig.getTestStepActionAgentPromptVersion(),
                "test_step_action_agent_system_prompt.md");
        var agentBuilder = builder(UiTestStepActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getActionRetryPolicy(), uiTestAgentConfig))
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()))
                .maxToolCallingRoundTrips(resolveMaxToolCallingRoundTrips());
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(mouseTools, keyboardTools, elementLocatorTools, commonTools, testContextDataTools),
                EmptyExecutionResult.class));
        return agentBuilder.build();
    }

    @Bean
    UiPreconditionActionAgent getPreconditionActionAgent(
            CommonTools commonTools,
            MouseTools mouseTools,
            KeyboardTools keyboardTools,
            ElementLocatorTools elementLocatorTools,
            TestContextDataTools testContextDataTools) {
        var model = modelFactory.getModel("precondition_action",
                uiTestAgentConfig.getPreconditionActionAgentModelName(),
                uiTestAgentConfig.getPreconditionActionAgentModelProvider());
        var prompt = loadSystemPrompt("precondition/executor", uiTestAgentConfig.getPreconditionAgentPromptVersion(),
                "precondition_action_agent_system_prompt.md");
        var agentBuilder = builder(UiPreconditionActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(uiTestAgentConfig.getActionRetryPolicy(), uiTestAgentConfig))
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()));
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(mouseTools, keyboardTools, elementLocatorTools, commonTools, testContextDataTools),
                EmptyExecutionResult.class));
        return agentBuilder.maxToolCallingRoundTrips(resolveMaxToolCallingRoundTrips()).build();
    }

    /**
     * A dedicated GUI grounding model is asked through the request shape it was trained on, which is measurably more accurate
     * than a tool call; every other model reports the location by calling the final-result tool.
     */
    @Bean
    UiElementGrounder getUiElementGrounder() {
        var modelName = uiTestAgentConfig.getElementBoundingBoxAgentModelName();
        var modelProvider = uiTestAgentConfig.getElementBoundingBoxAgentModelProvider();
        var promptVersion = uiTestAgentConfig.getElementBoundingBoxAgentPromptVersion();
        var format = uiTestAgentConfig.getGroundingOutputFormat();
        // Both self-hosted models are asked the way their vendor documents localization: their own instruction, no system
        // prompt, and a constrained schema rather than a tool call.
        if (format == CENTER_AND_SIZE) {
            var protocol = switch (modelProvider) {
                case HCOMPANY -> HOLO_CLICK_POSITION;
                case QWEN -> QWEN_CLICK_POINT;
                default -> null;
            };
            if (protocol != null) {
                return new SchemaConstrainedUiElementGrounder(
                        modelFactory.getSchemaConstrainedGroundingModel("element_grounding", modelName, modelProvider).chatModel(),
                        uiTestAgentConfig, protocol);
            }
        }
        var chatModel = modelFactory.getModel("element_grounding", modelName, modelProvider).chatModel();
        return switch (format) {
            case BOUNDING_BOX -> toolCallingGrounder(chatModel,
                    loadSystemPrompt("element_locator/bounding_box", promptVersion, "element_bounding_box_prompt.md"),
                    new BoundingBoxes(List.of()));
            case CENTER_AND_SIZE -> toolCallingGrounder(chatModel,
                    loadSystemPrompt("element_locator/element_region", promptVersion, "element_region_prompt.md"),
                    new ElementRegion(0, 0, 0, 0));
        };
    }

    private UiElementGrounder toolCallingGrounder(ChatModel chatModel, String systemPrompt, Object finalResultTool) {
        var agent = builder(UiElementGroundingAgent.class)
                .chatModel(chatModel)
                .systemMessageProvider(_ -> systemPrompt)
                .tools(finalResultTool)
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()))
                .build();
        return new ToolCallingUiElementGrounder(agent, uiTestAgentConfig);
    }

    @Bean
    BestUiElementMatchSelectionAgent getBestUiElementMatchSelectionAgent() {
        var model = modelFactory.getModel("ui_element_visual_match",
                uiTestAgentConfig.getUiElementVisualMatchAgentModelName(),
                uiTestAgentConfig.getUiElementVisualMatchAgentModelProvider());
        var prompt = loadSystemPrompt("element_locator/best_ui_match_selection", uiTestAgentConfig.getElementSelectionAgentPromptVersion(),
                "find_best_matching_ui_element_id.md");
        return builder(BestUiElementMatchSelectionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new BestUiElementVisualMatchResult(false, "", ""))
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()))
                .build();
    }

    @Bean
    UiElementDescriptionExtractionAgent getUiElementDescriptionExtractionAgent() {
        var model = modelFactory.getModel("ui_element_description_extraction",
                uiTestAgentConfig.getUiElementDescriptionExtractionAgentModelName(),
                uiTestAgentConfig.getUiElementDescriptionExtractionAgentModelProvider());
        var prompt = loadSystemPrompt("element_describer",
                uiTestAgentConfig.getUiElementDescriptionExtractionAgentPromptVersion(),
                "ui_element_description_extraction_prompt.md");
        return builder(UiElementDescriptionExtractionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(UiElementDescriptionResult.empty())
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()))
                .build();
    }

    @Bean
    UiElementExtendedDescriptionAgent getUiElementExtendedDescriptionAgent() {
        var model = modelFactory.getModel("ui_element_description_matching",
                uiTestAgentConfig.getUiElementDescriptionMatcherAgentModelName(),
                uiTestAgentConfig.getUiElementDescriptionMatcherAgentModelProvider());
        var prompt = loadSystemPrompt("element_describer", uiTestAgentConfig.getUiElementDescriptionMatcherAgentPromptVersion(),
                "description_matcher_prompt.md");
        return builder(UiElementExtendedDescriptionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new UiElementIdentificationResult(false, false,
                        new UiElementDescription("", "", "", "", false)))
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()))
                .build();
    }

    @Bean
    DbUiElementSelectionAgent getDbUiElementSelectionAgent() {
        var model = modelFactory.getModel("db_element_selection",
                uiTestAgentConfig.getDbElementCandidateSelectionAgentModelName(),
                uiTestAgentConfig.getDbElementCandidateSelectionAgentModelProvider());
        var prompt = loadSystemPrompt("db_element_selector", uiTestAgentConfig.getDbElementCandidateSelectionAgentPromptVersion(),
                "select_best_db_search_result_prompt.md");
        return builder(DbUiElementSelectionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new DbUiElementSelectionResult(false, false, "", ""))
                .toolArgumentsErrorHandler(forToolArguments(uiTestAgentConfig.getActionRetryPolicy()))
                .build();
    }
}
