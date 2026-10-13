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

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.tarik.ta.core.telemetry.GenAiAttributes.TOKEN_TYPE;

class GenAiMetricsTest {

    private InMemoryMetricReader metricReader;
    private SdkMeterProvider meterProvider;
    private GenAiMetrics metrics;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        meterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build();
        metrics = new GenAiMetrics(OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build());
    }

    @AfterEach
    void tearDown() {
        meterProvider.close();
    }

    private static TokenUsageRecord usage(String operationName, int inputTokens, int outputTokens, int cachedTokens) {
        return new TokenUsageRecord(operationName, "openai", "gpt-test", "gpt-test-0815", "api.example.org", inputTokens,
                outputTokens, cachedTokens, 1500);
    }

    @Test
    void recordTokenUsage_shouldProduceOneDataPointPerTokenType() {
        var record = usage("test_step_action", 10, 20, 5);

        metrics.recordTokenUsage(record.toAttributes(), record.inputTokens(), record.outputTokens(), record.cachedTokens());

        var dataPoints = metricReader.collectAllMetrics().stream()
                .filter(metric -> metric.getName().equals("gen_ai.client.token.usage"))
                .flatMap(metric -> metric.getHistogramData().getPoints().stream())
                .toList();
        assertThat(dataPoints).hasSize(3);
        assertThat(dataPoints).allSatisfy(point -> {
            assertThat(point.getAttributes().get(GenAiAttributes.OPERATION_NAME)).isEqualTo("test_step_action");
            assertThat(point.getAttributes().get(GenAiAttributes.PROVIDER_NAME)).isEqualTo("openai");
            assertThat(point.getAttributes().get(GenAiAttributes.RESPONSE_MODEL)).isEqualTo("gpt-test-0815");
            assertThat(point.getAttributes().get(GenAiAttributes.SERVER_ADDRESS)).isEqualTo("api.example.org");
        });
        assertThat(dataPoints).extracting(point -> point.getAttributes().get(TOKEN_TYPE), point -> (int) point.getSum())
                .containsExactlyInAnyOrder(tuple("input", 10),
                        tuple("output", 20),
                        tuple("cache_read", 5));
    }

    @Test
    void recordTokenUsage_shouldRecordNoDataPointForATokenTypeWhichTheProviderDoesNotReport() {
        metrics.recordTokenUsage(usage("test_step_action", 10, 20, 0).toAttributes(), 10, 20, 0);

        var tokenTypes = metricReader.collectAllMetrics().stream()
                .filter(metric -> metric.getName().equals("gen_ai.client.token.usage"))
                .flatMap(metric -> metric.getHistogramData().getPoints().stream())
                .map(point -> point.getAttributes().get(TOKEN_TYPE))
                .toList();
        assertThat(tokenTypes).containsExactlyInAnyOrder("input", "output");
    }

    @Test
    void recordOperationDuration_shouldRecordTheDurationInSeconds() {
        metrics.recordOperationDuration(usage("precondition_action", 1, 1, 0).toAttributes(), 1500);

        var points = metricReader.collectAllMetrics().stream()
                .filter(metric -> metric.getName().equals("gen_ai.client.operation.duration"))
                .flatMap(metric -> metric.getHistogramData().getPoints().stream())
                .toList();
        assertThat(points).singleElement().satisfies(point -> {
            assertThat(point.getSum()).isEqualTo(1.5, within(0.001));
            assertThat(point.getAttributes().get(GenAiAttributes.OPERATION_NAME)).isEqualTo("precondition_action");
        });
    }
}
