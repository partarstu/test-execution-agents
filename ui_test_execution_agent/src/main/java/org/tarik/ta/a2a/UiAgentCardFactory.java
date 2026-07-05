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
package org.tarik.ta.a2a;

import org.a2aproject.sdk.spec.AgentCard;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Singleton;
import org.tarik.ta.UiTestAgentConfig;

@Factory
public class UiAgentCardFactory {

    private final UiTestAgentConfig agentConfig;

    public UiAgentCardFactory(UiTestAgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    @Bean
    @Singleton
    public AgentCard agentCard() {
        return new AgentCardProducer(agentConfig.getExternalUrl(), agentConfig).agentCard();
    }
}
