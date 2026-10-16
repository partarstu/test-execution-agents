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

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.schema.SchemaMigrationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class Neo4jMigrationStateTest {
    private static final String DATABASE = "neo4j";

    private final Driver driver = mock(Driver.class);
    private final Neo4jMigrationState migrationState = new Neo4jMigrationState(driver, DATABASE);

    @Test
    void ensureSchemaReady_shouldNotMigrate_whenStartupMigrationSucceeded() {
        migrationState.markSucceeded();

        try (var migrations = mockStatic(SchemaMigrationManager.class)) {
            migrationState.ensureSchemaReady();

            migrations.verify(() -> SchemaMigrationManager.migrateOnStartup(any(), anyString()), never());
        }
    }

    @Test
    void ensureSchemaReady_shouldMigrateOnceAndMarkSucceeded_whenStartupMigrationFailed() {
        try (var migrations = mockStatic(SchemaMigrationManager.class)) {
            migrationState.ensureSchemaReady();
            migrationState.ensureSchemaReady();

            migrations.verify(() -> SchemaMigrationManager.migrateOnStartup(driver, DATABASE));
            assertThat(migrationState.isSucceeded()).isTrue();
        }
    }

    @Test
    void ensureSchemaReady_shouldThrowDatabaseConnectionException_whenMigrationFails() {
        try (var migrations = mockStatic(SchemaMigrationManager.class)) {
            migrations.when(() -> SchemaMigrationManager.migrateOnStartup(driver, DATABASE)).thenThrow(new IllegalStateException("down"));

            assertThatThrownBy(migrationState::ensureSchemaReady)
                    .isInstanceOf(DatabaseConnectionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(migrationState.isSucceeded()).isFalse();
        }
    }
}
