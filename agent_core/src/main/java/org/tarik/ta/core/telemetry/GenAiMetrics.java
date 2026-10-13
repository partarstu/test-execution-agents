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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.tarik.ta.core.telemetry.GenAiAttributes.TOKEN_TYPE;
import static org.tarik.ta.core.telemetry.GenAiAttributes.TOKEN_TYPE_CACHE_READ;
import static org.tarik.ta.core.telemetry.GenAiAttributes.TOKEN_TYPE_INPUT;
import static org.tarik.ta.core.telemetry.GenAiAttributes.TOKEN_TYPE_OUTPUT;

/**
 * The GenAI client metrics of the agent: how many tokens each sub-agent operation spends and how long its model calls
 * take. Both instruments follow the OpenTelemetry GenAI semantic conventions, so a collector can aggregate them
 * together with those of other services.
 */
@Singleton
public class GenAiMetrics {
    private static final String METER_NAME = "org.tarik.ta";

    /**
     * The bucket boundaries the GenAI semantic conventions prescribe for token counts: powers of four from 1 to 2^26,
     * which keeps the resolution useful across prompts that differ by orders of magnitude.
     */
    private static final List<Long> TOKEN_BUCKET_BOUNDARIES =
            List.of(1L, 4L, 16L, 64L, 256L, 1024L, 4096L, 16384L, 65536L, 262144L, 1048576L, 4194304L, 16777216L, 67108864L);

    private final LongHistogram tokenUsage;
    private final DoubleHistogram operationDuration;

    @Inject
    public GenAiMetrics(OpenTelemetry openTelemetry) {
        var meter = openTelemetry.getMeter(METER_NAME);
        this.tokenUsage = meter.histogramBuilder("gen_ai.client.token.usage")
                .setDescription("Number of tokens used by a single model call.")
                .setUnit("{token}")
                .ofLongs()
                .setExplicitBucketBoundariesAdvice(TOKEN_BUCKET_BOUNDARIES)
                .build();
        this.operationDuration = meter.histogramBuilder("gen_ai.client.operation.duration")
                .setDescription("Duration of a single model call.")
                .setUnit("s")
                .build();
    }

    /**
     * Records the token counts of one model call, skipping zero counts so that a provider reporting no cached tokens produces no
     * {@code cache_read} data point.
     */
    public void recordTokenUsage(@NotNull Attributes callAttributes, int inputTokens, int outputTokens, int cachedTokens) {
        recordTokens(callAttributes, TOKEN_TYPE_INPUT, inputTokens);
        recordTokens(callAttributes, TOKEN_TYPE_OUTPUT, outputTokens);
        recordTokens(callAttributes, TOKEN_TYPE_CACHE_READ, cachedTokens);
    }

    /**
     * Records the round-trip duration of one model call, in seconds as the convention requires.
     */
    public void recordOperationDuration(@NotNull Attributes callAttributes, long durationMillis) {
        operationDuration.record(durationMillis / 1000.0, callAttributes);
    }

    private void recordTokens(Attributes callAttributes, String tokenType, int count) {
        if (count > 0) {
            tokenUsage.record(count, callAttributes.toBuilder().put(TOKEN_TYPE, tokenType).build());
        }
    }
}
