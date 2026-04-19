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
package org.tarik.ta.knowledge_graph.service;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Singleton;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;

import java.util.Optional;

@Factory
public class KnowledgeServicesBeanFactory {
    private final ProcedureRepository procedureRepository;
    private final UiTestAgentConfig config;

    public KnowledgeServicesBeanFactory(ProcedureRepository procedureRepository, UiTestAgentConfig config) {
        this.procedureRepository = procedureRepository;
        this.config = config;
    }

    @Bean
    @Singleton
    public LocationHistoryRecorder locationHistoryRecorder(AsyncExecutionPersistenceService asyncExecutionPersistenceService) {
        if (!config.isLocationHistoryAndFailureHintsCollectionEnabled()) {
            return (elementId, located, locationTimeMs) -> {
            };
        }
        return asyncExecutionPersistenceService::updateElementStability;
    }

    @Bean
    @Singleton
    public ElementLocationHistoryLookup elementStabilityLookup() {
        return procedureRepository::getElementLocationHistory;
    }
}