/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.a2a;

import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.TextPart;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.telemetry.TokenUsageRecord;

import java.util.List;

import static org.tarik.ta.core.a2a.AbstractAgentExecutor.OBJECT_MAPPER;

/**
 * Reports the token usage of a finished task to its caller as the {@code token_usage} artifact, one entry per sub-agent operation.
 */
public final class TaskTokenUsage {
    private static final Logger LOG = LoggerFactory.getLogger(TaskTokenUsage.class);
    private static final String TOKEN_USAGE_ARTIFACT = "token_usage";

    private TaskTokenUsage() {
    }

    /**
     * Emits the usage before the task's terminal event; a failure is only logged, because a missing report must not fail the task.
     */
    public static void emit(@NotNull AgentEmitter emitter, @NotNull String taskId, @NotNull List<TokenUsageRecord> usage) {
        if (usage.isEmpty()) {
            return;
        }
        try {
            usage.forEach(record -> LOG.info("Token usage of task {}: {}", taskId, record.toLogLine()));
            emitter.addArtifact(List.of(new TextPart(OBJECT_MAPPER.writeValueAsString(usage))), null, TOKEN_USAGE_ARTIFACT, null);
        } catch (Exception e) {
            LOG.error("Failed to report the token usage of task {}.", taskId, e);
        }
    }
}
