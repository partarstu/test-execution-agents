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

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.ExecutableQuery;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.tarik.ta.KnowledgeAgentConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tarik.ta.knowledge_graph.model.node.SchemaVersion.CURRENT;

class KnowledgeGraphFactoryTest {
    private final KnowledgeGraphFactory factory = new KnowledgeGraphFactory();
    private final KnowledgeAgentConfig config = mock(KnowledgeAgentConfig.class);

    @Test
    void driver_shouldCreateTheDriverFromTheConfiguration() {
        when(config.getVectorDbUrl()).thenReturn("bolt://localhost:7687");
        when(config.getNeo4jUsername()).thenReturn("neo4j");
        when(config.getVectorDbToken()).thenReturn("secret");
        when(config.getNeo4jMaxConnectionPoolSize()).thenReturn(5);
        when(config.getNeo4jConnectionAcquisitionTimeoutSeconds()).thenReturn(5);
        when(config.getNeo4jConnectionTimeoutSeconds()).thenReturn(5);
        when(config.getNeo4jMaxTransactionRetryTimeSeconds()).thenReturn(5);

        try (var driver = factory.driver(config)) {
            assertThat(driver).isNotNull();
        }
    }

    @Test
    void schemaGuard_shouldVerifyTheSchemaVersionWhenItIsCreated() {
        var driver = givenDriverStoringVersion(CURRENT);
        when(config.getNeo4jDatabase()).thenReturn("neo4j");

        assertThat(factory.schemaGuard(driver, config)).isInstanceOf(SchemaVersionGuard.class);
    }

    @Test
    void schemaGuard_shouldRefuseToCreateTheGuard_whenTheSchemaVersionDiffers() {
        var driver = givenDriverStoringVersion(CURRENT + 1);
        when(config.getNeo4jDatabase()).thenReturn("neo4j");

        assertThatThrownBy(() -> factory.schemaGuard(driver, config)).isInstanceOf(IllegalStateException.class);
    }

    private static Driver givenDriverStoringVersion(int version) {
        var driver = mock(Driver.class);
        var query = mock(ExecutableQuery.class, RETURNS_SELF);
        var result = mock(EagerResult.class);
        var record = mock(Record.class);
        when(record.get("version")).thenReturn(Values.value(version));
        when(result.records()).thenReturn(List.of(record));
        when(query.execute()).thenReturn(result);
        when(driver.executableQuery(anyString())).thenReturn(query);
        return driver;
    }
}
