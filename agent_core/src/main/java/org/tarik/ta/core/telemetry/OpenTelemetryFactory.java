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

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Builds the {@link OpenTelemetry} instance the agent records its metrics with, configured through the standard {@code OTEL_*}
 * environment variables. All exporters default to {@code none}, so nothing is exported until {@code OTEL_METRICS_EXPORTER=otlp}
 * and {@code OTEL_EXPORTER_OTLP_ENDPOINT} are set.
 */
@Factory
public class OpenTelemetryFactory {
    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryFactory.class);
    private static final Map<String, String> EXPORTERS_OFF_BY_DEFAULT = Map.of(
            "otel.metrics.exporter", "none",
            "otel.traces.exporter", "none",
            "otel.logs.exporter", "none",
            "otel.service.name", "test-execution-agent");

    @Bean(destroyMethod = "close")
    @Singleton
    public OpenTelemetrySdk openTelemetrySdk() {
        var sdk = AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier(() -> EXPORTERS_OFF_BY_DEFAULT)
                .build()
                .getOpenTelemetrySdk();
        LOG.info("OpenTelemetry SDK initialised. Metrics exporter: {}", System.getenv().getOrDefault("OTEL_METRICS_EXPORTER", "none"));
        return sdk;
    }
}
