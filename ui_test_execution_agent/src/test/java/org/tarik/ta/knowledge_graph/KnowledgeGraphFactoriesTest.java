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
package org.tarik.ta.knowledge_graph;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeGraphFactoriesTest {

    @Test
    void embeddingModel_shouldExposeTheDocumentModelOfTheEmbeddingService() {
        var embeddingService = mock(EmbeddingService.class);
        var model = mock(EmbeddingModel.class);
        when(embeddingService.getModel()).thenReturn(model);

        assertThat(new KnowledgeGraphCoreFactory().embeddingModel(embeddingService)).isSameAs(model);
    }

    @Test
    void neo4jFactory_shouldCreateTheDriverAndAMigrationStateNotYetSucceeded() {
        var config = mock(UiTestAgentConfig.class);
        when(config.getNeo4jDatabase()).thenReturn("neo4j");
        when(config.getVectorDbUrl()).thenReturn("bolt://localhost:7687");
        when(config.getNeo4jUsername()).thenReturn("neo4j");
        when(config.getVectorDbToken()).thenReturn("secret");
        when(config.getNeo4jMaxConnectionPoolSize()).thenReturn(5);
        when(config.getNeo4jConnectionAcquisitionTimeoutSeconds()).thenReturn(5);
        when(config.getNeo4jConnectionTimeoutSeconds()).thenReturn(5);
        when(config.getNeo4jMaxTransactionRetryTimeSeconds()).thenReturn(5);

        try (var factory = new Neo4jFactory(config)) {
            Driver driver = factory.driver();

            assertThat(driver).isNotNull();
            assertThat(factory.migrationState(driver).isSucceeded()).isFalse();
        }
    }
}
