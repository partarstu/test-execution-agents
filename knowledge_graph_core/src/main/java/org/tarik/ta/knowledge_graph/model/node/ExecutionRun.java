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
package org.tarik.ta.knowledge_graph.model.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.ExecutionMode;

import java.time.Instant;
import java.util.UUID;

/** One execution of a test case, kept with its step resolutions for the newest {@code run.history.size} runs. */
public record ExecutionRun(@NotNull UUID id, @NotNull String testCaseName, @NotNull Instant startedAt, @Nullable Instant finishedAt,
                           @NotNull ExecutionMode mode, @Nullable Status status) {
    public static final String LABEL = "ExecutionRun";
    public static final String PROP_STARTED_AT = "startedAt";
    public static final String PROP_FINISHED_AT = "finishedAt";
    public static final String PROP_STATUS = "status";

    public enum Status { PASSED, FAILED, ERROR, ABORTED }
}
