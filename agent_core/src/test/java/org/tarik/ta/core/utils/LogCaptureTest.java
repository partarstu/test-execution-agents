/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
