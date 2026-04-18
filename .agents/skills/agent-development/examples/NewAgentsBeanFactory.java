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
import org.tarik.ta.agents.NewActionAgent;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.dto.NewActionResult;
import org.tarik.ta.tools.NewAgentTools;

import java.util.List;

import static dev.langchain4j.service.AiServices.builder;

@Factory
public class NewAgentsBeanFactory {

    @Bean
    @Singleton
    public NewActionAgent createNewActionAgent(ModelFactory modelFactory, NewTestAgentConfig config, NewAgentTools tools) {
        var model = modelFactory.getModel(config.getModelName(), config.getModelProvider());
        return builder(NewActionAgent.class)
                .chatModel(model.chatModel())
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(tools), NewActionResult.class))
                .build();
    }
}
