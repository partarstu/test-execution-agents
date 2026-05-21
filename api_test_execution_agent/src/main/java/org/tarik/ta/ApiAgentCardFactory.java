/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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

import io.a2a.spec.AgentCard;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Singleton;
import org.tarik.ta.a2a.AgentCardProducer;
import org.tarik.ta.core.AgentConfig;

@Factory
public class ApiAgentCardFactory {

    private final AgentConfig agentConfig;

    public ApiAgentCardFactory(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    @Bean
    @Singleton
    AgentCard agentCard() {
        return new AgentCardProducer().agentCard(agentConfig.getExternalUrl());
    }
}