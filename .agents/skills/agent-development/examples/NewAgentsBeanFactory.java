/*
 * Test Execution Agent Parent - Parent build/dependency management for the Test Execution Agents system.
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
