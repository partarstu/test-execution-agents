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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.avaje.inject.External;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScope;
import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

@BaseAgentRequestScope
public class LogCapture {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
    // Scope capture to the application's own logger so framework/third-party log lines (which may carry credentials or
    // request/response bodies) never enter the client-facing SSE stream.
    private static final String APP_LOGGER_NAME = "org.tarik.ta";

    private final Logger appLogger;
    private final StreamingEventEmitter eventEmitter;
    private StreamingAppender appender;

    public LogCapture(@External @NotNull StreamingEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
        this.appLogger = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);
    }

    public void start() {
        appender = new StreamingAppender();
        appender.setContext(appLogger.getLoggerContext());
        appender.setName("log-capture-streaming");
        appender.start();
        appLogger.addAppender(appender);
    }

    @PreDestroy
    public void stop() {
        if (appender != null) {
            appLogger.detachAppender(appender);
            appender.stop();
        }
    }

    public List<String> getLogs() {
        if (appender == null) {
            return new ArrayList<>();
        }
        return appender.snapshot().stream()
                .map(this::formatLogEvent)
                .collect(Collectors.toList());
    }

    private String formatLogEvent(ILoggingEvent event) {
        String timestamp = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp()));
        String level = event.getLevel().toString();
        String loggerName = getShortLoggerName(event.getLoggerName());
        return "%s %-5s %s - %s".formatted(timestamp, level, loggerName, event.getFormattedMessage());
    }

    private String getShortLoggerName(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            return "";
        }
        int lastDotIndex = loggerName.lastIndexOf('.');
        return lastDotIndex >= 0 ? loggerName.substring(lastDotIndex + 1) : loggerName;
    }

    /**
     * Captures every log event for the final report and streams each formatted line live through the
     * {@link StreamingEventEmitter}. To keep logging fast, {@code append} only records the event and hands the
     * pre-formatted line to a queue (mirroring logback's {@code AsyncAppender}); a single dedicated virtual thread
     * drains the queue and performs the comparatively expensive {@code emitLog} I/O off the logging threads.
     * Streaming a log line may itself produce log events on the drain thread, so a re-entrancy guard keeps those events
     * captured for the report while preventing them from being re-enqueued and streamed in an infinite loop.
     */
    private final class StreamingAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
        private static final int QUEUE_CAPACITY = 10_000;
        private final List<ILoggingEvent> capturedEvents = Collections.synchronizedList(new ArrayList<>());
        private final BlockingQueue<String> pendingLines = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        private final ThreadLocal<Boolean> forwarding = ThreadLocal.withInitial(() -> false);
        private boolean overflowWarned = false;
        private Thread drainThread;

        @Override
        public void start() {
            drainThread = Thread.ofVirtual().name("log-capture-drain").start(this::drainLoop);
            super.start();
        }

        @Override
        public void stop() {
            if (!isStarted()) {
                return;
            }
            super.stop();
            if (drainThread != null) {
                drainThread.interrupt();
                try {
                    drainThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        protected void append(ILoggingEvent event) {
            capturedEvents.add(event);
            if (forwarding.get()) {
                // Event produced by our own emitLog on the drain thread: keep it for the report but don't re-stream it.
                return;
            }
            if (!pendingLines.offer(formatLogEvent(event)) && !overflowWarned) {
                overflowWarned = true;
                addWarn("Log streaming queue reached its capacity of %d; live log lines will be dropped.".formatted(QUEUE_CAPACITY));
            }
        }

        private void drainLoop() {
            boolean interrupted = false;
            try {
                while (true) {
                    emit(pendingLines.take());
                }
            } catch (InterruptedException e) {
                interrupted = true;
            } finally {
                // Drain leftover lines with a clean interrupt status: emitLog performs interruptible blocking I/O
                // (MainEventBus.submit), which would otherwise fail immediately on a still-set interrupt flag. The
                // interrupt is re-asserted afterwards so the thread terminates as expected.
                List<String> remaining = new ArrayList<>();
                pendingLines.drainTo(remaining);
                remaining.forEach(this::emit);
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void emit(String logLine) {
            forwarding.set(true);
            try {
                eventEmitter.emitLog(logLine);
            } finally {
                forwarding.set(false);
            }
        }

        /**
         * Returns a defensive copy of the captured events taken under the synchronized list's own monitor, so readers
         * never iterate the list while another thread appends to it.
         */
        List<ILoggingEvent> snapshot() {
            synchronized (capturedEvents) {
                return new ArrayList<>(capturedEvents);
            }
        }
    }
}
