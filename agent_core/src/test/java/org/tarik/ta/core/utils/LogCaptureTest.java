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
package org.tarik.ta.core.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogCaptureTest {

    private LogCapture logCapture;
    private static final Logger LOG = LoggerFactory.getLogger(LogCaptureTest.class);

    @BeforeEach
    void setUp() {
        logCapture = new LogCapture();
    }

    @AfterEach
    void tearDown() {
        logCapture.stop();
    }

    @Test
    void getLogs_shouldReturnEmptyListWhenNotStarted() {
        assertThat(logCapture.getLogs()).isEmpty();
    }

    @Test
    void capture_shouldCaptureLogs() {
        logCapture.start();
        LOG.info("Test log message");
        LOG.error("Error message with {}", "param");

        List<String> logs = logCapture.getLogs();
        assertThat(logs).hasSizeGreaterThanOrEqualTo(2);
        assertThat(logs.stream().anyMatch(l -> l.contains("INFO") && l.contains("Test log message"))).isTrue();
        assertThat(logs.stream().anyMatch(l -> l.contains("ERROR") && l.contains("Error message with param"))).isTrue();
    }

    @Test
    void stop_shouldStopCapturing() {
        logCapture.start();
        LOG.info("First message");
        logCapture.stop();
        LOG.info("Second message");

        List<String> logs = logCapture.getLogs();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).contains("First message");
        assertThat(logs.get(0)).doesNotContain("Second message");
    }

    @Test
    void formatLogEvent_shouldHandleLoggerName() {
        logCapture.start();
        LOG.info("Message");
        List<String> logs = logCapture.getLogs();
        
        // Short logger name for LogCaptureTest should be "LogCaptureTest"
        assertThat(logs.get(0)).contains("LogCaptureTest");
    }
}
