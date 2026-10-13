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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.exceptions.DatabaseConnectionException;

/**
 * Runs the knowledge graph maintenance done at startup so that an unreachable Neo4j skips it instead of stopping the agent,
 * which then starts and connects on its first database operation.
 */
final class StartupDatabaseTasks {
    private static final Logger LOG = LoggerFactory.getLogger(StartupDatabaseTasks.class);

    private StartupDatabaseTasks() {
    }

    static void runUnlessDatabaseUnreachable(@NotNull String taskName, @NotNull Runnable task) {
        try {
            task.run();
        } catch (DatabaseConnectionException e) {
            LOG.warn("Neo4j is not reachable at startup, so the {} is skipped and runs again on the next start: {}", taskName,
                    e.getMessage());
        }
    }
}
