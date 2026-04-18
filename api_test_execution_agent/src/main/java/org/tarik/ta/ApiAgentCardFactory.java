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