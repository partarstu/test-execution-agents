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
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.knowledge_graph.location_history.ElementLocationHistoryLookup;
import org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;


@UiAgentRequestScope
@Factory
public class KnowledgeServicesBeanFactory {
    private final ProcedureRepository procedureRepository;

    public KnowledgeServicesBeanFactory(ProcedureRepository procedureRepository) {
        this.procedureRepository = procedureRepository;
    }

    @Bean
    public LocationHistoryRecorder locationHistoryRecorder(AsyncExecutionPersistenceService asyncExecutionPersistenceService) {
        return asyncExecutionPersistenceService::updateElementStability;
    }

    @Bean
    public ElementLocationHistoryLookup elementStabilityLookup() {
        return procedureRepository::getElementLocationHistory;
    }
}