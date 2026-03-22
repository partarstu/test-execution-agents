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

import org.tarik.ta.agents.*;
import org.tarik.ta.core.agents.PreconditionActionAgent;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.dto.UiElementLocationResult;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.tools.*;
import org.tarik.ta.knowledge_graph.model.node.UiElement.ElementLocationHistory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static dev.langchain4j.service.AiServices.builder;
import static org.tarik.ta.UiTestAgentConfig.*;
import static org.tarik.ta.core.agents.PreconditionVerificationAgent.RETRY_POLICY;
import static org.tarik.ta.core.model.ModelFactory.getModel;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

public class AgentFactory {

    private AgentFactory() {
    }

    public static UiElementResolutionAgent getKnowledgeCollectionElementResolutionAgent(
            LocationHistoryRecorder locationHistoryRecorder, Function<UUID, Optional<ElementLocationHistory>> stabilityLookup) {
        var model = getModel(getKnowledgeCollectionElementResolutionAgentModelName(), getKnowledgeCollectionElementResolutionAgentModelProvider());
        var prompt = loadSystemPrompt("knowledge/knowledge_collection_element_resolution", getKnowledgeCollectionElementResolutionAgentPromptVersion(),
                "ui_element_resolution_prompt.txt");
        var agentBuilder = builder(UiElementResolutionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(UiElementResolutionAgent.RETRY_POLICY))
                .maxSequentialToolsInvocations(getAgentToolCallsBudget());

        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(new ElementLocatorTools(locationHistoryRecorder, stabilityLookup), new UiElementDbTools(), new SpinnerTools()),
                UiElementLocationResult.class));
        return agentBuilder.build();
    }

    public static KnowledgeSuggestionAgent getKnowledgeSuggestionAgent() {
        var model = getModel(getKnowledgeSuggestionAgentModelName(), getKnowledgeSuggestionAgentModelProvider());
        var prompt =
                loadSystemPrompt("knowledge/suggestion", getKnowledgeSuggestionAgentPromptVersion(), "knowledge_suggestion_prompt.txt");
        return builder(KnowledgeSuggestionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(KnowledgeSuggestionResult.empty())
                .build();
    }

    public static UiTestStepVerificationAgent getTestStepVerificationAgent() {
        var model = getModel(getTestStepVerificationAgentModelName(), getTestStepVerificationAgentModelProvider(),
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

    public static UiTestStepActionAgent getUiTestStepActionAgent(CommonTools commonTools,
                                                                 LocationHistoryRecorder locationHistoryRecorder, Function<UUID, Optional<ElementLocationHistory>> stabilityLookup) {
        var model = getModel(getTestStepActionAgentModelName(), getTestStepActionAgentModelProvider());
        var prompt = loadSystemPrompt("test_step/executor", getTestStepActionAgentPromptVersion(),
                "test_step_action_agent_system_prompt.txt");
        var agentBuilder = builder(UiTestStepActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(UiTestStepActionAgent.RETRY_POLICY))
                .maxSequentialToolsInvocations(getAgentToolCallsBudget());
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(locationHistoryRecorder, stabilityLookup), commonTools),
                EmptyExecutionResult.class));
        return agentBuilder.build();
    }

    public static UiPreconditionVerificationAgent getPreconditionVerificationAgent() {
        var model = getModel(getPreconditionVerificationAgentModelName(),
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

    public static UiPreconditionActionAgent getPreconditionActionAgent(CommonTools commonTools,
                                                                       LocationHistoryRecorder locationHistoryRecorder, Function<UUID, Optional<ElementLocationHistory>> stabilityLookup) {
        var model = getModel(getPreconditionActionAgentModelName(), getPreconditionActionAgentModelProvider());
        var prompt = loadSystemPrompt("precondition/executor", getPreconditionAgentPromptVersion(),
                "precondition_action_agent_system_prompt.txt");
        var agentBuilder = builder(UiPreconditionActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolExecutionErrorHandler(new UiToolErrorHandler(PreconditionActionAgent.RETRY_POLICY));
        agentBuilder.toolProvider(new InheritanceAwareToolProvider<>(
                List.of(new MouseTools(), new KeyboardTools(), new ElementLocatorTools(locationHistoryRecorder, stabilityLookup), commonTools),
                EmptyExecutionResult.class));
        return agentBuilder.maxSequentialToolsInvocations(getAgentToolCallsBudget()).build();
    }
}
