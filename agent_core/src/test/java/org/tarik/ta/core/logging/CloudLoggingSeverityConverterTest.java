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
package org.tarik.ta.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class CloudLoggingSeverityConverterTest {

    private final CloudLoggingSeverityConverter converter = new CloudLoggingSeverityConverter();

    @Test
    void convert_shouldWriteWarnAsTheWarningSeverity() {
        assertThat(converter.convert(loggingEvent(Level.WARN))).isEqualTo("WARNING");
    }

    @Test
    void convert_shouldKeepTheNamesCloudLoggingShares() {
        assertThat(converter.convert(loggingEvent(Level.ERROR))).isEqualTo("ERROR");
        assertThat(converter.convert(loggingEvent(Level.INFO))).isEqualTo("INFO");
        assertThat(converter.convert(loggingEvent(Level.DEBUG))).isEqualTo("DEBUG");
    }

    @Test
    void convert_shouldWriteTraceAsTheDebugSeverity() {
        assertThat(converter.convert(loggingEvent(Level.TRACE))).isEqualTo("DEBUG");
    }

    private static LoggingEvent loggingEvent(Level level) {
        var event = new LoggingEvent();
        event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());
        event.setLevel(level);
        event.setMessage("message");
        return event;
    }
}
