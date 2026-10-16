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

import org.neo4j.driver.Driver;
import org.tarik.ta.exceptions.DatabaseConnectionException;
import org.tarik.ta.knowledge_graph.schema.SchemaMigrationManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks whether the schema migration succeeded and retries it before a query when it failed at startup.
 */
public class Neo4jMigrationState implements GraphSchemaGuard {
    private final AtomicBoolean succeeded = new AtomicBoolean(false);
    private final ReentrantLock migrationRetryLock = new ReentrantLock();
    private final Driver driver;
    private final String databaseName;

    public Neo4jMigrationState(Driver driver, String databaseName) {
        this.driver = driver;
        this.databaseName = databaseName;
    }

    public boolean isSucceeded() {
        return succeeded.get();
    }

    public void markSucceeded() {
        succeeded.set(true);
    }

    /** Ensures the schema migration has completed, retrying it when it failed at startup; a failure here aborts the current request. */
    @Override
    public void ensureSchemaReady() {
        if (isSucceeded()) {
            return;
        }
        migrationRetryLock.lock();
        try {
            // Double-check after acquiring the lock to avoid duplicate migration attempts
            if (isSucceeded()) {
                return;
            }
            try {
                SchemaMigrationManager.migrateOnStartup(driver, databaseName);
                markSucceeded();
            } catch (Exception e) {
                throw new DatabaseConnectionException("Schema migration failed during request — cannot proceed without DB", e);
            }
        } finally {
            migrationRetryLock.unlock();
        }
    }
}
