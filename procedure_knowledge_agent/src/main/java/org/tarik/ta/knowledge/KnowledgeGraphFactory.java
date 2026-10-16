/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
package org.tarik.ta.knowledge;

import io.avaje.inject.Bean;
import io.avaje.inject.Component;
import io.avaje.inject.Factory;
import org.neo4j.driver.Driver;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.knowledge_graph.GraphSchemaGuard;
import org.tarik.ta.knowledge_graph.Neo4jDrivers;
import org.tarik.ta.knowledge_graph.repository.Neo4jRepositorySupport;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;

/**
 * Wires the read-only knowledge graph access of the knowledge-graph-core module into this agent.
 */
@Factory
@Component.Import({Neo4jRepositorySupport.class, ProcedureRepository.class, EmbeddingService.class})
class KnowledgeGraphFactory {

    @Bean(destroyMethod = "close")
    Driver driver(KnowledgeAgentConfig config) {
        return Neo4jDrivers.create(config);
    }

    // Verifying here makes the agent refuse to start on a graph whose schema version it cannot read.
    @Bean
    GraphSchemaGuard schemaGuard(Driver driver, KnowledgeAgentConfig config) {
        var guard = new SchemaVersionGuard(driver, config.getNeo4jDatabase());
        guard.ensureSchemaReady();
        return guard;
    }
}
