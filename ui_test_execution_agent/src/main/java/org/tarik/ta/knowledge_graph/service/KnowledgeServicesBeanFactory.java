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