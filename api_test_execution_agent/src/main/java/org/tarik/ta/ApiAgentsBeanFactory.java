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
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.model.DefaultToolErrorHandler;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.tools.ApiAssertionTools;
import org.tarik.ta.tools.ApiRequestTools;
import org.tarik.ta.core.tools.TestContextDataTools;

import java.util.List;

import static dev.langchain4j.service.AiServices.builder;
import static org.tarik.ta.core.AgentConfig.*;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

/**
 * DI-managed factory that creates request-scoped API agent instances.
 * Each create* method produces a fresh agent instance intended for use within a single request.
 */
@Singleton
class ApiAgentsBeanFactory {
    private final ModelFactory modelFactory;

    @Inject
    ApiAgentsBeanFactory(ModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    ApiTestStepActionAgent createTestStepActionAgent(ApiRequestTools requestTools, ApiAssertionTools assertionTools,
                                                     TestContextDataTools dataTools) {
        var model = modelFactory.getModel(getTestStepActionAgentModelName(), getTestStepActionAgentModelProvider());
        var prompt = loadSystemPrompt("test_step/executor", getTestStepActionAgentPromptVersion(),
                "test_step_action_prompt.txt");
        return builder(ApiTestStepActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(requestTools, assertionTools, dataTools), VerificationExecutionResult.class))
                .toolExecutionErrorHandler(new DefaultToolErrorHandler(ApiTestStepActionAgent.RETRY_POLICY))
                .maxSequentialToolsInvocations(getAgentToolCallsBudget())
                .build();
    }

    ApiPreconditionActionAgent createPreconditionActionAgent(ApiRequestTools requestTools, ApiAssertionTools assertionTools,
                                                             TestContextDataTools dataTools) {
        var model = modelFactory.getModel(getPreconditionActionAgentModelName(), getPreconditionActionAgentModelProvider());
        var prompt = loadSystemPrompt("precondition/executor", getPreconditionAgentPromptVersion(),
                "precondition_execution_prompt.txt");
        return builder(ApiPreconditionActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(requestTools, assertionTools, dataTools), VerificationExecutionResult.class))
                .toolExecutionErrorHandler(new DefaultToolErrorHandler(ApiPreconditionActionAgent.RETRY_POLICY))
                .maxSequentialToolsInvocations(getAgentToolCallsBudget())
                .build();
    }
}
