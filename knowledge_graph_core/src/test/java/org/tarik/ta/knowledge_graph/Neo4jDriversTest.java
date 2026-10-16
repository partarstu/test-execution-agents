/*
 * knowledge-graph-core - Knowledge graph model, repositories and embeddings shared by the agents that use the knowledge graph.
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Neo4jDriversTest {

    @Test
    void create_shouldRejectTheConfiguration_whenPasswordIsMissing() {
        assertPasswordRejected(null);
    }

    @Test
    void create_shouldRejectTheConfiguration_whenPasswordIsBlank() {
        assertPasswordRejected("  ");
    }

    @Test
    void create_shouldCreateTheDriverWithoutConnecting_whenTheConfigurationIsComplete() {
        var config = mock(KnowledgeGraphConfig.class);
        when(config.getVectorDbUrl()).thenReturn("bolt://localhost:7687");
        when(config.getNeo4jUsername()).thenReturn("neo4j");
        when(config.getVectorDbToken()).thenReturn("secret");
        when(config.getNeo4jMaxConnectionPoolSize()).thenReturn(5);
        when(config.getNeo4jConnectionAcquisitionTimeoutSeconds()).thenReturn(5);
        when(config.getNeo4jConnectionTimeoutSeconds()).thenReturn(5);
        when(config.getNeo4jMaxTransactionRetryTimeSeconds()).thenReturn(5);

        try (var driver = Neo4jDrivers.create(config)) {
            assertThat(driver).isNotNull();
        }
    }

    private static void assertPasswordRejected(String password) {
        var config = mock(KnowledgeGraphConfig.class);
        when(config.getVectorDbUrl()).thenReturn("bolt://localhost:7687");
        when(config.getNeo4jUsername()).thenReturn("neo4j");
        when(config.getVectorDbToken()).thenReturn(password);

        assertThatThrownBy(() -> Neo4jDrivers.create(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VECTOR_DB_KEY");
    }
}
