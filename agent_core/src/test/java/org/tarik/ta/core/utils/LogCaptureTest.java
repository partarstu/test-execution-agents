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
import org.mockito.InOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.a2a.StreamingEventEmitter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LogCaptureTest {

    private LogCapture logCapture;
    private static final Logger LOG = LoggerFactory.getLogger(LogCaptureTest.class);

    @BeforeEach
    void setUp() {
        logCapture = new LogCapture(StreamingEventEmitter.NOOP);
    }

    @AfterEach
    void tearDown() {
        logCapture.stop();
    }

    @Test
    void capture_shouldStreamEachLogLineLiveInOrderAndFlushOnStop() {
        StreamingEventEmitter eventEmitter = mock(StreamingEventEmitter.class);
        LogCapture streamingCapture = new LogCapture(eventEmitter);
        streamingCapture.start();
        try {
            LOG.info("Streamed log message 1");
            LOG.info("Streamed log message 2");
            LOG.info("Streamed log message 3");
        } finally {
            streamingCapture.stop();
        }

        // stop() drains the queue before returning, so by now every line must have been emitted, in logging order.
        InOrder inOrder = inOrder(eventEmitter);
        inOrder.verify(eventEmitter).emitLog(contains("Streamed log message 1"));
        inOrder.verify(eventEmitter).emitLog(contains("Streamed log message 2"));
        inOrder.verify(eventEmitter).emitLog(contains("Streamed log message 3"));
    }

    @Test
    void capture_shouldNotCaptureOrStreamLogsOutsideAppLogger() {
        StreamingEventEmitter eventEmitter = mock(StreamingEventEmitter.class);
        LogCapture appLoggerCapture = new LogCapture(eventEmitter);
        appLoggerCapture.start();
        Logger externalLogger = LoggerFactory.getLogger("com.external.ThirdPartyLibrary");
        try {
            externalLogger.info("Sensitive third-party message");
        } finally {
            appLoggerCapture.stop();
        }

        assertThat(appLoggerCapture.getLogs()).noneMatch(line -> line.contains("Sensitive third-party message"));
        verify(eventEmitter, never()).emitLog(contains("Sensitive third-party message"));
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
    void getLogs_shouldNotThrowWhenAppendedConcurrently() throws InterruptedException {
        logCapture.start();
        var stop = new AtomicBoolean(false);
        var failure = new AtomicReference<Throwable>();

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 10_000 && !stop.get(); i++) {
                LOG.info("Concurrent message {}", i);
            }
        });
        Thread reader = new Thread(() -> {
            try {
                while (!stop.get()) {
                    logCapture.getLogs();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        reader.start();
        writer.start();
        writer.join();
        stop.set(true);
        reader.join();

        assertThat(failure.get()).isNull();
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
