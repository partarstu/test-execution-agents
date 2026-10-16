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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.knowledge_graph.model.node.SchemaVersion.CURRENT;

class SchemaVersionGuardTest {
    private final Driver driver = mock(Driver.class);
    private final ExecutableQuery query = mock(ExecutableQuery.class, RETURNS_SELF);
    private final EagerResult result = mock(EagerResult.class);

    @Test
    void ensureSchemaReady_shouldPassAndReadTheVersionOnce_whenTheGraphHasTheRequiredVersion() {
        givenStoredVersion(List.of(CURRENT));
        var guard = new SchemaVersionGuard(driver, "neo4j");

        guard.ensureSchemaReady();
        guard.ensureSchemaReady();

        verify(driver, times(1)).executableQuery(anyString());
    }

    @Test
    void ensureSchemaReady_shouldRefuseTheGraph_whenItsVersionDiffers() {
        givenStoredVersion(List.of(CURRENT - 1));
        var guard = new SchemaVersionGuard(driver, "neo4j");

        assertThatThrownBy(guard::ensureSchemaReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version %d".formatted(CURRENT - 1))
                .hasMessageContaining("reads version %d".formatted(CURRENT));
    }

    @Test
    void ensureSchemaReady_shouldRefuseTheGraph_whenItHasNoSchemaVersion() {
        givenStoredVersion(List.of());
        var guard = new SchemaVersionGuard(driver, "neo4j");

        assertThatThrownBy(guard::ensureSchemaReady).isInstanceOf(IllegalStateException.class).hasMessageContaining("version 0");
    }

    private void givenStoredVersion(List<Integer> versions) {
        var records = versions.stream().map(version -> {
            var record = mock(Record.class);
            when(record.get("version")).thenReturn(Values.value(version));
            return record;
        }).toList();
        when(driver.executableQuery(anyString())).thenReturn(query);
        when(query.execute()).thenReturn(result);
        when(result.records()).thenReturn(records);
    }
}
