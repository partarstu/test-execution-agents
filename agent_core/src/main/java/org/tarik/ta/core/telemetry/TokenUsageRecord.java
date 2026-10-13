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
package org.tarik.ta.core.telemetry;

import io.opentelemetry.api.common.Attributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.tarik.ta.core.telemetry.GenAiAttributes.OPERATION_NAME;
import static org.tarik.ta.core.telemetry.GenAiAttributes.PROVIDER_NAME;
import static org.tarik.ta.core.telemetry.GenAiAttributes.REQUEST_MODEL;
import static org.tarik.ta.core.telemetry.GenAiAttributes.RESPONSE_MODEL;
import static org.tarik.ta.core.telemetry.GenAiAttributes.SERVER_ADDRESS;

/**
 * What one model call - or, once accumulated, all calls of one sub-agent operation - cost. The same record feeds the
 * per-call debug log line, the metric attributes and the {@code token_usage} artifact of the finished task, so all
 * three describe the usage with the same names.
 */
public record TokenUsageRecord(@NotNull String operationName, @NotNull String providerName, @NotNull String requestModel,
                               @NotNull String responseModel, @Nullable String serverAddress, int inputTokens,
                               int outputTokens, int cachedTokens, long durationMillis) {

    public int totalTokens() {
        return inputTokens + outputTokens + cachedTokens;
    }

    /**
     * The attributes shared by both instruments, without the task id, whose unbounded values would explode the metric
     * cardinality.
     */
    public Attributes toAttributes() {
        var builder = Attributes.builder()
                .put(OPERATION_NAME, operationName)
                .put(PROVIDER_NAME, providerName)
                .put(REQUEST_MODEL, requestModel)
                .put(RESPONSE_MODEL, responseModel);
        if (serverAddress != null && !serverAddress.isBlank()) {
            builder.put(SERVER_ADDRESS, serverAddress);
        }
        return builder.build();
    }

    /** Adds the counts of another call of the same operation, keeping the receiver's operation, provider and model attributes. */
    public TokenUsageRecord plus(@NotNull TokenUsageRecord other) {
        return new TokenUsageRecord(operationName, providerName, requestModel, responseModel, serverAddress,
                inputTokens + other.inputTokens, outputTokens + other.outputTokens, cachedTokens + other.cachedTokens,
                durationMillis + other.durationMillis);
    }

    public String toLogLine() {
        return ("operation = %s, provider = %s, model = %s, input tokens = %d, output tokens = %d, cached tokens = %d, " +
                "total tokens = %d, duration = %d ms")
                .formatted(operationName, providerName, responseModel, inputTokens, outputTokens, cachedTokens,
                        totalTokens(), durationMillis);
    }
}
