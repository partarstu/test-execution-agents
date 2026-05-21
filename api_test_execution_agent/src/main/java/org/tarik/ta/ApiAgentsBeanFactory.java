/*
 * api-test-execution-agent - ${project.description}
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

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
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
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

/**
 * DI-managed factory that creates request-scoped API agent instances.
 * Each create* method produces a fresh agent instance intended for use within a single request.
 */
@ApiAgentRequestScope
@Factory
class ApiAgentsBeanFactory {
    private final ModelFactory modelFactory;
    private final ApiTestAgentConfig config;

    ApiAgentsBeanFactory(ModelFactory modelFactory, ApiTestAgentConfig config) {
        this.modelFactory = modelFactory;
        this.config = config;
    }

    @Bean
    ApiTestStepActionAgent testStepAgent(ApiRequestTools requestTools, ApiAssertionTools assertionTools,
                                                     TestContextDataTools dataTools) {
        var model = modelFactory.getModel(config.getTestStepActionAgentModelName(), config.getTestStepActionAgentModelProvider());
        var prompt = loadSystemPrompt("test_step/executor", config.getTestStepActionAgentPromptVersion(),
                "test_step_action_prompt.txt");
        return builder(ApiTestStepActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(requestTools, assertionTools, dataTools), VerificationExecutionResult.class))
                .toolExecutionErrorHandler(new DefaultToolErrorHandler(config.getActionRetryPolicy()))
                .maxToolCallingRoundTrips(config.getAgentToolCallsBudget())
                .build();
    }

    @Bean
    ApiPreconditionActionAgent preconditionAgent(ApiRequestTools requestTools, ApiAssertionTools assertionTools,
                                                             TestContextDataTools dataTools) {
        var model = modelFactory.getModel(config.getPreconditionActionAgentModelName(), config.getPreconditionActionAgentModelProvider());
        var prompt = loadSystemPrompt("precondition/executor", config.getPreconditionAgentPromptVersion(),
                "precondition_execution_prompt.txt");
        return builder(ApiPreconditionActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(requestTools, assertionTools, dataTools), VerificationExecutionResult.class))
                .toolExecutionErrorHandler(new DefaultToolErrorHandler(config.getActionRetryPolicy()))
                .maxToolCallingRoundTrips(config.getAgentToolCallsBudget())
                .build();
    }
}
